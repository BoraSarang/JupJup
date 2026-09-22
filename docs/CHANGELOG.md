# CHANGELOG — JupJup

## [Unreleased]

### R46 설정 축소 + 웹 트래픽 표시 (PLAN_v19 완결)
- **설정 4종**: 웹 이관 항목 UI 삭제, 배터리·자동시작만 유지
- **웹**: 4포털 네트워크 사용량 표시

### R45 관리웹 3차: pj 설정탭 + 토큰 표시 + 앱 슬리밍
- **pj_web**: 서랍 설정탭 (포트·자동시작)
- **앱 정보**: 서비스별 관리 토큰 표시 + 탭 복사
- **D**: 하단 수집·알림 탭 삭제 (웹 이관 완료분)

### R44 관리웹 2차: plan·community + 관리 토큰
- **plan_web**: 설정 서랍·소스 토글/수집/주기 (+ `POST /api/sources/{id}/interval` 신설)
- **community_web**: 사이트 소스 토글/수집/테스트/비우기·게시글 새로고침·알림 서랍 (+ `GET /api/sources` 신설)
- **C2 토큰**: 서비스별 `admin_token` 발급·영속, `/api 쓰기` 401 강제, 루프백 페어링, 4포털 자동 첨부·재시도

### R43 관리웹 1차: mac_web + 내부망 필터 (PLAN_v19)
- **mac_web 관리 서랍**: 설정 저장·소스 토글/수집/주기·시드·번역·알림 쓰기 (앱 기능 이관)
- **서버**: `POST /api/sources/{id}/interval` 신설 (앱 주기 변경 대응)
- **보안**: 4서비스 `lanOnly()` — 사설대역 외 403 (내부망 전용)
- 검증: node --check·unit·build+설치·lint

### R42b 네트워크 영속·집계·노출
- **DB**: mac v6→7·community v6→7·plan v5→6 (`crawl_logs rxBytes/txBytes`)
- **기록**: 3 워커 실행 델타 저장 · **API**: `/api/stats`·collect·trends rx/tx 노출
- **앱**: 대시보드 4카드 사용량 표시 · **예산**: 일일 200MB 초과 WARN(차단 없음)
- 검증: unit·build+설치·lint 성공

### R42a 네트워크 트래픽 측정 기반 (PLAN_v18)
- `common NetMeter` 서비스별 rx/tx 원자 카운터 + 크롤 3벌·AI 3종 계측 연결
- 동작 동결(주기·예의·타임아웃 유지), DB·API·UI는 R42b로 분리

### R41 뉴스 한글 번역
- **DB v6**: `titleKo`·`summaryKo` + MIGRATION_5_6 (앱 번역 패턴 이식)
- **워커**: TranslateWorker 뉴스 단계 (최신 30건/실행, ML Kit 온디바이스)
- **포털**: 목록·티커·상세·북마크 한/원문 표시, 전역 토글 연동
- **검증**: unit·build+설치·lint + 실기 마이그레이션 성공·무크래시

### R40 R30 공통화 3차 (CrawlStats)
- `CrawlStats` common 승격, 3벌 삭제 (호출부 import만 변경)
- 검증: CrawlStatsTest 2건·전 모듈 unit·assembleDebug+설치·lint 성공

### R39 뉴스 수집 알림 (사용자 제보)
- **문제**: 뉴스 수집 경로에 알림 호출이 전혀 없어 알림함이 비어 있었음
- **추가**: `NEWS_FOUND` 신규 뉴스 알림(최대 50건 상세) + 수집 완료 알림 연결
- **설정**: `notif_news` 토글 (앱 설정 화면·웹 `/api/settings`)
- **검증**: 전 모듈 unit·assembleDebug+설치·lint 성공

### R38 R30 공통화 2차 (DebugLogger·TimeUtils)
- `ServiceLogger`·`BaseTimeUtils` common 승격, 4벌·3벌 해소 (호출부 무변경, plan 전용부 유지)
- 검증: BaseTimeUtilsTest 4건·전 모듈 unit·assembleDebug+설치·lint 성공
- 잔여 유예: HttpServer·Scheduler/Worker·Notification·CrawlHttp (동작 분기)

### R37 수집예의 공용화 (R30 부분)
- `HostThrottler`+`parallelFetch`를 `:services:common`으로 승격, community/mac 중복분 삭제
- 테스트 common 통합 (ThrottlerTest 4건), 전 모듈 unit·빌드·lint 성공

### R36 R32 2단계 상세 병렬화 (PLAN_v17 유예분)
- **상세 병렬**: `crawlNews` 순차+1s delay → `parallelNews` (Semaphore 3 + `HostThrottler` 호스트별 1초 예의, R35 패턴 이식)
- **검증**: 단위 2건 신규·전 모듈 unit·assembleDebug+설치 성공, 실기 E2E 생략(사용자 공존)
- **2단계 종결**: 10분 FGS·외부 LLM은 현행 유지로 확정 (사용자 결정)

### R35 R31 후순위 구조 개선 (PLAN_v16 유예분)
- **상세 병렬**: `ensureSummaries`·`backfill` 순차+1s delay → `parallelFetch` (Semaphore 3 + `HostThrottler` 호스트별 1초 예의 유지, DB 반영은 순차)
- **보드 병렬**: `crawl()` 보드 직렬 → 최대 3병렬 (프로세스 전역 공유 스로틀러, 실패 보드 스킵 유지)
- **전건 스캔 제거**: `list` 매핑 `getAll` → `getByIds`, `stats` `getEnabled` → `countEnabled`
- **DB v6**: `index_posts_categoryId_sourceId` 복합 인덱스 + `MIGRATION_5_6` (Room 자동명 일치, LIKE 전방와일드는 유지 — 수천 행 규모에 FTS는 과잉)
- **검증**: 단위 4건 신규(ThrottlerTest)·전 모듈 unit·assembleDebug+설치·lint 성공, 실기 E2E 생략(사용자 공존)

