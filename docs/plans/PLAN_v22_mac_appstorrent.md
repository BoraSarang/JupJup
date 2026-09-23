# PLAN_v22 — AppStorrent 수집 + 설정 소스 3분리

> 이슈: JupJup-67v · 브랜치: feat/mac-games
> 범위: appstorrent.ru 게임·프로그램 메타데이터 + 설정 수집소스 그룹 분리

## 1. 결정 사항

| 항목 | 결정 |
|---|---|
| 소스 1 | `appstorrent_games` — `https://appstorrent.ru/games/` + 장르 페이지 |
| 소스 2 | `appstorrent_programs` — `https://appstorrent.ru/programs/` |
| 수집 범위 | **전체** (목록 페이지 N회 순회 + 상세 enrich) |
| 콘텐츠 | **다운로드·토렌트·마그넷 제외**. 제목·아이콘·본문 요약·장르·**출처 상세 링크만** |
| 라이선스 | `FREE` (사이트 제공 기준), price=0 |
| 태그 | 프로그램: `appstorrent` · 게임: `game,{한글장르},appstorrent` |
| CF | **Googlebot UA** 우회 (일반 UA 403 / Googlebot 200). 파서 fixture 테스트 + 라이브 격리 |
| 설정 UI | 수집 소스 **앱 스토어 / 게임 / 뉴스** 3그룹 |

## 2. AGENTS.local 규칙 갱신

기존: `크랙·활성화툴 사이트 수집 금지` → 아래로 대체:

- 크랙·리패키지 사이트는 **메타데이터 전용** 허용 (제목·요약·아이콘·장르·출처 링크)
- **다운로드 URI**(magnet·torrent·warez·direct file) 저장/표시 **금지**
- 본문에서 다운로드 구간 제거 후 `GAME_DESC_MAX` 상한 적용
- 공식 API/robots 우선, 요청 간격 1초+, 브라우저 UA 명시

## 3. 구현 체크리스트

- [x] `AGENTS.local.md` 크랙 규칙 갱신
- [x] `AppStorrentHtmlCrawler` — 목록/상세 파서 + 게임/프로그램 모드
- [x] Constants SOURCE/TYPE · CrawlerFactory · InitialDataSeeder (2건)
- [x] fixture `AppStorrentParseTest` — `games-item`/`soft-item`/`#tabs-1` (12건)
- [x] 설정 서랍 3그룹 (`adminSourcesApps/Games/News`)
- [x] `gameStoreOf`/`preferredMapping`에 appstorrent
- [x] unit · node --check · CHANGELOG/TODO/세션
- [x] CF 격리: `ChallengeFail` — 403/0건이 빈 SUCCESS되지 않음
- [x] **Googlebot UA** 우회 + 실제 셀렉터 (CF 403→200)
- [x] 수동수집 cancel+REPLACE (WORKManager KEEP 백오프 막힘)
- [x] 기기 라이브: 게임 **SUCCESS 287** · 프로그램 **SUCCESS 266** · magnet/btih 0

## 4. 장르 매핑 (URL 슬러그 → GameGenres)

`action→액션`, `adventure→어드벤처`, `rpg→RPG`, `strategy→전략`, `simulation|simulator→시뮬레이션`,
`puzzle→퍼즐`, `casual|arcade→캐주얼`, `indie|sandbox→인디`, `racing|sports|sport→레이싱·스포츠`,
`horror|survival→호러·서바이벌`, `platformer|craft|story→어드벤처`,
`shooter|fighter|stealth→액션`, `roguelite|roguelike→RPG`

## 4b. HTML 셀렉터 (2026-09 실측)

- 목록: `article.games-item` (게임) / `article.soft-item` (프로그램)
- 제목: `.subtitle h2` · 아이콘: `.icon img` · 버전: `.version` · 카테고리: `.tags_plugin a[href*=games/]`
- 상세: `h1` · 본문 `#tabs-1 .body-content` · 스크린샷 `.screenshots img` · 아이콘 `og:image`
- lastcomm/뉴스 블록은 셀렉터 밖 — 자동 제외
- 상세 enrich: `DETAIL_LIMIT=30` (본문·스크린샷) · 나머지는 목록 아이콘만

## 5. 설정 소스 그룹 규칙 (프론트)

| 그룹 | type |
|---|---|
| 앱 스토어 | `GITHUB_*`, `CHART_RSS`, `ITUNES_*`, `NAME_MATCH`, `MAS_DISCOVERY`, `REDDIT_JSON`, `APPSTORRENT_PROGRAMS` |
| 게임 | `STEAM_FREETOMAC`, `EPIC_FREE`, `APPSTORRENT_GAMES` |
| 뉴스 | `NEWS_RSS` |
