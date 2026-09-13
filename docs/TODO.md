# TODO — 줍줍 시리즈 통합 (JupJup)

> v1.0 통합 작업 목록. 항목 완료 시 `[x]`.

## R12 리팩토링 6단계 — 빌드·문자열·잔정리
- [x] PLAN_R6 초안
- [x] 6a 빌드·매니페스트 정리 (SDK 카탈로그 + 권한 app 일원화)
- [x] 6b 코드 잔정리 (catch 로그·SourceLocks·lastSeedStatus·문자열 리소스화·문서유령)
- [x] 빌드 + 단위(131/131) + lint + 실기 검증 (health·머지드매니페스트·크래시 0)
- [x] versionName 1.8.0 (versionCode 10)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R11 리팩토링 5단계 — 성능·DB
- [x] PLAN_R5 초안
- [x] 5a DB 쿼리 최적화 (배치 4종·트랜잭션·인덱스+마이그레이션 4→5)
- [x] 5b 블로킹 제거 (serveAsset·getLocalIp·Socket·FGS 기본인자)
- [x] 빌드 + 단위(131/131) + lint + 실기 검증 (DB승계·수집E2E·health·크래시 0)
- [x] versionName 1.7.0 (versionCode 9)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R10 리팩토링 4단계 — God object 분리
- [x] PLAN_R4 초안
- [x] 4a 서버 구조 분리 (Route 확장 절단 14파일, 동작 동결 + 매퍼 golden 테스트)
- [x] 4b plan 정렬(D1~D4) + 통계 미캐시 4종 캐시 + StatsCache 분리
- [x] 빌드 + 단위(129/129) + lint + 실기 검증 (health·포털 스팟체크·404·캐시히트·크래시 0)
- [x] versionName 1.6.0 (versionCode 8)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R9 리팩토링 3단계 — app 추상화 + ViewModel 테스트
- [x] PLAN_R3 초안
- [x] 3a ServiceAdapter + ServiceRegistry + 내비 정리 (CrawlScheduler db 파라미터 제거 포함)
- [x] 3b 소켓 타임아웃(500ms) + DashboardViewModelTest 6종 (ioDispatcher 주입으로 결정적 테스트)
- [x] 빌드 + 단위(app 6/6·mac+plan 106/106) + lint + 실기 검증 (health·크래시 0)
- [x] versionName 1.5.0 (versionCode 7)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

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
- [x] connected 테스트 8/8 통과 (app 인사이트 스모크 2 + mac Room 6), 포털 3000·3001 HTTP 200
- [x] versionName 1.2.0 (versionCode 2)으로 bump
- [x] 문서 갱신 (DESIGN·CHANGELOG·README 한/영·PLAN·세션 로그)

## R4 1.3.0 서비스-우선 내비게이션 개편
- [x] N1 내비게이션 재구성 (세그먼트+5탭+드로어 제거+오버플로우)
- [x] N2 대시보드/홈 정리 (insight→dashboard 리네임+활성 강조)
- [x] N3 폴리시+테스트+실기 검증 (connected app 4/4 + mac 6/6, 포털 200/200, 크래시 0)
- [x] N4 문서+DoD+커밋

## R8 리팩토링 2단계 — :services:common 추출
- [x] PLAN_R2 초안
- [x] 2a 모듈 + JupLog(릴리스 파일 로그) + NetUtils + SourceLocks + SettingsStores
- [x] 2b 서버 JSON 헬퍼 단일화 (mac 삭제·plan 20곳, 동작 동일만)
- [x] 빌드 + 단위 + lint + 실기 검증 (health·파일로그·승계·API 동등성·크래시 0)
- [x] versionName 1.4.0 (versionCode 6)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R7 리팩토링 1단계 — P0 안전 수정
- [x] PLAN_R1 초안
- [x] plan DB 폴백 데드코드 + 백업
- [x] 설정 범위 검증 (양 서비스 + saveSettings)
- [x] plan 워커 폭증 차단 (unique KEEP + SourceLocks)
- [x] build_and_run.sh pipefail + stale APK 가드
- [x] 빌드 + 단위 + lint + 실기 검증 (health·400·연타·크래시 0)
- [x] versionName 1.3.2 (versionCode 5)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R6 1.3.1 대시보드 서버 상태 레이스 수정
- [x] PLAN_v3 초안 (원인: refresh가 서버 바인드 전 소켓 체크)
- [x] DashboardViewModel 추적 재조회 (2초 뒤 1회) + 재조회 로그
- [x] 빌드 + 실기 설치·크래시 확인 (포털 3000·3001 HTTP 200)
- [x] 문서 갱신 (CHANGELOG·세션 로그)
- [x] versionName 1.3.1 (versionCode 4)으로 bump

## R5 1.3.0 릴리스 (첫 푸시)
- [x] 사전 검증 (assemble 성공 + 단위 mac 57/plan 49 + lint 오류 0, 기기 설치 없이)
- [x] origin 첫 푸시 (main)
- [x] CI lint 실패 수정 (SpecifyForegroundServiceType, 라이브러리 매니페스트에 dataSync 선언)
- [x] v1.3.0 태그 + GitHub Release (release.yml, CHANGELOG 1.3.0 섹션)
- [x] 세션 로그 갱신