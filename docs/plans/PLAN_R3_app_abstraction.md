# PLAN_R3 — app 추상화 + ViewModel 테스트 (2커밋)

## 3a: ServiceAdapter + 레지스트리 + 내비 정리 (커밋 1)
- 신규 `app/.../ui/nav/Services.kt`: public `enum Service { MAC, PLAN }`, `enum ServiceTab`, `ServiceRegistry`
  (`adapters: Map<Service, ServiceAdapter>`, `fragment(service, tab)` 팩토리 — MainActivity 이중 when 이전)
- 신규 `app/.../ui/dashboard/ServiceAdapter.kt`: `loadState(ip)`·`triggerCrawl`·`setCrawlEnabled`·`setServerRunning(ctx, running)`
  + `MacServiceAdapter`·`PlanServiceAdapter` (Runtime·포트·에러코드·TimeUtils 분기 흡수)
- `DashboardViewModel`: 어댑터 맵 주입(기본값=Registry, 테스트용 교체 가능) + 8개 복사 메서드 → `triggerCrawl(svc)`·`toggleCrawl(svc)`·`toggleServer(svc)`·`refresh(retry)`
  + 생성자 팩토리 (Fragment `by viewModels { Factory(app) }`)
- 선행: mac `CrawlScheduler.scheduleAll()/triggerImmediate()`에서 `db` 파라미터 제거 (내부에서 Runtime 경유, plan과 동일) + 호출 5곳 수정
- `MainActivity`: private enum 삭제 → Services 사용, `fragCache` → `findFragmentByTag` 우선, 리스너 재귀 가드, `getSerializable(key, Class)` 현대화,
  showAbout 폴백 포트 하드코딩 → 양 `Constants.DEFAULT_PORT`, `DashboardFragment.activeService: Service`
- 검증: 빌드·lint + 실기 (서비스 전환·5탭·대시보드 카드·서버 토글·About)

## 3b: 소켓 타임아웃 + 단위 테스트 (커밋 2)
- 공통 `NetUtils.isPortOpen(port, timeoutMs=500)` 신규 → 양 어댑터 사용 (무타임아웃 `Socket()` 제거)
- 신규 `app/src/test/.../DashboardViewModelTest.kt` (JUnit4 + coroutines-test, Robolectric 불필요):
  fake 어댑터로 refresh 병합·2초 재조회·토글/트리거 위임·isCrawling 전이 검증
- 검증: `:app:testDebugUnitTest` + 실기 health·크래시 0

## DoD
- CHANGELOG 1.5.0 + TODO R9 + 세션 로그. `Service("MAC"/"PLAN")` 문자열 분기 잔존 0건 목표
