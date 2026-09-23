# PLAN_v23 — mac 포털 커뮤니티 메뉴 (arch=B)

## 요구사항
- 메뉴 순서: 메인 · 앱 스토어 · 맥 게임 · **커뮤니티** · 뉴스
- 카테고리 3: 애플(apple) / 맥(mac) / AI(ai) + 전체
- 소스 6 · 주기 30분 · 설정 소스 그룹 4번째
- UX: 목록 + 본문 상세 (news와 동일 패턴)

## 아키텍처 (B: mac 전용)
- `TYPE_COMMUNITY_BOARD` + `CommunityBoardCrawler` (SelectorConfig 이식)
- 새 테이블 `community_posts` — `news_articles` 비사용 (main 충돌 방지)
- DB v9 → v10 (`MIGRATION_9_10`)
- API `GET /api/community` · `GET /api/community/{id}`
- community(3040) 서비스와 독립

## 소스 시드
| id | 이름 | URL | main | enabled |
|---|---|---|---|---|
| community_damoang_apple | 다모앙 애플모앙 | damoang.net/applemoang | apple | true |
| community_damoang_mac | 다모앙 맥모앙 | damoang.net/macmoang | mac | true |
| community_damoang_ai | 다모앙 AI | damoang.net/ai | ai | true |
| community_clien_mac | 클리앙 MAC | m.clien.net/service/board/cm_mac | mac | true |
| community_dc_apple | DC 애플 | gall.dcinside.com/board/lists?id=apple | apple | **false** (본문 0bytes 차단) |
| community_dc_macbook | DC 맥북 | gall.dcinside.com/mgallery/...id=macbook | mac | **false** |

## 셀렉터 (실측 2026-09-23)
- **다모앙**: Googlebot UA · `a.post-row` / `.post-title` / row href / HH:MM 시각 / 상세 `.prose`
- **클리앙**: `div.list_item` · `a.list_subject` · `.nickname` · `.list_time` · exclude `.notice` · 상세 `.post_content article, .post_article`

## 변경 파일
- Constants · CommunityPost · CommunityPostDao · Migration9to10 · MacDatabase
- crawler/community/{CommunitySelectorConfig,CommunityTimeParser,CommunityBoardCrawler}
- CommunityRepository · InitialDataSeeder · CrawlWorker · MacCommunityRoutes · MacJupJupRuntime
- mac_web/{index.html,app.js,style.css}
- test: CommunityBoardCrawlerTest

## DoD
- unit 통과 · `node --check app.js` · 메뉴 5개·설정 그룹·필터·상세 노출
- DC는 시드 off 유지 (차단 해제 시 토글 on)

## 이슈
- Beads: JupJup-294
