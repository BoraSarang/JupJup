# PLAN_R7 — 잔여 정리 (4커밋)

> 1~6단계 후속 후보 전부 처리. 원칙: 로그 전용 변경은 묶음, 동작 변경은 포털 검증.

## 7a: 에러코드 조회/저장 분리 (커밋 1, 로그 전용)

- `error_message_ko.json`에 `E-AND-DB-0404` 신규 ("데이터를 불러오지 못했습니다")
- 조회 9곳 `0402` → `0404`: 설정 조회×2, 홈 상태 조회×2, 알림 로드×2, 대시보드, 앱정보 포트, 소스 목록(mac)
- 변경 16곳은 `0402` 유지 (토글·주기·읽음/삭제·저장·정리·수동정리)
- 검증: 단위 (로그 문자열 미단언이라 기존 스위트) + grep 0402/0404 분류 확인

## 7b: escape/envelope 통일 (커밋 2, 출력 동등)

- plan 로컬 `escapeJson`(절단) 삭제 → 공용 무손실로 (토글·설정 catch·StatusPages).
  포털은 에러 본문 미파싱(JS 확인)이라 안전. 긴 메시지는 절단→전체로 개선
- plan `statsRoute` 에러 `"""{"error":"$code"}"""` → `respondError(code, 500)` (바이트 동일 출력)
- 검증: 단위 + 실기 에러 스팟체크 (토글 404·sync 404·stats 200 유지)

## 7c: mac stats 캐시 (커밋 3)

- `StatsCache`를 `:services:common`으로 승격 (`com.borasarang.common.cache`, TTL 공유).
  plan `StatsRepository`·테스트 import만 변경 (동작 무변경)
- mac `AppRepository`: `overview·trends·collect·insightsInput`을 `cached()`로 감쌈 (TTL 5분).
  `saveApps·purgeSource·cleanup`에 `invalidate()` (기존 호출부 파악 후 전 경로)
- 검증: 단위 + 실기 stats 4종 200 + 저장 후 무효화 (수집 E2E 시 신선도)

## 7d: 본문 리소스화 + 시드 영속화 + build-logic (커밋 3개)

- 7d-1 본문 리소스화: `NotificationService` 요약 본문·`DailySummaryWorker`·`describeStats`를
  `getString(format)` + `plurals(N건)`로. DB 저장값·크롤러 파싱용은 제외 유지
- 7d-2 시드 영속화: `lastSeedStatus/StartedAt`을 DataStore에 저장 (재시작 후에도 최종 결과 표시).
  기존 메모리 변수는 읽기 캐시로 유지
- 7d-3 build-logic: `jupjup.base` convention plugin (compileSdk/minSdk/compileOptions/
  testOptions/packaging) + 4 모듈 적용. `jupjup.room`은 묶지 않음 (mac/plan만, 차이 최소화 실패 시 분리)
- 범위 제외 (기록): `getFiltered` SQL 페이징 — 381건 sub-ms라 이득 없음, 정렬 파싱 Kotlin 의존로 risk만 있음

## DoD (각 커밋)

- assembleDebug + 단위 + lint 오류 0 + 실기 설치·health 200×2·크래시 0
- CHANGELOG 1.9.0 (7d-3에서 bump, versionCode 11) + TODO R13 + 세션 로그
