package com.borasarang.macjupjup.server

import com.borasarang.common.server.pathId
import com.borasarang.macjupjup.data.repository.CommunityFilter
import com.borasarang.macjupjup.util.Constants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 커뮤니티 라우트 (PLAN_v23).
 * 목록(main/q/페이지) · 상세(contentHtml 포함) · 카테고리 건수.
 */
internal fun HttpServerService.macCommunityRoutes(route: Route) {
    val application = app()

    route.get("/api/community") {
        val params = call.queryParameters
        val main = params["main"]?.takeIf { it.isNotBlank() && it != "all" }
        val result = application.communityRepository.list(
            CommunityFilter(
                main = main,
                sourceId = params["sourceId"]?.takeIf { it.isNotBlank() },
                q = params["q"]?.takeIf { it.isNotBlank() },
                page = params["page"]?.toIntOrNull() ?: 1,
                pageSize = params["pageSize"]?.toIntOrNull()
                    ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                    ?: Constants.API_DEFAULT_PAGE_SIZE,
            ),
        )
        call.respondText(
            communityListJson(result.posts, result.total, result.page, result.pageSize),
            ContentType.Application.Json,
        )
    }

    route.get("/api/community/{id}") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@get
        }
        val found = application.communityRepository.detail(id)
        if (found == null) {
            call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        } else {
            call.respondText(
                buildJsonObject {
                    communityElement(found).entries.forEach { (k, v) -> put(k, v) }
                    found.contentHtml?.let { put("contentHtml", it) }
                }.toString(),
                ContentType.Application.Json,
            )
        }
    }

    route.get("/api/community/counts") {
        val counts = application.communityRepository.countByMain()
        call.respondText(
            buildJsonObject {
                put("apple", counts[Constants.MAIN_APPLE] ?: 0)
                put("mac", counts[Constants.MAIN_MAC_COMMUNITY] ?: 0)
                put("ai", counts[Constants.MAIN_AI_COMMUNITY] ?: 0)
                put("all", application.communityRepository.countAll())
            }.toString(),
            ContentType.Application.Json,
        )
    }
}

internal fun communityElement(p: com.borasarang.macjupjup.data.db.entity.CommunityPost) =
    buildJsonObject {
        put("id", p.id)
        put("sourceId", p.sourceId)
        put("sourceName", p.sourceName)
        put("main", p.main)
        put("title", p.title)
        p.summary?.let { put("summary", it) }
        p.authorName?.let { put("authorName", it) }
        put("originalUrl", p.originalUrl)
        p.thumbnailUrl?.let { put("thumbnailUrl", it) }
        p.commentCount?.let { put("commentCount", it) }
        p.viewCount?.let { put("viewCount", it) }
        put("publishedAt", p.publishedAt)
        put("collectedAt", p.collectedAt)
        put("hasContent", p.contentHtml != null)
    }

internal fun communityListJson(
    posts: List<com.borasarang.macjupjup.data.db.entity.CommunityPost>,
    total: Int,
    page: Int,
    pageSize: Int,
): String {
    return buildJsonObject {
        put("posts", buildJsonArray {
            posts.forEach { add(communityElement(it)) }
        })
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }.toString()
}
