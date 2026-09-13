package com.borasarang.planjupjup.server

import com.borasarang.common.server.escapeJson
import com.borasarang.common.server.pathId
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.common.server.respondNotFound
import com.borasarang.planjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.jsonPrimitive

/**
 * 수집 제어 라우트 (R4). 소스 토글·즉시수집.
 * 동작 동결: HttpServerService에서 이동만 (404 정렬·sync 검증은 4b).
 */
internal fun HttpServerService.planCollectRoutes(route: Route) {
    val application = app()
    route.post("/api/sources/{id}/toggle") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondError("id required")
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
}
