package com.borasarang.communityjupjup.server.routes

import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.common.server.respondNotFound
import com.borasarang.communityjupjup.data.db.entity.SiteBoard
import com.borasarang.communityjupjup.server.HttpServerService
import com.borasarang.communityjupjup.util.CommunityCategories
import com.borasarang.communityjupjup.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 게시판 관리 라우트: 목록 조회 + 일괄 적용(추가·수정·삭제·on/off) + 소스 게시글 비우기.
 */
internal fun HttpServerService.cmBoardRoutes(route: Route) {    val application = app()

    // 게시판 목록 (source_id 없으면 전체)
    route.get("/api/boards") {
        val sourceId = call.queryParameters["source_id"] ?: call.queryParameters["sourceId"]
        val boards = if (sourceId.isNullOrBlank()) {
            application.database.siteBoardDao().getAll()
        } else {
            if (application.sourceRepository.getById(sourceId) == null) {
                return@get call.respondNotFound("unknown source_id")
            }
            application.database.siteBoardDao().getBySource(sourceId)
        }
        val counts = application.database.postDao().countBySource()
            .associate { it.sourceId to it.cnt }
        call.respondText(
            buildJsonObject {
                put("boards", buildJsonArray {
                    boards.forEach { b ->
                        add(buildJsonObject {
                            put("id", b.id)
                            put("sourceId", b.sourceId)
                            put("boardId", b.boardId)
                            put("boardName", b.boardName)
                            put("boardUrl", b.boardUrl)
                            put("categoryId", b.categoryId)
                            put("categoryName", CommunityCategories.nameOf(b.categoryId))
                            put("enabled", b.enabled)
                            put("intervalMinutes", b.intervalMinutes)
                        })
                    }
                })
                put("total", boards.size)
            }.toString(),
            ContentType.Application.Json,
        )
    }

    // 일괄 적용: [{id?, sourceId?, boardId, boardName, boardUrl, categoryId, enabled, intervalMinutes, _delete?}]
    route.post("/api/boards/batch") {
        val obj = call.receiveJsonObject()
        val defaultSourceId = obj?.get("sourceId")?.jsonPrimitive?.content
        if (obj == null) {
            return@post call.respondError("E-AND-VALID-0501")
        }
        val items = obj["boards"]?.jsonArray ?: JsonArray(emptyList())
        val dao = application.database.siteBoardDao()
        var created = 0
        var updated = 0
        var deleted = 0
        val errors = mutableListOf<String>()
        val touchedBoardIds = mutableListOf<Long>()
        val deletedBoardIds = mutableListOf<Long>()
        items.forEachIndexed { index, el ->
            try {
                val o: JsonObject = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.content?.toLongOrNull()
                if (o["_delete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true) {
                    if (id == null) {
                        errors += "#$index: 삭제 대상 id 없음"
                    } else {
                        val doomed = dao.getById(id)
                        if (doomed == null) {
                            errors += "#$index: 보드 없음 (id=$id)"
                        } else {
                            deleted += dao.deleteById(id)
                            deletedBoardIds += id
                        }
                    }
                    return@forEachIndexed
                }
                // 항목별 sourceId (없으면 최상위 sourceId로 폴백)
                val itemSourceId = o["sourceId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: defaultSourceId
                if (itemSourceId.isNullOrBlank() ||
                    application.sourceRepository.getById(itemSourceId) == null
                ) {
                    errors += "#$index: sourceId 불명"
                    return@forEachIndexed
                }
                val boardId = o["boardId"]?.jsonPrimitive?.content?.trim().orEmpty()
                val boardName = o["boardName"]?.jsonPrimitive?.content?.trim().orEmpty()
                val boardUrl = o["boardUrl"]?.jsonPrimitive?.content?.trim().orEmpty()
                val categoryId = o["categoryId"]?.jsonPrimitive?.intOrNull ?: 0
                val enabled = o["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                val interval = o["intervalMinutes"]?.jsonPrimitive?.intOrNull ?: 30
                if (boardId.isBlank() || boardName.isBlank()) {
                    errors += "#$index: boardId·boardName 필수"
                    return@forEachIndexed
                }
                if (!boardUrl.startsWith("http://") && !boardUrl.startsWith("https://")) {
                    errors += "#$index: boardUrl은 http(s) 필수"
                    return@forEachIndexed
                }
                if (CommunityCategories.byId(categoryId) == null) {
                    errors += "#$index: categoryId 1~10 필수"
                    return@forEachIndexed
                }
                if (interval !in com.borasarang.communityjupjup.worker.CrawlScheduler.ALLOWED_INTERVALS) {
                    errors += "#$index: 주기는 15/30/60/120분 중 선택"
                    return@forEachIndexed
                }
                if (id == null) {
                    dao.upsertAll(
                        listOf(
                            SiteBoard(
                                sourceId = itemSourceId,
                                boardId = boardId,
                                boardName = boardName,
                                boardUrl = boardUrl,
                                categoryId = categoryId,
                                enabled = enabled,
                                intervalMinutes = interval,
                            )
                        )
                    )
                    created++
                    // 생성된 행 식별: 동일 소스의 boardId로 재조회
                    dao.getBySource(itemSourceId).find { it.boardId == boardId }?.let {
                        touchedBoardIds += it.id
                    }
                } else {
                    // 다른 소스 소속 보드 수정 방지
                    val current = dao.getById(id)
                    if (current == null || current.sourceId != itemSourceId) {
                        errors += "#$index: 보드 없음 (id=$id)"
                        return@forEachIndexed
                    }
                    updated += dao.update(id, boardName, boardUrl, categoryId, enabled, interval)
                        .coerceAtMost(1)
                    touchedBoardIds += id
                }
            } catch (e: Exception) {
                errors += "#$index: ${e.message}"
            }
        }
        // 스케줄 동기화: 삭제분 취소, 변경분 재예약/취소
        val scheduler = application.crawlScheduler
        for (bid in deletedBoardIds) scheduler.cancelBoard(bid)
        for (bid in touchedBoardIds.distinct()) {
            val b = dao.getById(bid)
            if (b == null) continue
            val src = application.sourceRepository.getById(b.sourceId)
            if (b.enabled && src?.enabled == true) {
                scheduler.scheduleBoard(b)
                // 신규 보드는 바로 1회 수집 (이후 주기 수집)
                scope.launch { scheduler.triggerBoard(b.id) }
            } else {
                scheduler.cancelBoard(b.id)
            }
        }
        DebugLogger.i("게시판", "일괄 적용 생성$created 수정$updated 삭제$deleted 오류${errors.size}")
        call.respondText(
            buildJsonObject {
                put("ok", errors.isEmpty())
                put("created", created)
                put("updated", updated)
                put("deleted", deleted)
                put("errors", buildJsonArray { errors.forEach { add(JsonPrimitive(it)) } })
            }.toString(),
            ContentType.Application.Json,
        )
    }

    // 사이트(1차) 목록: domain 그룹 + 보드수·게시글수. 피드 필터가 아닌 관리 진입용.
    route.get("/api/sites") {
        val sources = application.sourceRepository.list()
        val boards = application.database.siteBoardDao().getAll()
        val counts = application.database.postDao().countBySource()
            .associate { it.sourceId to it.cnt }
        val boardsBySource = boards.groupBy { it.sourceId }
        call.respondText(
            buildJsonObject {
                put("sites", buildJsonArray {
                    sources.groupBy { it.domain }.forEach { (domain, group) ->
                        val ids = group.map { it.id }
                        add(buildJsonObject {
                            put("domain", domain)
                            put("name", com.borasarang.communityjupjup.util.CommunitySites.nameOf(domain))
                            put("sourceIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                            put("boardCount", ids.sumOf { boardsBySource[it]?.size ?: 0 })
                            put("postCount", ids.sumOf { counts[it] ?: 0 })
                            put("enabled", group.any { it.enabled })
                        })
                    }
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }

    // 사이트별 추천 게시판 카탈로그 (domain 없으면 전체, 모르면 404)
    route.get("/api/board-catalog") {
        val domain = call.queryParameters["domain"]?.trim().orEmpty()
        if (domain.isBlank()) {
            call.respondText(
                buildJsonObject {
                    put("catalog", buildJsonObject {
                        com.borasarang.communityjupjup.data.seed.BoardCatalog.BY_DOMAIN.forEach { (d, list) ->
                            put(d, catalogArray(list))
                        }
                    })
                }.toString(),
                ContentType.Application.Json,
            )
            return@get
        }
        val list = com.borasarang.communityjupjup.data.seed.BoardCatalog.forDomain(domain)
        if (list.isEmpty()) {
            return@get call.respondNotFound("unknown domain")
        }
        call.respondText(
            buildJsonObject {
                put("domain", domain)
                put("boards", catalogArray(list))
            }.toString(),
            ContentType.Application.Json,
        )
    }

    // 소스 게시글 비우기 (보드·소스 설정은 유지, 인코딩 깨짐 등 재생성용)
    route.post("/api/sources/{id}/purge") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank() || application.sourceRepository.getById(id) == null) {
            return@post call.respondNotFound("unknown source id")
        }
        val deleted = application.communityRepository.purgePosts(id)
        DebugLogger.i("게시판", "게시글 비우기 source=$id ${deleted}건")
        call.respondText(
            """{"purged":$deleted}""",
            ContentType.Application.Json,
        )
    }
}

private fun catalogArray(
    list: List<com.borasarang.communityjupjup.data.seed.CatalogBoard>,
): kotlinx.serialization.json.JsonArray {
    return buildJsonArray {
        list.forEach { b ->
            add(buildJsonObject {
                put("boardId", b.boardId)
                put("boardName", b.boardName)
                put("boardUrl", b.boardUrl)
                put("categoryId", b.categoryId)
            })
        }
    }
}
