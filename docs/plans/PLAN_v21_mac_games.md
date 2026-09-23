# PLAN_v21 — 맥 게임 메뉴 (Mac Games)

> 이슈: JupJup-l0g · 브랜치: feat/mac-games  
> 범위: 맥줍줍 포털 4열 메뉴 + Steam/Epic 게임 수집 + /api/games + 메인 노출

## 1. 결정 사항

| 항목 | 결정 |
|---|---|
| 메뉴 순서 | 메인 → 앱 스토어 → **맥 게임** → 뉴스 |
| Steam 소스 | `search/results` API (`maxprice=free&os=mac&category1=998`) — F2P 페이지 facet HTML은 JS 렌더라 동등 API 사용. 상위 40건 + tagids 장르 |
| Epic 소스 | `freeGamesPromotions` 주간 무료 API (browse CF 403 회피). 맥 전용 필터 없음 — 주간 무료 전체 |
| 저장 | `apps` 테이블 재사용, `category="게임"`, `tags`에 `game,{genre},{steam\|epic}` |
| 앱 스토어 | `excludeGames=true` 기본 → `category != '게임'` 제외 |
| 메인 | 조합 A — 하이라이트 4th 카드 + «새로운 맥 게임» 가로스크롤 + 히어로 통계 |

## 2. 카테고리(장르) — Steam tagid → 한글

전체 / 액션(19) / 어드벤처(21) / RPG(122) / 전략(9) / 시뮬레이션(599) / 퍼즐(1664) / 캐주얼(597) / 인디(492) / 멀티(128) / 레이싱·스포츠(699·701) / 호러·서바이벌(1667·1662)

## 3. 구현 체크리스트

- [x] GameGenres 유틸 + Constants 소스/타입
- [x] SteamFreeMacCrawler + EpicFreeGamesCrawler + Factory/Seeds
- [x] AppFilter.excludeGames + AppDao listGames/countGames
- [x] AppRepository.games + GET /api/games + /api/main games/counts.games
- [x] Steam/Epic fixture 테스트
- [x] 포털: 메뉴 4열 · view-games · 게임 뷰 · 메인 하이라이트/스크롤
- [x] 문서 TODO·CHANGELOG·DESIGN
- [x] node --check · unit test · build · 실기 E2E

## 4. API

`GET /api/games?genre=&source=&sort=newest&page=&pageSize=`
→ `{ games:[appElement + store/genres/sourceUrl], total, page, pageSize }`

`GET /api/main` 추가: `counts.games`, `games: [×8]`
