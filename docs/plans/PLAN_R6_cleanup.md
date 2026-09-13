# PLAN_R6 — 빌드·문자열·잔정리 (2커밋)

> 로드맵 1커밋에서 2분할 (빌드 검증과 코드 정리 분리). build-logic·0402 분리·본문 리소스화는 후속.

## 6a: 빌드·매니페스트 정리 (커밋 1)

- `libs.versions.toml` [versions]에 `android-compileSdk/ android-minSdk` 추가 → 4 모듈 하드코딩 교체
  (단일 진실 유지. compileOptions/testOptions/packaging 중복은 build-logic 후속으로 문서만)
- 권한 일원화: 9종 + `SystemForegroundService(dataSync)`를 `:app` 단일 소유,
  mac/plan 매니페스트에서 삭제 (service/receiver 선언만 남김, common 3종 유지)
- 검증: 빌드 + 머지드 매니페스트 확인 (`apk` 덤프 또는 설치 후 부팅리시버·FGS 동작) + 실기 health

## 6b: 코드 잔정리 (커밋 2)

- 빈 catch 로그 (10여곳, 1줄씩): CrawlWorker 실패누적 저장실패, DB close, CrawlHttp,
  NotificationFragment 액션 6곳, Home/Settings 사용자 액션 4곳.
  에러코드는 `error_message_ko.json` 정의분만 사용 (신규 필요 시 json 먼저 추가).
  의도적 무시(onDestroy·알림정리·JupLog 재귀·SourceLocks 경합·NetUtils 폴백)는 제외
- `SourceLocks.release`: idle 락 `remove` (무한증가 구조 해소, 3줄)
- `lastSeedStatus`: `try-finally` (취소 시 idle) + 시작시각 포함. 영속화는 후속
- Toast/채널/푸시제목 ~20개 리소스화 (`mac_/plan_` 체계, 포맷·DB저장값·크롤러 파싱용 제외)
- `docs/CHANGELOG.md` 문서 유령 2종 (`E-AND-STORE-0511`, `E-AND-SYNC-0521`) 정리
- 손대지 않음: `E-AND-DB-0402` 분리, 요약·알림 본문, `escape/envelope` 통일 (후속)
- 검증: 단위 + lint + 실기 (토스트·채널·시드 상태 동작 확인)

## DoD (각 커밋)

- assembleDebug + 단위 + lint 오류 0 + 실기 설치·health 200×2·크래시 0
- CHANGELOG 1.8.0 (6b에서 bump, versionCode 10) + TODO R12 + 세션 로그
