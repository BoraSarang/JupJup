package com.borasarang.planjupjup.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * 정적 포털 에셋 라우트 (R4). assets/plan_web 수동 서빙 — assets는 classpath가 아님.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.planAssetRoutes(route: Route) {
    route.get("/") {
        serveAsset(call, "plan_web/index.html", ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }
    route.get("/style.css") {
        serveAsset(call, "plan_web/style.css", ContentType.Text.CSS.withCharset(Charsets.UTF_8))
    }
    route.get("/app.js") {
        serveAsset(call, "plan_web/app.js", ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
    }
    route.get("/favicon.svg") {
        serveAsset(call, "plan_web/favicon.svg", ContentType.Image.SVG)
    }
}

private suspend fun HttpServerService.serveAsset(
    call: io.ktor.server.application.ApplicationCall,
    assetPath: String,
    contentType: ContentType,
) {
    try {
        val bytes = applicationContext.assets.open(assetPath).use { it.readBytes() }
        call.respondBytes(bytes, contentType)
    } catch (_: Exception) {
        call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
    }
}
