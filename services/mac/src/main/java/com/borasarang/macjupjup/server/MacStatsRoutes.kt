package com.borasarang.macjupjup.server

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 통계 라우트 (R4). KPI·트렌드·일별수집·인사이트.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.macStatsRoutes(route: Route) {
    val application = app()
    route.get("/api/stats") {
        val stats = application.appRepository.overview()
        call.respondText(
            buildJsonObject {
                put("totalApps", stats.totalApps)
                put("activeSources", stats.activeSources)
                stats.lastCollectedAt?.let { put("lastCollectedAt", it) }
            }.toString(),
            ContentType.Application.Json,
        )
    }
    route.get("/api/stats/trends") {
        val t = application.appRepository.trends()
        call.respondText(
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("byCategory", buildJsonObject {
                    t.byCategory.forEach { (k, v) -> put(k, v) }
                })
                put("byLicense", buildJsonObject {
                    t.byLicense.forEach { (k, v) -> put(k, v) }
                })
                put("newLast7d", t.newLast7d)
                put("updatedLast7d", t.updatedLast7d)
                put("versionBumpsLast7d", t.versionBumpsLast7d)
                put("aiTagCount", t.aiTagCount)
                put("menuBarTagCount", t.menuBarTagCount)
            }.toString(),
            ContentType.Application.Json,
        )
    }
    /** 일별 수집량 (그래프용). ?days=7/14/30, 기본 14 */
    route.get("/api/stats/collect") {
        val days = call.queryParameters["days"]?.toIntOrNull()?.coerceIn(1, 30) ?: 14
        val list = application.appRepository.collect(days)
        call.respondText(
            buildJsonObject {
                put("days", buildJsonArray {
                    list.forEach { d ->
                        add(
                            buildJsonObject {
                                put("day", d.day)
                                put("found", d.found)
                                put("new", d.newCount)
                                put("updated", d.updated)
                                put("runs", d.runs)
                                put("bySource", buildJsonArray {
                                    d.bySource.forEach { s ->
                                        add(
                                            buildJsonObject {
                                                put("sourceId", s.sourceId)
                                                put("sourceName", s.sourceName)
                                                put("found", s.found)
                                                put("new", s.newCount)
                                                put("updated", s.updated)
                                            },
                                        )
                                    }
                                })
                            },
                        )
                    }
                })
                put("total", buildJsonObject {
                    put("found", list.sumOf { it.found })
                    put("new", list.sumOf { it.newCount })
                    put("updated", list.sumOf { it.updated })
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    /** 트렌드 인사이트 카드 목록 */
    route.get("/api/stats/insights") {
        val insights = com.borasarang.macjupjup.data.repository.buildInsights(
            application.appRepository.insightsInput(),
        )
        call.respondText(
            buildJsonObject {
                put("insights", buildJsonArray {
                    insights.forEach { i ->
                        add(
                            buildJsonObject {
                                put("icon", i.icon)
                                put("title", i.title)
                                put("body", i.body)
                            },
                        )
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
}
