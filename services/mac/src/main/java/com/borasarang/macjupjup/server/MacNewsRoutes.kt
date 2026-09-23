package com.borasarang.macjupjup.server

import com.borasarang.common.server.pathId
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import com.borasarang.macjupjup.data.repository.NewsFilter
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.NewsCategories
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 뉴스 라우트 (R32 PLAN_v17).
 * 목록·상세·메인 대시보드. `/api/apps` 기존 유지.
 */
internal fun HttpServerService.macNewsRoutes(route: Route) {
    val application = app()

    route.get("/api/news") {
        val params = call.queryParameters
        val main = params["main"]?.takeIf { it.isNotBlank() }
        val subRaw = params["sub"]?.takeIf { it.isNotBlank() }
        // "전체"는 필터 해제
        val sub = if (subRaw == NewsCategories.SUB_ALL) null else subRaw
        val result = application.newsRepository.list(
            NewsFilter(
                main = main,
                sub = sub,
                sourceId = params["sourceId"]?.takeIf { it.isNotBlank() },
                q = params["q"]?.takeIf { it.isNotBlank() },
                page = params["page"]?.toIntOrNull() ?: 1,
                pageSize = params["pageSize"]?.toIntOrNull()
                    ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                    ?: Constants.API_DEFAULT_PAGE_SIZE,
            ),
        )
        // R49: 목록에 관련 앱 일괄 조회 (관련앱 레일 노출용)
        val related = application.newsRepository.relatedByNews(result.articles.map { it.id })
        call.respondText(
            newsListJson(result.articles, result.total, result.page, result.pageSize, related),
            ContentType.Application.Json,
        )
    }

    route.get("/api/news/{id}") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@get
        }
        val found = application.newsRepository.detail(id)
        if (found == null) {
            call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        } else {
            call.respondText(newsDetailJson(found), ContentType.Application.Json)
        }
    }

    /**
     * 올인원 대시보드 (R32).
     * 히어로(오늘 수집) + main별 건수 + main별 최신 4건 + 최근 업데이트 앱 8건.
     */
    route.get("/api/main") {
        val news = application.newsRepository
        val counts = news.countByMain()
        val today = news.countToday()
        val recentMac = news.recentByMain(NewsCategories.MAIN_MAC, 4)
        val recentAi = news.recentByMain(NewsCategories.MAIN_AI, 4)
        val recentSec = news.recentByMain(NewsCategories.MAIN_SEC, 4)
        val updatedApps = application.appRepository.list(
            com.borasarang.macjupjup.data.repository.AppFilter(
                sort = "updated",
                page = 1,
                pageSize = 8,
            ),
        )
        val totalApps = application.appRepository.overview().totalApps
        // R49: 메인 대시보드 뉴스에 관련 앱 일괄 조회
        val related = news.relatedByNews(
            (recentMac + recentAi + recentSec).map { it.id },
        )
        // PLAN_v21: 맥 게임 (최신 8 + 건수)
        val gamesPage = application.appRepository.games(page = 1, pageSize = 8, sort = "newest")
        val gameCount = gamesPage.total
        call.respondText(
            buildJsonObject {
                put("todayNews", today)
                put("totalApps", totalApps)
                put("counts", buildJsonObject {
                    put(NewsCategories.MAIN_MAC, counts[NewsCategories.MAIN_MAC] ?: 0)
                    put(NewsCategories.MAIN_AI, counts[NewsCategories.MAIN_AI] ?: 0)
                    put(NewsCategories.MAIN_SEC, counts[NewsCategories.MAIN_SEC] ?: 0)
                    put("games", gameCount)
                })
                put("mac", newsArray(recentMac, related))
                put("ai", newsArray(recentAi, related))
                put("sec", newsArray(recentSec, related))
                put("games", buildJsonArray {
                    gamesPage.apps.forEach { item ->
                        add(
                            buildJsonObject {
                                appElement(item.app).entries.forEach { (key, value) -> put(key, value) }
                                put("store", storeOfTags(item.app.tags))
                                put("genres", genreArray(item.app.tags))
                                item.sourceUrl?.let { put("sourceUrl", it) }
                            },
                        )
                    }
                })
                put("updatedApps", buildJsonArray {
                    updatedApps.apps.forEach { item ->
                        add(
                            buildJsonObject {
                                put("id", item.app.id)
                                put("name", item.app.name)
                                put("version", item.app.version ?: "")
                                put("category", item.app.category)
                                put("license", item.app.license)
                                put("price", item.app.price)
                                put("isNew", item.app.isNew)
                                item.app.iconUrl?.let { put("iconUrl", it) }
                                put("lastUpdatedAt", item.app.lastUpdatedAt)
                            },
                        )
                    }
                })
                put("generatedAt", System.currentTimeMillis())
            }.toString(),
            ContentType.Application.Json,
        )
    }
}

/** 목록용 뉴스 필드 (본문 제외) + 관련 앱 (R49) */
internal fun newsElement(
    a: NewsArticle,
    related: List<com.borasarang.macjupjup.data.db.entity.App> = emptyList(),
) = buildJsonObject {
    put("id", a.id)
    put("sourceId", a.sourceId)
    put("sourceName", a.sourceName)
    put("main", a.main)
    put("sub", a.sub)
    put("title", a.title)
    a.summary?.let { put("summary", it) }
    a.titleKo?.let { put("titleKo", it) }
    a.summaryKo?.let { put("summaryKo", it) }
    a.thumbnailUrl?.let { put("thumbnailUrl", it) }
    put("originalUrl", a.originalUrl)
    put("publishedAt", a.publishedAt)
    put("collectedAt", a.collectedAt)
    a.tags?.takeIf { it.isNotBlank() }?.let { put("tags", it) }
    put("relatedApps", relatedAppArray(related))
}

/** 관련 앱 카드 공용 (목록·상세 공통) */
internal fun relatedAppArray(
    apps: List<com.borasarang.macjupjup.data.db.entity.App>,
) = buildJsonArray {
    apps.forEach { app ->
        add(
            buildJsonObject {
                put("id", app.id)
                put("name", app.name)
                put("category", app.category)
                app.version?.let { put("version", it) }
                app.iconUrl?.let { put("iconUrl", it) }
            },
        )
    }
}

internal fun newsArray(
    articles: List<NewsArticle>,
    related: Map<String, List<com.borasarang.macjupjup.data.db.entity.App>> = emptyMap(),
) = buildJsonArray {
    articles.forEach { add(newsElement(it, related[it.id] ?: emptyList())) }
}

internal fun newsListJson(
    articles: List<NewsArticle>,
    total: Int,
    page: Int,
    pageSize: Int,
    related: Map<String, List<com.borasarang.macjupjup.data.db.entity.App>> = emptyMap(),
): String {
    return buildJsonObject {
        put("news", newsArray(articles, related))
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }.toString()
}

internal fun newsDetailJson(item: com.borasarang.macjupjup.data.repository.NewsRepository.NewsDetail): String {
    return buildJsonObject {
        newsElement(item.article, item.relatedApps).entries.forEach { (key, value) -> put(key, value) }
        item.article.contentHtml?.let { put("contentHtml", it) }
        put("relatedApps", relatedAppArray(item.relatedApps))
    }.toString()
}
