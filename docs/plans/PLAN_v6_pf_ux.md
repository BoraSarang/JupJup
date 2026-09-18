# PLAN_v6 — 프롬프트팩토리 UX 정리 (v1.11.x)

> 3분 초안. 사용자 요청 3건 묶음 수정.

## 1. 요구사항

1. 프롬프트 탭 = 프롬프트별 마지막 응답만 (프롬프트 2개면 카드 2개).
   전체 실행 기록 리스트는 상세 모달 안에서만.
2. 응답 기록 보면서 프롬프트 수정 가능 (상세 모달 내 인라인 편집+저장).
3. 응답은 마크다운 렌더링 (mac_web T-142 보수적 렌더러 이식: escape 선행, https 링크만, 이미지=링크).
4. 관리 탭 공급자 카드에 [모델 갱신][모두 사용][모두 해제] 버튼.
5. `신규 undefined개` 버그: 백엔드 refresh 응답에 `added` 누락 → 프론트가 `data.added` 참조.
6. `56770ms` 표시: 원시 ms 대신 읽기 쉬운 포맷 (`56.8초`, `1분 5초` 등).

## 2. 변경

- `ModelCatalog`: `setAllEnabled(provider, enabled)` 추가.
- `PfRoutes`:
  - `POST /api/providers/{name}/models/refresh` 응답에 `added` 포함.
  - `POST /api/providers/{name}/models/enabled {enabled}` 일괄 토글 신설.
- `pf_web/app.js`:
  - `formatDuration(ms)` 신설, 4곳 교체 (insight/pm/execution/modal).
  - `md()/mdInline()/stripHtml()` 이식, `modalResponse`·카드 마지막 응답에 적용 (`textContent`→`innerHTML`).
  - `renderPrompts` → 프롬프트별 `/executions?limit=1` 병렬 조회 후 마지막 응답 카드 렌더.
  - `refreshProviderModels` alert → `(data.added ?? 0)` 폴백 + SKIPPED 문구 분기.
  - 공급자 카드 액션에 모두사용/해제 + `setAllModelsEnabled()` (벌크 우선, 실패 시 단건 루프 폴백).
- `pf_web/index.html`: `modalResponse pre`→`div.md-body`, `pmContent pre`→`textarea+저장`.
- `pf_web/style.css`: `.md-body`, `.pm-last-response`, 벌크 버튼 스타일.

## 3. 검증

- `./build_and_run.sh build android` + `:services:promptfactory:testDebugUnitTest`
- 실기: 프롬프트 2개 → 카드 2개·마지막 응답 일치, 마크다운 렌더, 인라인 수정 저장, 모두사용/해제, 갱신 문구 `신규 N개`, duration `56.8초` 확인.

## 4. 후속 (스크린샷 피드백 2026-09-18)

- 위·아래 중복 제거: 상단 인사이트 섹션 비움, 프롬프트 카드 1개에 마지막 응답 통합.
- 마크다운 표 미렌더 (`| ... |` 평문) → GFM 테이블 지원 추가 (`md-table-wrap` + 가로 스크롤).
- 모델 검색: 공급자 카드별 `provider-search` 입력 + `보이는수/전체` 카운트.

## 5. v3 전면 개편 (2026-09-18, 플랜모드 합의)

- 정보구조: 상태카드·새로고침·3탭 삭제 → 슬림 헤더(로고+점+마지막실행+⚙️) + 프롬프트 칩 + 리포트 2열.
- 마스터-디테일: PC 2열(왼쪽 날짜·오른쪽 본문), 모바일 목록→전체화면. 모달 2층 폐지(기록보기 불가 오류 근본 해결: detailModal이 promptModal 뒤에 깔리던 문제).
- 마크다운: mac_web T-142 `md/mdInline/stripHtml/stripMd` + `.md-body` CSS 그대로 이식, GFM 표 패치만 추가. PF 보라 배경 폐지.
- 설정 서랍: ⚙️ 우측 서랍에 프롬프트 폼+공급자·모델(검색·일괄 유지). alert→toast, confirm은 파괴적 동작만 유지.

## 6. 표 셀 개행 수정 (2026-09-18)

- 원인: AI가 셀 안 줄바꿈을 `<br>`로 보내는데 `stripHtml`이 통째로 제거 → 한 줄로 붙음. `<URL>` 꺾쇠 링크도 같은 이유로 출처 칸이 비었음.
- 수정: `md()`에서 strip 전에 `<br>`→플레이스홀더(`\uE000`), `<URL>`→`[URL](URL)` 변환. `mdInline`에서 플레이스홀더→`<br>`, 셀 선행 `- `→`•`. `*라벨:*` 한정 굵게 추가. `stripMd`도 `<br>`→공백.
- 검증: 실 리포트 9602자로 하네스 렌더 → 표 9개, `<br>` 26개, 파이프 누수 0, 출처 링크 정상.

## 7. 리포트 형식 변경 (2026-09-18)

- 문제: 3번 섹션(서비스별 전체 표)이 셀마다 모델 5~7개라 모바일에서 안 어울림. 렌더러로 때울 게 아니라 프롬프트 지시를 바꿈.
- 변경: `SEED_PROMPT` 출력 형식 + 기기 1번 프롬프트 동기화(PUT). 표(`|`)는 2번 변경 요약(4행 이하·셀 20자 이하)에만 허용, 3번은 `### 서비스명`+모델별 불릿 1줄, 4·5번도 소제목+불릿. `<br>` 이어붙이기·한 줄 다모델 금지, 링크는 `[이름](URL)` 형식.
- 다음 예약 실행(매일 09:00)부터 새 형식. 기존 기록은 그대로(표 렌더 유지).
