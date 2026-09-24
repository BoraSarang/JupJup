package com.borasarang.common.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

/**
 * Ktor JSON API 공용 헬퍼 (services/mac 단일진실 4종 + escapeJson 무손실 정책, R2).
 * - mac: 기존 private들을 이 import로 교체 (동작 무변경)
 * - plan: 동일 동작 지점만 치환. stats envelope·take(300) 절단·toggle/sync 의미는 4단계에서
 */

/** JSON 문자열 escape (무손실: 따옴표·역슬래시·제어문자 처리) */
fun escapeJson(s: String): String {
    val sb = StringBuilder(s.length)
    s.forEach { c ->
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
    }
    return sb.toString()
}

/** 요청 바디 JSON 파싱 단일 진실. 실패 시 null */
suspend fun ApplicationCall.receiveJsonObject(): JsonObject? {
    return try {
        Json.parseToJsonElement(receiveText()) as? JsonObject
    } catch (_: Exception) {
        null
    }
}

/** 에러 envelope 단일 진실. 성공 응답은 respondText 직접 사용 */
suspend fun ApplicationCall.respondError(
    msg: String,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    respondText(
        """{"error":"${escapeJson(msg)}"}""",
        ContentType.Application.Json,
        status,
    )
}

suspend fun ApplicationCall.respondNotFound(
    msg: String = "Not found",
) = respondError(msg, HttpStatusCode.NotFound)

/** nullable put 단일 진실 */
fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    value?.let { put(key, it) }
}

fun JsonObjectBuilder.putIfNotNull(key: String, value: Long?) {
    value?.let { put(key, it) }
}

fun JsonObjectBuilder.putIfNotNull(key: String, value: Double?) {
    value?.let { put(key, it) }
}

fun JsonObjectBuilder.putIfNotNull(key: String, value: Int?) {
    value?.let { put(key, it) }
}

/** 경로 파라미터 id 단일 진실. 비어 있으면 null */
fun ApplicationCall.pathId(): String? =
    parameters["id"]?.takeIf { it.isNotBlank() }

fun ApplicationCall.pathIdLong(): Long? =
    parameters["id"]?.toLongOrNull()
