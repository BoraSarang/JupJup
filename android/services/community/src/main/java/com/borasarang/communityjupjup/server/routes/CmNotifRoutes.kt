package com.borasarang.communityjupjup.server.routes

import com.borasarang.common.server.pathIdLong
import com.borasarang.communityjupjup.server.HttpServerService
import com.borasarang.communityjupjup.util.Constants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 알림 라우트 (R4). 목록·상세·읽음·삭제·정리·미읽음수.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.cmNotifRoutes(route: Route) {
    val application = app()
    route.get("/api/notifications") {
        val params = call.queryParameters
        val type = params["type"]?.takeIf { it.isNotBlank() }
        val isRead = params["isRead"]?.toBooleanStrictOrNull()
        val page = params["page"]?.toIntOrNull() ?: 1
        val pageSize = params["pageSize"]?.toIntOrNull()
            ?.coerceIn(1, 100) ?: 20
        val repo = application.notificationRepository
        val items = repo.getPaged(type, isRead, page, pageSize)
        val total = repo.count(type, isRead)
        val unread = repo.countUnread()
        call.respondText(
            buildJsonObject {
                put("notifications", buildJsonArray {
                    items.forEach { n ->
                        add(
                            buildJsonObject {
                                put("id", n.id)
                                put("type", n.type)
                                put("summary", n.summary)
                                put("createdAt", n.createdAt)
                                put("isRead", n.isRead)
                            },
                        )
                    }
                })
                put("total", total)
                put("page", page)
                put("pageSize", pageSize)
                put("unreadCount", unread)
            }.toString(),
            ContentType.Application.Json,
        )
    }
    route.get("/api/notifications/{id}") {
        val id = call.pathIdLong()
        val found = id?.let { application.notificationRepository.getById(it) }
        if (found == null) {
            call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        } else {
            val detailEl = try {
                Json.parseToJsonElement(found.detailJson)
            } catch (_: Exception) {
                buildJsonObject { }
            }
            call.respondText(
                buildJsonObject {
                    put("notification", buildJsonObject {
                        put("id", found.id)
                        put("type", found.type)
                        put("summary", found.summary)
                        put("createdAt", found.createdAt)
                        put("isRead", found.isRead)
                    })
                    put("detail", detailEl)
                }.toString(),
                ContentType.Application.Json,
            )
        }
    }
    route.post("/api/notifications/{id}/read") {
        val id = call.pathIdLong()
        if (id == null) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
        } else {
            val updated = application.notificationRepository.markAsRead(id)
            call.respondText(
                """{"updated":$updated}""",
                ContentType.Application.Json,
            )
        }
    }
    route.post("/api/notifications/read-all") {
        val updated = application.notificationRepository.markAllAsRead()
        call.respondText(
            """{"updated":$updated}""",
            ContentType.Application.Json,
        )
    }
    route.delete("/api/notifications/{id}") {
        val id = call.pathIdLong()
        if (id == null) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
        } else {
            val deleted = application.notificationRepository.delete(id)
            call.respondText(
                """{"deleted":$deleted}""",
                ContentType.Application.Json,
            )
        }
    }
    route.post("/api/notifications/cleanup") {
        val days = try {
            application.preferences.getSettings().retentionDays
        } catch (_: Exception) {
            Constants.DEFAULT_RETENTION_DAYS
        }
        val before = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val deleted = application.notificationRepository.deleteOlderThan(before)
        call.respondText(
            """{"deleted":$deleted}""",
            ContentType.Application.Json,
        )
    }
    route.get("/api/notifications/unread-count") {
        val unread = application.notificationRepository.countUnread()
        call.respondText(
            """{"unreadCount":$unread}""",
            ContentType.Application.Json,
        )
    }
}
