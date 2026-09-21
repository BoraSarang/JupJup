package com.borasarang.communityjupjup.server

import com.borasarang.communityjupjup.util.CommunityCategories
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * 통계 라우트: overview·collect·trends·insights.
 */
internal fun HttpServerService.cmStatsRoutes(route: Route) {
    val application = app()
    route.get("/api/stats") {
        val stats = application.communityRepository.stats()
        call.respondText(
            buildJsonObject {
                put("totalPosts", stats.totalPosts)
                put("activeSources", stats.activeSources)
                stats.lastCollectedAt?.let { put("lastCollectedAt", it) }
            }.toString(),
            ContentType.Application.Json,
        )
    }
    // 카테고리·소스별 게시글 수 (V2 GET /admin/stats 대응)
    route.get("/api/stats/overview") {
        val byCategory = application.database.postDao().countByCategory()
        val bySource = application.database.postDao().countBySource()
        val sources = application.database.crawlSourceDao().getAll().associateBy { it.id }
        call.respondText(
            buildJsonObject {
                put("byCategory", buildJsonArray {
                    byCategory.forEach { c ->
                        add(buildJsonObject {
                            put("categoryId", c.categoryId)
                            put("categoryName", CommunityCategories.nameOf(c.categoryId))
                            put("count", c.cnt)
                        })
                    }
                })
                put("bySource", buildJsonArray {
                    bySource.forEach { s ->
                        add(buildJsonObject {
                            put("sourceId", s.sourceId)
                            put("sourceName", sources[s.sourceId]?.name ?: s.sourceId)
                            put("count", s.cnt)
                        })
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    // 일별 수집량 (days, 기본 14)
    route.get("/api/stats/collect") {
        val days = call.queryParameters["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 14
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val rows = application.database.crawlLogDao().collectByDay(since)
        call.respondText(
            buildJsonObject {
                put("trends", buildJsonArray {
                    rows.groupBy { it.day }.toSortedMap().forEach { (day, dayRows) ->
                        add(buildJsonObject {
                            put("day", day)
                            put("found", dayRows.sumOf { it.found })
                            put("newCount", dayRows.sumOf { it.newCount })
                            put("runs", dayRows.sumOf { it.runs })
                            put("failed", dayRows.sumOf { it.failed })
                            put("bySource", buildJsonArray {
                                dayRows.forEach { r ->
                                    add(buildJsonObject {
                                        put("sourceId", r.sourceId)
                                        put("sourceName", r.sourceName)
                                        put("found", r.found)
                                        put("newCount", r.newCount)
                                    })
                                }
                            })
                        })
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    // 수집 현황: 소스별 상태 + 3회 연속 실패 빨간 뱃지 근거 (V2 어드민 대시보드)
    route.get("/api/stats/trends") {
        val statuses = application.sourceRepository.list()
        call.respondText(
            buildJsonObject {
                put("sources", buildJsonArray {
                    statuses.forEach { s ->
                        val recent = application.sourceRepository.getRecentStatuses(s.id, 3)
                        val failing = recent.size == 3 && recent.all { it == "FAILED" }
                        add(buildJsonObject {
                            put("sourceId", s.id)
                            put("sourceName", s.name)
                            put("lastStatus", s.lastStatus)
                            put("failingStreak", failing)
                        })
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
}
