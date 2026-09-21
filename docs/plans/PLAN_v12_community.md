# PLAN_v12 — 커뮤니티 뉴스 크롤러 (`:services:community`)

> V2 원안(`JupJup-Community-News-Crawler-Plan-V2.md`, FastAPI/Celery/Postgres/S3/Next.js)을
> JupJup 로컬 규칙(Ktor+Room+WorkManager+바닐라웹)으로 이식. 포트 3003.

## 1. 네이밍 (AGENTS.local.md 준수)

- 모듈 `:services:community`, 패키지 `com.borasarang.communityjupjup`, 리소스 `cm_`
- DB `communityjupjup.db` (v1), DataStore `community_settings`, 채널 `jupjup_community_server` (FGS 4001, 크롤 4100대)
- UA `CommunityJupJup/1.0`, 에러코드 기존 체계 재사용 (`E-AND-CRAWL-02xx` 등, 신규 코드 없음)

## 2. V2 → 로컬 매핑 (변경 3점)

1. 서버 스택 → Ktor CIO + Room + WorkManager + 바닐라 4파일 (`community_web/`)
2. Playwright 불가 → OkHttp(HttpURLConnection)+Jsoup `BoardCrawler` + `selectorConfigJson` + `POST /api/crawl/test` 미리보기
3. 주기 3~10분 불가 (WorkManager 15분 하한) → 속보/핫딜 15분, 일반 30분

## 3. DB (Room v1, 마이그레이션 없음)

- `CommunityPost` (요약 500자만, 본문 저장 금지) · `SiteBoard` (source-board-카테고리 매핑) ·
  `CrawlSource` (domain+selectorConfigJson) · `CrawlLog`·`NotificationLog` (mac 패턴 재사용)
- `UnifiedCategory`는 Room 없이 `CommunityCategories` 정적 10종
- TTL: 핫딜 3일·중고 7일·그 외 retentionDays(기본 90일), `purgeExpired()` (기동 시 + 수동 정리)

## 4. 크롤러 (MVP 3사이트·5소스)

- `BoardCrawler`: 보드 목록만 수집 (상세 진입 없음), 행 스킵 내성, 빈 셀렉터 가드, `originalUrl` dedup
- `TimeParser` (상대/절대 관대 파싱) · `PriceParser` (핫딜 가격·할인율·중고 상태, 순수 함수)
- 시드: clien-park(유머30분)/jirum(핫딜15분), fmkorea-poten(속보15분)/humor(30분), ruliweb-best(속보30분)
- 제외: 뽐뿌(403)·인스티즈/오유(CF)·디시 — 차단 대응 없이 MVP 범위 밖

## 5. 서버 (6라우트, 기존 패턴)

- `CmAsset/Item/Collect/Stats/Notif/Settings` — Asset·Notif은 mac 복사, 나머지는 posts 전용 재작성
- API: `/api/health|/categories|/sources|/posts?category_id&source_id&q&sort&page|/posts/{id}|/ranking|/stats|/stats/overview|/stats/collect|/stats/trends|/logs`, `POST /api/sources/{id}/toggle|/api/sync|/api/crawl/test`, `GET·POST /api/settings` (토큰·번역 필드 제거)
- 토글 시 스케줄 동기화 (on=재예약, off=취소)

## 6. 웹 (`community_web/` 바닐라)

- V2.html은 React 번들이라 참조만: 헤더 10탭 / 좌 언론사 체크 / 중앙 피드(핫딜·중고 뱃지) / 우 랭킹+수집현황 / 상세=요약+원문 버튼 / 설정 서랍
- `node --check` + 서빙 확인

## 7. 앱 통합 (체크리스트 실측 반영)

- `CommunityJupJupRuntime.initialize` → `Service.COMMUNITY` + `CmServiceAdapter` →
  `DashboardViewModel.cm` (list 4조회) → `DashboardFragment.renderCm` + `dashboard_cm_*` 카드 →
  `MainActivity seg_community` + `nav_community` + about `about_cm_row` → `DashboardViewModelTest` 4어댑터

## 8. 검증

- `:services:community:assembleDebug` + `:app:assembleDebug` SUCCESS
- 단위: community 9종(파서·셀렉터·매퍼) + app 6종 SUCCESS
- lint community+app 오류 0, `node --check` 통과
- 실기 E2E (S22, 2026-09-21): 3003 health ok·clien-park 30건·clien-jirum 30건·ruliweb-best 32건
  (제목·작성자·조회·추천·시각·URL·카테고리 매핑 정상) · ranking/overview/웹에셋 200 ·
  3000/3002 회귀 ok (3001은 기기 설정상 자동시작 꺼짐, 본 변경 무관)

## 9. 실측 반영 (E2E 중 발견·수정)

