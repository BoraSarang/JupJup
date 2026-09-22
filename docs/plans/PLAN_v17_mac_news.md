# PLAN_v17 — 맥줍줍 뉴스 리뉴얼 (A안: 현 프로젝트 내)

> 플랫폼: AND · 예산: p95 300ms·메모리 250MB·캐시히트 70% · 상태: 진행 중
> 전제: `services:mac` 내 확장 (B안 분리 안 함). 목업 2종 기준 구성.
> 확정: v9 라이트 톤 유지, 기존 앱 카테고리 10종 유지 + 뉴스 뷰에 11종 칩 별도.

## 범위

- DB: Room v4→v5 (`news_articles`, `news_app_relation`) + `MIGRATION_4_5`
- 크롤러: `NewsRssCrawler`(RSS 12종) + `TYPE_NEWS_RSS` + 시드 12건 + WorkManager 15분 (10분 FGS 루프는 2단계)
- 분류/요약 1단계: 키워드 규칙 + RSS 카테고리 매핑, 요약은 본문 첫 문장 (외부 LLM 없음)
- 서버: `MacNewsRoutes` — `GET /api/news?main=&sub=` · `GET /api/news/:id` · `GET /api/main` (`/api/apps` 기존 유지)
- 포털: `mac_web` 대시보드 뷰(히어로 LIVE + 하이라이트 3 + 업데이트 가로스크롤 + 맥/AI/보안 4+4+4 + 사이드바) + 뉴스 뷰(탭 3 + 서브 18종 + 리스트/상세 분할 + 원문 상단 고정·출처 배너)
- 본문 이미지: 저장 없이 원본 URL, `<img referrerpolicy="no-referrer" loading="lazy">` 강제

## 원칙 (기존 규칙 준수)

- 수집 예의: delay 1s·UA 기존 상수·robots.txt 확인 (AGENTS.local)
- 저작권: 전문 저장하되 출처 배너 + 원문 링크 최상단 고정 (WARN 사유 PR 기재)
- 리소스 `mac_` 접두사, 에러코드 `E-AND-*`, DebugLogger 경유, 한국어 주석
- 기존 타임라인/Watchlist/통계·10종 카테고리 동작 동결

## 검증

- 단위테스트: RSS 파싱·hash 중복·이미지 절대경로 변환·sub 분류 (mac 모듈 기존 test 구조)
- `./build_and_run.sh` build + test + lint, `node --check` (mac_web)
- DoD: 한국어·CHANGELOG·TODO·ENDPOINTS·세션로그

## 2단계 유예

- 10분 FGS 타이머 루프, 외부 LLM 키 방식 분류/요약, 상세 병렬화
