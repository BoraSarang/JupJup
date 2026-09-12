# 🪙 줄줍 시리즈 — JupJup

macOS 앱 순위·Mac 앱 정보를 수집해주는 맥줍줍과, 알뜰폰 요금제/통신 요금 정보를 수집하는 요금줍줍을 **하나의 APK**로 통합한 프로젝트입니다.

갤럭시 폰을 **로컬 수집 서버**로 활용해 두 서비스의 웹 포털 데이터를 함께 만들고, 같은 네트워크의 브라우저에서 열어봅니다.

- **패키지**: `com.borasarang.jupjup` · 런처명: 줍줍
- **구성**: 맥줍줍(맥 앱 수집) + 요금줍줍(요금제 수집)
- **서비스 기본 포트**: 맥줍줍 3000 · 요금줍줍 3001 (각각 앱 설정에서 변경 가능)
- **포털**: `http://<폰 IP>:3000/` (맥줍줍) · `http://<폰 IP>:3001/` (요금줍줍)

## ✨ 핵심 기능

| 서비스 | 기능 |
|---|---|
| 🍎 맥줍줍 | macOS 앱 신규 출시·버전업·새 기능 자동 수집 (Setapp·차트·GitHub·Product Hunt 등), 트렌드 대시보드, 알림 센터 |
| 📱 요금줍줍 | 알뜰폰 요금제·통신 요금 정보 수집, 변경 감지 알림 |

통합 앱에서는 하단 네비게이션(맥줍줍 / 요금줍줍)으로 서비스를 전환하고, 상단 탭(홈 / 수집 소스 / 알림 / 설정)으로 기능을 이동합니다.

## 🗂️ 멀티모듈 구조

```
:app                   # 통합 셸 (JupJupApplication, MainActivity, 테마·아이콘)
:services:mac          # 맥줍줍 라이브러리 — 포트 3000, DB·설정 격리
:services:plan         # 요금줍줍 라이브러리 — 포트 3001, DB·설정 격리
```

두 서비스는 포트·DB·DataStore 설정·알림 채널·리소스(`mac_`/`plan_` 접두사)까지 격리되어 같은 프로세스에서 독립 동작합니다. Android `Application` 1개 제약 때문에 각 서비스의 초기화 로직은 `MacJupJupRuntime`/`PlanJupJupRuntime`(object)으로 제공됩니다.

## 📚 문서

| 문서 | 위치 |
|---|---|
| 개발 계획서 | [`docs/plans/`](docs/plans/) |
| 작업 체크리스트 | [`docs/TODO.md`](docs/TODO.md) |
| UI/UX 설계 | [`docs/DESIGN.md`](docs/DESIGN.md) |
| API 명세 | [`docs/api/ENDPOINTS.md`](docs/api/ENDPOINTS.md) |
| 권한 사유서 | [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) |
| 변경 이력 | [`docs/CHANGELOG.md`](docs/CHANGELOG.md) |
| 사용자 메시지 | `error_message_ko.json` |

## 🔨 빌드

JDK가 PATH에 없으면 Android Studio 번들 JBR(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`)을 사용합니다.

```bash
./build_and_run.sh build     # assembleDebug + 연결된 기기에 설치
./build_and_run.sh test      # 단위 테스트 (두 서비스)
./build_and_run.sh test full # 단위 + connected 테스트 (기기 재설치 주의)
./build_and_run.sh lint      # Lint
./build_and_run.sh clean
```

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 🔄 CI·배포

- `.github/workflows/ci.yml` — main push·PR 시 단위 테스트 + Lint
- `.github/workflows/release.yml` — `v*` 태그 push 시 `assembleRelease` + GitHub Release 업로드