- 클리앙 셀렉터: `a.list_subject` (앵커 자체 클래스), 시각 `.timestamp`, 공지 `.notice` 제외 (`excludeRow` 신설)
- 조회수 k/M 단위 파싱 (`65.5 k`→65500)
- UA: `(Linux; Android)`·`Mozilla` 포함 시 302 봇월 → `CommunityJupJup/1.0 (contact ...)` 로 200 확인
- 에펨코 2종: JS 보안 시스템으로 HTTP 수집 불가 실측 → `enabled=false` 보관 (Playwright 필요, Android 불가)
- 기존 설치분 승계: `seedMissing`이 셀렉터·활성·사유를 최신 시드로 갱신 (`updateSelector`)

## 10. R26b — 상세 요약 + 중복 제거 + 8소스 확장 (1.13.0 미배포분)

- **상세 요약**: 목록만 긁으면 요약이 비어 `요약 없음` 노출 → 신규 URL만 상세 진입 (30/회 상한) +
  미요약 백필 (15/회). 본문 저장 금지는 유지 (500자 절단). 사이트별 본문 셀렉터
  (clien `.post_content article` / ruli `.view_content` / ppomppu `.board-contents` /
  dc `.view_content_wrap` / theqoo `.xe_content`, 그 외 `[itemprop=articleBody]` 폴백)
- **중복 제거**: `originalUrl`에 UNIQUE가 없고 휘발 파라미터(`po`·`od`…)로 매번 새 행 →
  `canonicalUrl` 컬럼 + UNIQUE + `UrlCanonical` 정규화 + `MIGRATION_1_2` (기존 중복 삭제).
  재수집은 카운트만 갱신. 검증: park 재수집 `found=30 new=0`
- **주기**: 사용자 지시로 전체 30분 통일
- **8소스**: 뽐뿌(핫딜26)·디시베스트(속보50)·보배(유머30)·더쿠(속보20)·오유(유머30) 추가 등록,
  전원 첫 수집 SUCCESS. 불가: 인스티즈(JS셸)·MLBPARK(JS렌더)·SLR(URL 미확인)·판(큐레이션형)
- 실기 총 329건 (속보166·유머108·핫딜55). 후순위: 상세 셀렉터 chrome 잔재 다듬기

## 11. R26c — 게시판 관리 + 품질 수정 (1.13.0 미배포분)

- **언론사 필터 버그**: 체크가 브라우저에서 걸러져 1페이지 밖 글은 빈 화면+엉뚱한 건수 →
  `/api/posts`에 `source_id` 복수 파라미터 서버 필터 + 정확한 total
- **게시판 관리** (사용자 요청): 언론사별 관리 버튼 → 모달에서 보드 목록(on/off 체크·카테고리 변경·삭제·추가) →
  적용 1회(`POST /api/boards/batch`) + 적용 후 즉시 수집. `SiteBoard.enabled` + DB v3.
  검증: 추가→수정→삭제→검증 오류 메시지까지 E2E 통과
- **뽐뿌 깨짐**: EUC-KR을 UTF-8로 읽어 Mojibake → `CrawlHttp` charset 감지(헤더/meta).
  깨진 44건은 `POST /api/sources/{id}/purge`로 비우고 재수집 → 한글·줄바꿈·썸네일·가격 정상
- **줄바꿈**: 요약이 한 줄로 뭉개짐 → 본문 추출 시 문단 개행 보존 + CSS `pre-line`
- **깨진 문자**: 서로게이트 쌍 절단 방지(`takeSafe`) + 고립 서로게이트 제거.
  실측: 77건 전수에 lone surrogate·U+FFFD 0건 (깨짐은 EUC-KR 오판독이 원인)

## 12. R26d — 이미지 글 대응 + 단건 새로고침 (1.13.0 미배포분)

- **원인**: 짤·인증 글(이미지만)은 텍스트가 없어 요약이 null → `요약 없음` 노출이 정상 동작이었음.
  전문은 매번 다 가져오나 법적 정책상 500자 요약만 저장 (V2 §10)
- **대응**: 상세 추출이 대표 이미지+개수도 반환 (`DetailResult`). UI 조각(아이콘·버튼·배너) 제외,
  본문 첫 실이미지를 썸네일로 저장. 피드 카드·상세 모달에 이미지 표시,
  요약 없으면 `이미지 위주 게시글입니다` 안내
- **단건 새로고침**: `POST /api/posts/{id}/refresh` — 대기열과 무관하게 즉시 보충.
  검증: 루리웹 짤 글(76752409)에 webp 썸네일 부착 확인
- **교훈**: 백필 최신순+신규 유입 → 구 글 기아 발생. 신규 글은 수집 시 처리하므로 백필은 오래된 순.
  수집 시 기등록 행의 빈 상세도 그 자리에서 보충 (canonical 매칭)

## 13. R26e — 썸네일 프록시 (1.13.0 미배포분)

- **원인**: 피드 썸네일 깨짐. 루리웹·뽐뿌 CDN이 외부 Referer를 403 차단 실측
  (무 Referer·자사 Referer는 200)
- **대응**: `GET /api/thumb?url=` — 서버가 직접 받아 전달 (Referer 미전송).
  image/*만·3MB 상한·http(s)만·로컬/메타 주소 차단. 웹은 전부 프록시 경유 + `referrerpolicy`
- 검증: webp 138KB 200 응답, 로컬 URL 거부 확인
