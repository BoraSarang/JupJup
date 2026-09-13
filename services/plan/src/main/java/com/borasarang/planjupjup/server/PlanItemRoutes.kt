package com.borasarang.planjupjup.server

import com.borasarang.common.server.pathId
import com.borasarang.common.server.putIfNotNull
import com.borasarang.common.server.respondError
import com.borasarang.common.server.respondNotFound
import com.borasarang.planjupjup.data.repository.PlanFilter
import com.borasarang.planjupjup.util.Constants
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 요금제 아이템 라우트 (R4). health·목록·상세·소스 목록.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.planItemRoutes(route: Route) {
    val application = app()
    route.get("/api/health") {
        call.respondText(
            """{"status":"ok","timestamp":${System.currentTimeMillis()}}""",
            ContentType.Application.Json,
        )
    }
    route.get("/api/plans") {
        val params = call.queryParameters
        val filter = PlanFilter(
            network = params["network"]?.takeIf { it.isNotBlank() },
            carrier = params["carrier"]?.takeIf { it.isNotBlank() },
            minDataGb = params["minData"]?.toIntOrNull(),
            maxPrice = params["maxPrice"]?.toIntOrNull(),
            tag = params["tag"]?.takeIf { it.isNotBlank() },
            sort = params["sort"] ?: "price_asc",
            page = params["page"]?.toIntOrNull() ?: 1,
            pageSize = params["pageSize"]?.toIntOrNull()
                ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                ?: Constants.API_DEFAULT_PAGE_SIZE,
        )
        val result = application.planRepository.getPlans(filter)
        call.respondText(plansJson(result.plans, result.total, result.page, result.pageSize),
            ContentType.Application.Json)
    }
    route.get("/api/plans/{id}") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondError("id required")
            return@get
        }
        val found = application.planRepository.getPlanWithSources(id)
        if (found == null) {
            call.respondNotFound()
        } else {
            call.respondText(planJson(found), ContentType.Application.Json)
        }
    }
    route.get("/api/sources") {
        val sources = application.sourceRepository.getAll()
        val arr = buildJsonArray {
            sources.forEach { s ->
                add(
                    buildJsonObject {
                        put("id", s.id)
                        put("name", s.name)
                        put("type", s.type)
                        put("baseUrl", s.baseUrl)
                        put("enabled", s.enabled)
                        put("intervalHours", s.intervalHours)
                        put("intervalMinutes", s.intervalMinutes)
                        putIfNotNull("lastRunAt", s.lastRunAt)
                        put("lastStatus", s.lastStatus)
                        putIfNotNull("errorMessage", s.errorMessage)
                    },
                )
            }
        }
        call.respondText(arr.toString(), ContentType.Application.Json)
    }
}