### R34 뉴스 본문 단락화 (PLAN_v17 후속)
- **문제**: 뉴스 상세 본문이 한 줄로 표시 (RSS 텍스트 `Jsoup.text()` 합침 + 폴백 단일 `<p>` + 프론트 그대로 출력)
- **크롤러**: 폴백 단일 `<p>` → `paragraphize` (문장 경계 2~3문장씩 `<p>` 분할, 이스케이프)
- **포털**: `formatNewsBody` — 구조화 HTML은 유지(img 속성 강제), 플레인/단일 장문 `<p>`는 문단 분리
- **검증**: 단위 2건 추가·`node --check`·assembleDebug+설치 성공
- **기존 저장분**: 마이그레이션 없이 프론트에서 즉시 단락 표시

### R33 포털 목업 일치 재작성 (PLAN_v17)
- **배경**: 1차 구현이 라이트 테마로 목업과 불일치 → 목업 2종 브라우저 실측 후 전면 재작성
- **테마**: 다크 #0a0a0b·zinc 보더·lime/mono 포인트·Inter/JetBrains 폰트
- **메인**: 히어로 LIVE·하이라이트 3·필터칩·앱 가로스크롤·뉴스 4+4+4·사이드바 4종·푸터
- **뉴스**: TopTab(앱/뉴스/북마크)·분야탭·서브 18종·LIVE 티커·3열 분할·원문/북마크 버튼·정책 박스
- **앱스토어**: 기존 기능 전부 유지 (타임라인·Watchlist·통계·10종 카테고리·모달·알림)
- **API**: `/api/main` 확장 (license·price·totalApps)
- **크롤러**: 설명 HTML 이미지 썸네일·본문 텍스트 폴백 (상세 빈 화면 해소)
- **검증**: 라이브 스크린샷 3종(메인·뉴스·앱스토어) 대조 + 단위 15건·lint·node --check

### R32 맥줍줍 뉴스 리뉴얼 (PLAN_v17)
- **범위**: A안 확정 — `services:mac` 내 확장 (분리 신규 아님)
- **DB**: Room v4→v5 (`news_articles`·`news_app_relation` + MIGRATION_4_5)
- **크롤러**: `NewsRssCrawler`(RSS 12종: 맥 4·AI 4·보안 4) + 15분 스케줄, hash 중복 제거
- **서버**: `MacNewsRoutes` (`/api/news`·`/api/news/:id`·`/api/main`)
- **포털**: 대시보드 뷰 + 뉴스 뷰(탭 3·서브 18종·분할 상세·원문 고정·출처 배너)
- **버그 수정 3건**: Room DAO 닫힘 누락·RFC822 요일 오기·숫자 오프셋 패턴·앱매칭 단어경계
- **검증**: 단위 14건 성공·assembleDebug + 실기 설치 성공·lint 성공·node --check 통과
- **실기(3010)**: 마이그레이션 성공(앱 4813건 보존) + 뉴스 141건 수집(맥 60·AI 36·보안 45) + 상세·필터·포털 200 확인
- **유예(2단계)**: 10분 FGS 루프·외부 LLM 분류·상세 병렬화

### R31 크롤링 성능·퍼포먼스 (PLAN_v16)
- **앱·공통**: 대시보드 4개 카드 순차 → `async` 병렬 (소켓 500ms 합산 해소),
  `getLocalIp` 30초 캐시, PJ 어댑터 `getAll()` → `getEnabled()` (본문 전건 로딩 제거),
  `StatsCache.invalidatePrefix` 부분 무효화
- **DB**: community `savePosts` 트랜잭션화, 알림 조회 N+1 → IN 배치 (mac·community),
  mac `overview()` 전건 MAX → `MAX(lastRunAt)` 쿼리, plan `API_MAX_PAGE_SIZE` 1000 → 100
- **크롤러**: 타임아웃 30s → 20s (3서비스, delay 1s 예의 유지),
  정규식 precompile (BoardCrawler·TimeParser·Plan parseDataGb·CrawlHttp charset),
  mac/community 주기 워커 backoff EXPONENTIAL 30분 (plan과 통일)
- **서버**: community 통계 4종 5분 캐시 + 저장·정리 시 키별 무효화,
  thumb 메모리 캐시 20항목·TTL 10분 (갤러리 중복 fetch 제거)
- **검증**: assembleDebug 성공, unit 6모듈 성공, lint 성공, node --check 2종 통과
- **유예**: 상세 30건 순차·보드 직렬·LIKE 전방와일드·전건 스캔 — 구조 변경이라 다음 라운드

### R30 전체 리팩토링 + 버그·동작연결 (PLAN_v15)
- **1단계 전체**: `build_and_run.sh test` community 누락 추가, PJ 시작 버튼 "(3030)" 제거,
  홈 placeholder·주석 포트 하드코딩 제거, KDoc "두 서비스"→"네 서비스", Factory dispatcher 전달, 매직 포트 상수화
- **2단계 버그**: 앱정보 pj 행 추가(4서비스 표시), PJ 알림 탭 최근 20건 렌더, Plan `cancelAll()` 태그 일괄 취소 + 네임스페이스
- **웹 무음실패**: plan 5곳·community 2곳 catch에 `console.error` 추가
- **문서**: ENDPOINTS·AGENTS.local 기본포트 3010/3020/3030/3040 현행화
- **검증**: assembleDebug + 설치 성공, unit(6모듈) 성공, lint 성공, node --check 2종 통과
- **제외(정상 확인)**: 설정 POST 재시작 조건부·Registry 4×5·에셋-라우트·JS 포트·DataStore/채널 — 손대지 않음

### R29 포트 설정 실동작 + 기본 포트 변경 (전 서비스)
- **기본 포트 변경**: 맥줍 3010 · 요금 3020 · 프롬 저널 3030 · 커뮤니티 3040.
  하드코딩 잔여 전수 — 대시보드 "서버 시작 (3002)" 문구, 프롬 홈 레이아웃 `http://IP:3002`, Provider/연결 주석
- **포트 변경 즉시 적용 (근본 수정)**: mac/plan/community `SettingsViewModel.savePort`가
  `HttpServerService.start`를 호출해 이미 실행 중인 서버를 재시작하지 않던 버그를
  `restart`(ACTION_RESTART)로 교체. 각 `HttpServerService`에 `restart(context)` companion 추가.
