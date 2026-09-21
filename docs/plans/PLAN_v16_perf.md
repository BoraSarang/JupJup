# PLAN_v16 — 크롤링 성능·퍼포먼스 리팩토링

> 플랫폼: AND · 예산: p95 300ms·메모리 250MB·캐시히트 70%·Cold Start 2.0s
> 원칙: 동작 동결(수집 예의 delay 1s·주기·보관 유지), 병목만 절삭. 상세 30건 순차·보드 직렬 같은 구조 변경은 유예.

## 앱·공통 (p95 직격)
- A. 대시보드 병렬화: 4개 loadState 순차 → `async/awaitAll` (소켓 500ms×4 직렬 해소)
- B. `NetUtils.getLocalIp` 30s 캐시 (NIC 전수 순회 제거)
- C. `StatsCache.invalidate(prefix)` 부분 무효화 추가 (전체 clear 썬더링허드 완화)
- D. PJ 어댑터 `getAll()` → `getEnabled()` (본문 대량 로딩 제거)

## DB (잠금·메모리)
- E. Community `savePosts` `withTransaction` 원자화
- F. 알림 N+1 제거: mac `getByIds` 사용, community `getByIds` 추가 후 사용 (take 50 유지)
- G. mac `overview()` 전건 MAX → `MAX(lastRunAt)` 쿼리
- H. Plan `API_MAX_PAGE_SIZE` 1000 → 100 (직렬화 폭탄 방지)

## 크롤러 (CPU·배터리)
- I. 타임아웃 30s → 20s (3서비스 Constants, 느린 서버 워커 점유 단축)
- J. 정규식 precompile: BoardCrawler 2·TimeParser 2·Plan parseDataGb·CrawlHttp charset 2
- K. mac/community 주기 워커 backoff 추가 (plan과 동일 EXPONENTIAL 30min, 재시도 폭풍 방지)

## 서버 (p95·캐시)
- L. community 통계 캐시: overview/collect/trends + stats 5분 캐시 (mac/plan 패턴)
- M. thumb 메모리 캐시 20항목·TTL 10분 (5장 갤러리 중복 fetch 제거)

## 검증
- `./build_and_run.sh build` + `test unit` + `lint` + `node --check`
- DoD: 한국어·DebugLogger·에러코드(신규 없음)·CHANGELOG·TODO·세션로그
