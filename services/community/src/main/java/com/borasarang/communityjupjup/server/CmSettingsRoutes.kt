package com.borasarang.communityjupjup.server

import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.communityjupjup.data.repository.SettingsData
import com.borasarang.communityjupjup.data.repository.toView
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * 설정 라우트. 조회·저장(포트 변경 시 백그라운드 재시작).
 */
internal fun HttpServerService.cmSettingsRoutes(route: Route) {
    val application = app()
    route.get("/api/settings") {
        val s = application.preferences.getSettings()
        call.respondText(settingsJson(s.toView()), ContentType.Application.Json)
    }
    route.post("/api/settings") {
        val obj = call.receiveJsonObject()
        if (obj == null) {
            return@post call.respondError("E-AND-VALID-0501")
        }
        val current = application.preferences.getSettings()
        val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: current.port
        if (port !in Constants.MIN_PORT..Constants.MAX_PORT) {
            return@post call.respondError("E-AND-VALID-0502")
        }
        val retentionDays = obj["retentionDays"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: current.retentionDays
        if (retentionDays !in Constants.MIN_RETENTION_DAYS..Constants.MAX_RETENTION_DAYS) {
            return@post call.respondError("E-AND-VALID-0501")
        }
        val watchdogIntervalSec = obj["watchdogIntervalSec"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: current.watchdogIntervalSec
        if (watchdogIntervalSec !in Constants.MIN_WATCHDOG_SEC..Constants.MAX_WATCHDOG_SEC) {
            return@post call.respondError("E-AND-VALID-0501")
        }
        val next = SettingsData(
            port = port,
            retentionDays = retentionDays,
            autoStart = obj["autoStart"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.autoStart,
            watchdogIntervalSec = watchdogIntervalSec,
            notifCrawlComplete = obj["notifCrawlComplete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifCrawlComplete,
            notifNewPost = obj["notifNewPost"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifNewPost,
            notifFailure = obj["notifFailure"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifFailure,
        )
        application.preferences.saveSettings(next)
        if (next.port != currentPort) {
            DebugLogger.i("설정", "포트 변경 감지 — 서버 재시작 예약")
            // 라우트 스레드 블로킹(stop 최대 3s) 방지: 백그라운드 재시작
            scope.launch { restartServer() }
        }
        call.respondText(settingsJson(next.toView()), ContentType.Application.Json)
    }
}
