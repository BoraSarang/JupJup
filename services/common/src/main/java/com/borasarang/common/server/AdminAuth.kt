package com.borasarang.common.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText

/**
 * 관리웹 쓰기 인증 (R44, PLAN_v19).
 * GET은 읽기 전용 개방, /api 쓰기(POST/PUT/DELETE)는 X-Auth-Token 강제.
 * 토큰은 서비스별 DataStore(admin_token) 최초 1회 발급. member 호출만 사용
 * (크로스모듈 확장함수 FQN 미해결 회피 — R43 LanGuard 교훈).
 */
object AdminAuth {
    const val HEADER = "X-Auth-Token"

    fun extractToken(call: ApplicationCall): String? {
        call.request.header(HEADER)?.takeIf { it.isNotBlank() }?.let { return it }
        val auth = call.request.header(HttpHeaders.Authorization) ?: return null
        if (!auth.startsWith("Bearer ")) return null
        return auth.removePrefix("Bearer ").takeIf { it.isNotBlank() }
    }

    /** 루프백 판정 (초기 페어링용 /api/admin/token 전용) */
    fun isLoopback(host: String?): Boolean {
        val h = host?.trim()?.lowercase() ?: return false
        return h == "127.0.0.1" || h == "localhost" || h == "::1" || h == "[::1]"
    }

    /** 서버 블록에서 호출 — lanOnly() 다음 줄에 배치 */
    fun install(app: Application, tokenProvider: suspend () -> String?) {
        app.intercept(ApplicationCallPipeline.Setup) {
            val method = call.request.httpMethod
            if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) return@intercept
            if (!call.request.path().startsWith("/api/")) return@intercept
            val expected = try {
                tokenProvider()
            } catch (_: Exception) {
                null
            }
            val given = extractToken(call)
            if (expected.isNullOrBlank() || given != expected) {
                call.respondText(
                    """{"error":"unauthorized"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
                finish()
            }
        }
    }
}
