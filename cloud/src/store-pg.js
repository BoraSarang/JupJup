// Postgres store — same interface as store-memory.js.
// Tables per cloud/schema.sql. Merge semantics shared via merge.js.
import { mergeApps } from './merge.js';

const GAME_WHERE = `(category <> '게임' AND (tags IS NULL OR tags NOT LIKE '%game%'))`;
const ONLY_GAME_WHERE = `(category = '게임' OR tags LIKE '%game%')`;

function pageOf(v, dflt, max = 100) {
  const n = Number(v);
  if (!Number.isFinite(n) || n < 1) return dflt;
  return Math.min(Math.floor(n), max);
}

export function createPgStore(pool) {
  async function ingestApps(drafts) {
    let created = 0, updated = 0;
    const createdIds = [];
    const now = Date.now();
    for (const d of drafts) {
      if (!d?.app?.id) continue;
      const incoming = { ...d.app, lastUpdatedAt: d.app.lastUpdatedAt ?? now };
      const { rows } = await pool.query('SELECT * FROM apps WHERE id=$1', [incoming.id]);
      if (rows.length === 0) {
        const row = { ...incoming, firstSeenAt: incoming.firstSeenAt ?? now, isNew: incoming.isNew ?? true };
        // pg folds unquoted identifiers: quote lowercased keys to match schema columns.
        const cols = Object.keys(row).filter((c) => row[c] !== undefined);
        await pool.query(
          `INSERT INTO apps(${cols.map((c) => `"${c.toLowerCase()}"`).join(',')}) VALUES(${cols.map((_, i) => `$${i + 1}`).join(',')})`,
          cols.map((c) => row[c]),
        );
        created++; createdIds.push(incoming.id);
      } else {
        const merged = mergeApps(toApp(rows[0]), incoming);
        const cols = Object.keys(merged).filter((c) => c !== 'id' && merged[c] !== undefined);
        await pool.query(
          `UPDATE apps SET ${cols.map((c, i) => `"${c.toLowerCase()}"=$${i + 2}`).join(',')} WHERE id=$1`,
          [incoming.id, ...cols.map((c) => merged[c])],
        );
        updated++;
      }
      for (const m of d.mappings ?? []) {
        await pool.query(
          `INSERT INTO app_sources(appId,sourceName,sourceUrl,fetchedAt) VALUES($1,$2,$3,$4)
           ON CONFLICT(appId,sourceName) DO UPDATE SET sourceUrl=EXCLUDED.sourceUrl, fetchedAt=EXCLUDED.fetchedAt`,
          [incoming.id, m.sourceName, m.sourceUrl ?? null, m.fetchedAt ?? now],
        );
      }
      if (incoming.version) {
        await pool.query(
          `INSERT INTO version_history(appId,version,detectedAt,notesSummary) VALUES($1,$2,$3,$4) ON CONFLICT DO NOTHING`,
          [incoming.id, incoming.version, now, incoming.releaseNotesSummary ?? null],
        );
      }
    }
    return { created, updated, createdIds };
  }

  async function ingestNews(articles, relations = []) {
    let created = 0;
    for (const a of articles) {
      if (!a?.id) continue;
      const r = await pool.query(
        `INSERT INTO news_articles(id,sourceId,sourceName,main,sub,title,summary,titleKo,summaryKo,contentHtml,originalUrl,thumbnailUrl,publishedAt,collectedAt,tags)
         VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
         ON CONFLICT(id) DO UPDATE SET title=EXCLUDED.title, summary=EXCLUDED.summary, contentHtml=EXCLUDED.contentHtml, tags=EXCLUDED.tags
         RETURNING (xmax = 0) AS inserted`,
        [a.id, a.sourceId, a.sourceName, a.main, a.sub ?? '전체', a.title, a.summary ?? null, a.titleKo ?? null, a.summaryKo ?? null,
          a.contentHtml ?? null, a.originalUrl, a.thumbnailUrl ?? null, a.publishedAt, a.collectedAt ?? Date.now(), a.tags ?? null],
      );
      if (r.rows[0]?.inserted) created++;
    }
    for (const rel of relations) {
      if (!rel?.newsId || !rel?.appId) continue;
      await pool.query(`INSERT INTO news_app_relation(newsId,appId) VALUES($1,$2) ON CONFLICT DO NOTHING`, [rel.newsId, rel.appId]);
    }
    return { created, updated: articles.length - created };
  }

  async function ingestCommunity(posts) {
    let created = 0;
    for (const p of posts) {
      if (!p?.id) continue;
      const r = await pool.query(
        `INSERT INTO community_posts(id,sourceId,sourceName,main,title,summary,authorName,originalUrl,thumbnailUrl,commentCount,viewCount,contentHtml,publishedAt,collectedAt)
         VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT(id) DO NOTHING RETURNING id`,
        [p.id, p.sourceId, p.sourceName, p.main, p.title, p.summary ?? null, p.authorName ?? null, p.originalUrl,
          p.thumbnailUrl ?? null, p.commentCount ?? null, p.viewCount ?? null, p.contentHtml ?? null, p.publishedAt, p.collectedAt ?? Date.now()],
      );
      if (r.rows.length) created++;
    }
    return { created, updated: posts.length - created };
  }

  async function relatedAppsFor(newsIds) {
    if (!newsIds.length) return new Map();
    const rel = await pool.query('SELECT newsId, appId FROM news_app_relation WHERE newsId = ANY($1)', [newsIds]);
    const byNews = new Map(newsIds.map((id) => [id, []]));
    const appIds = [...new Set(rel.rows.map((r) => r.appid))];
    let byApp = new Map();
    if (appIds.length) {
      const { rows } = await pool.query('SELECT * FROM apps WHERE id = ANY($1)', [appIds]);
      byApp = new Map(rows.map((r) => [r.id, toApp(r)]));
    }
    for (const r of rel.rows) {
      const a = byApp.get(r.appid);
      if (a) byNews.get(r.newsid)?.push({ id: a.id, name: a.name, category: a.category, ...(a.version ? { version: a.version } : {}), ...(a.iconUrl ? { iconUrl: a.iconUrl } : {}) });
    }
    return byNews;
  }

  async function appsByIds(ids) {
    if (!ids.length) return [];
    const { rows } = await pool.query('SELECT * FROM apps WHERE id = ANY($1)', [ids]);
    return rows.map(toApp);
  }

  async function mainDetail() {
    const cnt = async (sql, args = []) => (await pool.query(sql, args)).rows[0].c;
    const counts = {
      mac: await cnt(`SELECT COUNT(*)::int c FROM news_articles WHERE main='mac'`),
      ai: await cnt(`SELECT COUNT(*)::int c FROM news_articles WHERE main='ai'`),
      sec: await cnt(`SELECT COUNT(*)::int c FROM news_articles WHERE main='sec'`),
      games: await cnt(`SELECT COUNT(*)::int c FROM apps WHERE ${ONLY_GAME_WHERE}`),
    };
    const recent = async (main) => (await pool.query(
      `SELECT * FROM news_articles WHERE main=$1 ORDER BY COALESCE(NULLIF(publishedAt,0), collectedAt) DESC LIMIT 4`, [main],
    )).rows.map(toNews);
    const mac = await recent('mac'), ai = await recent('ai'), sec = await recent('sec');
    const rel = await relatedAppsFor([...mac, ...ai, ...sec].map((a) => a.id));
    const withRel = (list) => list.map((a) => ({ ...a, relatedApps: rel.get(a.id) ?? [] }));
    const games = (await pool.query(`SELECT * FROM apps WHERE ${ONLY_GAME_WHERE} ORDER BY COALESCE(releaseDate, lastUpdatedAt) DESC LIMIT 8`)).rows.map(toApp);
    const updatedApps = (await pool.query(`SELECT * FROM apps WHERE ${GAME_WHERE} ORDER BY lastUpdatedAt DESC LIMIT 8`)).rows.map(toApp);
    const dayStart = new Date().setHours(0, 0, 0, 0);
    return {
      todayNews: await cnt('SELECT COUNT(*)::int c FROM news_articles WHERE collectedAt >= $1', [dayStart]),
      totalApps: await cnt('SELECT COUNT(*)::int c FROM apps'),
      counts, mac: withRel(mac), ai: withRel(ai), sec: withRel(sec), games, updatedApps,
    };
  }

  async function trends() {
    const g = async (col) => (await pool.query(`SELECT ${col} AS k, COUNT(*)::int c FROM apps WHERE ${GAME_WHERE} GROUP BY ${col}`)).rows;
    const byCategory = {}, byLicense = {};
    for (const r of await g('category')) byCategory[r.k] = r.c;
    for (const r of await g('license')) byLicense[r.k] = r.c;
    const bySource = (await pool.query('SELECT sourceName AS k, COUNT(*)::int c FROM app_sources GROUP BY sourceName')).rows
      .map((r) => ({ sourceId: r.k, sourceName: r.k, count: r.c }));
    const weekAgo = Date.now() - 7 * 86400_000;
    return {
      byCategory, byLicense, bySource,
      newLast7d: (await pool.query(`SELECT COUNT(*)::int c FROM apps WHERE ${GAME_WHERE} AND firstSeenAt >= $1`, [weekAgo])).rows[0].c,
      updatedLast7d: (await pool.query(`SELECT COUNT(*)::int c FROM apps WHERE ${GAME_WHERE} AND lastUpdatedAt >= $1`, [weekAgo])).rows[0].c,
      versionBumpsLast7d: 0,
      aiTagCount: (await pool.query(`SELECT COUNT(*)::int c FROM apps WHERE ${GAME_WHERE} AND tags LIKE '%AI%'`)).rows[0].c,
      menuBarTagCount: (await pool.query(`SELECT COUNT(*)::int c FROM apps WHERE ${GAME_WHERE} AND tags LIKE '%MenuBar%'`)).rows[0].c,
    };
  }
  async function sourcesFor(appIds) {
    if (!appIds.length) return new Map();
    const { rows } = await pool.query(
      `SELECT * FROM app_sources WHERE appId = ANY($1)`, [appIds],
    );
    const map = new Map(appIds.map((id) => [id, []]));
    for (const r of rows) map.get(r.appid)?.push({ sourceName: r.sourcename, sourceUrl: r.sourceurl, fetchedAt: Number(r.fetchedat) });
    return map;
  }

  async function listApps(f = {}) {
    const page = pageOf(f.page, 1), pageSize = pageOf(f.pageSize, 20);
    const where = [`(${GAME_WHERE})`];
    const args = [];
    if (f.q) { args.push(`%${f.q.toLowerCase()}%`); where.push(`(LOWER(name || ' ' || developer || ' ' || COALESCE(descriptionSnippet,'')) LIKE $${args.length})`); }
    if (f.license) { args.push(f.license); where.push(`license = $${args.length}`); }
    if (f.category) { args.push(f.category); where.push(`category = $${args.length}`); }
    const order = f.sort === 'firstSeen' ? 'firstSeenAt DESC' : 'COALESCE(releaseDate, lastUpdatedAt) DESC';
    let pre = '';
    if (f.sourceId) {
      args.push(f.sourceId); pre = `JOIN app_sources s ON s.appId = apps.id AND s.sourceName = $${args.length}`;
    }
    const total = (await pool.query(`SELECT COUNT(*)::int AS c FROM apps ${pre} WHERE ${where.join(' AND ')}`, args)).rows[0].c;
    const { rows } = await pool.query(
      `SELECT * FROM apps ${pre} WHERE ${where.join(' AND ')} ORDER BY ${order} LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
      [...args, pageSize, (page - 1) * pageSize],
    );
    const smap = await sourcesFor(rows.map((r) => r.id));
    return { items: rows.map((r) => ({ app: toApp(r), sources: smap.get(r.id) ?? [] })), total, page, pageSize };
  }

  async function listGames(f = {}) {
    const page = pageOf(f.page, 1), pageSize = pageOf(f.pageSize, 20);
    const where = [`(${ONLY_GAME_WHERE})`];
    const args = [];
    if (f.genre) { args.push(`%${f.genre}%`); where.push(`(tags LIKE $${args.length})`); }
    let pre = '';
    if (f.source) { args.push(f.source); pre = `JOIN app_sources s ON s.appId = apps.id AND s.sourceName = $${args.length}`; }
    const order = f.sort === 'firstSeen' ? 'firstSeenAt DESC' : 'COALESCE(releaseDate, lastUpdatedAt) DESC';
    const total = (await pool.query(`SELECT COUNT(*)::int AS c FROM apps ${pre} WHERE ${where.join(' AND ')}`, args)).rows[0].c;
    const { rows } = await pool.query(
      `SELECT * FROM apps ${pre} WHERE ${where.join(' AND ')} ORDER BY ${order} LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
      [...args, pageSize, (page - 1) * pageSize],
    );
    return { items: rows.map(toApp), total, page, pageSize };
  }

  async function listNews(f = {}) {
    const page = pageOf(f.page, 1), pageSize = pageOf(f.pageSize, 20);
    const where = ['1=1'];
    const args = [];
    if (f.main) { args.push(f.main); where.push(`main = $${args.length}`); }
    if (f.sub && f.sub !== '전체') { args.push(f.sub); where.push(`sub = $${args.length}`); }
    if (f.sourceId) { args.push(f.sourceId); where.push(`sourceId = $${args.length}`); }
    if (f.q) { args.push(`%${f.q.toLowerCase()}%`); where.push(`(LOWER(title || ' ' || COALESCE(summary,'')) LIKE $${args.length})`); }
    const order = 'COALESCE(NULLIF(publishedAt,0), collectedAt) DESC';
    const total = (await pool.query(`SELECT COUNT(*)::int AS c FROM news_articles WHERE ${where.join(' AND ')}`, args)).rows[0].c;
    const { rows } = await pool.query(
      `SELECT * FROM news_articles WHERE ${where.join(' AND ')} ORDER BY ${order} LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
      [...args, pageSize, (page - 1) * pageSize],
    );
    const rel = await relatedAppsFor(rows.map((r) => r.id));
    return { items: rows.map((r) => ({ ...toNews(r), relatedApps: rel.get(r.id) ?? [] })), total, page, pageSize };
  }

  async function listCommunity(f = {}) {
    const page = pageOf(f.page, 1), pageSize = pageOf(f.pageSize, 20);
    const where = ['1=1'];
    const args = [];
    if (f.main) { args.push(f.main); where.push(`main = $${args.length}`); }
    if (f.sourceId) { args.push(f.sourceId); where.push(`sourceId = $${args.length}`); }
    const order = 'COALESCE(NULLIF(publishedAt,0), collectedAt) DESC';
    const total = (await pool.query(`SELECT COUNT(*)::int AS c FROM community_posts WHERE ${where.join(' AND ')}`, args)).rows[0].c;
    const { rows } = await pool.query(
      `SELECT * FROM community_posts WHERE ${where.join(' AND ')} ORDER BY ${order} LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
      [...args, pageSize, (page - 1) * pageSize],
    );
    return { items: rows.map(toPost), total, page, pageSize };
  }

  return {
    ingestApps, ingestNews, ingestCommunity,
    listApps, listGames, listNews, listCommunity,
    appsByIds, trends, mainDetail,
    getApp: async (id) => {
      const { rows } = await pool.query('SELECT * FROM apps WHERE id=$1', [id]);
      if (!rows.length) return null;
      const smap = await sourcesFor([id]);
      const v = await pool.query('SELECT version, detectedAt, notesSummary, sourceUrl FROM version_history WHERE appId=$1 ORDER BY detectedAt DESC', [id]);
      return { app: toApp(rows[0]), sources: smap.get(id) ?? [], versions: v.rows.map((r) => ({ version: r.version, detectedAt: Number(r.detectedat), notesSummary: r.notessummary ?? null, sourceUrl: r.sourceurl ?? null })) };
    },
    getNews: async (id) => {
      const { rows } = await pool.query('SELECT * FROM news_articles WHERE id=$1', [id]);
      if (!rows.length) return null;
      const rel = await relatedAppsFor([id]);
      return { ...toNews(rows[0]), relatedApps: rel.get(id) ?? [] };
    },
    getCommunity: async (id) => {
      const { rows } = await pool.query('SELECT * FROM community_posts WHERE id=$1', [id]);
      return rows.length ? toPost(rows[0]) : null;
    },
    upsertSources: async (list) => {
      for (const s of list) {
        await pool.query(
          `INSERT INTO sources(id,name,type,baseUrl,enabled,intervalHours,intervalMinutes,lastRunAt,lastStatus,errorMessage)
           VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
           ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name, enabled=EXCLUDED.enabled, intervalMinutes=EXCLUDED.intervalMinutes, lastRunAt=EXCLUDED.lastRunAt, lastStatus=EXCLUDED.lastStatus`,
          [s.id, s.name, s.type, s.baseUrl, s.enabled, s.intervalHours ?? 0, s.intervalMinutes ?? 60, s.lastRunAt ?? null, s.lastStatus ?? 'NEVER_RUN', s.errorMessage ?? null],
        );
      }
    },
    watchlist: async () => (await pool.query('SELECT * FROM sources ORDER BY id')).rows.map((r) => ({
      id: r.id, name: r.name, type: r.type, baseUrl: r.baseurl, enabled: !!r.enabled,
      intervalHours: Number(r.intervalhours), intervalMinutes: Number(r.intervalminutes),
      lastRunAt: r.lastrunat == null ? null : Number(r.lastrunat), lastStatus: r.laststatus,
      errorMessage: r.errormessage ?? null,
    })),
    stats: async () => ({
      totalApps: (await pool.query(`SELECT COUNT(*)::int AS c FROM apps WHERE ${GAME_WHERE}`)).rows[0].c,
      activeSources: (await pool.query('SELECT COUNT(*)::int AS c FROM sources WHERE enabled').catch(() => ({ rows: [{ c: 0 }] }))).rows[0].c,
      netRxBytes: 0,
      netTxBytes: 0,
    }),
    collect: async () => ({ days: [], total: { found: 0, new: 0, updated: 0, rxBytes: 0, txBytes: 0 } }),
    insights: async () => ({ insights: [] }),
    main: async () => mainDetail(),
  };
}

