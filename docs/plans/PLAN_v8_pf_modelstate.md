# PLAN_v8 — 모델 투입 상태 영속화 + 갱신 해제보존 (R20)

> 3분 초안. 증상: NIM 84개·Google 3개 전부 해제 표시. 원인: `setAllEnabled(false)`(모두 해제 버튼).
> 구조 문제: `enabledModels` 메모리 전용 + `merge`가 명시 해제를 재활성화.

## 1. 변경

- `data/preferences/ModelEnabledStore.kt` 신규 (DataStore `pf_models`,
  `stringSetPreferencesKey("enabled_models_$provider")`, absent/null 구분 유지).
- `ModelCatalog`:
  - `attachStore()` + `suspend restoreEnabled()` (저장값 있을 때만 적용, 없으면 기본 전체투입 유지).
  - `setModelEnabled/setAllEnabled/merge` → `suspend` + 종료 시 `persist` (store 미부착 시 no-op → JVM 테스트 가능).
  - `merge` 신규 판정 `!in prevEnabled` → `!in baseIds` (명시 해제 보존).
  - `merge` → `internal` (테스트 접근).
- `PromptFactoryRuntime.initialize`: `attachStore` + `appScope`에서 `restoreEnabled()`.
- 호출자: `PfRoutes` 3곳 suspend 컨텍스트 그대로 (서명만 맞춤).

## 2. 테스트

- `ModelCatalogTest` 신규 (JVM): 전체투입 기본·개별 해제·모두 해제/투입·merge 해제보존·신규 자동투입.
- 영속화 복원은 JVM 불가(DataStore) → 실기 수동 검증.

## 3. 검증

- `:services:promptfactory:testDebugUnitTest` + `./build_and_run.sh build` + 설치.
- 실기: NIM/Google 모두 투입 → 재시작(앱 kill 후 기동) → 투입 유지 확인 →
  1개 해제 → 갱신 → 해제 유지 확인. `/api/providers` enabled 카운트로 판정.