- **프롬 저널 설정 화면 구축**: 포트·자동시작·배터리 예외·Exa 키·앱정보 (기존 스텁 교체, `SettingsViewModel` 신설)
- **프롬 저널 웹 설정 재시작 보완**: `POST /api/settings` 포트 변경 시 서버 재시작 누락 수정
- **실증**: S22 실기 — pj 3002→38602→3030, 커뮤니티 3003→38603→3040 실제 바인드 전환 확인.
  `/api/settings` 포트 변경 후 구 포트 닫힘·신 포트 응답 검증.
  참고: localhost:3000은 타 앱(외부 HTML 서버)이 점유 중 — 줍줍 서버 아님(미기동 시 대시보드 오인 소지)

## [1.13.0] - 2026-09-21
> 플랫폼: AND · 커뮤니티 뉴스 크롤러 신규 서비스 (R26~R28, PLAN_v12~v14)

### 신규
- **`:services:community` 모듈** 추가 (네임스페이스 `com.borasarang.communityjupjup`, 포트 3003)
- **Room DB v1**: `CommunityPost`(요약 500자만, 본문 저장 금지) + `SiteBoard`(보드-카테고리 매핑) + `CrawlSource`(selector_config) + `CrawlLog` + `NotificationLog`
- **통합 카테고리 10종** 정적 시드 (속보/유머/IT/신제품/게임/스포츠·차/핫딜/중고/생활/경제)
- **BoardCrawler** (V2 GenericSpider 로컬 구현): selector_config 기반 목록 수집 + `TimeParser` + `PriceParser`(핫딜·중고)
- **MVP 5소스 시드**: 클리앙 모공/알뜰, 에펨코 포텐/유머, 루리웹 베스트 (뽐뿌·인스티즈·오유·디시는 차단으로 제외)
- **Ktor 서버** (포트 3003): `/api/posts`(필터·검색·페이지네이션) `/api/posts/{id}` `/api/categories` `/api/sources` `/api/ranking` `/api/crawl/test`(셀렉터 미리보기) `/api/logs` + 통계 4종
- **웹 포털** (`community_web/`): 10탭 헤더 + 언론사 필터 + 피드(핫딜·중고 뱃지) + 랭킹/수집현황 + 설정 서랍
- **앱 통합**: 세그먼트 4열 + 대시보드 4카드 + 앱정보 커뮤니티 행
- TTL: 핫딜 3일·중고 7일·그 외 90일 (기동 시 + 수동 정리)
- **상세 요약**: 신규 게시글 상세 진입으로 500자 요약 저장 + 미요약 백필 (R26b)
- **중복 제거**: `canonicalUrl` UNIQUE + 휘발 파라미터 정규화 + DB v2 마이그레이션 (기존 중복 정리)
- **8소스 확장**: 뽐뿌·디시베스트·보배·더쿠·오유 추가 (전원 첫 수집 SUCCESS, 총 329건).
  수집 불가: 에펨코/인스티즈(JS 보안)·MLBPARK(JS 렌더)·SLR·판 — 사유 기록 후 비활성/제외
- 수집 주기 전체 30분 통일
- **언론사 필터修正**: 서버 측 복수 source_id 필터 (체크 1개여도 정확한 건수·피드)
- **게시판 관리**: 언론사별 보드 목록·on/off·카테고리·추가·삭제 + 일괄 적용 API (DB v3)
- **인코딩**: EUC-KR(뽐뿌) charset 감지 + 깨진 행 비우기 API + 요약 줄바꿈 보존 + 서로게이트 안전 절단
- **이미지 글**: 본문 대표 이미지 추출·표시 (짤 글 대응) + 단건 새로고침 API
- **썸네일 프록시**: CDN 핫링크 차단(403) 우회용 `/api/thumb`
- **R27 카테고리 중심 + 2단계 관리**: 왼쪽 카테고리 목록(상단 탭 삭제),
  사이트 관리(1차) → 게시판 관리(2차: on/off·카테고리·주기·추가·삭제),
  보드별 주기(15/30/60/120) + 보드 단위 스케줄 (DB v4),
  사이트 API + 추천 게시판 카탈로그

### 테스트
- 단위: community 9종 (파서·셀렉터·매퍼) + app 6종 SUCCESS · lint 오류 0
- 실기 (S22, 2026-09-21): 3003 health ok, 클리앙 모공/알뜰 각 30건·루리웹 32건 수집 확인,
  ranking/overview/웹 에셋 200, 3000/3002 회귀 ok
- 실측 반영: 클리앙 셀렉터 교정+공지 제외+조회수 단위, UA 봇월 회피
  (`CommunityJupJup/1.0 (contact ...)`), 에펨코 2종은 JS 보안월로 비활성 보관

### R28 상세 이미지·링크 + 보관기간 (PLAN_v14)
- **상세 이미지 목록**: `posts.imageUrls` (DB v5) — 본문 이미지 최대 5장을 메타로 저장.
  단건 새로고침·백필에서 짤·인증 글 갤러리로 활용.
- **링크 보존**: 요약 내 `🔗 텍스트: URL` 블록(최대 3개, http(s)만)·상세 모달 클릭 링크.
  이미지를 감싼 앵커/빈 텍스트는 제외(짤 슬라이드 링크 오염 방지).
- **웹 상세 모달**: 카드 `🖼N` 뱃지 + 다중 이미지 갤러리(`/api/thumb` 프록시, 52vh contain) +
  `modal-box` max-height+오버플로 스크롤 + `.actions sticky`(버튼 잘림 수정).
- **보관기간 옵션 확장**: 30/90 → **3/7/14/30/90**, 기본값 **3일**.
- 스크랩 기능: 사용자 보류 (후순위)

### 테스트
- 단위: community 13종 (파서·링크·이미지 한도·JSON 왕복) + app 6종 SUCCESS · lint 오류 0
- 실기 (S22, 2026-09-21): G마켓 글 refresh → 이미지 2장 저장·갤러리 렌더,
  디시 글 링크 3개 클릭 렌더, 스티키 버튼·스크롤·520자 이하 summary 확인

## [1.12.0] - 2026-09-19
> 플랫폼: MAC · 출처 필터 실측화 (R24, PLAN_v11)
- **실측 수집처**: 사이드바 출처를 실제 5종 + 실측 카운트로 교체 (하드코딩 placeholder 제거)
- **필터 동작화**: `sourceIds` 파라미터 → 대표 sourceId 기준 목록 필터 (DB 스키마 변경 없음)
- **트렌드 API**: `/api/stats/trends`에 `bySource[]` 추가

