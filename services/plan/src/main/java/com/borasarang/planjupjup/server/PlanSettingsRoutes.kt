package com.borasarang.planjupjup.server

import com.borasarang.common.server.escapeJson
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * 설정 라우트 (R4). 조회·저장(포트 변경 시 재시작).
 * 동작 동결: HttpServerService에서 이동만 (재시작 백그라운드화는 4b).
 */
internal fun HttpServerService.planSettingsRoutes(route: Route) {
    val application = app()
    route.get("/api/settings") {
        val s = application.preferences.getSettings()
        call.respondText(settingsJson(s), ContentType.Application.Json)
    }
    route.post("/api/settings") {
        val current = application.preferences.getSettings()
        try {
            val obj = call.receiveJsonObject()
            if (obj == null) {
                return@post call.respondError("E-AND-VALID-0501")
            }
            val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: current.port
            if (port !in Constants.MIN_PORT..Constants.MAX_PORT) {
                return@post call.respondError("E-AND-VALID-0502")
            }
            val retentionDays = obj["retentionDays"]?.jsonPrimitive?.content
                ?.toIntOrNull() ?: current.retentionDays
            if (retentionDays !in Constants.MIN_RETENTION_DAYS..Constants.MAX_RETENTION_DAYS) {
                return@post call.respondError("E-AND-VALID-0501")
            }
            val watchdogIntervalSec = obj["watchdogIntervalSec"]?.jsonPrimitive?.content
                ?.toIntOrNull() ?: current.watchdogIntervalSec
            if (watchdogIntervalSec !in Constants.MIN_WATCHDOG_SEC..Constants.MAX_WATCHDOG_SEC) {
                return@post call.respondError("E-AND-VALID-0501")
            }
            val next = SettingsData(
                port = port,
                retentionDays = retentionDays,
                autoStart = obj["autoStart"]?.jsonPrimitive?.content
                    ?.toBooleanStrictOrNull() ?: current.autoStart,
                watchdogIntervalSec = watchdogIntervalSec,
                notifCrawlComplete = obj["notifCrawlComplete"]?.jsonPrimitive?.content
                    ?.toBooleanStrictOrNull() ?: current.notifCrawlComplete,
                notifNewPlan = obj["notifNewPlan"]?.jsonPrimitive?.content
                    ?.toBooleanStrictOrNull() ?: current.notifNewPlan,
                notifFailure = obj["notifFailure"]?.jsonPrimitive?.content
                    ?.toBooleanStrictOrNull() ?: current.notifFailure,
            )
            application.preferences.saveSettings(next)
            DebugLogger.i("설정", "설정 저장 port=${next.port} retention=${next.retentionDays}")
            if (next.port != currentPort) {
                DebugLogger.i("설정", "포트 변경 감지 — 서버 재시작 예약")
                // 라우트 스레드 블로킹(stop 최대 3s) 방지: 백그라운드 재시작 (mac 동일)
                scope.launch { restartServer() }
            }
            call.respondText(settingsJson(next), ContentType.Application.Json)
        } catch (e: Exception) {
            call.respondText(
                """{"error":"${escapeJson(e.message ?: "bad request")}"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
        }
    }
}
