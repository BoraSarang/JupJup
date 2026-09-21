# PLAN_v15 — 전체 리팩토링(1단계) + 버그·동작연결(2단계)

> 플랫폼: AND · 규칙: AGENTS.local.md + rules/hard·workflow·quality
> 범위: 구조 중복은 저위험부터, 버그 P0/P1은 동작 연결 위주. 대규모 공통화(HttpServer 4벌 등)는 다음 라운드로 유예.

## 1단계 — 전체 리팩토링 (저위험·검증 가능)
- A. `build_and_run.sh test`에 `:services:community` 누락 → 추가 (회귀망 구멍)
- B. 문자열 하드코딩 제거: `dashboard_pj_btn_start_server` "(3030)" → "서버 시작" 통일
- C. placeholder 하드코딩: `pj_fragment_home` "http://IP:3030" → "http://IP:포트", Provider 주석 포트 제거
- D. KDoc drift: "두 서비스" → "네 서비스" (DashboardFragment·ViewModel·MainActivity)
- E. `DashboardViewModel.Factory` ioDispatcher 전달 누락 수정
- F. `HomeViewModel` 매직 포트 3030 → `Constants.DEFAULT_PORT`
- G. 문서 기본포트 현행화: ENDPOINTS·AGENTS.local 3010/3020/3030/3040

## 2단계 — 버그·동작 연결
- P0-1 앱정보 pj 행 누락: `dialog_about.xml` pj 행 추가 + `MainActivity.showAbout` 바인딩
- P0-2 PJ 알림 탭 빈 화면: 최근 실행 20건 렌더 (Runtime 직접 조회, 에러코드 E-AND-DB-0404)
- P0-3 Plan `cancelAll()` 하드코딩 5건 → 태그 기반 일괄 취소 + 네임스페이스 `planjupjup_crawl_` (수동 태그 고아 해소)
- 검증 제외 (이미 정상): 설정 POST 재시작 조건부·Registry 4×5·에셋-라우트·JS 포트·DataStore/채널

## 검증
- `./build_and_run.sh build` + `test unit` (community 포함) + `lint`
- DoD: 한국어·DebugLogger·에러코드·CHANGELOG·TODO·세션로그
