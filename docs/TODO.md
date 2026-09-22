# TODO — 줍줍 시리즈 통합 (JupJup)

> v1.0 통합 작업 목록. 항목 완료 시 `[x]`.

## R39 뉴스 수집 알림 (사용자 제보)
- [x] `NEWS_FOUND` 타입 + `createNewNewsNotification` (신규 50건 상세 포함)
- [x] `runNewsCrawl`에 신규·완료 알림 연결 (기존 무알림 해소, 실패는 기존 streak 경로)
- [x] `notif_news` 토글 (DataStore·설정 화면·웹 API) + 포털/앱 라벨
- [x] 검증: 전 모듈 unit·assembleDebug+설치·lint 성공 (실기 수집 E2E는 생략)

## R38 R30 공통화 2차 (DebugLogger·TimeUtils)
- [x] `ServiceLogger`+`BaseTimeUtils` common 승격, 4벌·3벌 해소 (호출부 무변경)
- [x] plan 전용부 유지 (formatPrice·30분 옵션·formatInterval 재정의)
- [x] 검증: BaseTimeUtilsTest 4건·전 모듈 unit·assembleDebug+설치·lint 성공
- [ ] 잔여(유예 유지): HttpServer 4벌·Scheduler/Worker·Notification 3벌·CrawlHttp (동작 분기 있어 별도 라운드)

## R37 수집예의 공용화 (R30 부분)
- [x] `HostThrottler`+`parallelFetch` common 승격 (`common.util.Throttler`)
- [x] community/mac 중복분 삭제, 테스트 common 통합 (ThrottlerTest 4건)
- [x] 검증: 전 모듈 unit·assembleDebug+설치·lint 성공

## R36 R32 2단계 상세 병렬화 (PLAN_v17 유예분)
- [x] `parallelNews` + `HostThrottler` (Semaphore 3, 호스트별 1초 예의, R35 패턴 이식)
- [x] `crawlNews` 상세 순차 → 병렬 (실패 건 스킵 유지, DB 반영은 기존 트랜잭션 그대로)
- [x] 검증: 단위 2건 신규·전 모듈 unit·assembleDebug+설치 성공, 실기 E2E 생략(사용자 공존)
- [x] 2단계 종결(사용자 결정): 10분 FGS·외부 LLM 모두 현행 유지 (A안)

## R35 R31 후순위 구조 개선 (PLAN_v16 유예분)
- [x] 상세 30건 순차→병렬: `fetchDetails`+`parallelFetch` (Semaphore 3 + HostThrottler 호스트별 1초 예의)
- [x] 보드 직렬→병렬: `crawl()` 보드 최대 3병렬 (실패 보드 스킵 유지, 전역 공유 스로틀러)
- [x] 전건 스캔 제거: `list` 매핑 getAll→getByIds, `stats` getEnabled→countEnabled
- [x] 복합 인덱스 DB v6 (`index_posts_categoryId_sourceId`, Room 자동명 일치) + MIGRATION_5_6
- [x] LIKE 전방와일드: FTS 보류 (수천 행 규모에 과잉 + JVM 검증 불가, 필터 인덱스로 절삭)
- [x] 검증: 단위 4건 신규·전 모듈 unit·assembleDebug+설치·lint + 실기 E2E는 사용자 공존으로 생략

## R34 뉴스 본문 단락화 (PLAN_v17 후속)
- [x] 크롤러 `paragraphize` (단일 `<p>` → 문장 기준 다문단)
- [x] 포털 `formatNewsBody` (플레인/단일 장문 `<p>` 문단 분리, 기존 저장분 즉시 적용)
- [x] 검증: 단위 2건·node --check·assembleDebug+설치

