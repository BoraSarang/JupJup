# PLAN_R5 — 성능·DB (2커밋)

> 원칙: 쿼리 동등성 우선. 페이징 SQL 이전·mac stats 캐시·영속화는 범위 밖.

## 5a: DB 쿼리 최적화 (커밋 1)

- `PlanSourceMappingDao.getByPlanIds(ids)` 신규 (`WHERE planId IN`) →
  `PlanRepository.getPlans` 1+N → 2쿼리 (`groupBy` 메모리 조인),
  `getPlanWithSources`도 배치 재사용 (2왕복 → 동일 2왕복이나 경로 통일)
- `saveCrawlResults`: 행마다 `getById` → 기존 `getByIds` 1회 + `associateBy`
- 소스관리 N+1: `CrawlLogDao` 양쪽에 `getRecentBySourceIds(ids, since)` 배치 신규 →
  plan `observeListItems` (Flow map 내 suspend/row 제거, `combine` 또는 선조회) +
  mac `getListItems` (루프 제거)
- `getCollectionHealth` 미스 페널티 축소: 소스별 `getRecentBySource` 루프 → 배치 1회
  (7d 윈도우 + 코틀린 `take(10)`), `planRows().maxOf` 전건 스캔 → `getLatestCollectedAt()` 재사용
- `logResult` 원자화: 양쪽 `insert + 상태갱신`을 `db.withTransaction` (PlanRepository 관용구)
- plan 인덱스 2종 + `Migration4to5` + version 5 + 등록:
  `Index(isNew, firstCollectedAt)` (getNewSince), `Index(networkType, mvnoNetwork, price)` (getFiltered).
  기존 단일 인덱스 유지 (삭제 시 마이그레이션 복잡도 증가)
- 테스트: `getPlans` 배치 조인 mockk (getByPlanIds 1회 검증) + 기존 스위트.
  logResult는 디바이스 수집 E2E로 검증 (mockkStatic 범위 초과)
- 검증: 단위·lint + 실기 설치(기존 DB 승계 확인: stats 데이터 유지) + 수집 1회 + stats 200

## 5b: 블로킹 제거 (커밋 2)

- plan `serveAsset`: route 스레드 직접 I/O → `withContext(IO)` + 실패 로그 (mac 패턴)
- `MainActivity.showAbout`: `getLocalIp` Main 호출 → 포트 조회 IO 블록으로 합류
- 양 `HomeViewModel.refresh`: `localIp` 대입 Main → IO Triple에 포함
- 양 `isServiceRunning` 무타임아웃 `Socket()` → 공통 `NetUtils.isPortOpen` (500ms)
- 양 `startInForeground(text = runningText())`: 기본인자가 Main에서 선평가 →
  시그니처 `text: String? = null` + 본문 `isForeground` 체크 뒤 계산 (동작 동일)
- 테스트: 기존 스위트 (VM 미변경 경로 없음). 실기: About·홈 새로고침·FGS 재시작 경로 확인

## DoD (각 커밋)

- assembleDebug + 단위 + lint 오류 0 + 실기 설치·health 200×2·크래시 0
- 5a 추가: 마이그레이션 승계 (삭제 데이터 0, stats 유지) + 수집 E2E 1회
- CHANGELOG 1.7.0 (5b에서 bump, versionCode 9) + TODO R11 + 세션 로그
