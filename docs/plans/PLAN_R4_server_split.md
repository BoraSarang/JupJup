# PLAN_R4 — God object 분리 (2커밋)

> 원칙: 구조 분리(4a)는 동작 동결(이동만). 의미 변경(4b)은 포털 영향 확인 후. envelope·escape 정책 통일은 별도 단계로 (포털 파서 동결).

## 4a: 서버 구조 분리 (커밋 1, 동작 동결)

- 방식: `fun HttpServerService.xxxRoutes(route: Route)` 확장 함수로 라우팅 블록 절단.
  같은 클래스 확장이라 `application` 해소·`return@post` 의미가 이동 전과 동일. `app()`·`restartServer()`·
  `scope`·`currentPort`·`lastSeedStatus(mac)`만 `private` → `internal` (동일 모듈 접근용, 동작 무변경).
- mac `server/` 신규 (HttpServerService는 생명주기+startServer 배선 ~60줄로 축소):
  - `MacAssetRoutes.kt` — 정적 4종(`/, style.css, app.js, favicon.svg`) + `serveAsset`
  - `MacItemRoutes.kt` — health, apps, apps/{id}, seed×2, watchlist
  - `MacCollectRoutes.kt` — sources toggle, sync, translate
  - `MacStatsRoutes.kt` — stats×4
  - `MacNotifRoutes.kt` — 알림 7종
  - `MacSettingsRoutes.kt` — settings 2종
  - `MacServerJson.kt` — 순수 매퍼 이동 (`appsJson, detailJson, appElement, settingsJson`)
- plan `server/` 신규 (동일 구조):
  - `PlanAssetRoutes.kt`, `PlanItemRoutes.kt` (health, plans, plans/{id}, sources GET),
    `PlanCollectRoutes.kt` (toggle, sync), `PlanStatsRoutes.kt` (stats 9종 + `statsRoute`),
    `PlanNotifRoutes.kt`, `PlanSettingsRoutes.kt`
  - `PlanServerJson.kt` — 순수 매퍼 이동 (`plansJson, planJson, planElement, planFields, settingsJson` +
    통계 직렬화 `overviewJson, brand/network/bucket/point/value/health/sourceHealth/insightElement`)
  - 단, plan 로컬 `escapeJson`(절단 정책)은 이동만 하고 공용 교체는 4b 이후 별도 단계 (envelope 동결)
- 테스트: 기존 스위트 + 신규 `MacServerJsonTest`·`PlanServerJsonTest` (settingsJson·대표 매퍼 golden, 이동 검증용)
- 검증: 빌드·단위·lint + 실기 설치·health 200×2·크래시 0 + 포털 스팟체크
  (`/api/apps, /api/plans, /api/stats, /api/stats/overview` 200 + 응답 키 존재)

## 4b: plan 정렬 + 통계 캐시 (커밋 2, 의미 변경)

- D1 토글 404: plan `SourceRepository.toggle(id): Boolean?` 신규 (mac 패턴, 없음→null) +
  `POST /api/sources/{id}/toggle` 미존재 시 404 (mac과 동일 본문). 기존 `toggleEnabled(id)` 유지
  (네이티브 `SourceManageViewModel` 호출부 무변경). 포털 미사용 확인됨 (JS에 `sources/` fetch 없음).
- D2 sync 검증: `sourceId` 지정됐는데 미존재면 `respondNotFound("unknown sourceId")` (mac 동일).
  포털은 `{}` 전송이라 영향 없음.
- D3 알림 상세 폴백: 깨진 `detailJson`은 `{}` 폴백 (mac `L517~521` 패턴). StatusPages 500 승격 방지.
- D4 설정 재시작: 포트 변경 시 `scope.launch { restartServer() }` (mac 동일, 라우트 스레드 3s 블로킹 제거).
- 통계 캐시: `StatsCache.kt` 분리 (CacheEntry+cached+invalidate, TTL 5분 유지) +
  미캐시 4종(`getCrawlTrend, getNewPlanTrend, getValueRanking, getCollectionHealth`)을 `cached()`로 감쌈.
  모델(L373~523) → `Models.kt`로 이동 (충돌 시 `Stats*` 접두). `getStats`(경량 3쿼리)는 무캐시 유지.
  무효화 호출(`saveCrawlResults, cleanupOldPlans`)은 그대로 — 캐시 키 추가분도 전체 clear라 커버됨.
- 테스트: `toggle` null 단위 테스트 (기존 plan 스위트 방식 따름) + 캐시 히트 테스트(가능 시).
  포털 stats 9종 스팟체크 (첫 호출·재호출 200 + 동일 `generatedAt`이면 히트).

## DoD (각 커밋)

- assembleDebug + 단위(app 6 + mac·plan 전체) + lint 오류 0 + 실기 설치·health 200×2·크래시 0
- 4a 추가: 포털 스팟체크 4종 + 매퍼 golden 테스트
- 4b 추가: 토글 404·sync 404 curl 확인 + stats 캐시 히트 확인
- CHANGELOG 1.6.0 (4b에서 bump, versionCode 8) + TODO R10 + 세션 로그
- 남은 것 (5·6단계로): escape/envelope 통일, mac stats 캐시, N+1·인덱스, convention plugin
