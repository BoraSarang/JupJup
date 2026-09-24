# 🪙 줍줍 시리즈 — JupJup

맥줍줍·요금줍줍·프롬프트 저널·커뮤니티줍줍을 **하나의 APK**로 통합한 프로젝트입니다.

갤럭시 폰을 **로컬 수집 서버**로 활용해 각 서비스의 웹 포털 데이터를 만들고, 같은 네트워크의 브라우저에서 열어봅니다.

- **패키지**: `com.borasarang.jupjup` · 런처명: 줍줍
- **구성**: 맥줍줍(맥 앱 수집) + 요금줍줍(요금제 수집) + 프롬프트 저널(프롬프트 저널) + 커뮤니티줍줍(커뮤니티 뉴스)
- **서비스 기본 포트**: 맥줍줍 3010 · 요금줍줍 3020 · 프롬프트 저널 3030 · 커뮤니티 3040 (각각 앱 설정에서 변경 가능)
- **포털**: `http://<폰 IP>:3010/` · `:3020/` · `:3030/` · `:3040/`

## ✨ 핵심 기능

| 서비스 | 기능 |
|---|---|
| 🍎 맥줍줍 | macOS 앱 신규 출시·버전업·새 기능 자동 수집 (Setapp·차트·GitHub·Product Hunt 등), 트렌드 대시보드, 알림 센터 |
| 📱 요금줍줍 | 알뜰폰 요금제·통신 요금 정보 수집, 변경 감지 알림 |
| ✍️ 프롬프트 저널 | 프롬프트 저널·실행 기록 관리, 뉴스룸 웹 포털 |
| 💬 커뮤니티줍줍 | 커뮤니티 게시글 수집, 뉴스형 웹 포털 |

통합 앱은 시작 화면 **대시보드**에서 네 서비스를 카드로 묶어 보여주고(상태·주소·통계·수집/서버 조작, 활성 서비스 강조), 상단 서비스 전환(4열 세그먼트)과 하단 탭(대시보드/홈/설정)으로 이동합니다. 앱 정보는 툴바 `⋮` 메뉴에서 확인합니다.

## 🗂️ 멀티모듈 구조

Gradle 프로젝트 루트는 저장소의 `android/`입니다 (`android/settings.gradle.kts`).

```
:app                    # 통합 셸 (JupJupApplication, MainActivity, 테마·아이콘)
:services:common        # 공통 라이브러리 (Throttler·NetMeter·AI·검색 등)
:services:mac           # 맥줍줍 — 포트 3010, DB·설정 격리
:services:plan          # 요금줍줍 — 포트 3020, DB·설정 격리
:services:promptjournal # 프롬프트 저널 — 포트 3030, DB·설정 격리
:services:community     # 커뮤니티줍줍 — 포트 3040, DB·설정 격리
```

네 서비스는 포트·DB·DataStore 설정·알림 채널·리소스(`mac_`/`plan_`/`pj_`/`cm_` 접두사)까지 격리되어 같은 프로세스에서 독립 동작합니다. Android `Application` 1개 제약 때문에 각 서비스의 초기화 로직은 `{Prefix}JupJupRuntime`(object)으로 제공됩니다.

## 📚 문서

| 문서 | 위치 |
|---|---|
| 개발 계획서 | [`docs/plans/`](docs/plans/) |
| 작업 체크리스트 | [`docs/TODO.md`](docs/TODO.md) |
| 보류 항목 | [`docs/BACKLOG.md`](docs/BACKLOG.md) |
| UI/UX 설계 | [`docs/DESIGN.md`](docs/DESIGN.md) |
| API 명세 | [`docs/api/ENDPOINTS.md`](docs/api/ENDPOINTS.md) |
| 권한 사유서 | [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) |
| 변경 이력 | [`docs/CHANGELOG.md`](docs/CHANGELOG.md) |
| 사용자 메시지 | `error_message_ko.json` |

## 🔨 빌드

JDK가 PATH에 없으면 Android Studio 번들 JBR(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`)을 사용합니다.

```bash
./build_and_run.sh build     # assembleDebug + 연결된 기기에 설치
./build_and_run.sh test      # 단위 테스트 (서비스 모듈)
./build_and_run.sh test full # 단위 + connected 테스트 (기기 재설치 주의)
./build_and_run.sh lint      # Lint
./build_and_run.sh clean
```

디버그 APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## 🔄 CI·배포

- `.github/workflows/ci.yml` — main push·PR 시 단위 테스트 + Lint
- `.github/workflows/release.yml` — `v*` 태그 push 시 `assembleRelease` + GitHub Release 업로드
