# cloud/ — JupJup cloud API (PLAN_v27 T-27-1)

기기=수집+push, 클라우드=pgsql+API, Vercel=웹 정적 분리 중 클라우드측.

## 구성
- `schema.sql` — Room v11 9테이블 Postgres DDL (epoch-ms→BIGINT, CSV TEXT 유지, JSON 2곳 JSONB)
- `src/merge.js` — `AppRepository.mergeApps` 병합 포트 (preferSnippet·license rank·version bump)
- `src/store-memory.js` — Room DAO 의미 패리티 인메모리 스토어 (테스트·로컬용)
- `src/store-pg.js` — Postgres 스토어 (ingest 구현됨, 읽기 쿼리는 T-27-1 후속)
- `src/server.js` — 프레임워크리스 HTTP API (`/api/health`, `/ingest/*`, `/api/apps|games|news|community|main|watchlist|stats`)
- `src/index.js` — 단독 실행 (`DATABASE_URL` 없으면 메모리)

## 실행
- `npm install && npm test` (7/7)
- `cp .env.example .env` 후 `npm start` (기본 `:3021`)
- DDL 적용: `psql $DATABASE_URL -f schema.sql`

## 주의
- PG 읽기 패리티(list 쿼리)는 미구현 — `store-pg.js` 스텁, 메모리 스토어가 의미 기준.
- 시크릿은 `.env`만. `node_modules/` 커밋 금지.
