package com.borasarang.macjupjup.server.routes

import com.borasarang.macjupjup.server.HttpServerService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * 정적 포털 에셋 라우트 (R4). assets/mac_web 수동 서빙 — assets는 classpath가 아님.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.macAssetRoutes(route: Route) {
    route.get("/") {
        serveAsset(call, "mac_web/index.html", ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }
    route.get("/style.css") {
        serveAsset(call, "mac_web/style.css", ContentType.Text.CSS.withCharset(Charsets.UTF_8))
    }
    route.get("/app.js") {
        serveAsset(call, "mac_web/app.js", ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
    }
    route.get("/favicon.svg") {
        serveAsset(call, "mac_web/favicon.svg", ContentType.Image.SVG)
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
        com.borasarang.macjupjup.util.DebugLogger.w("서버", "에셋 서빙 실패 $assetPath: ${e.message}")
        call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
    }
}