function toApp(row) {
  // pg folds unquoted identifiers to lowercase; schema columns are camelCase.
  const g = (...names) => { for (const n of names) if (row[n] !== undefined) return row[n]; return null; };
  const num = (v) => (v === null || v === undefined ? null : Number(v));
  return {
    id: row.id, platform: row.platform, name: row.name, developer: row.developer,
    license: row.license, price: Number(row.price), currency: row.currency, category: row.category,
    tags: row.tags ?? null, trackId: row.trackid == null ? null : Number(row.trackid),
    repoFullName: row.repofullname ?? null, homepageUrl: row.homepageurl ?? null,
    version: row.version ?? null, prevVersion: row.prevversion ?? null,
    releaseNotesSummary: row.releasenotessummary ?? null, releaseDate: num(row.releasedate),
    descriptionSnippet: row.descriptionsnippet ?? null, longDescription: row.longdescription ?? null,
    iconUrl: row.iconurl ?? null, descriptionKo: row.descriptionko ?? null,
    longDescriptionKo: row.longdescriptionko ?? null, releaseNotes: row.releasenotes ?? null,
    releaseNotesKo: row.releasenotesko ?? null, sellerName: row.sellername ?? null,
    fileSize: num(row.filesize), minOs: row.minos ?? null, contentRating: row.contentrating ?? null,
    forks: num(row.forks), issues: num(row.issues), licenseName: row.licensename ?? null,
    screenshotUrls: row.screenshoturls ?? null, averageRating: num(row.averagerating),
    ratingCount: num(row.ratingcount), stars: num(row.stars),
    primaryLanguage: row.primarylanguage ?? null, supportedLanguages: row.supportedlanguages ?? null,
    topics: row.topics ?? null, firstSeenAt: Number(row.firstseenat),
    lastUpdatedAt: Number(row.lastupdatedat), isNew: !!row.isnew,
    sourceId: g('sourceid') ?? null, licenseOverride: row.licenseoverride ?? null,
  };
}

function toNews(row) {
  return {
    id: row.id, sourceId: row.sourceid, sourceName: row.sourcename, main: row.main, sub: row.sub,
    title: row.title, summary: row.summary ?? null, titleKo: row.titleko ?? null,
    summaryKo: row.summaryko ?? null, contentHtml: row.contenthtml ?? null,
    originalUrl: row.originalurl, thumbnailUrl: row.thumbnailurl ?? null,
    publishedAt: Number(row.publishedat), collectedAt: Number(row.collectedat), tags: row.tags ?? null,
  };
}

function toPost(row) {
  return {
    id: row.id, sourceId: row.sourceid, sourceName: row.sourcename, main: row.main,
    title: row.title, summary: row.summary ?? null, authorName: row.authorname ?? null,
    originalUrl: row.originalurl, thumbnailUrl: row.thumbnailurl ?? null,
    commentCount: row.commentcount == null ? null : Number(row.commentcount),
    viewCount: row.viewcount == null ? null : Number(row.viewcount),
    contentHtml: row.contenthtml ?? null, publishedAt: Number(row.publishedat),
    collectedAt: Number(row.collectedat),
  };
}
