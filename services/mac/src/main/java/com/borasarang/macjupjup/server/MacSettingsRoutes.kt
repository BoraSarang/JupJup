package com.borasarang.macjupjup.server

import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.macjupjup.data.repository.SettingsData
import com.borasarang.macjupjup.data.repository.toView
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.maskToken
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * 설정 라우트 (R4). 조회·저장(포트 변경 시 백그라운드 재시작).
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.macSettingsRoutes(route: Route) {
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
        val tokenRaw = obj["githubToken"]?.jsonPrimitive?.content
        val next = SettingsData(
            port = port,
            retentionDays = retentionDays,
            autoStart = obj["autoStart"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.autoStart,
            watchdogIntervalSec = watchdogIntervalSec,
            // 토큰 키가 없으면 기존 유지, 빈 문자열이면 삭제
            githubToken = if (obj.containsKey("githubToken")) (tokenRaw ?: "") else current.githubToken,
            translateKo = obj["translateKo"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.translateKo,
            notifCrawlComplete = obj["notifCrawlComplete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifCrawlComplete,
            notifNewApp = obj["notifNewApp"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifNewApp,
            notifFailure = obj["notifFailure"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: current.notifFailure,
        )
        application.preferences.saveSettings(next)
        if (tokenRaw != null) {
            DebugLogger.i("설정", "GitHub 토큰 저장됨 (${maskToken(tokenRaw)})")
        }
        if (next.port != currentPort) {
            DebugLogger.i("설정", "포트 변경 감지 — 서버 재시작 예약")
            // 라우트 스레드 블로킹(stop 최대 3s) 방지: 백그라운드 재시작
            scope.launch { restartServer() }
        }
        call.respondText(settingsJson(next.toView()), ContentType.Application.Json)
    }
}
