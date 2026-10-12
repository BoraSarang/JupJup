-- JupJup cloud schema (Postgres) — Room v11 entities mirrored.
-- Source of truth for column mapping: android/services/mac .../data/db/entity/*.kt
-- epoch-ms times are BIGINT (lossless vs Room Long). CSV-ish TEXT columns stay TEXT.

CREATE TABLE IF NOT EXISTS apps (
  id TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  name TEXT NOT NULL,
  developer TEXT NOT NULL,
  license TEXT NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  currency TEXT NOT NULL,
  category TEXT NOT NULL,
  tags TEXT,
  trackId BIGINT,
  repoFullName TEXT,
  homepageUrl TEXT,
  version TEXT,
  prevVersion TEXT,
  releaseNotesSummary TEXT,
  releaseDate BIGINT,
  descriptionSnippet TEXT,
  longDescription TEXT,
  iconUrl TEXT,
  descriptionKo TEXT,
  longDescriptionKo TEXT,
  releaseNotes TEXT,
  releaseNotesKo TEXT,
  sellerName TEXT,
  fileSize BIGINT,
  minOs TEXT,
  contentRating TEXT,
  forks INTEGER,
  issues INTEGER,
  licenseName TEXT,
  screenshotUrls TEXT,
  averageRating DOUBLE PRECISION,
  ratingCount INTEGER,
  stars INTEGER,
  primaryLanguage TEXT,
  supportedLanguages TEXT,
  topics TEXT,
  firstSeenAt BIGINT NOT NULL,
  lastUpdatedAt BIGINT NOT NULL,
  isNew BOOLEAN NOT NULL,
  sourceId TEXT,
  licenseOverride TEXT,
  UNIQUE (name, developer)
);
CREATE INDEX IF NOT EXISTS idx_apps_license ON apps(license);
CREATE INDEX IF NOT EXISTS idx_apps_category ON apps(category);
CREATE INDEX IF NOT EXISTS idx_apps_trackId ON apps(trackId);
CREATE INDEX IF NOT EXISTS idx_apps_repo ON apps(repoFullName);
CREATE INDEX IF NOT EXISTS idx_apps_updated ON apps(lastUpdatedAt);
CREATE INDEX IF NOT EXISTS idx_apps_isNew ON apps(isNew);
CREATE INDEX IF NOT EXISTS idx_apps_tags ON apps(tags);
CREATE INDEX IF NOT EXISTS idx_apps_firstSeen ON apps(firstSeenAt);
CREATE INDEX IF NOT EXISTS idx_apps_lic_cat_upd ON apps(license, category, lastUpdatedAt);

CREATE TABLE IF NOT EXISTS app_sources (
  appId TEXT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  sourceName TEXT NOT NULL,
  sourceUrl TEXT,
  fetchedAt BIGINT NOT NULL,
  PRIMARY KEY (appId, sourceName)
);
CREATE INDEX IF NOT EXISTS idx_app_sources_source ON app_sources(sourceName);

CREATE TABLE IF NOT EXISTS sources (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  baseUrl TEXT NOT NULL,
  enabled BOOLEAN NOT NULL,
  intervalHours INTEGER NOT NULL,
  intervalMinutes INTEGER NOT NULL,
  lastRunAt BIGINT,
  lastStatus TEXT NOT NULL,
  errorMessage TEXT,
  selectorConfigJson JSONB
);
CREATE INDEX IF NOT EXISTS idx_sources_type ON sources(type);
CREATE INDEX IF NOT EXISTS idx_sources_enabled ON sources(enabled);

CREATE TABLE IF NOT EXISTS version_history (
  appId TEXT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  version TEXT NOT NULL,
  detectedAt BIGINT NOT NULL,
  notesSummary TEXT,
  source TEXT,
  sourceUrl TEXT,
  PRIMARY KEY (appId, version)
);
CREATE INDEX IF NOT EXISTS idx_version_app ON version_history(appId);
CREATE INDEX IF NOT EXISTS idx_version_detected ON version_history(detectedAt);

CREATE TABLE IF NOT EXISTS crawl_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sourceId TEXT NOT NULL,
  sourceName TEXT NOT NULL,
  startedAt BIGINT NOT NULL,
  finishedAt BIGINT,
  status TEXT NOT NULL,
  plansFound INTEGER NOT NULL DEFAULT 0,
  plansNew INTEGER NOT NULL DEFAULT 0,
  plansUpdated INTEGER NOT NULL DEFAULT 0,
  errorMessage TEXT,
  rxBytes BIGINT NOT NULL DEFAULT 0,
  txBytes BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_logs_source ON crawl_logs(sourceId);
CREATE INDEX IF NOT EXISTS idx_logs_started ON crawl_logs(startedAt);

CREATE TABLE IF NOT EXISTS notification_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type TEXT NOT NULL,
  summary TEXT NOT NULL,
  detailJson JSONB NOT NULL,
  createdAt BIGINT NOT NULL,
  isRead BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_notif_type ON notification_logs(type);
CREATE INDEX IF NOT EXISTS idx_notif_created ON notification_logs(createdAt);

CREATE TABLE IF NOT EXISTS news_articles (
  id TEXT PRIMARY KEY,
  sourceId TEXT NOT NULL,
  sourceName TEXT NOT NULL,
  main TEXT NOT NULL,
  sub TEXT NOT NULL,
  title TEXT NOT NULL,
  summary TEXT,
  titleKo TEXT,
  summaryKo TEXT,
  contentHtml TEXT,
  originalUrl TEXT NOT NULL UNIQUE,
  thumbnailUrl TEXT,
  publishedAt BIGINT NOT NULL,
  collectedAt BIGINT NOT NULL,
  tags TEXT
);
CREATE INDEX IF NOT EXISTS idx_news_main_sub_pub ON news_articles(main, sub, publishedAt);
CREATE INDEX IF NOT EXISTS idx_news_pub ON news_articles(publishedAt);

CREATE TABLE IF NOT EXISTS news_app_relation (
  newsId TEXT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
  appId TEXT NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
  PRIMARY KEY (newsId, appId)
);
CREATE INDEX IF NOT EXISTS idx_news_rel_app ON news_app_relation(appId);

CREATE TABLE IF NOT EXISTS community_posts (
  id TEXT PRIMARY KEY,
  sourceId TEXT NOT NULL,
  sourceName TEXT NOT NULL,
  main TEXT NOT NULL,
  title TEXT NOT NULL,
  summary TEXT,
  authorName TEXT,
  originalUrl TEXT NOT NULL UNIQUE,
  thumbnailUrl TEXT,
  commentCount INTEGER,
  viewCount INTEGER,
  contentHtml TEXT,
  publishedAt BIGINT NOT NULL,
  collectedAt BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cm_main_pub ON community_posts(main, publishedAt);
CREATE INDEX IF NOT EXISTS idx_cm_main_source ON community_posts(main, sourceId);
CREATE INDEX IF NOT EXISTS idx_cm_pub ON community_posts(publishedAt);
CREATE INDEX IF NOT EXISTS idx_cm_collected ON community_posts(collectedAt);
