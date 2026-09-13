# PLAN_v3 — 대시보드 서버 상태 표시 레이스 수정 (3분 초안)

> 소규모 버그 픽스. 관련: `DashboardViewModel`, `DashboardFragment`. 버전 bump 없음.

## 증상
- 서버(3000·3001) 실제 기동 중인데 대시보드에 "중지됨"으로 표시됨
- 이미 켜진 서버를 또 "시작" 누르는 오조작 발생

## 원인 (logcat 증거)
- `00:45:38.059` 대시보드 새로고침 → `00:45:38.405/407` 서버 기동 완료
- `refresh()` → `Socket(127.0.0.1, port)` 체크가 서버 포트 바인드 **전**에 실행 → false
- `toggleMac/PlanServer()`도 시작/중지 직후 바로 `refresh()`라 전환 중 상태를 읽음

## 수정안 (1파일)
- `app/.../ui/dashboard/DashboardViewModel.kt`만 수정
- `refresh(retry: Boolean = true)`: 조회 후 mac·plan 중 하나라도 중지 표시면 2초 뒤 1회 재조회 (`refresh(retry = false)`, 재귀 없음)
- 토글 함수는 기존 즉시 `refresh()` 유지 — 내부 추적 재조회가 자동 보정
- 재조회 시 `[대시보드] 서버 미기동 감지 — 2초 뒤 재조회` 로그 1개 (DebugLogger 의무)

## 검증
- `./build_and_run.sh build` (기기 자동 설치) → logcat에 재조회 로그 + 최종 상태 확인
- `curl /api/health` 3000·3001 HTTP 200, 크래시 0
- 단위 테스트: 서비스 모듈 변경 없음 → 기존 결과 유효, smoke 성격상 생략 (커밋/PR 시 full)
