package com.borasarang.macjupjup.crawler

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** JsonObject 안전 추출 헬퍼 (키 없음·JsonNull → null). T-132: 앞뒤 공백 제거 (버전 "4.3.4 " 오판 방지) */
internal fun JsonObject.str(key: String): String? {
    val el = get(key) ?: return null
    if (el is JsonNull) return null
    return try {
        el.jsonPrimitive.content.trim().ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

internal fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()
