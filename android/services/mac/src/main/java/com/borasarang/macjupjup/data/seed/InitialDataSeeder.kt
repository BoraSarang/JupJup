package com.borasarang.macjupjup.data.seed

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 초기 수집 소스 6개 시드 (Setapp·PH·HN·MMB 제거됨). selectorConfigJson=null = 코드 기본값 사용.
 * R32: 뉴스 RSS 12개 추가 (맥 4·AI 4·보안 4, 15분 주기).
 * PLAN_v23: 커뮤니티 보드 6개 (30분, DC 2종 off).
 * 제거된 소스 잔재는 Application 시작 시 purge (행·매핑·고아앱·로그 삭제).
 * DB v1 기존 기기는 없는 소스만 추가(seedMissing).
 */
object InitialDataSeeder {

    private fun seeds() = listOf(
                CrawlSource(
                    id = Constants.SOURCE_GITHUB_SEARCH,
                    name = "GitHub 신규 저장소",
                    type = Constants.TYPE_GITHUB_SEARCH,
                    baseUrl = "https://api.github.com",
                    enabled = true,
                    intervalHours = 6,
                    intervalMinutes = 360,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_GITHUB_RELEASES,
                    name = "GitHub 릴리즈 추적",
                    type = Constants.TYPE_GITHUB_RELEASES,
                    baseUrl = "https://api.github.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_CHART_RSS,
                    name = "Mac 차트 RSS",
                    type = Constants.TYPE_CHART_RSS,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_ITUNES_LOOKUP,
                    name = "iTunes 버전 폴링",
                    type = Constants.TYPE_ITUNES_LOOKUP,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_NAME_MATCH,
                    name = "iTunes 이름 대조",
                    type = Constants.TYPE_NAME_MATCH,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 168,
                    intervalMinutes = 10080,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_MAS_DISCOVERY,
                    name = "MAS 키워드 발견",
                    type = Constants.TYPE_MAS_DISCOVERY,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_REDDIT_MACAPPS,
                    name = "Reddit r/macapps",
                    type = Constants.TYPE_REDDIT_JSON,
                    baseUrl = "https://www.reddit.com/r/macapps",
                    enabled = true,
                    intervalHours = 12,
                    intervalMinutes = 720,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_STEAM_FREE_MAC,
                    name = "Steam 무료 맥 게임",
                    type = Constants.TYPE_STEAM_FREETOMAC,
                    baseUrl = "https://store.steampowered.com",
                    enabled = true,
                    intervalHours = 6,
                    intervalMinutes = 360,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_EPIC_FREE,
                    name = "Epic 주간 무료 게임",
                    type = Constants.TYPE_EPIC_FREE,
                    baseUrl = "https://store-site-backend-static.ak.epicgames.com",
                    enabled = true,
                    intervalHours = 12,
                    intervalMinutes = 720,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_APPSTORRENT_GAMES,
                    name = "AppStorrent 게임",
                    type = Constants.TYPE_APPSTORRENT_GAMES,
                    baseUrl = "https://appstorrent.ru",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_APPSTORRENT_PROGRAMS,
                    name = "AppStorrent 프로그램",
                    type = Constants.TYPE_APPSTORRENT_PROGRAMS,
                    baseUrl = "https://appstorrent.ru",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
            ) + newsSeeds() + communitySeeds()

    /** 뉴스 RSS 12종 (R32). intervalMinutes가 스케줄 기준값, intervalHours는 표시용 */
    private fun newsSeeds() = listOf(
        Triple(Constants.SOURCE_NEWS_MACRUMORS, "MacRumors", "https://feeds.macrumors.com/MacRumors-All"),
        Triple(Constants.SOURCE_NEWS_9TO5MAC, "9to5Mac", "https://9to5mac.com/feed/"),
        Triple(Constants.SOURCE_NEWS_APPLE, "Apple Newsroom", "https://www.apple.com/newsroom/rss-feed.rss"),
        Triple(Constants.SOURCE_NEWS_MACWORLD, "Macworld", "https://www.macworld.com/feed"),
        Triple(Constants.SOURCE_NEWS_MARKTECHPOST, "MarkTechPost", "https://www.marktechpost.com/feed/"),
        Triple(Constants.SOURCE_NEWS_GOOGLE_BLOG, "Google Research", "https://research.google/blog/rss/"),
        Triple(Constants.SOURCE_NEWS_OPENAI, "OpenAI", "https://openai.com/blog/rss/"),
        Triple(Constants.SOURCE_NEWS_TECHCRUNCH_AI, "TechCrunch AI", "https://techcrunch.com/category/artificial-intelligence/feed/"),
        Triple(Constants.SOURCE_NEWS_HACKERNEWS, "The Hacker News", "https://feeds.feedburner.com/TheHackersNews"),
        Triple(Constants.SOURCE_NEWS_BLEEPING, "BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
        Triple(Constants.SOURCE_NEWS_BOAN, "보안뉴스", "https://www.boannews.com/media/news_rss.xml"),
        Triple(Constants.SOURCE_NEWS_DAILYSECU, "데일리시큐", "https://www.dailysecu.com/rss/allArticle.xml"),
    ).map { (id, name, url) ->
        CrawlSource(
            id = id,
            name = name,
            type = Constants.TYPE_NEWS_RSS,
            baseUrl = url,
            enabled = true,
            intervalHours = 1,
            intervalMinutes = Constants.NEWS_INTERVAL_MINUTES,
            lastRunAt = null,
            lastStatus = Constants.STATUS_NEVER_RUN,
            errorMessage = null,
            selectorConfigJson = null,
        )
    }

    /**
     * 커뮤니티 보드 6종 (PLAN_v23). 30분 주기.
     * DC 2종은 실측 body 0bytes(차단) — enabled=false, 해제 시 토글 on.
     */
    private fun communitySeeds(): List<CrawlSource> {
        fun cfg(main: String, listRow: String, title: String, author: String = "", time: String = "",
                excludeRow: String = "", detailContent: String = "", comments: String = ""): String =
            buildJsonObject {
                put("main", JsonPrimitive(main))
                put("listRow", JsonPrimitive(listRow))
                put("title", JsonPrimitive(title))
                put("author", JsonPrimitive(author))
                put("time", JsonPrimitive(time))
                put("excludeRow", JsonPrimitive(excludeRow))
                put("detailContent", JsonPrimitive(detailContent))
                put("comments", JsonPrimitive(comments))
            }.toString()

        fun src(id: String, name: String, url: String, selector: String, enabled: Boolean = true) =
            CrawlSource(
                id = id,
                name = name,
                type = Constants.TYPE_COMMUNITY_BOARD,
                baseUrl = url,
                enabled = enabled,
                intervalHours = 1,
                intervalMinutes = Constants.COMMUNITY_INTERVAL_MINUTES,
                lastRunAt = null,
                lastStatus = Constants.STATUS_NEVER_RUN,
                errorMessage = null,
                selectorConfigJson = selector,
            )

        return listOf(
            src(
                Constants.SOURCE_COMMUNITY_DAMOANG_APPLE,
                "다모앙 애플모앙",
                "https://damoang.net/applemoang",
                cfg(
                    Constants.MAIN_APPLE,
                    listRow = "a.post-row",
                    title = ".post-title",
                    author = ".post-meta-text",
                    time = ".post-meta-text",
                    detailContent = ".prose, .post-content",
                ),
            ),
            src(
                Constants.SOURCE_COMMUNITY_DAMOANG_MAC,
                "다모앙 맥모앙",
                "https://damoang.net/macmoang",
                cfg(
                    Constants.MAIN_MAC_COMMUNITY,
                    listRow = "a.post-row",
                    title = ".post-title",
                    author = ".post-meta-text",
                    time = ".post-meta-text",
                    detailContent = ".prose, .post-content",
                ),
            ),
            src(
                Constants.SOURCE_COMMUNITY_DAMOANG_AI,
                "다모앙 AI",
                "https://damoang.net/ai",
                cfg(
                    Constants.MAIN_AI_COMMUNITY,
                    listRow = "a.post-row",
                    title = ".post-title",
                    author = ".post-meta-text",
                    time = ".post-meta-text",
                    detailContent = ".prose, .post-content",
                ),
            ),
            // 클리앙 MAC — m.clien 구조 동일, 공지 제외, 상세 셀렉터 community 시드와 동일
            src(
                Constants.SOURCE_COMMUNITY_CLIEN_MAC,
                "클리앙 MAC",
                "https://m.clien.net/service/board/cm_mac",
                cfg(
                    Constants.MAIN_MAC_COMMUNITY,
                    listRow = "div.list_item",
                    title = "a.list_subject, .list_title a, [data-role=list-title-text]",
                    author = ".nickname, .list_author .nickname",
                    time = ".list_time, .list_time .timestamp",
                    excludeRow = ".notice",
                    detailContent = ".post_content article, .post_article",
                    comments = ".list_reply .line",
                ),
            ),
            // DC는 목록 body 0bytes 차단 — 시드 off (해제 시 설정에서 토글)
            src(
                Constants.SOURCE_COMMUNITY_DC_APPLE,
                "DC 애플",
                "https://gall.dcinside.com/board/lists/?id=apple",
                cfg(
                    Constants.MAIN_APPLE,
                    listRow = "tr.ub-content",
                    title = ".gall_tit a, .li_subject a",
                    author = ".gall_writer, .nickname",
                    time = ".gall_date, .gall_tu",
                    excludeRow = ".notice_item",
                    detailContent = ".write_content",
                ),
                enabled = false,
            ),
            src(
                Constants.SOURCE_COMMUNITY_DC_MACBOOK,
                "DC 맥북",
                "https://gall.dcinside.com/mgallery/board/lists/?id=macbook",
                cfg(
                    Constants.MAIN_MAC_COMMUNITY,
                    listRow = "tr.ub-content",
                    title = ".gall_tit a, .li_subject a",
                    author = ".gall_writer, .nickname",
                    time = ".gall_date, .gall_tu",
                    excludeRow = ".notice_item",
                    detailContent = ".write_content",
                ),
                enabled = false,
            ),
        )
    }

    suspend fun seedIfEmpty(db: MacDatabase) {
        if (db.crawlSourceDao().getAll().isNotEmpty()) {
            seedMissing(db)
            migrateIntervals(db)
            return
        }
        DebugLogger.i("시드", "초기 수집 소스 시드 시작")
        db.crawlSourceDao().upsertAll(seeds())
        DebugLogger.i("시드", "초기 수집 소스 시드 완료")
    }

    /** 기존 DB에 없는 신규 소스만 추가 (v1→v1.1 name_match 등) */
    suspend fun seedMissing(db: MacDatabase) {
        val dao = db.crawlSourceDao()
        val missing = seeds().filter { dao.getById(it.id) == null }
        if (missing.isEmpty()) return
        DebugLogger.i("시드", "신규 소스 추가: ${missing.map { it.id }}")
        dao.upsertAll(missing)
    }

    /**
     * CPU 부하 완화용 주기 하향 마이그레이션 (기존 설치분).
     * 뉴스 <30분 → 30분, 커뮤니티 <60분 → 60분. 이미 긴 값은 유지.
     */
    suspend fun migrateIntervals(db: MacDatabase) {
        val dao = db.crawlSourceDao()
        var updated = 0
        for (s in dao.getAll()) {
            val target = when (s.type) {
                Constants.TYPE_NEWS_RSS -> Constants.NEWS_INTERVAL_MINUTES
                Constants.TYPE_COMMUNITY_BOARD -> Constants.COMMUNITY_INTERVAL_MINUTES
                else -> continue
            }
            if (s.intervalMinutes < target) {
                dao.updateInterval(s.id, target, target / 60)
                updated++
            }
        }
        if (updated > 0) DebugLogger.i("시드", "수집 주기 마이그레이션 ${updated}건 (뉴스≥${Constants.NEWS_INTERVAL_MINUTES}분·커뮤니티≥${Constants.COMMUNITY_INTERVAL_MINUTES}분)")
    }
}
