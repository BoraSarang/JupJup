package com.borasarang.common.server

import com.borasarang.common.prefs.ExaKeyStore
import com.borasarang.common.search.ExaSearchClient
import io.ktor.http.ContentType
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Exa 검색 API 키 관리·테스트 REST 라우트 (promptjournal PjRoutes 승격).
 * 루트 경로는 `/search` (호출부 `/api` 네스트 유지 시 route("/api") 안에 넣는다).
 */
fun Route.exaSearchKeyRoutes(
    store: ExaKeyStore,
    log: (tag: String, msg: String) -> Unit = { _, _ -> },
    defaultTestQuery: String = "OpenRouter 무료 모델 최신 2026",
) {
    route("/search") {
        get("/key") {
            val hasKey = store.getKey().isNotBlank()
            call.respondText("""{"hasApiKey":$hasKey}""", ContentType.Application.Json)
        }

        post("/key") {
            val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
            val key = body["apiKey"]?.jsonPrimitive?.content
                ?: return@post call.respondError("apiKey가 필요합니다")
            store.setKey(key)
            log("검색", "Exa API 키 저장 (${key.length}자, 마스킹)")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        post("/test") {
            val key = store.getKey()
            if (key.isBlank()) return@post call.respondError("Exa API 키가 설정되지 않았습니다")
            val body = call.receiveJsonObject()
            val query = body?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: defaultTestQuery
            val result = ExaSearchClient(key).search(query, numResults = 3)
            val payload = result.fold(
                onSuccess = { list ->
                    buildJsonObject {
                        put("ok", JsonPrimitive(true))
                        put("count", JsonPrimitive(list.size))
                        put("query", JsonPrimitive(query))
                        list.firstOrNull()?.let {
                            put("sample", buildJsonObject {
                                put("title", JsonPrimitive(it.title))
                                put("url", JsonPrimitive(it.url))
                            })
                        }
                    }.toString()
                },
                onFailure = { e ->
                    buildJsonObject {
                        put("ok", JsonPrimitive(false))
                        put("error", JsonPrimitive(e.message ?: "검색 실패"))
                    }.toString()
                },
            )
            call.respondText(payload, ContentType.Application.Json)
        }
    }
}
