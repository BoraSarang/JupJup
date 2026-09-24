# PLAN v26 — 내부 패키지 재그룹 (1~4단계)

## 목표
내부 패키지만 재그룹. **불변**: `namespace`/`applicationId`, 모듈 구조, 리소스 접두사(`mac_`/`plan_`/`pj_`/`cm_`), 포트(3010/3020/3030/3040), DataStore 파일명, 최상단 패키지, `java` 소스셋 경로.

## 단계

### 1. Migration → `data/db/migration/`
- mac·plan·community의 `*Migration.kt` 이동, pj 인라인 유지
- `*Database.kt`에 FQN import

### 2. `*Routes.kt` → `server/routes/`
- `HttpServerService`·`*ServerJson.kt` 위치 유지
- Routes에서 `HttpServerService` + ServerJson 심볼 import, 서비스에서 routes 확장 함수 import

### 3. util 도메인 분리
| 모듈 | 이동 | 루트 유지 |
|------|------|-----------|
| mac | `category/` `merge/` `translate/` | Constants, DebugLogger, TimeUtils |
| community | `category/` `text/` `merge/` | Constants, DebugLogger, TimeUtils |
| common | `net/` (NetUtils·NetMeter·NetBudget) | BaseTimeUtils, CrawlStats, Throttler |
| plan/pj | 스킵 | — |

### 4. plan 루트 테스트 미러
- crawler: CrawlConfigTest, CrawlerParseTest, MergeUtilsTest
- crawler.carrier: KtmMappingTest
- util: PlanMetricsTest, ScheduleLogicTest
- data.repository: StatsModelTest

## 검증 (단계마다)
`assembleDebug` + `testDebugUnitTest` + `lintDebug`

## 결과
- 커밋: `10b7618`, `6d53336`, `6bcb336`, 4단계 테스트 이동
- unit **273/0**
