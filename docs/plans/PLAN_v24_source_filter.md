# PLAN_v24 — 각 대메뉴 수집 소스 필터

> 이슈: JupJup-1dq (CLOSED) · 날짜: 2026-09-23 · 상태: 완료

## 요구

앱 스토어·맥 게임·커뮤니티·뉴스 사이드바 **정렬 아래** 「수집 소스」 선택. 소스 선택 시 해당 사이트 수집분만 노출.

## 설계

| 메뉴 | UI 위치 | API 파라미터 | 소스 type |
|------|---------|--------------|-----------|
| 앱 스토어 | `#appSourceChoices` (정렬 하단) | `/api/apps?sourceIds=` | 앱형 (뉴스/커뮤니티/게임 제외) |
| 맥 게임 | `#gameCollectSourceChoices` (정렬 하단, 기존 스토어 소스와 별개) | `/api/games?sourceId=` | STEAM_FREETOMAC / EPIC_FREE / APPSTORRENT_GAMES |
| 커뮤니티 | `#cmSourceChoices` (목록 칼럼 상단 필터 카드) | `/api/community?sourceId=` | COMMUNITY_BOARD |
| 뉴스 | `#newsSourceChoices` (A/B 공통 필터 바) | `/api/news?sourceId=` | NEWS_RSS |

- 소스 후보: `/api/watchlist` 1회 캐시 → `sourcesForMenu(kind)` type 분기
- state: `filters.sourceId` · `games.sourceId` · `news.sourceId` · `community.sourceId`
- 선택 시 page=1 (+ news/community detailId=null) 후 재조회
- DAO: `AND (:sourceId IS NULL OR sourceId = :sourceId)` (게임·뉴스·커뮤니티)

## 검증

- [x] unit **130/0** `./gradlew :services:mac:testDebugUnitTest`
- [x] `node --check app.js`
- [x] `./build_and_run.sh build` 기기 설치 Success
- [x] API 필터: news_macrumors 45/317 · community_damoang_{apple,mac,ai} 24/24/23 (전체 71) · games steam 40 / epic 2 · apps chart_rss 86 / reddit 29
- [ ] 4메뉴 UI에서 소스 칩 클릭 육안 확인 (수동)
