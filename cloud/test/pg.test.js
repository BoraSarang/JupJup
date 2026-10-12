// Live Postgres parity test. Runs only when TEST_DATABASE_URL is set:
//   TEST_DATABASE_URL=postgres://postgres:jupjup@127.0.0.1:5433/jupjup npm test
// Applies schema.sql fresh, then exercises ingest + reads through the
// HTTP server backed by the PG store.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createServer } from '../src/server.js';

const DB = process.env.TEST_DATABASE_URL;
const describe = DB ? test : test.skip;

describe('pg parity (live)', async (t) => {
  const { Pool } = await import('pg');
  const { createPgStore } = await import('../src/store-pg.js');
  const pool = new Pool({ connectionString: DB });
  const schema = fs.readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
  for (const tbl of ['news_app_relation', 'version_history', 'app_sources', 'crawl_logs', 'notification_logs', 'news_articles', 'community_posts', 'sources', 'apps']) {
    await pool.query(`DROP TABLE IF EXISTS ${tbl} CASCADE`);
  }
  await pool.query(schema);
  const srv = createServer(createPgStore(pool), { ingestToken: 't' });
  await new Promise((r) => srv.listen(0, r));
  const p = srv.address().port;
  const get = (path) => fetch(`http://127.0.0.1:${p}${path}`).then(async (r) => ({ code: r.status, body: await r.json() }));
  const post = (path, obj) => fetch(`http://127.0.0.1:${p}${path}`, {
    method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer t' }, body: JSON.stringify(obj),
  }).then(async (r) => ({ code: r.status, body: await r.json() }));
  t.after(() => { srv.close(); pool.end(); });

  await t.test('ingest apps + merge + reads', async () => {
    const d1 = { app: { id: 'a1', platform: 'macOS', name: 'Alpha', developer: 'Dev', license: 'FREE', price: 0, currency: 'USD', category: '유틸', descriptionSnippet: 'short', firstSeenAt: 1, lastUpdatedAt: 10 }, mappings: [{ sourceName: 'github', sourceUrl: 'https://x' }] };
    assert.equal((await post('/ingest/apps', { drafts: [d1] })).body.created, 1);
    const d2 = { app: { ...d1.app, license: 'OSS', descriptionSnippet: 'a much longer and better description text here yes', lastUpdatedAt: 20, version: '2.0' }, mappings: [] };
    const r2 = await post('/ingest/apps', { drafts: [d2] });
    assert.equal(r2.body.updated, 1);
    const detail = await get('/api/apps/a1');
    assert.equal(detail.body.app.license, 'OSS');
    assert.equal(detail.body.app.version, '2.0');
    assert.equal(detail.body.sources[0].sourceName, 'github');
    assert.equal(detail.body.versions[0].version, '2.0');
    // game exclusion
    await post('/ingest/apps', { drafts: [{ app: { id: 'g1', platform: 'macOS', name: 'Game', developer: 'D', license: 'FREE', price: 0, currency: 'USD', category: '게임', tags: 'game', firstSeenAt: 1, lastUpdatedAt: 30 }, mappings: [] }] });
    assert.equal((await get('/api/apps')).body.total, 1);
    assert.equal((await get('/api/games')).body.total, 1);
  });

  await t.test('news/community ordering + main/stats', async () => {
    const h = { 'content-type': 'application/json', authorization: 'Bearer t' };
    await fetch(`http://127.0.0.1:${p}/ingest/news`, { method: 'POST', headers: h, body: JSON.stringify({ articles: [
      { id: 'n1', sourceId: 's', sourceName: 'S', main: 'mac', sub: '전체', title: 'old', originalUrl: 'u1', publishedAt: 5, collectedAt: 100 },
      { id: 'n2', sourceId: 's', sourceName: 'S', main: 'mac', sub: '전체', title: 'new', originalUrl: 'u2', publishedAt: 50, collectedAt: 10 },
    ], relations: [{ newsId: 'n2', appId: 'a1' }] }) });
    const news = await get('/api/news');
    assert.equal(news.body.items[0].id, 'n2');
    assert.deepEqual((await get('/api/news/n2')).body.relatedAppIds, ['a1']);
    const main = await get('/api/main');
    assert.equal(main.body.counts.apps, 1);
    assert.equal(main.body.counts.games, 1);
  });
});