> 플랫폼: AND · 모델 관리 수정 + Zen 추가 + NIM 삭제 (R23, PLAN_v10)
- **불일치 수정**: 편집폼에 저장값과 다른 모델이 표시되던 문제 (카탈로그에 없던 nemotron이 첫 항목으로 둔갑).
  저장 ID가 목록에 없으면 `(목록에 없음)` placeholder로 그대로 표시, 자동 치환 금지
- **카탈로그 가드**: 정적 base 분리 보관·삭제 금지, 프롬프트 참조 ID 삭제 금지, 미등록 투입 ID 유지
- **OpenCode Zen 추가 후 삭제**: 무료 티어가 OpenCode 외부 API를 403 차단함이 실기 확인됨 → 통째로 제거
- **NIM 삭제**: 코드·웹·테스트에서 제거 (사용 프롬프트 0건, 실행기록 표기는 유지)
- 검증: ModelCatalogTest 7/7, 실기 providers OR 6·ZEN 8·NIM 소멸, 프롬프트 값 유지

> 플랫폼: AND · 프롬프트팩토리 → 프롬프트 저널 개명 (R22)
- **표시 이름**: `프롬프트팩토리` → `프롬프트 저널` 전수 (화면·웹·문서·로그), 영문 `Prompt Factory` → `Prompt Journal`
- **코드 식별자**: `:services:promptjournal` 모듈, `com.borasarang.promptjournaljupjup` 패키지,
  `PromptJournal{Runtime,Database,Scheduler,Worker}` · `Pj{Routes,AssetRoutes,ServiceAdapter,Settings}` ·
  리소스 `pf_*` → `pj_*` (레이아웃·문자열·색상·ID) · `PROMPTJOURNAL` enum · `pj_web` 에셋 · 알림 채널 `jupjup_pj_server`
- **데이터 승계** (1회 마이그레이션): `promptfactory.db(+journal/shm/wal)` → `promptjournal.db`,
  DataStore `pf_{settings,api_keys,models}` → `pj_*`, 구 Work(`promptfactory_prompt` 태그·`pf_prompt_{id}`) 취소 후 재예약
- 실기 검증: 빌드·단위·lint 통과, 3002 health `promptjournal` 응답, 프롬프트 2건·실행기록·설정 승계 확인

> 플랫폼: MAC · 맥줍줍 웹 포털 리디자인 (PLAN_v9, Apple 네이티브 톤)
- **사이드바 네비게이션**: 타임라인/Watchlist/통계 분리, 필터를 `출처`(GitHub/MAS/Homebrew 카운트)·`유형`(전체/오픈소스/프리/유료) 그룹화
- **카드 시스템**: 48px 아이콘 + 제목 + 설명 2줄 클램프, 언어 컬러닷(Swift/파이썬/JS/TS/Rust/Go/C++), NEW 도트, ★별점 간결화
- **그리드**: 3열 반응형(1100px↓ 2열, 720px↓ 1열), rounded-2xl, 소프트 섀도우, hover 떠오름
- **모달 재구성**: 64px 히어로 아이콘 + 메타 pill + CTA(홈페이지/GitHub Repo) 상단 배치, 탭(소개/특징/새기능) 구조
- **톤**: 배경 #f5f5f7, Pretendard 폰트, 다크모드 지원, macOS 메뉴바 앱답게 둥글고 가볍게
- **모바일**: ≤720px에서 사이드바 오버레이 드로어 전환, 터치 타겟 44px 이상
- **핫픽스**: 모달 포커스 링(닫기 버튼으로 초기 포커스) · 통계 막대 미표시(클래스 불일치) · 알림센터 깨진 아이콘(숨김 처리) ·
  소요시간 표기(`9시간 9분` + `YYYY-MM-DD HH:mm`) · 모달 상단 고정(6vh) · 카드 언어닷 세로 중앙 ·
  사이드바 배지 전체 카운트 고정 + 카테고리 타임라인 하위 이전(타임라인 선택 시만 표시) ·
  좁은 화면 잘림(그리드 `minmax(0,1fr)` + 카드/탑바 오버플로) · 다크모드 모달 글자색(dialog UA override) ·
  버전 테이블 셀 마크다운 렌더링

> 플랫폼: AND · 프롬프트 저널 뉴스룸 리디자인 (PLAN_v7, 외부 키트 적용)
- **Masthead**: `PROMPT JOURNAL / 프롬프트 저널` serif + 오늘 날짜 + 제N호 박스, Tailwind CDN + Noto Serif KR
- **발행 아카이브**: 왼쪽 리스트에 제N호·조간/석간/단신 + 헤드라인 요약, BREAKING/휴간 배지
- **기사 지면**: 응답 첫 줄 H1 헤드라인 + kicker + `제N호 발행 · 날짜 조간 · 취재 N초` 메타, factbox 얇은 border
- **용어 전환**: 최신호 발행/기사 복사/이 호 폐기/취재 지침서/취재원 관리·동기화·투입
- **설정 서랍 2탭**: 브리핑 채널(폼) / 취재원 관리(공급자·키·모델)
- **다듬기 (R17)**: 시드 프롬프트에 헤드라인 지침(첫 줄 35자 이내 평문), BREAKING은 신규 모델 있을 때만(없으면 휴간),
  지면 메타에서 SUCCESS/모델명 제거(모델명은 툴팁), 아카이브 요약 2줄 클램프
