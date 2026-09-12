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
- [ ] .github/workflows/ci.yml 통합 (Pages 제거)
- [ ] .github/workflows/release.yml

## R1 마무리
- [ ] README.md (한) / README.en.md (영)
- [ ] DoD 체크 + CHANGELOG 갱신
- [ ] 첫 커밋