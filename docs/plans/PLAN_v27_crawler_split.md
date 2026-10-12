# PLAN_v27 — 크롤링 기기 분리 (기기=수집+push, 클라우드=pgsql+API, Vercel=웹)
> 생성일: 2026-10-12 | 플랫폼: android+server+web | 작성자: opencode

## 1. 목표 (1줄)
mac 크롤링만 Android 기기에 남기고 DB/API는 클라우드 Postgres로, 웹은 Vercel 정적으로 분리한다.

## 2. 범위
- 플랫폼: android(수집) + server(클라우드 API+PG) + web(Vercel 정적)
- 기술 스택: 기기 기존 유지(Ktor CIO·Room·WorkManager·Jsoup) + 클라우드 Postgres(Supabase/Neon택1, JDBC/Exposed) + Vercel Static. 선정 이유: 읽기/쓰기 경로를 끊어야 NAT·LanGuard·CORS 문제를 우회하고 pgsql 요구를 만족.
- design_profile: custom (유지)
- 불변: 크롤러 11종·15분 Pipeline 스케줄·수집 예의(1초+·UA)·`AppDraft` 병합 규칙·기존 GET `/api/*` 응답 모양.

## 3. 문서 위치
- PLAN: 본 문서
- TODO: docs/TODO.md T-27 등록 (아래 §8)
- DESIGN: docs/DESIGN.md §신설(분리 아키텍처) 갱신
- API: docs/api/ENDPOINTS.md에 `/ingest/*` 3종 추가

## 4. 아키텍처
- 기기: 크롤러→`CrawlRunner`→Room 저장(캐시 유지)→클라우드 push. 발신만 하므로 포트포워딩·인바운드 불필요.
- 클라우드: `POST /ingest/apps|news|community`(기기 토큰) + 기존 읽기 `GET /api/*` 그대로 + Postgres 9테이블(Room Entity 미러).
- Vercel: `mac_web/` 4파일 정적, API 베이스만 클라우드로.

## 5. 단계
1. **클라우드 스키마**: Room Entity 9개→PG DDL (`apps`, `app_sources`, `sources`, `version_history`, `crawl_logs`, `notification_logs`, `news_articles`, `news_app_relation`, `community_posts`). `mergeApps` 상당 병합은 서버측.
2. **ingest API**: `POST /ingest/apps`(List<AppDraft>), `/ingest/news`(articles+relations), `/ingest/community`. 기기 토큰(Bearer) 인증, 멱등 upsert, 실패 시 기기 재시도 큐.
3. **읽기 API**: 기존 `GET /api/main|apps|games|news|community|stats|watchlist` 스펙 복사 (응답 JSON 바이트 동등 목표).
4. **기기 push 분기**: `CrawlRunner.kt` 3곳(`saveApps`·`saveArticles`·커뮤니티 저장 뒤)에 업로드 호출 + `UploadWorker`(지수백오프, 미전송분 Room 플래그/큐). 스케줄·FGS·예의 로직 무변경.
5. **웹 베이스 URL화**: `app.js` `api()`에 `window.JUPJUP_API_BASE`/`?api=` 지원 (상대경로 기본 유지 → 기기 직결 회귀 가능).
6. **보안**: ingest는 기기 토큰, 쓰기(토글/수집 트리거)는 당분간 기기 직결 유지 또는 클라우드 큐 경유(2차). `LanGuard`·`AdminAuth` 기기측 무변경.

## 6. 성능 예산
- budgets.json 참조 (p95 300ms·캐시 70%). override: ingest p95 500ms 허용(배치성), 업로드 실패율 <1%, 기기 추가 배터리 영향 측정·기록만.

## 7. 에러 코드
- 사용할 범위: E-AND-NET-09xx (업로드: 0901 전송실패·0902 인증·0903 재시도초과), 클라우드측 E-SRV-ING-01xx.

## 8. TODO (T-27)
- [ ] T-27-1 클라우드 DDL + ingest 3종 + 읽기 API 패리티
- [ ] T-27-2 기기 UploadWorker + CrawlRunner push 분기 + 재시도 큐
- [ ] T-27-3 app.js API 베이스 + Vercel 정적 배포 + E2E(수집→push→웹 노출)

## 9. 빌드 & 검증 계획
- `./build_and_run.sh build|test|lint` (기기측 회귀: unit·node --check)
- 클라우드: ingest 멱등 테스트(중복 POST 2회 동일 결과) + 읽기 패리티(기기 `/api/main` vs 클라우드 diff)
- 실기 E2E: 수집 1사이클 → PG 행 확인 → Vercel 웹 렌더 확인

## 10. 예외 규칙 (있으면)
- 없음. 시크릿(기기 토큰·PG 접속정보)은 DataStore/환경변수만, 커밋·로그 금지.
