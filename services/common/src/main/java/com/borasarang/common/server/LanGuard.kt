package com.borasarang.common.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText

/**
 * 내부망 전용 가드 (R43, PLAN_v19).
 * Ktor `0.0.0.0` 바인드는 유지(LAN 접속 필요)하되, 사설대역 외 요청은 403.
 * 순수 JVM 판정 + Ktor intercept 1줄 배선.
 */
object LanGuard {
    /** 사설·로컬 출발지 판정 (IPv4 + IPv6 루프백) */
    fun isPrivateHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase() ?: return false
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "[::1]") return true
        if (h.startsWith("10.")) return true
        if (h.startsWith("192.168.")) return true
        if (h.startsWith("169.254.")) return true
        if (h.startsWith("172.")) {
            val second = h.removePrefix("172.").substringBefore(".").toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        // Ktor가 emitting하는 bracket IPv6 사설 (fc00::/7, fe80::/10 링크로컬)
        if (h.startsWith("[fc") || h.startsWith("[fd") || h.startsWith("[fe80")) return true
        if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")) return true
        return false
    }

    /** 서버 블록에서 호출 — 사설대역 외 전부 403 (내부망 전용) */
    fun install(app: Application) {
        app.intercept(ApplicationCallPipeline.Setup) {
            if (!isPrivateHost(call.request.origin.remoteHost)) {
                call.respondText(
                    """{"error":"forbidden: LAN only"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Forbidden,
                )
                finish()
            }
        }
    }
}
