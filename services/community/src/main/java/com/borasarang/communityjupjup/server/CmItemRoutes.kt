package com.borasarang.communityjupjup.server

import com.borasarang.common.server.escapeJson
import com.borasarang.common.server.pathId
import com.borasarang.common.server.putIfNotNull
import com.borasarang.common.server.respondNotFound
import com.borasarang.communityjupjup.data.repository.PostFilter
import com.borasarang.communityjupjup.util.CommunityCategories
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 조회 라우트: health·posts·detail·categories·sources·ranking.
 */
internal fun HttpServerService.cmItemRoutes(route: Route) {
    val application = app()
    route.get("/api/health") {
        call.respondText(
            """{"status":"ok","service":"community","port":$currentPort}""",
            ContentType.Application.Json,
        )
    }
    route.get("/api/categories") {
        call.respondText(categoriesJson(), ContentType.Application.Json)
    }
    route.get("/api/sources") {
        val statuses = application.sourceRepository.list()
        val counts = application.database.postDao().countBySource()
            .associate { it.sourceId to it.cnt }
        call.respondText(sourcesJson(statuses, counts), ContentType.Application.Json)
    }
    route.get("/api/posts") {
        val params = call.queryParameters
        val categoryId = params["category_id"]?.toIntOrNull()
            ?: params["categoryId"]?.toIntOrNull()
        if (categoryId != null && CommunityCategories.byId(categoryId) == null) {
            return@get call.respondNotFound("unknown category_id")
        }
        val sourceParams = params.getAll("source_id").orEmpty() +
            params.getAll("sourceId").orEmpty()
        val sourceIds = sourceParams.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val singleSourceId = sourceIds.singleOrNull()
        if (singleSourceId != null && application.sourceRepository.getById(singleSourceId) == null) {
            return@get call.respondNotFound("unknown source_id")
        }
        if (sourceIds.size > 1) {
            val known = application.sourceRepository.list().map { it.id }.toSet()
            if (sourceIds.any { it !in known }) {
                return@get call.respondNotFound("unknown source_id")
            }
        }
        val filter = PostFilter(
            categoryId = categoryId,
            sourceId = singleSourceId,
            sourceIds = if (sourceIds.size > 1) sourceIds else emptySet(),
            q = params["q"]?.takeIf { it.isNotBlank() },
            sort = params["sort"]?.takeIf { it == "popular" } ?: "latest",
            page = params["page"]?.toIntOrNull() ?: 1,
            pageSize = params["pageSize"]?.toIntOrNull()
                ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE) ?: Constants.API_DEFAULT_PAGE_SIZE,
        )
        val paged = application.communityRepository.list(filter)
        call.respondText(postsJson(paged), ContentType.Application.Json)
    }
    route.get("/api/posts/{id}") {
        val id = call.pathId()?.toLongOrNull()
        val item = id?.let { application.communityRepository.detail(it) }
        if (item == null) {
            return@get call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        }
        call.respondText(postDetailJson(item), ContentType.Application.Json)
    }
    // 랭킹: 최근 24h 좋아요순 상위 20 (V2 GET /api/ranking)
    route.get("/api/ranking") {
        val since = System.currentTimeMillis() - 86_400_000L
        val top = application.database.postDao().topByLikesSince(since, 20)
        val sources = application.database.crawlSourceDao().getAll().associateBy { it.id }
        val boards = application.database.siteBoardDao().getAll().associateBy { it.id }
        call.respondText(
            buildJsonObject {
                put("ranking", buildJsonArray {
                    top.forEach { p ->
                        add(postElement(
                            com.borasarang.communityjupjup.data.repository.PostListItem(
                                post = p,
                                sourceName = sources[p.sourceId]?.name,
                                boardName = boards[p.boardId]?.boardName,
                                categoryName = CommunityCategories.nameOf(p.categoryId),
                            )
                        ))
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    // 단일 게시글 상세 다시 가져오기 (어드민·수동 새로고침용)
    route.post("/api/posts/{id}/refresh") {
        val id = call.pathId()?.toLongOrNull()
        val item = id?.let { application.communityRepository.detail(it) }
        if (item == null) {
            return@post call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        }
        val source = item.source
        if (source == null) {
            return@post call.respondNotFound("unknown source")
        }
        return@post try {
            val crawler = com.borasarang.communityjupjup.crawler.BoardCrawler(
                source,
                application.database,
            )
            val config = com.borasarang.communityjupjup.crawler.SelectorConfig.parse(source.selectorConfigJson)
            val detail = crawler.parseDetail(
                fetchBody(item.post.originalUrl) ?: return@post call.respondText(
                    """{"ok":false,"error":"fetch failed"}""",
                    ContentType.Application.Json,
                ),
                item.post.originalUrl,
                config,
            )
            application.database.postDao().updateDetailByCanonical(
                item.post.canonicalUrl,
                detail.summary?.takeIf { it.isNotBlank() } ?: "",
                detail.thumbnailUrl ?: "",
                com.borasarang.communityjupjup.crawler.encodeImageUrls(detail.imageUrls)
                    .takeIf { detail.imageUrls.isNotEmpty() },
            )
            // 썸네일만 있어도 성공 (짤·인증 글)
            val ok = !detail.summary.isNullOrBlank() || !detail.thumbnailUrl.isNullOrBlank()
            call.respondText(
                buildJsonObject {
                    put("ok", ok)
                    putIfNotNull("summary", detail.summary?.take(200))
                    putIfNotNull("thumbnailUrl", detail.thumbnailUrl)
                    put("imageCount", detail.imageCount)
                    put("images", buildJsonArray { detail.imageUrls.forEach { add(JsonPrimitive(it)) } })
                }.toString(),
                ContentType.Application.Json,
            )
        } catch (e: Exception) {
            com.borasarang.communityjupjup.util.DebugLogger.e("서버", "E-AND-SRV-0103", "상세 새로고침 실패: ${e.message}", e)
            call.respondText(
                """{"ok":false,"error":"${com.borasarang.common.server.escapeJson(e.message ?: "error")}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }
}

private suspend fun fetchBody(url: String): String? {
    return try {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.borasarang.communityjupjup.crawler.CrawlHttp.get(url)
        }
        if (!result.isOk) null else result.body
    } catch (_: Exception) {
        null
    }
}
