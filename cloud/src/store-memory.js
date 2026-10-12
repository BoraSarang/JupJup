// In-memory store implementing the same semantics as the Room DAOs
// (COALESCE ordering, game exclusion, pagination). Used for tests and
// local dev without Postgres. PG store must mirror these semantics.
import { mergeApps } from './merge.js';

const effTime = (r) => r.releaseDate ?? r.lastUpdatedAt ?? 0;

export function createMemoryStore() {
  const apps = new Map(); // id -> app
  const mappings = new Map(); // appId -> [{sourceName, sourceUrl, fetchedAt}]
  const versions = new Map(); // appId -> [{version, detectedAt, notesSummary, sourceUrl}]
  const news = new Map();
  const newsRel = new Map(); // newsId -> Set(appId)
  const community = new Map();
  const sources = new Map();
  const state = { seedStatus: 'idle' };

  function ingestApps(drafts, now = Date.now()) {
    let created = 0, updated = 0;
    const createdIds = [];
    for (const d of drafts) {
      if (!d || !d.app || !d.app.id) continue;
      const incoming = { ...d.app, lastUpdatedAt: d.app.lastUpdatedAt ?? now };
      const ex = apps.get(incoming.id);
      if (!ex) {
        apps.set(incoming.id, { ...incoming, firstSeenAt: incoming.firstSeenAt ?? now, isNew: incoming.isNew ?? true });
        created++; createdIds.push(incoming.id);
      } else {
        apps.set(incoming.id, mergeApps(ex, incoming));
        updated++;
      }
      if (Array.isArray(d.mappings)) {
        const list = mappings.get(incoming.id) ?? [];
        for (const m of d.mappings) {
          const i = list.findIndex((x) => x.sourceName === m.sourceName);
          const row = { sourceName: m.sourceName, sourceUrl: m.sourceUrl ?? null, fetchedAt: m.fetchedAt ?? now };
          if (i >= 0) list[i] = row; else list.push(row);
        }
        mappings.set(incoming.id, list);
      }
      if (incoming.version) {
        const list = versions.get(incoming.id) ?? [];
        if (!list.some((v) => v.version === incoming.version)) {
          list.push({ version: incoming.version, detectedAt: now, notesSummary: incoming.releaseNotesSummary ?? null, sourceUrl: null });
        }
        versions.set(incoming.id, list);
      }
    }
    return { created, updated, createdIds };
  }

  function ingestNews(articles, relations = []) {
    let created = 0;
    for (const a of articles) {
      if (!a || !a.id) continue;
      if (!news.has(a.id)) { news.set(a.id, { ...a }); created++; }
      else news.set(a.id, { ...news.get(a.id), ...a });
    }
    for (const r of relations) {
      if (!r || !r.newsId || !r.appId) continue;
      if (!newsRel.has(r.newsId)) newsRel.set(r.newsId, new Set());
      newsRel.get(r.newsId).add(r.appId);
    }
    return { created, updated: articles.length - created };
  }

  function ingestCommunity(posts) {
    let created = 0;
    for (const p of posts) {
      if (!p || !p.id) continue;
      if (!community.has(p.id)) { community.set(p.id, { ...p }); created++; }
    }
    return { created, updated: posts.length - created };
  }

  const isGame = (a) => a.category === '게임' || String(a.tags ?? '').split(',').includes('game');

  function listApps({ q = '', license = '', category = '', sort = 'updated', sourceId = '', page = 1, pageSize = 20 } = {}) {
    let rows = [...apps.values()].filter((a) => !isGame(a));
    if (q) { const s = q.toLowerCase(); rows = rows.filter((a) => (`${a.name} ${a.developer} ${a.descriptionSnippet ?? ''}`).toLowerCase().includes(s)); }
    if (license) rows = rows.filter((a) => a.license === license);
    if (category) rows = rows.filter((a) => a.category === category);
    if (sourceId) rows = rows.filter((a) => (mappings.get(a.id) ?? []).some((m) => m.sourceName === sourceId));
    rows.sort(sort === 'firstSeen'
      ? (x, y) => (y.firstSeenAt ?? 0) - (x.firstSeenAt ?? 0)
      : (x, y) => effTime(y) - effTime(x));
    const total = rows.length;
    const items = rows.slice((page - 1) * pageSize, page * pageSize).map((app) => ({ app, sources: mappings.get(app.id) ?? [] }));
    return { items, total, page, pageSize };
  }

  function listGames({ genre = '', source = '', sort = 'newest', page = 1, pageSize = 20 } = {}) {
    let rows = [...apps.values()].filter(isGame);
    if (genre) rows = rows.filter((a) => String(a.tags ?? '').split(',').includes(genre));
    if (source) rows = rows.filter((a) => (mappings.get(a.id) ?? []).some((m) => m.sourceName === source));
    rows.sort(sort === 'firstSeen'
      ? (x, y) => (y.firstSeenAt ?? 0) - (x.firstSeenAt ?? 0)
      : (x, y) => effTime(y) - effTime(x));
    const total = rows.length;
    return { items: rows.slice((page - 1) * pageSize, page * pageSize), total, page, pageSize };
  }

  function listNews({ main = '', sub = '', q = '', sourceId = '', page = 1, pageSize = 20 } = {}) {
    let rows = [...news.values()];
    if (main) rows = rows.filter((a) => a.main === main);
    if (sub && sub !== '전체') rows = rows.filter((a) => a.sub === sub);
    if (sourceId) rows = rows.filter((a) => a.sourceId === sourceId);
    if (q) { const s = q.toLowerCase(); rows = rows.filter((a) => (`${a.title} ${a.summary ?? ''}`).toLowerCase().includes(s)); }
    rows.sort((x, y) => ((y.publishedAt || 0) || y.collectedAt) - ((x.publishedAt || 0) || x.collectedAt));
    const total = rows.length;
    return { items: rows.slice((page - 1) * pageSize, page * pageSize), total, page, pageSize };
  }

  function listCommunity({ main = '', sourceId = '', page = 1, pageSize = 20 } = {}) {
    let rows = [...community.values()];
    if (main) rows = rows.filter((a) => a.main === main);
    if (sourceId) rows = rows.filter((a) => a.sourceId === sourceId);
    rows.sort((x, y) => ((y.publishedAt || 0) || y.collectedAt) - ((x.publishedAt || 0) || x.collectedAt));
    const total = rows.length;
    return { items: rows.slice((page - 1) * pageSize, page * pageSize), total, page, pageSize };
  }

  return {
    ingestApps, ingestNews, ingestCommunity,
    listApps, listGames, listNews, listCommunity,
    getApp: (id) => (apps.get(id) ? { app: apps.get(id), sources: mappings.get(id) ?? [], versions: versions.get(id) ?? [] } : null),
    getNews: (id) => (news.get(id) ? { ...news.get(id), relatedAppIds: [...(newsRel.get(id) ?? [])] } : null),
    getCommunity: (id) => community.get(id) ?? null,
    upsertSources: (list) => { for (const s of list) sources.set(s.id, { ...(sources.get(s.id) ?? {}), ...s }); },
    watchlist: () => [...sources.values()],
    stats: () => ({ totalApps: apps.size, totalNews: news.size, totalCommunity: community.size }),
    main: () => {
      const all = [...apps.values()].filter((a) => !isGame(a));
      const byTime = [...all].sort((x, y) => effTime(y) - effTime(x));
      return { counts: { apps: all.length, games: apps.size - all.length, news: news.size }, latest: byTime.slice(0, 8) };
    },
    state,
  };
}
