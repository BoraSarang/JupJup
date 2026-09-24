package com.borasarang.communityjupjup.server.routes

import com.borasarang.common.server.escapeJson
import com.borasarang.common.server.pathId
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondNotFound
import com.borasarang.communityjupjup.crawler.CrawlerFactory
import com.borasarang.communityjupjup.server.HttpServerService
import com.borasarang.communityjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 수집 제어 라우트: 소스 토글·즉시수집·어드민 크롤 테스트.
 */
internal fun HttpServerService.cmCollectRoutes(route: Route) {
    val application = app()
    route.post("/api/sources/{id}/toggle") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@post
        }
        val next = application.sourceRepository.toggle(id)
        if (next == null) {
            call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        } else {
            // 스케줄 동기화: on이면 소속 보드 재예약, off면 취소
            if (next) {
                application.crawlScheduler.scheduleSource(id)
            } else {
                application.crawlScheduler.cancelSource(id)
            }
            call.respondText(
                """{"id":"${escapeJson(id)}","enabled":$next}""",
                ContentType.Application.Json,
            )
        }
    }
    route.post("/api/sync") {
        val sourceId = call.receiveJsonObject()
            ?.get("sourceId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        DebugLogger.i("수동수집", "즉시 수집 요청 sourceId=$sourceId")
        if (!sourceId.isNullOrBlank() &&
            application.sourceRepository.getById(sourceId) == null
        ) {
            return@post call.respondNotFound("unknown sourceId")
        }
        application.crawlScheduler.triggerImmediate(sourceId)
        call.respondText(
            """{"accepted":true}""",
            ContentType.Application.Json,
            HttpStatusCode.Accepted,
        )
    }
    // 어드민: 셀렉터 테스트 — 저장 없이 파싱 미리보기 최대 5건 (V2 POST /admin/crawl/test)
    route.post("/api/crawl/test") {
        val obj = call.receiveJsonObject()
        val sourceId = obj?.get("sourceId")?.jsonPrimitive?.content
        val source = sourceId?.let { application.sourceRepository.getById(it) }
        if (source == null) {
            return@post call.respondNotFound("unknown sourceId")
        }
        return@post try {
            val crawler = CrawlerFactory(application.database).create(source)
            val outcome = crawler.crawl()
            outcome.fold(
                onSuccess = { drafts ->
                    call.respondText(
                        buildJsonObject {
                            put("ok", true)
                            put("found", drafts.size)
                            put("preview", buildJsonArray {
                                drafts.take(5).forEach { d ->
                                    add(buildJsonObject {
                                        put("title", d.title)
                                        put("author", d.authorName ?: "")
                                        put("url", d.originalUrl)
                                    })
                                }
                            })
                        }.toString(),
                        ContentType.Application.Json,
                    )
                },
                onFailure = { e ->
                    call.respondText(
                        buildJsonObject {
                            put("ok", false)
                            put("error", e.message ?: "crawl failed")
                        }.toString(),
                        ContentType.Application.Json,
                    )
                },
            )
        } catch (e: Exception) {
            call.respondText(
                buildJsonObject {
                    put("ok", false)
                    put("error", e.message ?: "crawl failed")
                }.toString(),
                ContentType.Application.Json,
            )
        }
    }
    // 어드민: 수집 로그 (V2 GET /admin/logs)
    route.get("/api/logs") {
        val limit = call.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
        val logs = application.sourceRepository.recentLogs(limit)
        call.respondText(
            buildJsonObject {
                put("logs", buildJsonArray {
                    logs.forEach { l ->
                        add(buildJsonObject {
                            put("id", l.id)
                            put("sourceName", l.sourceName)
                            put("startedAt", l.startedAt)
                            l.finishedAt?.let { put("finishedAt", it) }
                            put("status", l.status)
                            put("found", l.postsFound)
                            put("newCount", l.postsNew)
                            l.errorMessage?.let { put("error", it) }
                        })
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
}
