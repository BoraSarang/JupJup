# PLAN_v25 — 본문 전체화 풀패치

> 이슈: JupJup-qai · 날짜: 2026-09-23 · 상태: 구현·검증

## 요구

GitHub README·앱 스토어·뉴스·커뮤니티 모두 **본문 전문 수집이 기본**. 스키마 분리·크롤러 상향·백필·API/프론트 렌더 개편. 기존 수집 데이터는 스키마 수정 후 재수집으로 채움(데이터 이관 없음).

## 설계

| 영역 | 변경 |
|------|------|
| 스키마 | `App.longDescription` / `longDescriptionKo` 추가 · DB **v11** `Migration10to11` (ALTER 2컬럼, 이관 없음) |
| 상수 | `APP_SUMMARY_LEN=300` · `APP_BODY_MAX=20000` · `RELEASE_NOTES_MAX=20000` · `NEWS_FULL_BODY_MIN_LEN=800` |
| GitHub | README 보강 `readmeLimit 10→90` · 전문 → `longDescription` · 짧은 소개 → `descriptionSnippet` (마커 없음) |
| 뉴스 | 피드 본문 없음/짧음(<800)/HTML 없음 → 원문 fetch · 기존 짧은 행 `updateContentBody` 백필 · 매 실행 |
| 앱/게임 | MAS·iTunes·Reddit·Steam·Epic·AppStorrent snippet(300)/long(20000) 분리 · releaseNotes 20000 |
| API | `MacServerJson`에 `longDescription`·`longDescriptionKo` 출력 |
| 프론트 | 모달 소개 = 짧은 발췌 · 세부 설명 = 전문 · 새 기능 = releaseNotes 전문 우선 |
| 번역 | `longDescriptionKo` 4000자 이하 ML Kit · `getUntranslated`에 전문 조건 포함 |

## 검증

- [x] unit **132/0** `./gradlew :services:mac:testDebugUnitTest` (MergeTest 2 · MacServerJsonTest 레거시 방어 1 포함)
- [x] `node --check app.js`
- [x] `./build_and_run.sh build` 기기 설치 Success
- [x] 실기: 앱 100/100 `longDescription` · snippet ≤300 · 게임 long 유지 · 뉴스 상세 body 7k–17k · `/api/sync` 202
- [x] 병합 상한: snippet/Ko ≤300 · 레거시 긴 snippet → longDescription 승계
- [x] API 방어: `appElement` 발췌 300 상한 + 긴 snippet 승계 (재수집 전 잔여 포함)
- [x] **JupJup-ggb** 본문 품질·표시 복구: 모달 EN 폴백·GitHub README 우선순위·id충돌·preferSnippet·RUNNING 고착 수정 — unit **142/0** · 실기 programs 266/270·games 294·GitHub long 213→228

## 잔여 (비차단)

- GitHub README: 토큰 미등록 시 미인증 한도(상한 30/시간) — 설정에서 PAT 등록 시 가속. 재수집 주기마다 본문 없는 id 선행 (JupJup-ggb closed)
- 뉴스 paywall 1건 body 307 (원문 fetch 실패 시 피드 유지 — 의도된 폴백)
- DB 잔여 긴 snippet 1건(`steam-mypartyisgrinding` windows 태그·미재수집) — API 발췌 상한으로 노출은 정상, 재수집 시 병합 승계
- AppStorrent 목록 미포함 고아 11건 (programs 4·games 7) — 주기 재수집에서 목록 재진입분만 채움 (본문 있는 266/270·294는 완료)
- KO 전문 4000자 초과는 미번역 유지(EN 전문 노출) — ML Kit 비용 절약 정책 (JupJup-ggb closed)
