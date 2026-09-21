# AGENTS.local.md — JupJup (줍줍 시리즈 통합) 프로젝트 특화 규칙

> 공통 가이드는 `~/.config/opencode/AGENTS.md` 참조. 여기에는 변경 항목만 기록.

- **적용 플랫폼 확정**: android+server — Android 멀티모듈 (`:app` + `:services:{service}`) + Ktor 임베디드 서버(포트 3000/3001/3002). 추적 대상은 macOS 앱·알뜰폰 요금제 등 서비스별 상이, 빌드·테스트는 Android만.
- **design_profile**: custom — 웹 포털/뉴스룸 자체 디자인 시스템, 토큰은 docs/DESIGN.md.
- **구조**: Gradle 멀티모듈. `:services:mac`(com.borasarang.macjupjup, 포트 3000), `:services:plan`(com.borasarang.planjupjup, 포트 3001), `:services:promptjournal`(com.borasarang.promptjournaljupjup, 포트 3002), `:app`(com.borasarang.jupjup). 신규 서비스는 `:services:{id}` 모듈 + `ServiceRegistry` 등록 + `settings.gradle.kts` include.
- **리소스 접두사 필수**: 라이브러리 모듈 간 리소스 머지 충돌 방지. 모든 리소스 파일명·id·name에 `mac_` / `plan_` / `pj_` / 서비스별 접두사. 신규 서비스도 동일하게.
- **데이터 격리**: 서비스별 포트(mac 3000 / plan 3001 / pj 3002), 서비스별 Room DB 파일·마이그레이션, 서비스별 DataStore(`settings_{service}`), 서비스별 알림 채널.
- **Application 패턴**: 통합 `JupJupApplication`만 사용. 각 서비스는 `{Prefix}JupJupApplication` 대신 `{Prefix}JupJupRuntime`(object, `fun initialize(context)`)로 전환.
- **phisical 디바이스**: Galaxy S22 실기 E2E. 사용자 사용 중이면 headless·smoke/unit만, full은 사전 확인.
- **수집 예의 고정**: 공식 API 우선, 요청 간격 1초+, UA 명시, robots.txt 확인. 크랙·활성화툴 사이트 수집 금지. 릴리즈노트 전문 복제 금지(요약+링크만).
- **시크릿**: GitHub 토큰은 설정 화면 입력→DataStore만. 커밋·로그 금지(마스킹).
- **버전 고정**: `gradle/libs.versions.toml` 단일 진실 (AGP 9.3.1·Ktor 3.5.2·Room 2.7.0·Work 2.9.0).
- **서버 예외**: 공통 server 규칙(Node/Go)과 달리 Ktor(CIO) 임베디드 사용. /health 기준·p95 300ms·캐시 70% 예산은 그대로 적용.
- **빌드**: 루트 `./build_and_run.sh` 경유만. JBR 자동 JAVA_HOME.
- **랜딩/Pages 미운영**: GitHub Pages·landing 폴더 없음. 문서는 README(한/영) + docs/ 만.