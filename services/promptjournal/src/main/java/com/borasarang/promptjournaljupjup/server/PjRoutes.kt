package com.borasarang.promptjournaljupjup.server

import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.ai.AiClientFactory
import com.borasarang.promptjournaljupjup.ai.AiProvider
import com.borasarang.promptjournaljupjup.ai.ModelCatalog
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.data.db.entity.PromptExecution
import com.borasarang.promptjournaljupjup.util.DebugLogger
import com.borasarang.promptjournaljupjup.Constants
import com.borasarang.common.server.receiveJsonObject
import com.borasarang.common.server.respondError
import com.borasarang.common.server.respondNotFound
import com.borasarang.common.server.pathIdLong
import com.borasarang.common.server.putIfNotNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun pjRoutes(route: Route) {
    route.route("/api") {
        healthRoute()
        promptsRoute()
        executionsRoute()
        executeRoute()
        insightsRoute()
        providersRoute()
        settingsRoute()
    }
}

private fun Route.healthRoute() {
    get("/health") {
        call.respondText(
            """{"status":"ok","service":"promptjournal","port":${PromptJournalRuntime.preferences.getSettings().port}}""",
            ContentType.Application.Json,
        )
    }
}

// ── 프롬프트 CRUD ────────────────────────────────────────────────
private fun Route.promptsRoute() {
    route("/prompts") {
        get {
            val prompts = PromptJournalRuntime.promptRepository.getAll()
            val json = buildJsonArray {
                prompts.forEach { p -> add(promptToJson(p)) }
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        post {
            val body = call.receiveJsonObject()
            if (body == null) return@post call.respondError("요청 바디가 없습니다")
            val title = body["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@post call.respondError("제목이 필요합니다")
            val content = body["content"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@post call.respondError("프롬프트 내용이 필요합니다")

            val prompt = Prompt(
                title = title,
                content = content,
                provider = body["provider"]?.jsonPrimitive?.content ?: "OPENROUTER",
                modelId = body["modelId"]?.jsonPrimitive?.content ?: "",
                scheduleType = body["scheduleType"]?.jsonPrimitive?.content ?: "daily",
                scheduleValue = body["scheduleValue"]?.jsonPrimitive?.content ?: "09:00",
                enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                usePreviousResult = body["usePreviousResult"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            )
            val id = PromptJournalRuntime.promptRepository.save(prompt)
            if (prompt.enabled) PromptJournalRuntime.scheduler.scheduleOne(prompt.copy(id = id))
            DebugLogger.i("프롬프트", "[$id] 생성: $title")
            call.respondText("""{"ok":true,"id":$id}""", ContentType.Application.Json)
        }

        route("/{id}") {
            get {
                val id = call.pathIdLong() ?: return@get call.respondNotFound("ID가 필요합니다")
                val p = PromptJournalRuntime.promptRepository.getById(id)
                    ?: return@get call.respondNotFound("프롬프트를 찾을 수 없습니다")
                call.respondText(promptToJson(p).toString(), ContentType.Application.Json)
            }

            put {
                val id = call.pathIdLong() ?: return@put call.respondNotFound("ID가 필요합니다")
                val body = call.receiveJsonObject() ?: return@put call.respondError("요청 바디가 없습니다")
                val repo = PromptJournalRuntime.promptRepository
                val current = repo.getById(id) ?: return@put call.respondNotFound("프롬프트를 찾을 수 없습니다")

                val updated = current.copy(
                    title = body["title"]?.jsonPrimitive?.content ?: current.title,
                    content = body["content"]?.jsonPrimitive?.content ?: current.content,
                    provider = body["provider"]?.jsonPrimitive?.content ?: current.provider,
                    modelId = body["modelId"]?.jsonPrimitive?.content ?: current.modelId,
                    scheduleType = body["scheduleType"]?.jsonPrimitive?.content ?: current.scheduleType,
                    scheduleValue = body["scheduleValue"]?.jsonPrimitive?.content ?: current.scheduleValue,
                    enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: current.enabled,
                    usePreviousResult = body["usePreviousResult"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: current.usePreviousResult,
                    updatedAt = System.currentTimeMillis(),
                )
                repo.save(updated)
                PromptJournalRuntime.scheduler.scheduleOne(updated)
                DebugLogger.i("프롬프트", "[$id] 수정: ${updated.title}")
                call.respondText("""{"ok":true}""", ContentType.Application.Json)
            }

            delete {
                val id = call.pathIdLong() ?: return@delete call.respondNotFound("ID가 필요합니다")
                val deleted = PromptJournalRuntime.promptRepository.deleteById(id)
                if (!deleted) return@delete call.respondNotFound("프롬프트를 찾을 수 없습니다")
                PromptJournalRuntime.scheduler.cancelOne(id)
                call.respondText("""{"ok":true}""", ContentType.Application.Json)
            }

            get("/executions") {
                val id = call.pathIdLong() ?: return@get call.respondNotFound("ID가 필요합니다")
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 100
                val executions = PromptJournalRuntime.promptRepository.getRecentExecutions(id, limit)
                val json = buildJsonArray {
                    executions.forEach { ex -> add(executionToJson(ex)) }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            post("/execute") {
                val id = call.pathIdLong() ?: return@post call.respondNotFound("ID가 필요합니다")
                val p = PromptJournalRuntime.promptRepository.getById(id)
                    ?: return@post call.respondNotFound("프롬프트를 찾을 수 없습니다")
                if (PromptJournalRuntime.providerKeys.getKey(p.provider).isBlank()) {
                    return@post call.respondError("API 키가 설정되지 않았습니다 (${p.provider})")
                }
                PromptJournalRuntime.scheduler.triggerImmediate(id)
                call.respondText("""{"ok":true,"promptId":$id}""", ContentType.Application.Json)
            }
        }
    }
}

// ── 실행 기록 ─────────────────────────────────────────────────────
private fun Route.executionsRoute() {
    route("/executions") {
        get {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val executions = PromptJournalRuntime.promptExecutionRepository.getRecent(limit)
            val json = buildJsonArray {
                executions.forEach { ex -> add(executionToJson(ex)) }
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        get("/{id}") {
            val id = call.pathIdLong() ?: return@get call.respondNotFound("ID가 필요합니다")
            val ex = PromptJournalRuntime.promptExecutionRepository.getById(id)
                ?: return@get call.respondNotFound("실행 기록을 찾을 수 없습니다")
            call.respondText(executionToJson(ex).toString(), ContentType.Application.Json)
        }

        delete("/{id}") {
            val id = call.pathIdLong() ?: return@delete call.respondNotFound("ID가 필요합니다")
            val deleted = PromptJournalRuntime.promptExecutionRepository.deleteById(id)
            if (deleted == 0) return@delete call.respondNotFound("실행 기록을 찾을 수 없습니다")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }
    }
}

// ── 즉시 실행 (호환 유지 — promptId 지정) ──────────────────────────
private fun Route.executeRoute() {
    post("/execute") {
        val body = call.receiveJsonObject()
        val promptId = body?.get("promptId")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val p = if (promptId > 0) {
            PromptJournalRuntime.promptRepository.getById(promptId)
        } else {
            PromptJournalRuntime.promptRepository.getEnabled().firstOrNull()
        }
        if (p == null) return@post call.respondError("실행할 프롬프트가 없습니다")
        if (PromptJournalRuntime.providerKeys.getKey(p.provider).isBlank()) {
            return@post call.respondError("API 키가 설정되지 않았습니다 (${p.provider})")
        }
        PromptJournalRuntime.scheduler.triggerImmediate(p.id)
        call.respondText("""{"ok":true,"promptId":${p.id},"title":"${p.title.replace("\"", "\\\"")}"}""", ContentType.Application.Json)
    }
}

// ── 인사이트 (최근 성공 결과 요약) ────────────────────────────────
private fun Route.insightsRoute() {
    get("/insights") {
        val prompts = PromptJournalRuntime.promptRepository.getAll()
        val json = buildJsonObject {
            put("prompts", buildJsonObject {
                prompts.forEach { p ->
                    val recent = PromptJournalRuntime.promptRepository.getRecentExecutions(p.id, 1)
                    val last = recent.firstOrNull()
                    if (last != null) {
                        put(p.id.toString(), buildJsonObject {
                            put("title", JsonPrimitive(p.title))
                            put("provider", JsonPrimitive(p.provider))
                            put("modelId", JsonPrimitive(p.modelId))
                            put("status", JsonPrimitive(last.status))
                            put("executedAt", JsonPrimitive(last.executedAt))
                            put("durationMs", JsonPrimitive(last.durationMs))
                            putIfNotNull("errorMessage", last.errorMessage)
                            if (last.status == "SUCCESS") {
                                val summary = summarize(last.response)
                                put("insight", JsonPrimitive(summary))
                            }
                        })
                    }
                }
            })
        }
        call.respondText(json.toString(), ContentType.Application.Json)
    }
}

// ── 공급자·모델 관리 ──────────────────────────────────────────────
private fun Route.providersRoute() {
    route("/providers") {
        get {
            val keys = PromptJournalRuntime.providerKeys.getAllKeys()
            val json = buildJsonArray {
                AiProvider.entries.forEach { provider ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(provider.name))
                        put("displayName", JsonPrimitive(provider.displayName))
                        put("hasApiKey", JsonPrimitive(keys.containsKey(provider.name)))
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
            val key = body["apiKey"]?.jsonPrimitive?.content ?: return@post call.respondError("apiKey가 필요합니다")
            PromptJournalRuntime.providerKeys.setKey(name, key)
            DebugLogger.i("공급자", "$name API 키 저장 (${key.length}자, 마스킹)")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        post("/{name}/models/refresh") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            val key = PromptJournalRuntime.providerKeys.getKey(provider.name)
            val result = ModelCatalog.refresh(provider, key)
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
            val enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return@post call.respondError("enabled가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            ModelCatalog.setAllEnabled(provider, enabled)
            DebugLogger.i("공급자", "$name 전체 모델 ${if (enabled) "사용" else "해제"}")
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        post("/{name}/models/{modelId}/enabled") {
            val name = call.parameters["name"] ?: return@post call.respondError("공급자가 필요합니다")
            val modelId = call.parameters["modelId"] ?: return@post call.respondError("모델이 필요합니다")
            val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
            val enabled = body["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return@post call.respondError("enabled가 필요합니다")
            val provider = AiProvider.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: return@post call.respondError("지원하지 않는 공급자: $name")
            ModelCatalog.setModelEnabled(provider, modelId, enabled)
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }
    }
}

// ── 일반 설정 (포트·자동시작) ──────────────────────────────────────
private fun Route.settingsRoute() {
    get("/settings") {
        val settings = PromptJournalRuntime.preferences.getSettings()
        val json = buildJsonObject {
            put("port", JsonPrimitive(settings.port))
            put("autoStart", JsonPrimitive(settings.autoStart))
        }
        call.respondText(json.toString(), ContentType.Application.Json)
    }

    post("/settings") {
        val body = call.receiveJsonObject() ?: return@post call.respondError("요청 바디가 없습니다")
        val current = PromptJournalRuntime.preferences.getSettings()
        val updated = current.copy(
            port = body["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: current.port,
            autoStart = body["autoStart"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: current.autoStart,
        )
        PromptJournalRuntime.preferences.saveSettings(updated)
        DebugLogger.i("설정", "설정 저장 완료 port=${updated.port}")
        call.respondText("""{"ok":true}""", ContentType.Application.Json)
    }
}

// ── JSON 헬퍼 ─────────────────────────────────────────────────────
private fun promptToJson(p: Prompt) = buildJsonObject {
    put("id", JsonPrimitive(p.id))
    put("title", JsonPrimitive(p.title))
    put("content", JsonPrimitive(p.content))
    put("provider", JsonPrimitive(p.provider))
    put("modelId", JsonPrimitive(p.modelId))
    put("scheduleType", JsonPrimitive(p.scheduleType))
    put("scheduleValue", JsonPrimitive(p.scheduleValue))
    put("enabled", JsonPrimitive(p.enabled))
    put("usePreviousResult", JsonPrimitive(p.usePreviousResult))
    put("createdAt", JsonPrimitive(p.createdAt))
    put("updatedAt", JsonPrimitive(p.updatedAt))
}

private fun executionToJson(ex: PromptExecution) = buildJsonObject {
    put("id", JsonPrimitive(ex.id))
    put("promptId", JsonPrimitive(ex.promptId))
    put("provider", JsonPrimitive(ex.provider))
    put("modelId", JsonPrimitive(ex.modelId))
    put("prompt", JsonPrimitive(ex.prompt))
    put("response", JsonPrimitive(ex.response))
    put("executedAt", JsonPrimitive(ex.executedAt))
    put("durationMs", JsonPrimitive(ex.durationMs))
    put("status", JsonPrimitive(ex.status))
    putIfNotNull("errorMessage", ex.errorMessage)
}

/** 응답에서 인사이트 요약 추출 — 첫 500자를 자연스러운 경계에서 잘라 반환 */
private fun summarize(response: String): String {
    val clean = response.trim()
    if (clean.length <= 500) return clean
    val cut = clean.take(500)
    val boundary = cut.lastIndexOf('\n').takeIf { it > 100 } ?: cut.lastIndexOf(' ').takeIf { it > 100 } ?: 500
    return cut.take(boundary).trimEnd() + "…"
}