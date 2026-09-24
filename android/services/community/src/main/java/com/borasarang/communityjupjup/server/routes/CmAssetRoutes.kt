package com.borasarang.communityjupjup.server.routes

import com.borasarang.communityjupjup.server.HttpServerService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * 정적 포털 에셋 라우트 (R4). assets/community_web 수동 서빙 — assets는 classpath가 아님.
 * 동작 동결: HttpServerService에서 이동만.
 */
private data class ThumbEntry(val bytes: ByteArray, val contentType: String, val cachedAt: Long)

/** 썸네일 메모리 캐시 — 갤러리 최대 5장 동시 요청 시 CDN 반복 fetch 제거 (20항목·TTL 10분) */
private val thumbCache = java.util.concurrent.ConcurrentHashMap<String, ThumbEntry>()
private const val THUMB_CACHE_TTL_MS = 10 * 60 * 1000L
private const val THUMB_CACHE_MAX = 20

private fun evictOldestThumb() {
    val oldestKey = thumbCache.entries.minByOrNull { it.value.cachedAt }?.key ?: return
    thumbCache.remove(oldestKey)
}

internal fun HttpServerService.cmAssetRoutes(route: Route) {
    route.get("/") {
        serveAsset(call, "community_web/index.html", ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }
    route.get("/style.css") {
        serveAsset(call, "community_web/style.css", ContentType.Text.CSS.withCharset(Charsets.UTF_8))
    }
    route.get("/app.js") {
        serveAsset(call, "community_web/app.js", ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
    }
    route.get("/favicon.svg") {
        serveAsset(call, "community_web/favicon.svg", ContentType.Image.SVG)
    }
    // 썸네일 프록시: CDN 핫링크 차단(외부 Referer 403) 우회. 서버가 직접 받아 전달.
    route.get("/api/thumb") {
        val raw = call.queryParameters["url"]?.trim().orEmpty()
        if (!isAllowedThumbUrl(raw)) {
            return@get call.respondText(
                """{"error":"invalid url"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
        }
        val fetched = thumbCache[raw]?.takeIf {
            System.currentTimeMillis() - it.cachedAt < THUMB_CACHE_TTL_MS
        }?.let {
            com.borasarang.communityjupjup.crawler.HttpBytes(200, it.bytes, it.contentType, null)
        } ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.borasarang.communityjupjup.crawler.CrawlHttp.getBytes(raw)
        }.also {
            if (it.isOk && it.bytes != null) {
                if (thumbCache.size >= THUMB_CACHE_MAX) evictOldestThumb()
                thumbCache[raw] = ThumbEntry(it.bytes, it.contentType ?: "image/jpeg", System.currentTimeMillis())
            }
        }
        if (!fetched.isOk || fetched.bytes == null) {
            return@get call.respondText(
                """{"error":"fetch failed"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadGateway,
            )
        }
        call.response.headers.append(io.ktor.http.HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondBytes(
            fetched.bytes,
            ContentType.parse(fetched.contentType ?: "image/jpeg"),
        )
    }
}

/**
 * 썸네일 프록시 허용 판정 (순수 함수).
 * http(s)만, 로컬·메타데이터 주소 차단 (SSRF 방지).
 */
internal fun isAllowedThumbUrl(raw: String): Boolean {
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

private suspend fun HttpServerService.serveAsset(
    call: io.ktor.server.application.ApplicationCall,
    assetPath: String,
    contentType: ContentType,
) {
    try {
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            applicationContext.assets.open(assetPath).use { it.readBytes() }
        }
        call.respondBytes(bytes, contentType)
    } catch (e: Exception) {
        com.borasarang.communityjupjup.util.DebugLogger.w("서버", "에셋 서빙 실패 $assetPath: ${e.message}")
        call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
    }
}
