# PLAN_v19 — 관리웹 이관 + 내부망 전용 (R43~)

> 플랫폼: AND+server · 예산: p95 300ms·250MB·캐시 70% · 상태: 진행 중
> 목표: 앱 기능 → 웹 관리 이관, 앱은 현황+포털 런처만. 접속은 내부망 전용.
> 순서: R43(B1 mac_web + C1 IP필터) → R44(B2 plan + B3 community + C2 토큰) → R45(B4 pj + D 앱 슬리밍)

## R43 범위 (본 문서)

- B1 mac_web 관리 완성: 설정 서랍(GET/POST /api/settings) + 소스 토글·즉시수집·주기
  + 시드·번역 실행 + 알림 읽음/삭제/전체읽음. 서버 `POST /api/sources/{id}/interval` 신설.
- C1 내부망 IP 필터: `common LanGuard.isPrivateHost` + 4서비스 routing intercept,
  사설대역 외 403. 바인드 `0.0.0.0` 유지(필요), CORS 미설치 유지.

## R43 실적

- B1 mac_web 관리 서랍 + `POST /api/sources/{id}/interval` 신설
- C1 4서비스 `LanGuard.install` (사설대역 외 403)

## R44 범위

- B2 plan_web: 설정 서랍 + 소스 토글/수집/주기 (`POST /api/sources/{id}/interval` 신설)
- B3 community_web: 사이트 소스 토글/수집/테스트/비우기 + 게시글 새로고침 + 알림 서랍
  + `GET /api/sources` 신설
- C2 관리 토큰: 서비스별 `admin_token` 최초 발급·영속 + `/api 쓰기` 토큰 강제(401) +
  루프백 전용 `GET /api/admin/token` 페어링 + 4포털 fetch 패치·401 입력 재시도
- 페어링: 기기 내 브라우저 `http://127.0.0.1:PORT/api/admin/token` 또는
  `adb forward tcp:PORT tcp:PORT` 후 PC localhost 조회. 앱 토큰 표시는 R45.

## R44 실적

- B2 plan_web + B3 community_web + C2 관리 토큰 4서비스·4포털 (PR #14)

## R45 범위
- B4 pj_web: 서랍 4번째 탭(서버 설정: 포트·자동시작, GET/POST /api/settings)
- 토큰 표시: 앱 정보(About) 대화상자에 서비스별 관리 토큰 + 탭하여 복사
- D 앱 슬리밍 1차: 하단 SOURCE·NOTIF 탭 삭제 (8 프래그먼트 + 3 VM + 14 레이아웃),
  대시보드·홈·설정만 유지. 설정 화면 축소는 후속.

## R45 실적

- B4·토큰 표시·D 1차 완료 (PR #15)

## R46 범위 (PLAN_v19 완결)

- A 설정 화면 축소 4종: 웹 이관 항목(토큰·포트·보관·Watchdog·알림·로그·앱정보) UI 삭제,
  OS 전용(배터리 예외·자동시작)만 유지. DataStore 키·saveSettings 구조는 유지.
- B 웹 트래픽 표시 4포털: mac kpi + plan kpi + community 위젯 + pj `/api/health` 확장·헤더 표시

## 원칙 (R43~R44 공통)

- 쓰기 API 동작 변경 없음. 웹은 기존 API 재사용 + interval 1개 신설.
- 토큰 값은 GET 응답에 절대 포함하지 않음(기존 `githubTokenSet` 유지).
- 인증 토큰(C2)은 R44. R43은 IP 필터만으로 내부망 전용 달성.
- 앱 코드는 R43에서 삭제하지 않음(D는 R45).

## 검증

- `node --check` 3종 + unit 6모듈 + assembleDebug+설치 + lint
- 실기: 3010 포털에서 설정 저장·토글·수집·알림 스팟체크 (사용자 공존 시 생략 가능)
- DoD: 한국어·CHANGELOG·TODO·ENDPOINTS·세션로그

## R48 범위 (통합 관리자 ID/PW)

- 앱 정보(About)에 ID/PW 등록 → 4서비스 DataStore에 동일 저장 (통합).
- 서버: 통합 PW 설정 시 ID/PW 인증 우선, 미설정 시 기존 토큰 폴백.
- 웹: `X-Admin-Id`/`X-Admin-Pw` 자동 첨부 + 401 시 입력 재시도 (토큰 병행).
- 대시보드 4카드 토큰 표시·복사 마무리 (폴백 확인용).
