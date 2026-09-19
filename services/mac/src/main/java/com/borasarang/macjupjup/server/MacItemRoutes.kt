package com.borasarang.macjupjup.server

import com.borasarang.common.server.escapeJson
import com.borasarang.common.server.pathId
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.macjupjup.data.repository.AppFilter
import com.borasarang.macjupjup.util.Constants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 앱 아이템 라우트 (R4). health·목록·상세·수동시드·워치리스트.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun HttpServerService.macItemRoutes(route: Route) {
    val application = app()
    route.get("/api/health") {
        call.respondText(
            """{"status":"ok","timestamp":${System.currentTimeMillis()}}""",
            ContentType.Application.Json,
        )
    }
    route.get("/api/apps") {
        val params = call.queryParameters
        val filter = AppFilter(
            license = params["license"]?.takeIf { it.isNotBlank() },
            category = params["category"]?.takeIf { it.isNotBlank() },
            tag = params["tag"]?.takeIf { it.isNotBlank() },
            q = params["q"]?.takeIf { it.isNotBlank() },
            sourceIds = params["sourceIds"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            sort = params["sort"] ?: "newest",
            page = params["page"]?.toIntOrNull() ?: 1,
            pageSize = params["pageSize"]?.toIntOrNull()
                ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                ?: Constants.API_DEFAULT_PAGE_SIZE,
            bumped = params["bumped"]?.toBooleanStrictOrNull() ?: false,
            updatedOnly = params["updatedOnly"]?.toBooleanStrictOrNull() ?: false,
        )
        val result = application.appRepository.list(filter)
        call.respondText(
            appsJson(result.apps.map { it }, result.total, result.page, result.pageSize),
            ContentType.Application.Json,
        )
    }
    route.get("/api/apps/{id}") {
        val id = call.pathId()
        if (id.isNullOrBlank()) {
            call.respondText(
                """{"error":"id required"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@get
        }
        val found = application.appRepository.detail(id)
        if (found == null) {
            call.respondText(
                """{"error":"Not found"}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
        } else {
            call.respondText(detailJson(found), ContentType.Application.Json)
        }
    }
    /**
     * T-061: 수동 시드. 차트·키워드 밖 니치 앱(SoundPaste류)을 trackId·이름으로 직접 등록.
     * body: {"trackId":6471012328} 또는 {"name":"SoundPaste"}
     * 백그라운드 실행 + 상태 조회 (요청 스레드 블로킹 제거).
     */
    route.post("/api/apps/seed") {
        val json = call.receiveJsonObject()
        val trackId = json?.get("trackId")?.jsonPrimitive?.content?.toLongOrNull()
        val name = json?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (trackId == null && name == null) {
            return@post call.respondError("trackId or name required")
        }
                    setSeedState("running", System.currentTimeMillis())
                    val appRef = application
                    scope.launch {
            try {
                val query = if (trackId != null) {
                    com.borasarang.macjupjup.crawler.itunes.itunesLookupUrl("$trackId")
                } else {
                    com.borasarang.macjupjup.crawler.itunes.itunesSearchUrl(name!!)
                }
                val resp = com.borasarang.macjupjup.crawler.CrawlHttp.get(query)
                if (!resp.isOk) throw IllegalStateException("iTunes HTTP ${resp.code}")
                val seedSource = com.borasarang.macjupjup.data.db.entity.CrawlSource(
                    id = "manual_seed", name = "수동 시드", type = "MAS_DISCOVERY",
                    baseUrl = "https://itunes.apple.com", enabled = true,
                    intervalHours = 0, intervalMinutes = 0,
                    lastRunAt = null, lastStatus = "NEVER_RUN",
                    errorMessage = null, selectorConfigJson = null,
                )
                val parser = com.borasarang.macjupjup.crawler.mas.MacStoreDiscoveryCrawler(seedSource)
                // lookup/search 응답 형태 동일 → parseSearch 단일 경로
                val hits = parser.parseSearch(resp.body)
                val drafts = if (trackId != null) {
                    hits
                } else {
                    val norm = com.borasarang.macjupjup.util.MergeUtils.normalizeName(name!!)
                    hits.filter {
                        com.borasarang.macjupjup.util.MergeUtils.normalizeName(it.app.name) == norm
                    }
                }
                if (drafts.isEmpty()) {
                    setSeedState("not_found")
                    return@launch
                }
                val saved = appRef.appRepository.saveApps(
                    drafts.map { it.app },
                    drafts.flatMap { it.mappings },
                )
                com.borasarang.macjupjup.util.DebugLogger.i(
                    "수동시드",
                    "[FEATURE] 수동 시드 저장 created=${saved.created} updated=${saved.updated}",
                )
                setSeedState("done seeded=${drafts.size} created=${saved.created}")
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // R6: scope 취소(onDestroy) 시 거짓 진행중 방지 + 취소 전파
                            setSeedState("idle")
                            throw e
                        } catch (e: Exception) {
                com.borasarang.macjupjup.util.DebugLogger.e(
                    "수동시드", "E-AND-CRAWL-0201", "시드 실패: ${e.message}", e,
                )
                setSeedState("error ${e.message}")
            }
        }
        call.respondText(
            """{"accepted":true}""",
            ContentType.Application.Json,
            HttpStatusCode.Accepted,
        )
    }
    route.get("/api/apps/seed/status") {
        call.respondText(
            """{"status":"${escapeJson(lastSeedStatus)}"}""",
            ContentType.Application.Json,
        )
    }
    route.get("/api/watchlist") {
        val sources = application.sourceRepository.list()
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
                        s.lastRunAt?.let { put("lastRunAt", it) }
                        put("lastStatus", s.lastStatus)
                        s.errorMessage?.let { put("errorMessage", it) }
                    },
                )
            }
        }
        call.respondText(arr.toString(), ContentType.Application.Json)
    }
}
