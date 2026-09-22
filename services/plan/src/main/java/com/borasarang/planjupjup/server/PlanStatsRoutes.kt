package com.borasarang.planjupjup.server

import com.borasarang.common.server.putIfNotNull
import com.borasarang.common.server.respondError
import com.borasarang.planjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 통계 라우트 (R4). KPI·overview·브랜드·망·분포·트렌드·가성비·헬스·인사이트.
 * 동작 동결: HttpServerService에서 이동만 (캐시 확대는 4b).
 */
internal fun HttpServerService.planStatsRoutes(route: Route) {
    val application = app()
    route.get("/api/stats") {
        val stats = application.planRepository.getStats()
        call.respondText(
            buildJsonObject {
                put("totalPlans", stats.totalPlans)
                put("activeSources", stats.activeSources)
                putIfNotNull("lastCollectedAt", stats.lastCollectedAt)
                put("netRxBytes", stats.netRx24h)
                put("netTxBytes", stats.netTx24h)
            }.toString(),
            ContentType.Application.Json,
        )
    }
    // ---------- 통계 대시보드 API ----------
    route.get("/api/stats/overview") {
        statsRoute(call, "E-AND-SRV-0105") {
            overviewJson(application.statsRepository.getOverview())
        }
    }
    route.get("/api/stats/brands") {
        statsRoute(call, "E-AND-SRV-0105") {
            val brand = call.queryParameters["brand"]?.takeIf { it.isNotBlank() }
            val list = application.statsRepository.getBrands()
                .filter { brand == null || it.brand == brand }
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("brands", buildJsonArray {
                    list.forEach { add(brandElement(it)) }
                })
            }.toString()
        }
    }
    route.get("/api/stats/networks") {
        statsRoute(call, "E-AND-SRV-0105") {
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("networks", buildJsonArray {
                    application.statsRepository.getNetworks()
                        .forEach { add(networkElement(it)) }
                })
            }.toString()
        }
    }
    route.get("/api/stats/distribution") {
        statsRoute(call, "E-AND-SRV-0105") {
            val type = call.queryParameters["type"] ?: "price"
            val buckets = if (type == "data") {
                application.statsRepository.getDataDistribution()
            } else {
                application.statsRepository.getPriceDistribution()
            }
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("type", type)
                put("buckets", buildJsonArray {
                    buckets.forEach { add(bucketElement(it)) }
                })
            }.toString()
        }
    }
    route.get("/api/stats/trends") {
        statsRoute(call, "E-AND-SRV-0105") {
            val params = call.queryParameters
            val type = params["type"]?.takeIf { it == "new" } ?: "crawl"
            val gran = params["gran"]?.takeIf { it == "hourly" } ?: "daily"
            val days = params["days"]?.toIntOrNull() ?: 30
            val points = if (type == "new") {
                application.statsRepository.getNewPlanTrend(days, gran)
            } else {
                application.statsRepository.getCrawlTrend(days, gran)
            }
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("type", type)
                put("gran", gran)
                put("points", buildJsonArray {
                    points.forEach { add(pointElement(it)) }
                })
            }.toString()
        }
    }
    route.get("/api/stats/value-ranking") {
        statsRoute(call, "E-AND-SRV-0106") {
            val params = call.queryParameters
            val network = params["network"]?.takeIf { it == "5G" || it == "LTE" }
            val limit = params["limit"]?.toIntOrNull() ?: 10
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("items", buildJsonArray {
                    application.statsRepository.getValueRanking(network, limit)
                        .forEachIndexed { idx, item -> add(valueItemElement(item, idx + 1)) }
                })
            }.toString()
        }
    }
    route.get("/api/stats/collection-health") {
        statsRoute(call, "E-AND-SRV-0105") {
            healthJson(application.statsRepository.getCollectionHealth())
        }
    }
    route.get("/api/stats/insights") {
        statsRoute(call, "E-AND-SRV-0107") {
            buildJsonObject {
                put("generatedAt", System.currentTimeMillis())
                put("cache", "memory")
                put("insights", buildJsonArray {
                    application.statsRepository.getInsights()
                        .forEach { add(insightElement(it)) }
                })
            }.toString()
        }
    }
}

/** 공통 예외 처리 — 신규 통계 에러코드로 응답 (E-AND-SRV-0105/0106/0107) */
private suspend fun HttpServerService.statsRoute(
    call: io.ktor.server.application.ApplicationCall,
    code: String,
    block: suspend () -> String,
) {
    try {
        call.respondText(block(), ContentType.Application.Json)
    } catch (e: Exception) {
        DebugLogger.e("통계", code, "통계 API 오류 ${call.request.local.uri}: ${e.message}", e)
        // R7: 공용 envelope (출력 바이트 동일)
        call.respondError(code, HttpStatusCode.InternalServerError)
    }
}
