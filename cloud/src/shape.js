// Device-shaped response builders. Key names mirror the Android Ktor server
// (appsJson / gamesJson / newsListJson / communityListJson / main / watchlist)
// so mac_web works unmodified against the cloud API.
import { storeOfTags, genreArray } from './tags.js';

const slimApp = (a) => ({
  id: a.id, name: a.name, version: a.version ?? '', category: a.category,
  license: a.license, price: a.price, isNew: !!a.isNew,
  ...(a.iconUrl ? { iconUrl: a.iconUrl } : {}),
  lastUpdatedAt: a.lastUpdatedAt,
});

const relatedApp = (a) => ({
  id: a.id, name: a.name, category: a.category,
  ...(a.version ? { version: a.version } : {}),
  ...(a.iconUrl ? { iconUrl: a.iconUrl } : {}),
});

export function appsList({ items, total, page, pageSize }) {
  return {
    apps: items.map(({ app, sources }) => ({
      ...app,
      ...((sources ?? [])[0] ? { sourceName: sources[0].sourceName, ...(sources[0].sourceUrl ? { sourceUrl: sources[0].sourceUrl } : {}) } : {}),
    })),
    total, page, pageSize,
  };
}

export function gamesList({ items, total, page, pageSize }) {
  return {
    games: items.map((app) => ({ ...app, store: storeOfTags(app.tags), genres: genreArray(app.tags) })),
    total, page, pageSize,
  };
}

export function newsList({ items, total, page, pageSize }) {
  return {
    news: items.map((a) => ({ ...a, relatedApps: a.relatedApps ?? [] })),
    total, page, pageSize,
  };
}

export function newsDetail(article, relatedApps) {
  return { ...article, relatedApps: (relatedApps ?? []).map(relatedApp) };
}

export function communityList({ items, total, page, pageSize }) {
  return {
    posts: items.map((p) => ({ ...p, hasContent: p.contentHtml != null })),
    total, page, pageSize,
  };
}

export function mainDoc(m) {
  return {
    todayNews: m.todayNews ?? 0,
    totalApps: m.totalApps ?? 0,
    counts: m.counts ?? {},
    mac: m.mac ?? [], ai: m.ai ?? [], sec: m.sec ?? [],
    games: (m.games ?? []).map((app) => ({ ...app, store: storeOfTags(app.tags), genres: genreArray(app.tags) })),
    updatedApps: (m.updatedApps ?? []).map(slimApp),
    generatedAt: Date.now(),
  };
}

export function watchlist(sources) {
  return (sources ?? []).map((s) => ({
    id: s.id, name: s.name, type: s.type, baseUrl: s.baseUrl, enabled: !!s.enabled,
    intervalHours: s.intervalHours ?? 0, intervalMinutes: s.intervalMinutes ?? 60,
    ...(s.lastRunAt != null ? { lastRunAt: Number(s.lastRunAt) } : {}),
    lastStatus: s.lastStatus ?? 'NEVER_RUN',
    ...(s.errorMessage ? { errorMessage: s.errorMessage } : {}),
  }));
}
