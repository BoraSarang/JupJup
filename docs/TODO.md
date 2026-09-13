# TODO — 줍줍 시리즈 통합 (JupJup)

> v1.0 통합 작업 목록. 항목 완료 시 `[x]`.

## D1 문서·스캐폴드
- [x] AGENTS.local.md
- [x] docs/plans/PLAN_v1_jupjup.md
- [x] docs/TODO.md
- [x] docs/DESIGN.md
- [x] docs/CHANGELOG.md
- [x] docs/PERMISSIONS.md
- [x] docs/api/ENDPOINTS.md
- [x] error_message_ko.json (루트)
- [x] settings.gradle.kts / root build.gradle.kts / gradle 카탈로그 / wrapper
- [x] .gitignore / local.properties / build_and_run.sh

## S1 services:mac 이관
- [x] services/mac/build.gradle.kts (namespace com.borasarang.macjupjup)
- [x] services/mac/Manifest (권한+서비스+리시버)
- [x] MacJupJupApplication → MacJupJupRuntime(object, initialize(context))
- [x] HttpServerService: app 캐스팅 → Runtime, asset 경로 mac_web/
- [x] worker/scheduler/fragment/VM 캐스팅 치환 (Runtime)
- [x] 리소스 `mac_` 접두사 + 코드 R 참조 치환
- [x] 테스트 이관

## S2 services:plan 이관
- [x] services/plan/build.gradle.kts (namespace com.borasarang.planjupjup)
- [x] services/plan/Manifest
- [x] PlanJupJupApplication → PlanJupJupRuntime
- [x] HttpServerService: app 캐스팅 → Runtime, asset 경로 plan_web/
- [x] worker/scheduler/fragment/VM 캐스팅 치환
- [x] 리소스 `plan_` 접두사 + 코드 R 참조 치환
- [x] 테스트 이관

## A1 app 통합
- [x] JupJupApplication (두 Runtime 초기화 + autoStart 서버 기동)
- [x] MainActivity (하단 서비스 네비 + 기능 탭)
- [x] app Manifest (LAUNCHER·통합 테마·아이콘)
- [x] app 리소스 (테마·아이콘·문자열·레이아웃)

## B1 빌드 검증
- [x] ./build_and_run.sh build (assembleDebug) 성공
- [x] 단위 테스트 통과 (두 서비스)
- [x] lint 통과

## C1 CI/workflow
- [x] .github/workflows/ci.yml 통합 (Pages 제거)
- [x] .github/workflows/release.yml

## R1 마무리
- [x] README.md (한) / README.en.md (영)
- [x] 실기 검증: 포털 3000·3001 HTTP 200, 크래시 없음 (`52ea844`)
- [x] 최종 DoD 체크 + CHANGELOG 갱신 (1.0.1 수정·1.1.0 UI 개편 반영)
- [x] 첫 커밋 (`c724b9f`)
- [x] 통합 UI 개편: 하단 햄버거+기능 드로어, 앱바 서비스명, 아이콘 팩 적용

## R2 1.1.0 UI 개편 (별도 커밋 진행)
- [x] 리디자인 코드 커밋

## R3 1.2.0 시리즈 인사이트
- [x] InsightFragment + InsightViewModel (두 서비스 병렬 카드·수집/서버 조작)
- [x] 하단 인사이트 탭 + 드로어 시리즈 항목 + 앱바 타이틀 규칙
- [x] 빌드 오류 수정 (TimeUtils·M3 스타일·non-transitive R·refreshData)
- [x] 예외 처리 (서비스별 DebugLogger + 에러코드) + 화면 진입 로그
- [x] 오타 수정 (줄줍→줍줍)
- [x] 단위 테스트 + lint 통과, 실기 설치·크래시 확인
- [x] 문서 갱신 (DESIGN·CHANGELOG·README 한/영·PLAN·세션 로그)