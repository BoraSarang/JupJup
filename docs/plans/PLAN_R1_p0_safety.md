# PLAN_R1 — P0 안전 수정 (1커밋)

> 범위: 데이터 손실·무한루프·워커 폭증·빌드 마스킹. 동작 변경은 방어적 검증 추가만.

## R1-1 plan DB 폴백 데드코드 + 백업 없음
- `PlanDatabase.getInstanceFallback`이 `instance ?:` 반환이라 파괴 재생성 절대 미동작 → mac `MacDatabase` 패턴으로 수정 (`resetInstance()` 후 재생성)
- `PlanJupJupRuntime.initialize` DB 실패 경로에 mac 동일 백업(`files/db-backup/`) 추가
- 검증: 단위 테스트 (DB 로직 변경이므로 기존 mac DaoRepositoryTest + 빌드)

## R1-2 설정 범위 검증 없음 (양 서비스)
- mac settings POST: `retentionDays`·`watchdogIntervalSec` 범위 검증 없이 저장 → 음수 보관기간 시 전량 삭제 대상, 주기 0 이하 시 `delay(≤0)` 폭주
- plan settings POST: port·retention 무음 `coerceIn`/폴백 → mac처럼 `E-AND-VALID-0501/0502` 400 + 검증 추가
- `Constants`에 범위 상수 확인 후 양쪽 settings POST에 동일 가드 (mac `E-AND-VALID-0502` 재사용)
- 검증: 단위 테스트 + 실기 health

## R1-3 plan 수동수집 워커 폭증
- plan `triggerImmediate`가 일반 `enqueue` → mac처럼 unique KEEP + `SourceLocks` 상당 가드 이식
- `cancelAll` 하드코딩 5종은 5단계에서 동적 조회로 (여기선 워커 폭증만 차단)
- 검증: 빌드 + 실기 `/api/sync` 연타 시 워커 1개 유지 (logcat)

## R1-4 build_and_run.sh 파이프 마스킹
- `set -o pipefail` 추가 + APK 존재 검사를 타임스탬프(stale 방지)로 강화
- `test unit`에 `:app:testDebugUnitTest` 포함 (현재 app 제외됨)
- 검증: 스크립트 `build` 성공 + 고의 실패 시 중단 확인은 생략 (위험하므로 리뷰만)

## DoD
- assembleDebug + 단위(mac 57/plan 49) + lint 오류 0 + 실기 설치·health 200·크래시 0
- CHANGELOG 1.3.2 섹션 + TODO R7 + 세션 로그
