import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createMemoryStore } from '../src/store-memory.js';
import { createServer } from '../src/server.js';

const get = (port, path, headers = {}) => fetch(`http://127.0.0.1:${port}${path}`, { headers }).then(async (r) => ({ code: r.status, body: await r.json() }));
const post = (port, path, obj, headers = {}) => fetch(`http://127.0.0.1:${port}${path}`, {
  method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(obj),
}).then(async (r) => ({ code: r.status, body: await r.json() }));

async function withServer(fn) {
  const srv = createServer(createMemoryStore(), { ingestToken: 't' });
  await new Promise((r) => srv.listen(0, r));
  try { await fn(srv.address().port); } finally { srv.close(); }
}

test('ingest requires token; second identical ingest is idempotent-ish (no dup created)', async () => {
  await withServer(async (p) => {
    const draft = { app: { id: 'x', name: 'N', developer: 'D', license: 'FREE', price: 0, currency: 'USD', platform: 'macOS', category: '유틸', firstSeenAt: 1, lastUpdatedAt: 2 }, mappings: [] };
    assert.equal((await post(p, '/ingest/apps', { drafts: [draft] })).code, 401);
    const r1 = await post(p, '/ingest/apps', { drafts: [draft] }, { authorization: 'Bearer t' });
    assert.equal(r1.body.created, 1);
    const r2 = await post(p, '/ingest/apps', { drafts: [draft] }, { authorization: 'Bearer t' });
    assert.equal(r2.body.created, 0);
    assert.equal(r2.body.updated, 1);
    const list = await get(p, '/api/apps');
    assert.equal(list.body.total, 1);
  });
});

test('apps list excludes games; games endpoint serves them; news ordering prefers publishedAt', async () => {
  await withServer(async (p) => {
    const h = { authorization: 'Bearer t' };
    await post(p, '/ingest/apps', { drafts: [
      { app: { id: 'app1', name: 'A', developer: 'D', license: 'FREE', price: 0, currency: 'USD', platform: 'macOS', category: '유틸', firstSeenAt: 1, lastUpdatedAt: 10 }, mappings: [] },
      { app: { id: 'g1', name: 'G', developer: 'D', license: 'FREE', price: 0, currency: 'USD', platform: 'macOS', category: '게임', tags: 'game', firstSeenAt: 1, lastUpdatedAt: 20 }, mappings: [] },
    ] }, h);
    assert.equal((await get(p, '/api/apps')).body.total, 1);
    assert.equal((await get(p, '/api/games')).body.total, 1);
    await post(p, '/ingest/news', { articles: [
      { id: 'n1', sourceId: 's', sourceName: 'S', main: 'mac', sub: '전체', title: 'old', originalUrl: 'u1', publishedAt: 5, collectedAt: 100 },
      { id: 'n2', sourceId: 's', sourceName: 'S', main: 'mac', sub: '전체', title: 'new', originalUrl: 'u2', publishedAt: 50, collectedAt: 10 },
    ] }, h);
    const news = await get(p, '/api/news');
    assert.equal(news.body.items[0].id, 'n2');
    assert.equal((await get(p, '/api/main')).body.counts.apps, 1);
    assert.equal((await get(p, '/api/health')).body.ok, true);
  });
});

test('schema.sql covers all 9 Room tables', () => {
  const sql = fs.readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
  for (const t of ['apps', 'app_sources', 'sources', 'version_history', 'crawl_logs', 'notification_logs', 'news_articles', 'news_app_relation', 'community_posts']) {
    assert.ok(sql.includes(`CREATE TABLE IF NOT EXISTS ${t}`), t);
  }
});
