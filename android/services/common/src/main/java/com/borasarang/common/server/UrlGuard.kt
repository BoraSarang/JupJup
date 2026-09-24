package com.borasarang.common.server

/**
 * 썸네일/외부 URL 허용 판정 (순수 함수, SSRF 방지).
 * http(s)만, 로컬·메타데이터 주소 차단.
 */
fun isAllowedThumbUrl(raw: String): Boolean {
    if (raw.isBlank() || raw.length > 2000) return false
    return try {
        val uri = java.net.URI(raw)
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") return false
        if (host.startsWith("169.254.") || host.startsWith("192.168.") || host.startsWith("10.")) return false
        if (host == "metadata.google.internal") return false
        true
    } catch (_: Exception) {
        false
    }
}
