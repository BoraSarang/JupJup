# PLAN_v18 — 네트워크 트래픽 측정 기반 (R42a)

> 플랫폼: AND · 예산: p95 300ms·메모리 250MB·캐시히트 70% · 상태: 진행 중
> 전제: OS NetworkStatsManager 미사용. 자체 앱레벨 계측 (DroidRelay TrafficLedger 패턴 차용하되 Room 구조에 맞게 변형).
> 범위: R42a 측정 기반만. DB 마이그레이션·집계 API·UI는 R42b로 분리.

## 범위

- 공통: `common/util/NetMeter`(서비스별 rx/tx 원자 카운터, 순수 JVM) + 단위테스트
- 크롤 3벌: `mac/community/plan CrawlHttp` → `HttpResult(rxBytes/txBytes)` + `NetMeter.record`
- AI 3종: `OpenRouterClient/ExaSearchClient/ModelCatalog.fetchOpenRouter` → 요청/응답 바이트 `NetMeter.record`
- 동작 동결: 수집 주기·예의(1초)·타임아웃·재시도 변경 없음. 측정만 추가.

## 원칙

- `HttpResult` 필드 추가는 기본값 0으로 호환 유지. 기존 호출부 무변경.
- `NetMeter`는 메모리 카운터만. 영속(DB)·집계는 R42b.
- 실패 응답도 바이트 계측(헤더 수준). `too large/not image`는 0이 아닌 실제 수신분 기록.
- 에러코드 신규 없음. 기존 SRV/크롤 코드 재사용. `[PERF][net]` 로그 규격.

## 검증

- 단위: `NetMeterTest`(가산·스냅샷·리셋·포맷) + 기존 6모듈 회귀
- `./build_and_run.sh` build + test + lint, 실기 E2E 생략(사용자 공존)
- DoD: 한국어·CHANGELOG Unreleased·TODO R42a·세션로그

## R42b 예고 (별도)

- `CrawlLog rxBytes/txBytes` 컬럼 + Migration + DAO SUM + `/api/stats` envelope + 대시보드/웹 표시 + 일일 예산 WARN

## R42b 실적 (2026-09-22)

- DB: mac v6→7·community v6→7·plan v5→6 (`crawl_logs rxBytes/txBytes`, 기존 행 0)
- 기록: 3 워커 NetMeter 델타 → `logResult(rxBytes/txBytes)` (성공·실패 전부)
- 집계: mac/community `netTotals(30)` 캐시 + plan 24h 합산(`getStats`·`getOverview`·`getCrawlTrend`)
- API: `/api/stats` net 필드 3종 + collect/trends 일별·소스별 rx/tx
- 앱: 대시보드 4카드 `netLabel` (mac/community 30일·plan 24h·pj AI 프로세스 누적)
- 예산: `NetBudget` 일일 200MB 초과 시 `[트래픽]` WARN (차단 없음)
- 웹 포털 표시는 R43 관리웹 이관 시 통합
