// Framework-free HTTP API. Response shapes mirror the device Ktor server
// (apps/news/games/posts keys, device main/watchlist/stats envelopes).
// Auth: GET open, POST /ingest/* requires Bearer INGEST_TOKEN.
import http from 'node:http';
import { appsList, gamesList, newsList, newsDetail, communityList, mainDoc, watchlist } from './shape.js';

export function createHandler(store, opts = {}) {
  const ingestToken = opts.ingestToken ?? process.env.INGEST_TOKEN ?? '';
  const allowOrigin = opts.allowOrigin ?? process.env.ALLOW_ORIGIN ?? '*';

  function send(res, code, obj) {
    const body = JSON.stringify(obj);
    res.writeHead(code, {
      'content-type': 'application/json; charset=utf-8',
      'access-control-allow-origin': allowOrigin,
      'access-control-allow-headers': 'authorization, content-type, x-auth-token, x-admin-id, x-admin-pw',
      'access-control-allow-methods': 'GET,POST,OPTIONS',
    });
    res.end(body);
  }

  const readBody = (req) => new Promise((resolve, reject) => {
    let s = '';
    req.on('data', (c) => { s += c; if (s.length > 8_000_000) reject(new Error('body too large')); });
    req.on('end', () => { try { resolve(s ? JSON.parse(s) : {}); } catch (e) { reject(e); } });
    req.on('error', reject);
  });

  return async (req, res) => {
    try {
      const url = new URL(req.url ?? '/', 'http://x');
      const q = (k, d = '') => url.searchParams.get(k) ?? d;
      if (req.method === 'OPTIONS') return send(res, 204, {});
      if (req.method === 'GET' && url.pathname === '/api/health') {
        return send(res, 200, { ok: true, service: 'jupjup-cloud' });
      }
      if (req.method === 'POST' && url.pathname.startsWith('/ingest/')) {
        const auth = req.headers.authorization ?? req.headers['x-auth-token'] ?? '';
        const given = String(auth).replace(/^Bearer\s+/i, '');
        if (!ingestToken || given !== ingestToken) return send(res, 401, { error: 'unauthorized' });
        const body = await readBody(req).catch(() => null);
        if (!body) return send(res, 400, { error: 'invalid json' });
        const kind = url.pathname.slice('/ingest/'.length);
        if (kind === 'apps') {
          if (Array.isArray(body.sources)) await store.upsertSources(body.sources);
          return send(res, 200, await store.ingestApps(body.drafts ?? body.items ?? []));
        }
        if (kind === 'news') {
          if (Array.isArray(body.sources)) await store.upsertSources(body.sources);
          return send(res, 200, await store.ingestNews(body.articles ?? [], body.relations ?? []));
        }
        if (kind === 'community') {
          if (Array.isArray(body.sources)) await store.upsertSources(body.sources);
          return send(res, 200, await store.ingestCommunity(body.posts ?? []));
        }
        if (kind === 'sources') {
          if (!Array.isArray(body.sources)) return send(res, 400, { error: 'sources[] required' });
          await store.upsertSources(body.sources);
          return send(res, 200, { updated: body.sources.length });
        }
        return send(res, 404, { error: 'unknown ingest kind' });
      }
      if (req.method === 'GET' && url.pathname === '/api/apps') {
        return send(res, 200, appsList(await store.listApps({
          q: q('q'), license: q('license'), category: q('category'), sort: q('sort', 'updated'),
          sourceId: q('sourceId') || q('sourceIds'), page: Number(q('page', '1')), pageSize: Number(q('pageSize', '20')),
        })));
      }
      if (req.method === 'GET' && url.pathname.startsWith('/api/apps/')) {
        const item = await store.getApp(decodeURIComponent(url.pathname.slice('/api/apps/'.length)));
        if (!item) return send(res, 404, { error: 'not found' });
        return send(res, 200, { ...item.app, sources: item.sources ?? [], versions: item.versions ?? [] });
      }
      if (req.method === 'GET' && url.pathname === '/api/games') {
        return send(res, 200, gamesList(await store.listGames({
          genre: q('genre'), source: q('source') || q('sourceId'), sort: q('sort', 'newest'),
          page: Number(q('page', '1')), pageSize: Number(q('pageSize', '20')),
        })));
      }
      if (req.method === 'GET' && url.pathname === '/api/news') {
        return send(res, 200, newsList(await store.listNews({
          main: q('main'), sub: q('sub'), q: q('q'), sourceId: q('sourceId'),
          page: Number(q('page', '1')), pageSize: Number(q('pageSize', '20')),
        })));
      }
      if (req.method === 'GET' && url.pathname.startsWith('/api/news/')) {
        const item = await store.getNews(decodeURIComponent(url.pathname.slice('/api/news/'.length)));
        if (!item) return send(res, 404, { error: 'not found' });
        return send(res, 200, newsDetail(item, item.relatedApps ?? []));
      }
      if (req.method === 'GET' && url.pathname === '/api/community') {
        return send(res, 200, communityList(await store.listCommunity({
          main: q('main'), sourceId: q('sourceId'),
          page: Number(q('page', '1')), pageSize: Number(q('pageSize', '20')),
        })));
      }
      if (req.method === 'GET' && url.pathname.startsWith('/api/community/')) {
        const item = await store.getCommunity(decodeURIComponent(url.pathname.slice('/api/community/'.length)));
        return item ? send(res, 200, { ...item, hasContent: item.contentHtml != null }) : send(res, 404, { error: 'not found' });
      }
      if (req.method === 'GET' && url.pathname === '/api/main') return send(res, 200, mainDoc(await (store.mainDetail ? store.mainDetail() : store.main())));
      if (req.method === 'GET' && url.pathname === '/api/watchlist') return send(res, 200, watchlist(await store.watchlist()));
      if (req.method === 'GET' && url.pathname === '/api/stats') return send(res, 200, await store.stats());
      if (req.method === 'GET' && url.pathname === '/api/stats/trends') {
        const t = await store.trends();
        return send(res, 200, { generatedAt: Date.now(), ...t });
      }
      if (req.method === 'GET' && url.pathname === '/api/stats/collect') return send(res, 200, await store.collect());
      if (req.method === 'GET' && url.pathname === '/api/stats/insights') return send(res, 200, await store.insights());
      return send(res, 404, { error: 'not found' });
    } catch (e) {
      return send(res, 500, { error: String(e?.message ?? e) });
    }
  };
}

export function createServer(store, opts = {}) {
  return http.createServer(createHandler(store, opts));
}
