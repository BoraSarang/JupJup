package com.borasarang.common.server

import com.borasarang.common.ai.AiProvider
import com.borasarang.common.ai.ModelCatalog
import com.borasarang.common.prefs.ProviderKeyStore
import io.ktor.http.ContentType
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * AI 공급자·모델 관리 REST 라우트 (promptjournal PjRoutes 승격).
 * [keys]·[protectedModelIds]·[log]은 서비스가 주입한다.
 * protectedModelIds: 병합 삭제 가드용 — 해당 공급자를 참조하는 문서의 modelId 집합.
 */
fun Route.aiProvidersRoute(
    keys: ProviderKeyStore,
    protectedModelIds: suspend (AiProvider) -> Set<String> = { emptySet() },
    log: (tag: String, msg: String) -> Unit = { _, _ -> },
) {
    route("/providers") {
        get {
            val allKeys = keys.getAllKeys()
            val json = buildJsonArray {
                AiProvider.entries.forEach { provider ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(provider.name))
                        put("displayName", JsonPrimitive(provider.displayName))
                        put("hasApiKey", JsonPrimitive(allKeys.containsKey(provider.name)))
                        put("models", buildJsonArray {
                            ModelCatalog.allModels(provider).forEach { m ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(m.id))
                                    put("name", JsonPrimitive(m.name))
                                    put("contextWindow", JsonPrimitive(m.contextWindow))
                                    put("enabled", JsonPrimitive(ModelCatalog.enabledModelsFor(provider).any { it.id == m.id }))
                                })
                            }
                        })
                    })
                }
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        post("/{name}/key") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
            val key = body["apiKey"]?.jsonPrimitive?.content
                ?: return@post call.respondError("apiKey가 필요합니다")
            keys.setKey(name, key)
            log("공급자", "$name API 키 저장 (${key.length}자, 마스킹)")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        post("/{name}/models/refresh") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            val key = keys.getKey(provider.name)
            val protectedIds = protectedModelIds(provider)
            val result = ModelCatalog.refresh(provider, key, protectedIds)
            buildJsonObject {
                put("ok", JsonPrimitive(result.status != ModelCatalog.RefreshStatus.FAILED))
                put("provider", JsonPrimitive(provider.name))
                put("status", JsonPrimitive(result.status.name))
                put("count", JsonPrimitive(result.count))
                put("added", JsonPrimitive(result.added))
                putIfNotNull("errorMessage", result.errorMessage)
            }.let { call.respondText(it.toString(), ContentType.Application.Json) }
        }

        post("/{name}/models/enabled") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
            val enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: return@post call.respondError("enabled가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            ModelCatalog.setAllEnabled(provider, enabled)
            log("공급자", "$name 전체 모델 ${if (enabled) "사용" else "해제"}")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        post("/{name}/models/{modelId}/enabled") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val modelId = call.parameters["modelId"] ?: return@post call.respondError("모델이 필요합니다")
            val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
            val enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: return@post call.respondError("enabled가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            ModelCatalog.setModelEnabled(provider, modelId, enabled)
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }
    }
}