## R33 포털 목업 일치 재작성 (PLAN_v17)
- [x] 목업 2종 브라우저 실측 (다크 #0a0a0b·히어로·3카드·칩·분할뷰 확인)
- [x] `mac_web` 전면 재작성 (index·style·app.js, 다크 테마)
- [x] 메인: 히어로 LIVE·하이라이트 3·필터칩·가로스크롤·뉴스 4+4+4·사이드바·푸터
- [x] 뉴스: TopTab·분야탭·서브칩·티커·3열(목록/상세/레일)·북마크(localStorage)
- [x] 앱스토어: 기존 타임라인/Watchlist/통계·10종 카테고리 유지 (다크 only)
- [x] 수정: /api/main 확장(license·price·totalApps)·설명 이미지 썸네일·본문 폴백·태그 품질
- [x] 검증: 라이브 스크린샷 3종 대조 + 단위 15건·lint·node --check + 실기 E2E

## R32 맥줍줍 뉴스 리뉴얼 (PLAN_v17)
- [x] PLAN_v17 초안 (A안 확정, 예산 p95 300ms·250MB·캐시 70%)
- [x] DB: Room v4→v5 (`news_articles`·`news_app_relation` + MIGRATION_4_5)
- [x] 크롤러: `NewsRssCrawler`(RSS 12종) + TYPE_NEWS_RSS + 시드 + 15분 스케줄
- [x] 분류/요약 1단계: 키워드 규칙 + RSS 카테고리 매핑 (외부 LLM 없음)
- [x] 서버: `MacNewsRoutes` (`/api/news`·`/api/news/:id`·`/api/main`, `/api/apps` 유지)
- [x] 포털: 대시보드 뷰 + 뉴스 뷰(탭 3·서브 18종·분할 상세·원문 고정·출처 배너)
- [x] 검증: 단위 14건·assembleDebug·lint·node --check + 실기(3010) E2E 141건 수집 + CHANGELOG·세션 로그
- [x] 2단계 부분완료(R36): 상세 병렬화
- [x] 2단계 종결: 10분 FGS 루프·외부 LLM 분류 모두 현행 유지 (사용자 A안 결정)

## R31 크롤링 성능·퍼포먼스 (PLAN_v16)
- [x] PLAN_v16 초안 (예산 p95 300ms·250MB·캐시 70%)
- [x] 앱: 대시보드 4-way 병렬·IP 30s 캐시·PJ getEnabled·StatsCache 부분무효화
- [x] DB: community 트랜잭션·알림 IN 배치·mac MAX 쿼리·plan 페이지 100
- [x] 크롤러: 타임아웃 20s·정규식 precompile 6곳·mac/community backoff
- [x] 서버: community 통계 4종 캐시·thumb 메모리 캐시
- [x] 검증: assembleDebug·unit 6모듈·lint·node --check + CHANGELOG·세션 로그
- [x] 후순위(구조 변경 유예, R35에서 완료): 상세 30건 병렬·보드 병렬·LIKE 인덱스 대응·전건 스캔 제거

## R30 전체 리팩토링 + 버그·동작연결 (PLAN_v15)
- [x] PLAN_v15 초안 (1단계 전체 + 2단계 버그 범위 확정)
- [x] 1단계: build test community 추가·문자열/placeholder 하드코딩 제거·KDoc·Factory·매직포트·문서 포트 현행화
- [x] 2단계: 앱정보 pj 행·PJ 알림 최근기록·Plan cancelAll 태그화·웹 console.error
- [x] 검증: assembleDebug+설치·unit 6모듈·lint·node --check + CHANGELOG·세션 로그
- [x] 부분완료(R37 수집예의·R38 로거/시간): HttpServer·Scheduler·Notification·CrawlHttp는 동작 분기로 유예

## R29 포트 설정 실동작 + 기본 포트 변경 + 프롬 저널 설정 화면
- [x] 기본 포트 변경: 앱(4610) 서버 시작 문구·홈 레이아웃 하드코딩 포함 3010/3020/3030/3040 전수
- [x] `savePort` 버그 수정: mac/plan/community에서 `HttpServerService.start` → `restart`(ACTION_RESTART)로 교체 (재시작 안 되던 근본 원인)
- [x] 각 HttpServerService companion에 `restart(context)` 추가 (mac/plan/community/pj)
- [x] promptjournal 웹 설정 POST 재시작 누락 보완 (PjRoutes `/api/settings` → 포트 변경 시 restart)
- [x] 프롬 저널 Android 설정 화면 구축 (포트·자동시작·배터리·Exa 키·앱정보) + `SettingsViewModel`
- [x] 빌드·단위 테스트(4모듈)·lint 통과
- [x] 실기 검증: pj 3002→38602→3030, cm 3003→38603→3040 포트 변경 실제 재시작 확인 / 3000은 타 앱 점유 확인
- [x] 문서: CHANGELOG·세션 로그
- [x] 참고: spring 포트 3000은 줍줍 아님 (다른 앱이 점유, 시리즈 서버 미기동 시 대시보드 오인 가능)

## R28 상세 이미지·링크 + 보관기간 확장 (PLAN_v14, 1.13.0)
- [x] DB v5 `posts.imageUrls` + `MIGRATION_4_5` + `CommunityPost.imageUrls`
- [x] `DetailResult.imageUrls/links` + `BodyLink` + `encode/decodeImageUrls` + `PostDraft.imageUrls`
- [x] `parseDetail` 링크 블록(최대 3) + 이미지 목록(최대 5, UI 조각 제외)
- [x] `extractLinks` 이미지 전용 앵커·빈 텍스트 제외 (짤 슬라이드 링크 오염 정리)
- [x] DAO COALESCE 보충 + refresh는 새 파싱 신뢰(빈 값 클리어)
- [x] 서버 `imageCount`/`images[]` + 웹 카드 🖼 뱃지 + 모달 갤러리·🔗 렌더·sticky 액션·스크롤
- [x] 보관기간 3/7/14/30/90 라디오 + 기본 3
- [x] 테스트 4종 추가 (링크·이미지 앵커·5장 상한·JSON 왕복) + lint + 실기 브라우저 E2E
- [x] 문서: PLAN_v14 + CHANGELOG + 세션 로그
- [ ] 후순위(사용자 보류): 스크랩 기능 — 그닥 땡기지 않음

## R26 커뮤니티 뉴스 크롤러 신규 서비스 (PLAN_v12, 1.13.0)
- [x] `:services:community` 모듈 + settings include + app 의존성
- [x] DB v1 (CommunityPost·SiteBoard·CrawlSource·CrawlLog·NotificationLog) + CommunityJupJupRuntime + community_settings
- [x] 크롤러: BoardCrawler + TimeParser + PriceParser + SelectorConfig + CrawlerFactory + MVP 5소스 시드
- [x] 스케줄러·워커 (15분 하한, TTL purge) + 서버 6라우트 (posts·ranking·crawl/test·logs 포함)
- [x] 웹 포털 community_web 4파일 (10탭·언론사 필터·피드·랭킹·설정 서랍)
- [x] 앱 통합 (COMMUNITY enum·CmServiceAdapter·대시보드 4카드·seg_community·about 행)
- [x] 단위 테스트 (community 9종 + app 6종) + 빌드 + lint + node --check
- [x] versionName 1.13.0 (versionCode 15) bump
- [x] 문서 갱신 (PLAN_v12·CHANGELOG·DESIGN §10·ENDPOINTS·AGENTS·세션 로그)
- [x] 실기 검증: 3003 health·clien 30+30건·ruli 32건·ranking/overview/웹 200·3000/3002 회귀
- [x] R26b 상세 요약 (신규 30/회 + 미요약 백필 15/회, 500자 제한 유지)
- [x] R26b 중복 제거 (canonicalUrl UNIQUE + v2 마이그레이션, 재수집 new=0 확인)
- [x] R26b 8소스 확장 (뽐뿌26·디시50·보배30·더쿠20·오유30, 총 329건) + 주기 30분 통일
- [x] R26c 언론사 필터 서버화 + 게시판 관리 (모달·일괄적용·DB v3) + EUC-KR/줄바꿈/서로게이트 수정
- [x] R26d 이미지 글 썸네일 + 단건 새로고침 API
- [x] R26e 썸네일 프록시 (CDN 403 우회)
- [x] R27 카테고리 중심 + 2단계 관리 (PLAN_v13, DB v4, 보드 단위 스케줄)
- [ ] 후순위: 에펨코/인스티즈/MLBPARK/SLR/판 (수집 불가, 사유 기록됨) · 상세 셀렉터 chrome 잔재 다듬기

## R25 1.12.0 릴리스
- [x] versionName 1.12.0 (versionCode 14) bump
- [x] release.yml setup-android 제거 (ci.yml과 동일 수정)
- [x] CHANGELOG Unreleased → 1.12.0 확정
- [x] PR 머지 → v1.12.0 태그 + GitHub Release (release.yml)

## R24 출처 필터 실측화 (PLAN_v11)
- [x] PLAN_v11 초안
- [x] 백엔드: countBySource + sourceIds 필터 + trends.bySource
- [x] 프론트: 출처 동적 렌더 + 필터 전송 + 리셋
- [x] 실기 검증 (합계 일치·필터 조합·브라우저 상호작용)
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R23 모델 관리 수정 + Zen 추가 + NIM 삭제 (PLAN_v10)
- [x] PLAN_v10 초안
- [x] stale 내성 (편집폼 placeholder + 정적 nemotron + merge 가드 + restore 유지)
- [x] OpenCode Zen 공급자 (enum·ZenClient·factory·catalog·refresh·웹)
- [x] NIM 삭제 (NimClient·enum·분기·웹·테스트)
- [x] ModelCatalogTest 7/7 + 빌드 + lint + 실기 검증
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R22 프롬프트팩토리 → 프롬프트 저널 개명
- [x] 인벤토리 (표시 40건·코드 60파일·DB/DataStore/Work 파일명)
- [x] 표시 이름 교체 (한글·영문 `Prompt Journal`)
- [x] 코드 식별자 개명 (모듈·패키지·클래스·리소스 `pj_`·enum·에셋·채널)
- [x] 데이터 마이그레이션 (`migrateLegacyFiles` + `migrateLegacyWork`)
- [x] 빌드 + 단위 + lint + 실기 검증 (3002·웹·프롬프트 2건·실행기록 승계)
- [x] 문서 갱신 (AGENTS·DESIGN·CHANGELOG·PLAN 파일명·세션 로그)

## R21 맥줍줍 웹 포털 리디자인 (PLAN_v9)
- [x] PLAN_v9 초안 작성
- [x] style.css 디자인 토큰 + 레이아웃 전면 재작성
- [x] index.html 시맨틱 HTML 구조 재작성 (사이드바/그리드/모달)
- [x] app.js 로직 전면 재작성 (사이드바 상태/카드 렌더/모달 탭)
- [x] 빌드 + smoke/unit 테스트 + 실기 검증
- [x] 문서 갱신 (CHANGELOG·DESIGN·세션 로그)

## R20 모델 투입 상태 영속화 (PLAN_v8)
- [x] PLAN_v8 초안
- [x] ModelEnabledStore + ModelCatalog 영속화·merge 수정
- [x] ModelCatalogTest 신규 (4/4)
- [x] 빌드 + 설치 + 실기 검증
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R19 역슬래시·헤드라인 폴백 (사용자 피드백 2종)
- [x] `\id\` → 코드 칩 (deslash 수동 스캐너, 본문/요약/카드+복사버튼)
- [x] 내용 없을 땐 H1 = 신규 무료 모델 없음 (빈 응답·라벨뿐인 첫줄)
- [x] 하네스 13어설션 + 빌드 + 설치 + 3포트 헬스체크 + 문서 갱신

## R18 뉴스룸 v2 다듬기 (v2 키트 6개 항목)
- [x] H1 35자 추출 (레거시 긴 첫줄 대응) + kicker 일일 브리핑 날짜 + 아카이브 요약=인사이트 1줄
- [x] 서비스 표→카드형 섹션 (배지+ID 복사+비고+↗출처), 요약 표는 paper 배경
- [x] 휴간 회색점 + 인사이트 lead 타이포 + keep-all + masthead 간격
- [x] 빌드 + 설치 + 실기 검증
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R17 뉴스룸 다듬기 3종 (v1.12.0, 사용자 피드백)
- [x] 시드 프롬프트 헤드라인 지침 (첫 줄 35자 이내 평문) + 기기 1번 프롬프트 PUT 동기화
- [x] BREAKING 조건부 (신규 모델 있을 때만, 없으면 휴간) + 메타에서 SUCCESS/모델명 제거 (툴팁으로)
- [x] 왼쪽 요약 2줄 클램프 CSS
- [x] 브리핑 채널 목록 (편집/폐간) — 서랍 Tab1 빈 화면 수정
- [x] 빌드 + 설치 + 3포트 헬스체크 + 문서 갱신

## R16 뉴스룸 리디자인 — 발행 아카이브 (v1.12.0, PLAN_v7)
- [x] PLAN_v7 초안
- [x] 웹: Masthead + Tailwind CDN + serif + 발행주기 Pill
- [x] 웹: 아카이브 뱃지 (제N호·조간/석간·BREAKING/휴간) + 기사 지면 (H1/kicker/factbox)
- [x] 웹: 용어사전 전량 치환 + 설정 서랍 2탭 (브리핑 채널/취재원 관리)
- [x] 빌드 + JS 체크 (실기 검증 잔여: 최신호 발행·기사 복사·이 호 폐기·지침서 저장·동기화)
- [x] 문서 갱신 (DESIGN·CHANGELOG·세션 로그)

## R15 프롬프트 저널 UX 정리 (v1.11.x, PLAN_v6)
- [x] PLAN_v6 초안
- [x] 백엔드: refresh 응답 added 포함 + 일괄 enabled API + setAllEnabled
- [x] 웹: 프롬프트별 마지막 응답 1건 카드 + 마크다운 렌더 + 기록보며 인라인 수정 + 모두사용/해제 + duration 포맷
- [x] 후속: 인사이트/카드 중복 제거 + GFM 테이블 렌더 + 모델 검색
- [x] v3 전면 개편: 슬림헤더+칩+2열 마스터디테일+서랍, 맥줍 md/CSS 이식, 모달2층 제거, toast
- [x] 빌드 + JS 체크 (실기 검증 잔여: 표 리포트·날짜 전환·서랍 저장·토스트)

## R14 프롬프트 저널 재설계 — 다중 프롬프트 관리 (v1.11.0)
- [x] PLAN_v5 초안
- [x] DB v2 마이그레이션 (Prompt 엔티티·DAO·MIGRATION_1_2·초기화)
- [x] ProviderKeyStore (공급자별 API키 DataStore) + ModelCatalog (갱신·활성토글)
- [x] 라우트 재설계 (prompts CRUD/executions/insights/providers)
- [x] 스케줄러·워커 프롬프트id별 + 이전결과 주입
- [x] 웹 재설계 (조회+관리, 모바일 대응) + 앱 축소·어댑터 정리
- [x] 빌드 + 단위 + lint + 실기 검증 (시드 일치·SUCCESS×2·주입·웹UI)
- [x] versionName 1.11.0 (versionCode 13)으로 bump
- [x] 문서 갱신 (CHANGELOG·PLAN_v5·세션 로그)

## R13 잔여 정리 (R7)
- [x] PLAN_R7 초안
- [x] 7a 에러코드 조회/저장 분리 (0404 신규 + 조회 9곳)
- [x] 7b escape/envelope 통일 (공용 승격 + 바이트 동등)
- [x] 7c mac stats 캐시 (StatsCache 공용 승격 + 무효화)
- [x] 7d-1 본문 리소스화 / 7d-2 시드 영속화 / 7d-3 build-logic
- [x] 빌드 + 단위(131/131) + lint + 실기 검증 (health·stats·시드영속·크래시 0)
- [x] versionName 1.9.0 (versionCode 11)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R12 리팩토링 6단계 — 빌드·문자열·잔정리
- [x] PLAN_R6 초안
- [x] 6a 빌드·매니페스트 정리 (SDK 카탈로그 + 권한 app 일원화)
- [x] 6b 코드 잔정리 (catch 로그·SourceLocks·lastSeedStatus·문자열 리소스화·문서유령)
- [x] 빌드 + 단위(131/131) + lint + 실기 검증 (health·머지드매니페스트·크래시 0)
- [x] versionName 1.8.0 (versionCode 10)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R11 리팩토링 5단계 — 성능·DB
- [x] PLAN_R5 초안
- [x] 5a DB 쿼리 최적화 (배치 4종·트랜잭션·인덱스+마이그레이션 4→5)
- [x] 5b 블로킹 제거 (serveAsset·getLocalIp·Socket·FGS 기본인자)
- [x] 빌드 + 단위(131/131) + lint + 실기 검증 (DB승계·수집E2E·health·크래시 0)
- [x] versionName 1.7.0 (versionCode 9)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R10 리팩토링 4단계 — God object 분리
- [x] PLAN_R4 초안
- [x] 4a 서버 구조 분리 (Route 확장 절단 14파일, 동작 동결 + 매퍼 golden 테스트)
- [x] 4b plan 정렬(D1~D4) + 통계 미캐시 4종 캐시 + StatsCache 분리
- [x] 빌드 + 단위(129/129) + lint + 실기 검증 (health·포털 스팟체크·404·캐시히트·크래시 0)
- [x] versionName 1.6.0 (versionCode 8)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R9 리팩토링 3단계 — app 추상화 + ViewModel 테스트
- [x] PLAN_R3 초안
- [x] 3a ServiceAdapter + ServiceRegistry + 내비 정리 (CrawlScheduler db 파라미터 제거 포함)
- [x] 3b 소켓 타임아웃(500ms) + DashboardViewModelTest 6종 (ioDispatcher 주입으로 결정적 테스트)
- [x] 빌드 + 단위(app 6/6·mac+plan 106/106) + lint + 실기 검증 (health·크래시 0)
- [x] versionName 1.5.0 (versionCode 7)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## D1 문서·스캐폴드
- [x] AGENTS.local.md
- [x] docs/plans/PLAN_v1_jupjup.md
- [x] docs/TODO.md
- [x] docs/DESIGN.md
- [x] docs/CHANGELOG.md
- [x] docs/PERMISSIONS.md
- [x] docs/api/ENDPOINTS.md
- [x] error_message_ko.json (루트)
- [x] settings.gradle.kts / root build.gradle.kts / gradle 카탈로그 / wrapper
- [x] .gitignore / local.properties / build_and_run.sh

## S1 services:mac 이관
- [x] services/mac/build.gradle.kts (namespace com.borasarang.macjupjup)
- [x] services/mac/Manifest (권한+서비스+리시버)
- [x] MacJupJupApplication → MacJupJupRuntime(object, initialize(context))
- [x] HttpServerService: app 캐스팅 → Runtime, asset 경로 mac_web/
- [x] worker/scheduler/fragment/VM 캐스팅 치환 (Runtime)
- [x] 리소스 `mac_` 접두사 + 코드 R 참조 치환
- [x] 테스트 이관

## S2 services:plan 이관
- [x] services/plan/build.gradle.kts (namespace com.borasarang.planjupjup)
- [x] services/plan/Manifest
- [x] PlanJupJupApplication → PlanJupJupRuntime
- [x] HttpServerService: app 캐스팅 → Runtime, asset 경로 plan_web/
- [x] worker/scheduler/fragment/VM 캐스팅 치환
- [x] 리소스 `plan_` 접두사 + 코드 R 참조 치환
- [x] 테스트 이관

## A1 app 통합
- [x] JupJupApplication (두 Runtime 초기화 + autoStart 서버 기동)
- [x] MainActivity (하단 서비스 네비 + 기능 탭)
- [x] app Manifest (LAUNCHER·통합 테마·아이콘)
- [x] app 리소스 (테마·아이콘·문자열·레이아웃)

## B1 빌드 검증
- [x] ./build_and_run.sh build (assembleDebug) 성공
- [x] 단위 테스트 통과 (두 서비스)
- [x] lint 통과

## C1 CI/workflow
- [x] .github/workflows/ci.yml 통합 (Pages 제거)
- [x] .github/workflows/release.yml

## R1 마무리
- [x] README.md (한) / README.en.md (영)
- [x] 실기 검증: 포털 3000·3001 HTTP 200, 크래시 없음 (`52ea844`)
- [x] 최종 DoD 체크 + CHANGELOG 갱신 (1.0.1 수정·1.1.0 UI 개편 반영)
- [x] 첫 커밋 (`c724b9f`)
- [x] 통합 UI 개편: 하단 햄버거+기능 드로어, 앱바 서비스명, 아이콘 팩 적용

## R2 1.1.0 UI 개편 (별도 커밋 진행)
- [x] 리디자인 코드 커밋

## R3 1.2.0 시리즈 인사이트
- [x] InsightFragment + InsightViewModel (두 서비스 병렬 카드·수집/서버 조작)
- [x] 하단 인사이트 탭 + 드로어 시리즈 항목 + 앱바 타이틀 규칙
- [x] 빌드 오류 수정 (TimeUtils·M3 스타일·non-transitive R·refreshData)
- [x] 예외 처리 (서비스별 DebugLogger + 에러코드) + 화면 진입 로그
- [x] 오타 수정 (줄줍→줍줍)
- [x] 단위 테스트 + lint 통과, 실기 설치·크래시 확인
- [x] connected 테스트 8/8 통과 (app 인사이트 스모크 2 + mac Room 6), 포털 3000·3001 HTTP 200
- [x] versionName 1.2.0 (versionCode 2)으로 bump
- [x] 문서 갱신 (DESIGN·CHANGELOG·README 한/영·PLAN·세션 로그)

## R4 1.3.0 서비스-우선 내비게이션 개편
- [x] N1 내비게이션 재구성 (세그먼트+5탭+드로어 제거+오버플로우)
- [x] N2 대시보드/홈 정리 (insight→dashboard 리네임+활성 강조)
- [x] N3 폴리시+테스트+실기 검증 (connected app 4/4 + mac 6/6, 포털 200/200, 크래시 0)
- [x] N4 문서+DoD+커밋

## R8 리팩토링 2단계 — :services:common 추출
- [x] PLAN_R2 초안
- [x] 2a 모듈 + JupLog(릴리스 파일 로그) + NetUtils + SourceLocks + SettingsStores
- [x] 2b 서버 JSON 헬퍼 단일화 (mac 삭제·plan 20곳, 동작 동일만)
- [x] 빌드 + 단위 + lint + 실기 검증 (health·파일로그·승계·API 동등성·크래시 0)
- [x] versionName 1.4.0 (versionCode 6)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R7 리팩토링 1단계 — P0 안전 수정
- [x] PLAN_R1 초안
- [x] plan DB 폴백 데드코드 + 백업
- [x] 설정 범위 검증 (양 서비스 + saveSettings)
- [x] plan 워커 폭증 차단 (unique KEEP + SourceLocks)
- [x] build_and_run.sh pipefail + stale APK 가드
- [x] 빌드 + 단위 + lint + 실기 검증 (health·400·연타·크래시 0)
- [x] versionName 1.3.2 (versionCode 5)으로 bump
- [x] 문서 갱신 (CHANGELOG·세션 로그)

## R6 1.3.1 대시보드 서버 상태 레이스 수정
- [x] PLAN_v3 초안 (원인: refresh가 서버 바인드 전 소켓 체크)
- [x] DashboardViewModel 추적 재조회 (2초 뒤 1회) + 재조회 로그
- [x] 빌드 + 실기 설치·크래시 확인 (포털 3000·3001 HTTP 200)
- [x] 문서 갱신 (CHANGELOG·세션 로그)
- [x] versionName 1.3.1 (versionCode 4)으로 bump

## R5 1.3.0 릴리스 (첫 푸시)
- [x] 사전 검증 (assemble 성공 + 단위 mac 57/plan 49 + lint 오류 0, 기기 설치 없이)
- [x] origin 첫 푸시 (main)
- [x] CI lint 실패 수정 (SpecifyForegroundServiceType, 라이브러리 매니페스트에 dataSync 선언)
- [x] v1.3.0 태그 + GitHub Release (release.yml, CHANGELOG 1.3.0 섹션)
- [x] 세션 로그 갱신

## PJ1 프롬프트 저널 — 모듈 스캐폴드 (PLAN_v4)
- [x] PLAN_v4 초안
- [x] `:services:promptjournal` 모듈 생성 (build.gradle.kts, AndroidManifest.xml)
- [x] `settings.gradle.kts` include 추가
- [x] `build-logic/jupjup.base` 적용 확인
- [x] 빌드 검증 (`:services:promptjournal:assembleDebug`)

## PJ2 프롬프트 저널 — DB·DataStore·Runtime
- [x] Room DB (`PromptJournalDatabase`) + DAO + Entity
- [x] DataStore PreferencesManager (`pj_settings`)
- [x] PromptJournalRuntime (object, initialize)
- [x] DebugLogger (common.JupLog 파사드)
- [x] Constants (포트 3002, 에러코드 프리픽스)

## PJ3 프롬프트 저널 — AI 클라이언트
- [x] AiProvider enum (OPENROUTER, NIM, GOOGLE_AI_STUDIO)
- [x] AiClient 인터페이스
- [x] OpenRouterClient 구현
- [x] NimClient 구현
- [x] GoogleAiStudioClient 구현
- [x] AiClientFactory
- [x] PromptExecutionRepository

## PJ4 프롬프트 저널 — HTTP 서버
- [x] HttpServerService (Ktor CIO 포트 3002)
- [x] PjRoutes (`/api/health`, `/api/executions`, `/api/execute`, `/api/settings`, `/api/models`)
- [x] PjAssetRoutes (pj_web/ 정적 서빙)

## PJ5 프롬프트 저널 — 스케줄러
- [x] PromptJournalScheduler (WorkManager)
- [x] PromptJournalWorker (AI 호출 + DB 저장 + 알림)

## PJ6 프롬프트 저널 — UI 통합
- [x] HomeFragment, ProviderManageFragment, SettingsFragment, NotificationFragment
- [x] PjServiceAdapter (app 모듈)
- [x] ServiceRegistry에 PROMPTJOURNAL 등록 (enum + fragment 분기)
- [x] DashboardFragment 카드 표시
- [x] MainActivity 내비 3열 세그먼트 추가

## PJ7 프롬프트 저널 — 웹 페이지
- [x] pj_web/index.html (목록 + 상세 + 설정 + 모델)
- [x] pj_web/app.js (API 호출 + DOM 조작)
- [x] pj_web/style.css (Material Design 스타일)
- [x] pj_web/favicon.svg

## PJ8 프롬프트 저널 — 검증 + 버전 bump
- [x] 빌드 (`:app:assembleDebug` + `:services:promptjournal:assembleDebug`)
- [x] 단위 테스트 (`:services:promptjournal:testDebugUnitTest`)
- [x] lint 오류 0 (`:app:lintDebug`)
- [x] versionName 1.10.0 (versionCode 12) bump
- [x] error_message_ko.json에 E-AND-REPORT-08xx 추가

## PJ9 프롬프트 저널 — 문서 갱신
- [x] DESIGN.md v1.4 반영
- [x] CHANGELOG.md 1.10.0 섹션
- [x] AGENTS.local.md에 포트 3002 + pj_ 접두사 언급
- [x] error_message_ko.json E-AND-REPORT-08xx 추가
- [x] 세션 로그 (.agent/session-2026-09-18-and.md)

## PJ10 프롬프트 저널 — 실기 검증 (S22, 1.10.0)
- [x] 대시보드 pj 카드 3번째 추가 (fragment_dashboard.xml + DashboardViewModel Triple + DashboardFragment)
- [x] `PjServiceAdapter.setServerRunning` 토글 역전 버그 수정
- [x] 서버 3002 기동 + `/api/health` 200 + 웹 포털 에셋 서빙
- [x] 프롬프트 저장 (무료AI모델-일일리포트-프롬프트.md) + AI 실행 SUCCESS 3건 (nemotron-3-super 등)
- [x] autoStart=true → 앱 재시작 후 서버 자동 기동 확인
- [x] 대시보드 pj 카드 갱신 확인 (실행 횟수 10회, 활성, 서버 실행 중)