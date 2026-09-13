# PLAN_R2 — :services:common 본격 추출 (2커밋)

> 원칙: 스키마 변경 없음 (Entity 이동은 5단계로), 호출부 무변경 우선, 동작 변경은 릴리스 로그 1건만.

## 2a: 모듈 + 로거/유틸 (커밋 1)
- `settings.gradle.kts`에 `:services:common` 추가, `services/common/build.gradle.kts` 신규
  (namespace `com.borasarang.common`, `resourcePrefix "jup_"`, deps: timber·datastore·ktor-server-core·serialization-json·coroutines)
- 공통 신규:
  - `log/JupLog.kt` — Timber 코어. `init(tag, isDebug, logDir)`: logcat 트리는 디버그만(전역 1회), 파일 트리는 항상(태그별 1회, `files/logs/<tag>.log`, 512KB 로테이션). 릴리스 로그 소실 해결
  - `util/NetUtils.kt` — 양쪽 바이트 동일 부분을 그대로 이관
  - `worker/SourceLocks.kt` — release는 `IllegalMonitorStateException`만 처리
  - `prefs/SettingsStores.kt` — 파일명 주입 DataStore 팩토리 (`mac_settings`·`plan_settings` 유지)
- 전환: 양 `DebugLogger`는 시그니처 동일 파사드로 (내부만 JupLog 위임, `init(context)`로 변경, TAG/raw 유지)
  - `MacJupJupRuntime:65`·`PlanJupJupRuntime:68`만 `init(appContext)`로 변경
- 전환: 양 `NetUtils` 삭제 → import 9곳을 common으로 (app 3곳 포함)
- 전환: 양 `SourceLocks` 삭제 → common 사용 (mac·plan CrawlWorker)
- 전환: 양 `PreferencesManager`의 `settingsStore`를 팩토리로 (파일명 동일, 동작 무변경)
- 제외: `TimeUtils` (주기 옵션·전용 포맷 상이, 모듈 유지), `BootReceiver` (3단계 ServiceAdapter 후), Entity 3종 (5단계 마이그레이션과 함께)

## 2b: 서버 JSON 헬퍼 (커밋 2)
- 공통 신규 `server/JsonApi.kt`: `escapeJson`(mac 무손실 정책), `receiveJsonObject`, `respondError/respondNotFound`, `putIfNotNull` ×4, `pathId/pathIdLong`
- mac: 기존 privates 삭제 → common import (동작 무변경)
- plan: 동일 동작 지점만 치환 (방금 추가한 settings 400 포함). toggle 200·sync 무검증·stats envelope·take(300) 절단은 4단계에서 (동작 변경이므로)

## DoD (각 커밋)
- assembleDebug + 단위 + lint 오류 0 + 실기 설치·health 200·크래시 0
- 2a 추가: 릴리스가 아닌 debug에서도 `files/logs/MacJupJup.log` 생성 확인 (`adb shell run-as` 또는 Device Explorer 대신 `ls`)
- CHANGELOG 1.4.0 섹션 + TODO R8 + 세션 로그
