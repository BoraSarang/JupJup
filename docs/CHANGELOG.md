# CHANGELOG — JupJup

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
- `error_message_ko.json`: `E-AND-CRAWL-0221` (수집 중지/재개 상태 저장 실패) 추가
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, 크래시 0
- 런처 아이콘 교체: 제공 아이콘 팩 적용 (블루 런처 + 적응형 흰 배경/투명 정면)
- 콘텐츠 정돈: 서비스 홈 카드 구성 유지하되 앱바·하단바로 영역을 구획, 16dp 패딩 유지