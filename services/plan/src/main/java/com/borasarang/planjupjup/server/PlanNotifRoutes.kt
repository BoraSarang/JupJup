package com.borasarang.planjupjup.server

import com.borasarang.common.server.pathIdLong
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.common.server.respondNotFound
import com.borasarang.planjupjup.util.Constants
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * 알림 라우트 (R4). 목록·상세·읽음·삭제·정리·미읽음수.
 * 동작 동결: HttpServerService에서 이동만 (detail 폴백은 4b).
 */
internal fun HttpServerService.planNotifRoutes(route: Route) {
    val application = app()
    route.get("/api/notifications") {
        val params = call.queryParameters
        val page = params["page"]?.toIntOrNull() ?: 1
        val pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val type = params["type"]?.takeIf { it.isNotBlank() }
        val isRead = params["isRead"]?.let {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        val list = application.notificationRepository.getPaged(type, isRead, page, pageSize)
        val total = application.notificationRepository.count(type, isRead)
        val unreadCount = application.notificationRepository.countUnread()
        call.respondText(
            buildJsonObject {
                put("notifications", buildJsonArray { list.forEach { add(Json.parseToJsonElement(it.toJson())) } })
                put("total", total)
                put("page", page)
                put("pageSize", pageSize)
                put("unreadCount", unreadCount)
            }.toString(),
            ContentType.Application.Json,
        )
    }
    route.get("/api/notifications/{id}") {
        val id = call.pathIdLong()
        if (id == null) {
            call.respondError("id required")
            return@get
        }
        val log = application.notificationRepository.getById(id)
        if (log == null) {
            call.respondNotFound()
            return@get
        }
        call.respondText(
            buildJsonObject {
                put("notification", Json.parseToJsonElement(log.toJson()))
                put("detail", try {
                    Json.parseToJsonElement(log.detailJson)
                } catch (_: Exception) {
                    buildJsonObject { }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    route.post("/api/notifications/{id}/read") {
        val id = call.pathIdLong()
        if (id == null) {
            call.respondError("id required")
            return@post
        }
        val updated = application.notificationRepository.markAsRead(id)
        call.respondText(
            buildJsonObject { put("updated", updated) }.toString(),
            ContentType.Application.Json,
        )
    }
    route.post("/api/notifications/read-all") {
        val updated = application.notificationRepository.markAllAsRead()
        call.respondText(
            buildJsonObject { put("updated", updated) }.toString(),
            ContentType.Application.Json,
        )
    }
    route.delete("/api/notifications/{id}") {
        val id = call.pathIdLong()
        if (id == null) {
            call.respondError("id required")
            return@delete
        }
        val deleted = application.notificationRepository.delete(id)
        call.respondText(
            buildJsonObject { put("deleted", deleted) }.toString(),
            ContentType.Application.Json,
        )
    }
    route.post("/api/notifications/cleanup") {
        val days = call.receiveJsonObject()
            ?.get("days")?.jsonPrimitive?.content?.toIntOrNull()
            ?: currentRetentionDays()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val deleted = application.notificationRepository.deleteOlderThan(cutoff)
        call.respondText(
            buildJsonObject { put("deleted", deleted) }.toString(),
            ContentType.Application.Json,
        )
    }
    route.get("/api/notifications/unread-count") {
        val count = application.notificationRepository.countUnread()
        call.respondText(
            buildJsonObject { put("unreadCount", count) }.toString(),
            ContentType.Application.Json,
        )
    }
}

private suspend fun HttpServerService.currentRetentionDays(): Int {
    return try { app().preferences.getSettings().retentionDays } catch (_: Exception) { Constants.DEFAULT_RETENTION_DAYS }
}
