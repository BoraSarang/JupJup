# PLAN v10 — 프롬프트 저널 모델 관리 수정 + Zen 추가 + NIM 삭제 (R23)

> 날짜: 2026-09-19 · 플랫폼: Android (프롬프트 저널) · 상태: 완료
> 동기: 편집폼에 저장값과 다른 모델이 표시됨 (DB 진실 vs 카탈로그 불일치)

## 1. 원인 (실측)

- DB·API·워커 진실: 두 프롬프트 모두 `OPENROUTER` + `nvidia/nemotron-3-super-120b-a12b:free`
- 카탈로그 진실: `/api/providers` OPENROUTER 5종에 nemotron 없음 (정적 목록에 없고 성공한 refresh 없음)
- 편집폼 `populateModelSelect`는 일치 옵션 없이 첫 항목을 표시 → "다르게 나온다" + 저장 시 잘못 덮어씀

## 2. 수정 (AIModelTalk 이식)

- `OpenRouterClient.supportedModels`에 nemotron 추가 (시드 기본값 = 정적 보호)
- 편집폼 stale 플레이스홀더: 저장 ID가 목록에 없으면 `'<id>' (목록에 없음)` 옵션을 selected로 삽입, 자동 치환 금지
- `merge` 스냅샷 가드: 직전 원격−현재 원격 차집합만 삭제, 정적 base(`staticBase` 분리 보관)·프롬프트 참조 ID는 삭제 금지
- `restoreEnabled`·병합 메모리에서 미등록 투입 ID 유지 (DataStore 침식 방지)
- `ModelCatalogTest` 7종 (기존 4 + 정적 포함·정적 삭제금지·참조 보호 3종)

## 3. OpenCode Zen 공급자 — 추가 후 삭제

- 추가했었으나 실기 검증에서 Zen 무료 티어가 OpenCode 외부 API 호출을 403 차단함이 확인됨
  (`FreeTierError: OpenCode's free tier can only be used from within OpenCode`)
- 유료 모델 편입·유료 테스트는 사용자 결정으로 제외 → 공급자 통째로 삭제
- 교훈: 외부 게이트웨이 무료 티어는 인앱 사용 가능 여부를 실기 호출로 먼저 확인할 것

## 4. NIM 삭제

- `NimClient`·enum·factory·카탈로그·웹·테스트 제거. 기기 실측 NIM 사용 프롬프트 0건이라 마이그레이션 불필요
- 유지: 과거 실행기록 표기, 시드 시장조사 문구, 에러코드

## 5. 검증

- 단위 7/7, 빌드·lint SUCCESS
- 실기: providers = OR 6·GAS 3·ZEN 8 (NIM 소멸), nemotron enabled, 프롬프트 2건 값 유지
- 잔여: Zen 키 등록 후 동기화·실행 1건 (사용자 키 필요)
