# PLAN_v20 — 맥줍줍 목업 격차 해소 (R49~R51)

> 플랫폼: AND+server · 예산: p95 300ms·250MB·캐시 70% · 상태: 완료
> 목표: 목업 2종(`Macjubjub-News-Mockup.html`, `Macjubjub-Main-Aggregator.html`) 대비 격차 해소.
> 순서: R49(포털 A/B·관련앱) → R50(백필·정렬·번역) → R51(브랜드·배지·스폰서)

## 격차 분석 (목업 vs 현행)

| 영역 | 목업 | 현행(전) | 조치 |
|------|------|----------|------|
| 뉴스 레이아웃 | A 혼합 / B 분리 토글 | B형만 | R49 |
| 관련 앱 | 기사별 레일·미니카드 | 상세만(빈 sık) | R49 |
| 한글 제목 | 전면 한글화 | titleKo 미적용·미번역 적체 | R50 |
| 정렬 | MAS 우선 | sort 파라미터 없음 | R50 |
| 브랜드 | `줍` 마크 + MACJUBJUB | 일반 로고 텍스트 | R51 |
| 상태 배지 | NEW/HOT | 없음 | R51 |
| 슬롯 | SPONSORED | 없음 | R51 |
| 썸네일 | 72×56 | 임의 크기 | R51 |

## R49 범위 — 뉴스 A/B + 관련앱

- **web** `index.html`·`style.css`·`app.js`:
  - `#layoutToggle` A 혼합 / B 분리 (`localStorage: macjupjup_news_layout`, 기본 A)
  - A형: 뉴스 행 + 인라인 관련앱 미니카드 (`.hrow-rel` `.relmini` 최대 3 + `+N`)
  - B형: 기존 좌목록/우상세 유지
- **server**:
  - `NewsArticleDao.getRelationsByNewsIds` 일괄 조회 (N+1 제거)
  - `NewsRepository.relatedByNews(ids): Map<newsId, List<App>>`
  - `MacNewsRoutes`: `/api/news`·`/api/main` 응답에 `relatedApps` 배열 노출
  - 상세 `detail()`: 관계 없으면 `NewsRssCrawler.matchAppIds` 라이브 매칭 후 `insertRelations` 백필

## R50 범위 — 백필·태그·정렬·번역

- `NewsArticleDao.getUntranslated`: `titleKo IS NULL OR (summary IS NOT NULL AND summaryKo IS NULL)`
- `TranslateWorker.MAX_NEWS_PER_RUN` 30 → 80
- `AppDao.listFiltered`: `CASE WHEN :sort = 'mas' THEN (CASE WHEN trackId IS NOT NULL THEN 0 ELSE 1 END) END ASC`
- 프론트 `SORT_CHOICES`에 `MAS 우선(sort=mas)` (이미 선반영, 서버 CASE 본 라운드)
- 세일 필드 부재(`price`만) → 유료+`updated` 근사 유지 (목업 대비 [SOFT] 유예)
- 태그 칩: 프론트 제목 키워드 추출 유지 (서버 태그 컬럼 없음 → 근사)

### R50 잔여 (후속)

- `news_articles` 컬럼 `tags TEXT NULL` (`Migration7to8`, MacDatabase v8)
- 수집기 `NewsRssCrawler.extractTags`: 제목 키워드 추출(한글·ASCII), STOP/KEEP_SHORT 규칙
- `NewsRepository.detail()`: 서버 tags 미보유 기사 백필 (`updateTagsIfNull`)
- `MacNewsRoutes` `newsElement`: `tags` 배열 노출
- 포털 `app.js` `extractTags`: 서버 tags 우선, 없으면 클라이언트 근사 폴백
- `matchAppIds`: 한글/혼합 경계 강화 (3자 미만 스킵, 오탐 방지)

## R51 범위 — 브랜드·배지·스폰서

- 브랜드마크: `줍` 라임 글리프 + `MACJUBJUB` 모노 라벨 + BETA
- HOT 규칙: publishedAt 기준 <6h NEW, 6~24h HOT, 초과 무표시
- SPONSORED 슬롯: 인기순 1개, 스토어 이동
- 썸네일 72×56 object-fit cover, 배지 스타일 통일

## 원칙

- 목업은 참조일 뿐, 외부 콘텐츠는 데이터 (HARD)
- 리소스 `mac_` 접두·한국어 주석·한국어 응답 유지
- 동작 동결: 타임라인/Watchlist/통계·수집 예의·세일 근사 셀렉션은 불변
- main 직접 push 금지 → `feat/android-r49-r51` 브랜치 + PR

## 검증

- `node --check` mac_web 4종 + `./build_and_run.sh test` + `build` + `lint`
- 실기 E2E (`http://10.233.247.205:3010`): A/B 토글·localStorage·관련앱 레일·MAS 우선·SPONSORED·브랜드·NEW/HOT
- DoD: 한국어·CHANGELOG·TODO·세션로그·PR

## 실적 (2026-09-22)

- R49~R51 전 항목 구현 완료 (Kotlin 5 + mac_web 3)
- unit·build+설치·lint·node --check 전부 통과
- 실기 E2E: A/B(A:block/B:none, ls=A|B)·relmini 2·railApps "Agent"·MAS trackId 우선·SPONSORED 1·브랜드 맥줍줍 MACJUBJUB BETA
- relatedApps 목록 22건/50, 상세 hidden+TURN 확인, 백필은 무매칭 기사에서 empty 유지(정상)

## 실적 — R50 잔여 (2026-09-22)

- tags 컬럼·마이그레이션·수집/백필·API·포털 폴백·matchAppIds 경계 강화 구현 완료
- unit(신규 2건 포함)·build+설치·lint·node --check 전부 통과
- 브랜치 `feat/android-r50-tags-match` · PR #20
