# CHANGELOG — JupJup

## [1.3.0] - 2026-09-13
> 플랫폼: AND · 서비스-우선 내비게이션 개편

### UI/UX
- 상단 **서비스 전환(맥줍줍/요금줍줍)** 상시 표시 — 전환 시 대시보드로 강제 이동, 현재 서비스가 항상 명시됨
- 하단 5탭: 대시보드(통합) / 홈(서비스 상세) / 수집 소스 / 알림 / 설정
- 드로어 제거 — 앱 정보(포트·버전)는 툴바 `⋮` 메뉴로 이동
- **인사이트 → 대시보드** 명칭 변경 + 활성 서비스 카드 강조 (스트로크 + "현재" 배지)
- 앱바 타이틀 = 항상 현재 서비스명
- **앱 정보 다이얼로그 개편**: 앱 아이콘·버전 배지, 서비스별 포털 주소(IP:포트), 하단 GitHub 링크(`github.com/BoraSarang/JupJup`, 브라우저/에러 처리 포함)

### 수정
- material 1.12.0에 SegmentedButton 미포함 확인 → `MaterialButtonToggleGroup` 세그먼트형으로 구현 (버전 고정 규칙 유지)
- 깨진 connected 테스트 이관 시 잔재 정리 (드로어 메뉴·헤더·아이콘 5종 삭제)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, `:app:connectedDebugAndroidTest` 4/4 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0

## [1.2.0] - 2026-09-13
> 플랫폼: AND · 시리즈 인사이트 시작 화면

### UI/UX
- 시작 화면 **인사이트** 신규: 맥줍줍·요금줍줍 두 서비스 카드를 한 화면에 병렬 표시 (상태 도트·접속 주소·통계 3개)
- 카드에서 직접 **[지금 수집]·[수집 중지/재개]·[서버 시작/중지]** 조작 (서비스 화면 진입 없이 제어)
- 하단 첫 탭 `서버 상태` → `인사이트`로 교체 (`ic_tab_insight` 신규), 드로어에 `줍줍 시리즈` 항목 추가 (`ic_nav_series` 신규)
- 앱바 타이틀: 인사이트면 시리즈명, 그 외 탭은 선택된 서비스명

### 수정
- 인사이트 빌드 오류 수정: `TimeUtils.formatRelative` 참조 정정, M3 Filled 버튼 스타일명 정정 (`Widget.Material3.Button`), 라이브러리 색상 non-transitive R 참조, `refreshData()` 추가
- `InsightViewModel` 예외 처리: silent catch 제거 → 서비스별 `DebugLogger` + 에러코드 (`E-AND-CRAWL-0201/0211/0221`, `E-AND-DB-0402`) 매핑, 화면 진입 로그(`인사이트 화면 진입`) 추가
- 오타 수정: `줄줍` → `줍줍` (README 제목 포함 5곳)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·`:app:lintDebug` 통과 (경고 51건, 오류 0 — 인사이트 관련 6건은 기존 패턴과 동일한 경미 경고), 설치 후 크래시 없음
- 검증(connected·E2E): `:app:connectedDebugAndroidTest` 2/2 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0, 인사이트 진입 로그 확인

## [1.0.0] - 2026-09-13
> 플랫폼: AND · 통합 (mac+plan)

### 통합 (처음이벤트)
- 두 독립 앱(MacJupJup·PlanJupJup)을 단일 APK `JupJup`으로 통합
- Gradle 멀티모듈: `:app` + `:services:mac` + `:services:plan`
- 서비스별 격리: 포트(mac 3000 / plan 3001)·DB·설정(DataStore)·알림채널·리소스(mac_/plan_ 접두사)
- `Application` → `{Prefix}JupJupRuntime`(object) 전환, 통합 `JupJupApplication` 하나로 두 서비스 기동
- 하단 서비스 네비(맥줍줍/요금줍줍) + 기능 탭(홈/수집 소스/알림/설정) 통합 UI
- 루트 `build_and_run.sh` 단일 빌드 디스패처
- GitHub Pages·landing 제거 → README 한/영 + docs/ 만

### 마이그레이션
- 데이터 마이그레이션 없음 (신규 설치 기준). 기존 앱 데이터 이관 미지원.
- 메모리/성능 영향: 두 서버 동시 기동으로 약 2배 서버 프로세스(각자 CIO 엔진), DB 2개

### 수정 (1.0.1)
- **크래시 #1**: 두 서비스의 DataStore 파일명이 동일(`settings`) → `mac_settings`/`plan_settings`로 격리 (E-AND-STORE-0511)
- **크래시 #2**: WorkManager `SystemForegroundService`에 `foregroundServiceType="dataSync"` 누락 → app manifest에서 `tools:node="merge"`로 주입 (E-AND-SYNC-0521)
- 검증: 실기(LB-R5CT215F4QK) 포털 3000·3001 모두 HTTP 200, 크래시 없음

## [1.1.0] - 2026-09-13
> 플랫폼: AND · 통합 UI 개편

### UI/UX
- 상단 기능 탭(TabLayout) 제거 → 하단 바 좌측 **햄버거** + 기능 **드로어**(홈/수집 소스/알림/설정)로 대체
- 하단 바: 서비스 전환(맥줍줍/요금줍줍)은 유지, Material Toolbar 스타일로 정돈
- 상단 앱바 추가: 현재 서비스명 표시 (기존 홈 내부 로고/타이틀 중복 제거)

### 수정 (1.1.1)
- 탐색 구조 재개편: 상단 앱바 햄버거 = **서비스 드로어**(맥줍줍/요금줍줍), 하단 = **기능 탭**(서버 상태·수집 소스·알림·설정) — 하단 우측 서비스 메뉴 제거
- "홈" 탭 → "서버 상태"로 이름 변경 (화면 유지), 서버 상태 탭 전용 아이콘(`ic_tab_server`) 신규
- 각 서비스 홈에 **수집 중지/시작 토글**(일시정지): 중지 시 실행·예약 수집 전체 취소 + `crawl_enabled` 영속 저장(앱 재시작 후 유지), 재개 시 주기 스케줄 재예약
- 각 웹 포털에 파비콘(`favicon.svg`, 원본 앱 아이콘 벡터 기반) + `HttpServerService` 라우트 (`ContentType.Image.SVG`, `/favicon.svg`)
- 파비콘 서비스별 분리 시안(원본 요소 활용): 맥줍줍=파란 그라디언트+코인 링 중심(수집), 요금줍줍=차콜 슬레이트+막대 3/기준선 중심(요금 비교)+상단 코인 링
- `error_message_ko.json`: `E-AND-CRAWL-0221` (수집 중지/재개 상태 저장 실패) 추가
- **서버 상태 표시 버그 수정**: 홈(서버 상태) refresh가 기동 완료 전 스냅샷(false)에 고정 → 서버 기동 대기 폴링(최대 5회·1초 간격, `isServiceRunning`)으로 시작 직후 상태 정확 반영
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, 크래시 0
- 런처 아이콘 교체: 제공 아이콘 팩 적용 (블루 런처 + 적응형 흰 배경/투명 정면)
- 콘텐츠 정돈: 서비스 홈 카드 구성 유지하되 앱바·하단바로 영역을 구획, 16dp 패딩 유지