- **채널 목록**: 설정 Tab1에 채널 카드 (발행주기·편집/폐간, `DELETE /api/prompts/{id}` 연동)
- **v2 다듬기 (R18)**: H1 35자 추출(레거시 긴 첫줄 대응, 단어 경계+…) + kicker에 일일 브리핑 날짜,
  아카이브 요약=인사이트 1줄+페이드, 서비스 표→카드형(모델 배지+ID 복사+비고+↗출처),
  요약 표 paper(#fafaf7) 배경, 휴간 회색점, 인사이트 lead serif 17px/1.7, keep-all, masthead 간격 축소
- **역슬래시 (R19)**: LLM이 뱉는 `\모델ID\`를 코드 칩으로 (본문·아카이브 요약·서비스 카드+ID 복사),
  빈 응답·라벨뿐인 첫줄의 H1은 `신규 무료 모델 없음`으로
- **모델 투입 상태 영속화 (R20)**: `ModelEnabledStore` (DataStore `pj_models`) — 해제/투입이 재시작 후에도 유지.
  `merge` 수정으로 갱신 시 명시 해제가 부활하지 않음. `ModelCatalogTest` 4종.
- 백엔드·API·DB 변경 없음 (pj_web 3파일 + 시드 프롬프트 문구만)

> 플랫폼: AND · 프롬프트 저널 웹 v3 리포트 중심 개편 (PLAN_v6)

### 변경
- **웹 v3**: 상태카드·새로고침·3탭 삭제 → 슬림 헤더(로고+상태점+마지막실행+⚙️) + 프롬프트 칩 + 2열 마스터-디테일(PC 왼쪽 날짜·오른쪽 본문, 모바일 전체화면)
- **모달 2층 폐지**: 기록보기 불가(상세 모달이 프롬프트 모달 뒤에 깔림) 근본 해결 — 실행 항목 인라인 렌더
- **마크다운**: mac_web T-142 그대로 이식 + GFM 표 패치, `<br>`·`<URL>` 보존, `*라벨:*` 굵게, alert→toast
- **설정 서랍**: ⚙️ 우측 서랍에 프롬프트 폼 + 공급자·모델(검색·모두사용/해제·갱신 `added` 수정)
- **시드 프롬프트 출력 형식**: 표는 변경 요약에만, 서비스별 목록은 `###`+불릿 1모델1줄
- 본문 최대폭 1200→1600px, 헤더·칩을 본문 폭에 정렬

## [1.11.0] - 2026-09-18
> 플랫폼: AND · 프롬프트 저널 다중 프롬프트 재설계

### 신규
- **프롬프트 CRUD**: `Prompt` 엔티티 + DB v2 마이그레이션 (MIGRATION_1_2, 기존 실행기록 초기화)
  - 제목·본문·공급자·모델·스케줄(daily/once·시각)·활성화·이전결과주입
- **다중 프롬프트 관리**: 프롬프트별 스케줄 개별 예약 (`pj_prompt_{id}`), 실행 결과 리스트
- **이전 결과 주입**: `usePreviousResult` ON이면 직전 SUCCESS 응답 원문을 `[어제까지 기록]`에 삽입
- **공급자별 API 키 관리**: `ProviderKeyStore` (DataStore `pj_api_keys`) — 웹에서 등록/교체
- **모델 카탈로그**: `ModelCatalog` (AIModelTalk 패턴 이식) — 런타임 목록 갱신 + 모델 활성 토글
- **웹 v2 (조회+관리)**: 프롬프트 탭(인사이트+카드→상세 결과), 실행 기록, 관리 탭(프롬프트 폼+공급자·키·모델)
- **인사이트 API**: `/api/insights` 프롬프트별 최근 SUCCESS 500자 요약
- **시드 프롬프트**: MD(`무료AI모델-일일리포트-프롬프트.md`) 원문 1개 자동 등록
- 앱 홈: 프롬프트 등록·활성 건수 표시 (설정·공급자 관리 스텁화)

### 구조
- 라우트 v2: `prompts` CRUD / `prompts/{id}/executions` / `execute(promptId)` / `providers`(key·models·refresh·enabled)
- 스케줄러·워커: 프롬프트id 기반 재설계
- `PjSettings` 축소(port/autoStart만) — 단일 프롬프트 필드 제거
- `PjServiceAdapter`: 활성 프롬프트 수 표시, `triggerImmediate(first.id)`

### 개선 (S23 실기 발견)
- 모달 닫기 × 터치 타겟 44→48px
- 탭 전환 시 프롬프트/상세 모달 자동 닫힘

### 테스트
- 빌드 `:app:assembleDebug` SUCCESS · 단위 테스트 SUCCESS · lint **BUILD SUCCESSFUL**
- 실기 검증 (10.207.33.235:5555, 1.11.0): 시드 1건 자동 등록(**MD 원문과 diff 일치**),
  API키 등록→즉시 실행 **SUCCESS ×2** (id11 84.8s / id12 56.8s),
  **이전 결과 주입 확인**(id12 prompt에 직전 SUCCESS 삽입), `/api/insights` 정상,
  웹 UI 데스크톱+모바일(390×844) agent-browser 검증 PASS (콘솔 에러 0)
- versionName 1.11.0 (versionCode 13)

## [1.10.0] - 2026-09-18
> 플랫폼: AND · 프롬프트 저널 신규 서비스

### 신규
- **`:services:promptjournal` 모듈** 추가 (네임스페이스 `com.borasarang.promptjournaljupjup`, 포트 3002)
- **AI 클라이언트 3종**: OpenRouter, NVIDIA NIM, Google AI Studio — 각각 OkHttp + kotlinx.serialization 기반
- **Room DB**: `PromptExecution` 엔티티 + `PromptExecutionDao` (실행 기록 저장/조회/삭제)
- **DataStore 설정**: 공급자/모델/프롬프트/스케줄/API키/활성화 여부 영속 저장
- **WorkManager 스케줄러**: 매일/1회 실행 스케줄 + 즉시 실행 트리거
- **Ktor HTTP 서버** (포트 3002): `/api/health`, `/api/executions`, `/api/execute`, `/api/settings`, `/api/models`
- **웹 포털** (`pj_web/`): 실행 기록 목록/상세, 설정 변경, 모델 목록, 즉시 실행
- **UI 4종 프래그먼트**: 홈/공급자관리/설정/알림
- **ServiceAdapter + ServiceRegistry** 연동: 대시보드 3열 카드 표시
- **MainActivity 세그먼트** 3열 확장 (맥줍줍/요금줍줍/프롬프트 저널)
- 기본 프롬프트: 무료 AI 모델 트래킹 리포트 (참고 파일 내장)
- `PromptJournalRuntime` object: DB/DataStore/스케줄러 초기화

### 구조
- app build.gradle.kts: `:services:promptjournal` 의존성 추가
- JupJupApplication: `PromptJournalRuntime.initialize()` 호출
- Services.kt: `Service.PROMPTJOURNAL` enum + fragment 분기
- strings.xml: `nav_pj`, `dashboard_pj_title` 추가

### 테스트
- 빌드: `:app:assembleDebug` + `:services:promptjournal:assembleDebug` **BUILD SUCCESSFUL**
- lint: `:app:lintDebug` **BUILD SUCCESSFUL** (오류 0)
- 단위 테스트: `:services:promptjournal:testDebugUnitTest` **BUILD SUCCESSFUL**
- 실기 검증 (S22, 1.10.0): pj 대시보드 카드 표시·서버 3002 기동·health 200·**AI 실행 SUCCESS 3건** (nemotron-3-super-120b 35.9s 등)·autoStart 자동 기동 확인
- **실기 중 버그 수정**: `PjServiceAdapter.setServerRunning` 토글 역전 (running=true 시 start 호출) → Mac/Plan과 동일하게 stop으로 수정, 대시보드 pj 카드 추가

## [1.9.0] - 2026-09-14
> 플랫폼: AND · 잔여 정리 (리팩토링 7단계)

### 정리
- 에러코드 조회/저장 분리: `E-AND-DB-0404` 신규 ("데이터를 불러오지 못했습니다").
  조회 9곳(설정·홈·알림×2, 대시보드, 앱정보 포트, mac 소스 목록) 전환, 변경 16곳은 `0402` 유지
- escape/envelope 통일: plan 로컬 절단 `escapeJson` 삭제 → 공용 무손실로,
  plan `statsRoute` 에러를 `respondError`로 (바이트 동일 출력)
- mac stats 캐시: `StatsCache`를 `:services:common`으로 승격 (`common.cache`),
  mac `overview·trends·collect·insightsInput` 캐시 + `saveApps·purgeSource·cleanup` 무효화
- 알림·요약 본문 리소스화: 양 `NotificationService`에 Context 주입 + `getString`/`plurals`,
  `DailySummaryWorker` 2종, `describeStats` 2종(Context 인자). DB 저장 문구와 동일 출력
- 시드 영속화: mac `lastSeedStatus/StartedAt`을 DataStore에 저장·복원.
  재시작 시 `running` 유령 상태는 `idle`로 정정
- build-logic: `jupjup.base` convention plugin (SDK·컴파일·패키징·단위테스트) + 4 모듈 적용.
  AGP 9 `CommonExtension` 프로퍼티식, 버전은 카탈로그가 진실. `jupjup.room`은 분리 유지

### 테스트
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0,
  설치 후 health 200×2·mac stats 4종 200·시드 상태 영속 확인·크래시 0

## [1.8.0] - 2026-09-14
> 플랫폼: AND · 빌드·문자열·잔정리 (리팩토링 6단계)

### 정리
- SDK 단일 진실: `compileSdk/minSdk`를 카탈로그로 (`libs.versions.toml`, 4 모듈)
- 권한 일원화: 9종을 `:app` 소유로 (mac/plan 삭제, 머지 확인).
  `SystemForegroundService`는 라이브러리 단독 lint 때문에 3곳 유지
- 빈 catch 12곳 로그화 (연속실패 기록·실패푸시·DB close·CookieManager·알림 액션 6종).
  의도적 무시(종료·채널·폴백·재귀방지)는 제외
- `SourceLocks.release` 유휴 락 제거, 시드 취소 시 `idle` 복구 + 시작시각 기록
- Toast·채널·푸시제목 17종 리소스화 (`mac_/plan_` 체계. 포맷·DB값·파서용 제외)
- 문서 유령 코드 2종 정리 (v1.0.0 항목의 미등록 코드 표기 제거)

### 테스트
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0, 설치 후 health 200×2, 크래시 0
- 후속 (범위 밖): build-logic convention plugin, `E-AND-DB-0402` 조회/저장 분리,
  요약·알림 본문 리소스화, 시드 상태 영속화, escape/envelope 통일

## [1.7.0] - 2026-09-14
> 플랫폼: AND · 성능·DB (리팩토링 5단계)

### 성능
- N+1 제거: plan `getPlans` 1+N → 2쿼리 (`getByPlanIds` 배치 + 메모리 조인),
  `saveCrawlResults` 배치 (`getByIds`), 소스관리 양쪽 배치 (`getRecentBySourceIds`),
  `getCollectionHealth` 배치 + `MAX(collectedAt)` 재사용
- plan 인덱스 2종 (`isNew+firstCollectedAt`, `networkType+mvnoNetwork+price`) + 마이그레이션 4→5
- `logResult` 원자화 (양쪽 insert+상태갱신 `withTransaction`)
- plan `serveAsset` IO 격리 + 로그, `getLocalIp` Main 호출 제거 (About·홈·FGS 알림 캐시),
  `isServiceRunning` 공용 타임아웃 헬퍼로 교체

### 테스트
- 신규: `PlanBatchTest` 2종 (배치 1회·빈페이지 미조회)
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0, 마이그레이션 승계 확인 (381건 유지·인덱스 생성),
  수집 E2E 1회, health 200×2, 크래시 0
- 참고: 첫 마이그레이션 시도에서 Room 인덱스명 불일치 크래시 → `index_plans_*` 규칙명으로 수정 후 정상

## [1.6.0] - 2026-09-14
> 플랫폼: AND · God object 분리 (리팩토링 4단계)

### 구조
- 양 `HttpServerService` (895·873줄) → 도메인별 Route 확장 분리 (에셋·아이템·수집·통계·알림·설정 + JSON 매퍼).
  본체는 생명주기+배선만. `app()`·`restartServer()`·`scope`·`currentPort`만 internal 공개, 동작 동결
- plan `StatsRepository` (546줄) → `StatsCache`(TTL 5분·시간주입 테스트 가능) + 집계 + `Models.kt`(모델 이동)

### 수정
- plan 토글 미존재 200+enabled:false → 404 (mac 동일). `toggle(id): Boolean?` 신규, 기존 `toggleEnabled` 유지
- plan sync 미존재 sourceId 무조건 202 → 404 (`respondNotFound`, mac 동일). 포털은 `{}` 전송이라 영향 없음
- plan 알림 상세 깨진 `detailJson` 500 승격 → `{}` 폴백 (mac 동일)
- plan 설정 포트 변경 시 라우트 스레드 직접 `restartServer()` (최대 3s 블로킹) → `scope.launch` (mac 동일)
- 미캐시 4종(`getCrawlTrend, getNewPlanTrend, getValueRanking, getCollectionHealth`) 캐시 적용 (파라미터 키)

### 테스트
- 신규: `StatsCacheTest` 6종 (TTL·히트·무효화 + ranking/health 리포지토리 히트) + `SourceToggleTest` 2종 +
  4a `Mac/PlanServerJsonTest` 9종
- 검증: 실기(R5CT215F4QK) 단위 129/129, lint 오류 0, 설치 후 health 200×2, 토글·sync 404 확인,
  stats 9종 200, 수집 중 무효화 정상 동작 확인, 크래시 0

## [1.5.0] - 2026-09-14
> 플랫폼: AND · app 추상화 + ViewModel 테스트 (리팩토링 3단계)

### 구조
- `ServiceAdapter` 신규 (Mac/Plan 구현체): 대시보드 상태 조회·수집 트리거·중지/재개·서버 토글의 서비스 분기 흡수
- `ServiceRegistry` 신규 (`Services.kt`): 어댑터 맵 + fragment 팩토리 — MainActivity 이중 when 제거
- `DashboardViewModel`: 어댑터 맵 주입(기본값=Registry) + `ioDispatcher` 주입(기본값=IO) — 8개 복사 메서드 → svc 키 4종으로 축소
- 공통 `NetUtils.isPortOpen(port, timeoutMs=500)`: 무타임아웃 소켓 hang 제거

### 테스트
- `DashboardViewModelTest` 6종 신규 (JVM, Robolectric 불필요): 병합·2초 재조회·재귀 없음·서버/수집 위임
- 검증: 실기(R5CT215F4QK) 단위 app 6/6 + mac·plan 106/106, lint 오류 0, 설치 후 health 3000·3001 HTTP 200, 크래시 0

## [1.4.0] - 2026-09-14
> 플랫폼: AND · :services:common 추출 (리팩토링 2단계)

### 구조
- `:services:common` 신규 (namespace `com.borasarang.common`, 리소스 접두 `jup_`)
- `JupLog` 코어: logcat(디버그만) + 파일 로그(항상, `files/logs/<태그>.log`, 512KB 로테이션) — 릴리스 로그 소실 해결. 양 `DebugLogger`는 시그니처 동일 파사드로 위임
- `NetUtils`·`SourceLocks`·DataStore 팩토리(`SettingsStores`, 파일명 승계) 이관, 양 모듈 삭제
- 서버 JSON 헬퍼 단일화 (`escapeJson` 무손실·`receiveJsonObject`·`respondError/NotFound`·`putIfNotNull`·`pathId/Long`) — mac privates 삭제, plan 20곳 치환 (동작 동일 지점만)
- 제외 (후속 단계): `TimeUtils` (주기 옵션 상이), `BootReceiver` (3단계), Entity (5단계), plan toggle/sync 의미·stats envelope (4단계)

### 수정
- 공통 모듈 lint 권한 오류 수정 (매니페스트에 INTERNET·NETWORK_STATE·WIFI_STATE 선언)
- 검증: 실기(R5CT215F4QK) 단위·lint 통과, 설치 후 health 3000·3001 HTTP 200, 파일 로그 2종 생성, 설정 승계 확인, API 응답 동등성(400·404·sources·settings) 확인, 크래시 0

## [1.3.2] - 2026-09-14
> 플랫폼: AND · P0 안전 수정 (리팩토링 1단계)

### 수정
- plan DB 폴백 데드코드 수정 (`resetInstance` 후 재생성) + 재생성 전 원본 백업 (mac과 동일)
- 설정 범위 검증: 보관기간 1~365일 상수 신설, 양 서비스 settings POST에서 포트·보관기간·watchdog 범위 벗어나면 400 (`E-AND-VALID-0501/0502`), `saveSettings`에도 coerce 이중 방어
- plan 수동수집 워커 폭증 차단: unique KEEP + 20초 stagger + `SourceLocks` 상호배제 이식 (mac 패턴)
- `build_and_run.sh`: `pipefail` 추가 + 빌드 전 기존 APK 제거 (stale 설치 방지) + 단위에 `:app` 포함
- 검증: 실기(R5CT215F4QK) 단위·lint 통과, 설치 후 health 3000·3001 HTTP 200, 설정 오입력 400 4종 확인, `/api/sync` 3연타 중복 적재 없음, 크래시 0

## [1.3.1] - 2026-09-14
> 플랫폼: AND · 대시보드 서버 상태 표시 레이스 수정

### 수정
- 대시보드 새로고침이 서버 포트 바인드 전에 소켓 체크해서 "중지됨"으로 뜨던 문제 수정 — 중지 표시 시 2초 뒤 1회 자동 재조회 (`DashboardViewModel.refresh(retry)`, 재귀 없음)
- 서버 시작/중지 토글 직후 상태도 추적 재조회가 자동 보정, 재조회 진입 로그(`서버 미기동 감지 — 2000ms 뒤 재조회`) 추가
- 검증: 실기(R5CT215F4QK) 콜드스타트에서 재조회 로그 + 최종 실행중 표시 확인, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0

## [1.3.0] - 2026-09-13
> 플랫폼: AND · 서비스-우선 내비게이션 개편

### UI/UX
- 상단 **서비스 전환(맥줍줍/요금줍줍)** 상시 표시 — 전환 시 대시보드로 강제 이동, 현재 서비스가 항상 명시됨
- 하단 5탭: 대시보드(통합) / 홈(서비스 상세) / 수집 소스 / 알림 / 설정
- 드로어 제거 — 앱 정보(포트·버전)는 툴바 `⋮` 메뉴로 이동
- **인사이트 → 대시보드** 명칭 변경 + 활성 서비스 카드 강조 (스트로크 + "현재" 배지)
- 앱바 타이틀 = 항상 현재 서비스명
- **앱 정보 다이얼로그 개편**: 앱 아이콘·버전 배지, 서비스별 포털 주소(IP:포트), 하단 GitHub 링크(`github.com/BoraSarang/JupJup`, 브라우저/에러 처리 포함)

### 수정
- material 1.12.0에 SegmentedButton 미포함 확인 → `MaterialButtonToggleGroup` 세그먼트형으로 구현 (버전 고정 규칙 유지)
- 깨진 connected 테스트 이관 시 잔재 정리 (드로어 메뉴·헤더·아이콘 5종 삭제)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, `:app:connectedDebugAndroidTest` 4/4 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0

## [1.2.0] - 2026-09-13
> 플랫폼: AND · 시리즈 인사이트 시작 화면

### UI/UX
- 시작 화면 **인사이트** 신규: 맥줍줍·요금줍줍 두 서비스 카드를 한 화면에 병렬 표시 (상태 도트·접속 주소·통계 3개)
- 카드에서 직접 **[지금 수집]·[수집 중지/재개]·[서버 시작/중지]** 조작 (서비스 화면 진입 없이 제어)
- 하단 첫 탭 `서버 상태` → `인사이트`로 교체 (`ic_tab_insight` 신규), 드로어에 `줍줍 시리즈` 항목 추가 (`ic_nav_series` 신규)
- 앱바 타이틀: 인사이트면 시리즈명, 그 외 탭은 선택된 서비스명

### 수정
- 인사이트 빌드 오류 수정: `TimeUtils.formatRelative` 참조 정정, M3 Filled 버튼 스타일명 정정 (`Widget.Material3.Button`), 라이브러리 색상 non-transitive R 참조, `refreshData()` 추가
- `InsightViewModel` 예외 처리: silent catch 제거 → 서비스별 `DebugLogger` + 에러코드 (`E-AND-CRAWL-0201/0211/0221`, `E-AND-DB-0402`) 매핑, 화면 진입 로그(`인사이트 화면 진입`) 추가
- 오타 수정: `줄줍` → `줍줍` (README 제목 포함 5곳)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·`:app:lintDebug` 통과 (경고 51건, 오류 0 — 인사이트 관련 6건은 기존 패턴과 동일한 경미 경고), 설치 후 크래시 없음
- 검증(connected·E2E): `:app:connectedDebugAndroidTest` 2/2 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0, 인사이트 진입 로그 확인

## [1.0.0] - 2026-09-13
> 플랫폼: AND · 통합 (mac+plan)

### 통합 (처음이벤트)
- 두 독립 앱(MacJupJup·PlanJupJup)을 단일 APK `JupJup`으로 통합
- Gradle 멀티모듈: `:app` + `:services:mac` + `:services:plan`
- 서비스별 격리: 포트(mac 3000 / plan 3001)·DB·설정(DataStore)·알림채널·리소스(mac_/plan_ 접두사)
- `Application` → `{Prefix}JupJupRuntime`(object) 전환, 통합 `JupJupApplication` 하나로 두 서비스 기동
- 하단 서비스 네비(맥줍줍/요금줍줍) + 기능 탭(홈/수집 소스/알림/설정) 통합 UI
- 루트 `build_and_run.sh` 단일 빌드 디스패처
- GitHub Pages·landing 제거 → README 한/영 + docs/ 만

### 마이그레이션
- 데이터 마이그레이션 없음 (신규 설치 기준). 기존 앱 데이터 이관 미지원.
- 메모리/성능 영향: 두 서버 동시 기동으로 약 2배 서버 프로세스(각자 CIO 엔진), DB 2개

### 수정 (1.0.1)
- **크래시 #1**: 두 서비스의 DataStore 파일명이 동일(`settings`) → `mac_settings`/`plan_settings`로 격리
- **크래시 #2**: WorkManager `SystemForegroundService`에 `foregroundServiceType="dataSync"` 누락 → app manifest에서 `tools:node="merge"`로 주입
- 검증: 실기(LB-R5CT215F4QK) 포털 3000·3001 모두 HTTP 200, 크래시 없음

## [1.1.0] - 2026-09-13
> 플랫폼: AND · 통합 UI 개편

### UI/UX
- 상단 기능 탭(TabLayout) 제거 → 하단 바 좌측 **햄버거** + 기능 **드로어**(홈/수집 소스/알림/설정)로 대체
- 하단 바: 서비스 전환(맥줍줍/요금줍줍)은 유지, Material Toolbar 스타일로 정돈
- 상단 앱바 추가: 현재 서비스명 표시 (기존 홈 내부 로고/타이틀 중복 제거)

### 수정 (1.1.1)
- 탐색 구조 재개편: 상단 앱바 햄버거 = **서비스 드로어**(맥줍줍/요금줍줍), 하단 = **기능 탭**(서버 상태·수집 소스·알림·설정) — 하단 우측 서비스 메뉴 제거
- "홈" 탭 → "서버 상태"로 이름 변경 (화면 유지), 서버 상태 탭 전용 아이콘(`ic_tab_server`) 신규
- 각 서비스 홈에 **수집 중지/시작 토글**(일시정지): 중지 시 실행·예약 수집 전체 취소 + `crawl_enabled` 영속 저장(앱 재시작 후 유지), 재개 시 주기 스케줄 재예약
- 각 웹 포털에 파비콘(`favicon.svg`, 원본 앱 아이콘 벡터 기반) + `HttpServerService` 라우트 (`ContentType.Image.SVG`, `/favicon.svg`)
- 파비콘 서비스별 분리 시안(원본 요소 활용): 맥줍줍=파란 그라디언트+코인 링 중심(수집), 요금줍줍=차콜 슬레이트+막대 3/기준선 중심(요금 비교)+상단 코인 링
- `error_message_ko.json`: `E-AND-CRAWL-0221` (수집 중지/재개 상태 저장 실패) 추가
- **서버 상태 표시 버그 수정**: 홈(서버 상태) refresh가 기동 완료 전 스냅샷(false)에 고정 → 서버 기동 대기 폴링(최대 5회·1초 간격, `isServiceRunning`)으로 시작 직후 상태 정확 반영
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, 크래시 0
- 런처 아이콘 교체: 제공 아이콘 팩 적용 (블루 런처 + 적응형 흰 배경/투명 정면)
- 콘텐츠 정돈: 서비스 홈 카드 구성 유지하되 앱바·하단바로 영역을 구획, 16dp 패딩 유지