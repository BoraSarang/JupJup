# PLAN v1.0 — 줍줍 시리즈 통합 (JupJup)

> 날짜: 2026-09-13 · 플랫폼: Android (Galaxy S22) · 상태: 진행 중

## 1. 개요 (Why)

기존 독립 앱 두 개(MacJupJup·PlanJupJup, 동일 골격)를 **단일 APK 하나로 통합**.
크롤링 엔진은 하나의 앱 프로세스에서 동시에 돌되, **설정·포트·DB는 서비스별로 완전 격리**.
향후 비슷한 개념의 수집 서비스(예: bookjupjup·gamejupjup)를 **모듈만 추가**해 붙일 수 있는
"통합 줍줍" 플랫폼을 만든다.

## 2. 목표 (What)

- [x] `:app` + `:services:mac` + `:services:plan` Gradle 멀티모듈
- [x] 두 서비스 동시 기동 (각자 독립 포트/DB/설정/알림채널)
- [x] 하단 네비게이션 기반 서비스 선택 UI
- [x] 데이터 마이그레이션 없음 (신규 설치 기준)
- [x] GitHub Pages/landing 제거, README 한/영 + docs/ 만
- [x] 루트 `./build_and_run.sh` 단일 진입점 + 통합 CI

## 3. 구조 (How)

```
JupJup/
├── app/                      # application (com.borasarang.jupjup)
│   ├── JupJupApplication      # 두 Runtime 초기화 + 서버 자동 기동
│   ├── ui/main/MainActivity   # 하단 네비(서비스) + 기능 탭
│   └── res(공용)              # 런처 아이콘·테마·색상·문자열
├── services/
│   ├── mac/                   # library (com.borasarang.macjupjup)
│   │   ├── MacJupJupRuntime   # object. initialize(context)만 실행
│   │   ├── crawler/db/repo/worker/server/ui  # 기존 코드 1:1
│   │   └── res                # mac_ 접두사 리소스
│   └── plan/                  # library (com.borasarang.planjupjup)
│       ├── PlanJupJupRuntime
│       └── ...
└── docs/ gradle/ .github/
```

### 3.1 격리 규칙 (서비스별)
| 자원 | 규칙 |
|---|---|
| 포트 | mac 3000 / plan 3001 (설정 변경 가능, MIN 1024~MAX 65535) |
| DB | 각자 Room 인스턴스·마이그레이션·파일 유지 |
| 설정 | DataStore 파일 분리 (`settings_mac` / `settings_plan`) |
| 알림 채널 | 채널 ID 분리 (`jupjup_mac_server` / `jupjup_plan_server`) |
| 리소스 | **모든 리소스명·파일명에 `mac_`/`plan_` 접두사** (머지 충돌 방지) |
| 크롤러 | 각자 CrawlerFactory + Constants 타입 상수 유지 |
| 웹 에셋 | assets 경로 분리 (`mac_web/` / `plan_web/`) |

### 3.2 Application → Runtime 전환
- Android는 `Application`이 통합 앱에 1개만 존재 → 각 서비스의 Application을
  `object XxxJupJupRuntime { fun initialize(context) }`로 변환.
- 서비스 코드의 `application as XxxJupJupApplication` 캐스팅을 Runtime 참조로 치환.

### 3.3 UI
- 하단 네비: 서비스 선택 (`맥줍줍` / `요금줍줍`)
- 기능 탭: `홈 / 수집 소스 / 알림 / 설정` (활성 서비스의 기존 Fragment 재사용)

## 4. 포트 지정 근거
- 기존 두 앱이 모두 3000 기본 → 같은 폰에서 동시 기동 시 포트 충돌.
- 서비스별 기본 포트 고정(3000/3001) + 부팅 시 충돌 감지(기존 Watchdog 로직 활용)
- 사용자 결정: "권장하는 방법" → 기본값 고정 + 설정 변경.

## 5. 작업 분해
| 단계 | 항목 |
|---|---|
| D1 | 문서 + 스캐폴드 |
| S1 | services:mac 이관 (복사→Runtime→리소스 접두사) |
| S2 | services:plan 이관 |
| A1 | app 통합 (Application/MainActivity/Manifest) |
| B1 | 빌드 검증 + 단위 테스트 |
| C1 | CI/workflow 통합 |
| R1 | README 한/영 + 마무리 |

## 6. 확장 가이드 (신규 서비스)
1. `services/{id}` 모듈 생성 (기존 서비스 복제)
2. `{Id}JupJupRuntime` 작성 + `{id}_` 접두사 리소스
3. `settings.gradle.kts` include + `build_and_run.sh` 명령 추가
4. app `JupJupApplication`에 Runtime 초기화 추가 + MainActivity에 탭 추가
5. 기본 포트 신규 배정 (3000+n) + CHANGELOG 기록