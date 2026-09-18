package com.borasarang.promptfactoryjupjup.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 공급자별 모델 카탈로그 — AIModelTalk ModelCatalog 패턴 이식.
 * - 기본 목록: 정적 (실측 검증된 무료 모델)
 * - 런타임 갱신: 공급자 /models 조회 → 병합 (새 모델 추가, 활성 상태 유지)
 * - 활성(사용) 모델 토글: 프롬프트 등록 시 모델 select에서 사용
 */
object ModelCatalog {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 공급자별 현재 모델 목록 (정적 기본 + 갱신 병합) */
    private val currentModels: MutableMap<AiProvider, List<AiClient.ModelInfo>> = mutableMapOf()

    /** 공급자별 활성(사용) 모델 ID */
    private val enabledModels: MutableMap<AiProvider, MutableSet<String>> = mutableMapOf()

    fun init() {
        for (provider in AiProvider.entries) {
            val base = when (provider) {
                AiProvider.OPENROUTER -> OpenRouterClient("").supportedModels
                AiProvider.NIM -> NimClient("").supportedModels
                AiProvider.GOOGLE_AI_STUDIO -> GoogleAiStudioClient("").supportedModels
            }
            currentModels[provider] = base
            enabledModels[provider] = base.map { it.id }.toMutableSet()
        }
    }

    /** 전체 모델 목록 (공급자별). 갱신 상태와 무관하게 현재 보유 목록 */
    fun allModels(provider: AiProvider): List<AiClient.ModelInfo> {
        synchronized(this) {
            return currentModels[provider].orEmpty()
        }
    }

    /** 활성(사용) 모델 목록 — 프롬프트 등록 select 용 */
    fun enabledModelsFor(provider: AiProvider): List<AiClient.ModelInfo> {
        synchronized(this) {
            val enabled = enabledModels[provider].orEmpty()
            return currentModels[provider].orEmpty().filter { enabled.contains(it.id) }
        }
    }

    fun setModelEnabled(provider: AiProvider, modelId: String, enabled: Boolean) {
        synchronized(this) {
            val set = enabledModels.getOrPut(provider) { mutableSetOf() }
            if (enabled) set.add(modelId) else set.remove(modelId)
        }
    }

    /** 공급자 전체 모델 일괄 토글 — 관리 탭 모두 사용/해제용 */
    fun setAllEnabled(provider: AiProvider, enabled: Boolean) {
        synchronized(this) {
            enabledModels[provider] = if (enabled) {
                currentModels[provider].orEmpty().map { it.id }.toMutableSet()
            } else {
                mutableSetOf()
            }
        }
    }

    /** 주요 공급자 목록 (관리 화면용) */
    val providers: List<AiProvider> = AiProvider.entries

    /** 공급자 모델 목록 런타임 갱신 — Status.OK/FAILED/SKIPPED */
    suspend fun refresh(provider: AiProvider, apiKey: String): RefreshResult {
        if (apiKey.isBlank()) {
            return RefreshResult(provider, RefreshStatus.SKIPPED, errorMessage = "API 키 미설정")
        }
        return try {
            val remote = when (provider) {
                AiProvider.OPENROUTER -> fetchOpenRouter(apiKey)
                AiProvider.NIM -> fetchNim(apiKey)
                AiProvider.GOOGLE_AI_STUDIO -> fetchGoogle(apiKey)
            }
            merge(provider, remote)
        } catch (e: Exception) {
            RefreshResult(provider, RefreshStatus.FAILED, errorMessage = e.message ?: "오류")
        }
    }

    private suspend fun fetchOpenRouter(apiKey: String): List<AiClient.ModelInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${AiProvider.OPENROUTER.baseUrl}/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            val root = json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!id.endsWith(":free")) return@mapNotNull null
                val ctx = obj["context_length"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                AiClient.ModelInfo(
                    id = id,
                    name = obj["name"]?.jsonPrimitive?.content ?: id,
                    contextWindow = ctx,
                )
            }
        }
    }

    private suspend fun fetchNim(apiKey: String): List<AiClient.ModelInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${AiProvider.NIM.baseUrl}/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            val root = json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                AiClient.ModelInfo(
                    id = id,
                    name = obj["id"]?.jsonPrimitive?.content ?: id,
                    contextWindow = obj["context_length"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }
        }
    }

    private suspend fun fetchGoogle(apiKey: String): List<AiClient.ModelInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${AiProvider.GOOGLE_AI_STUDIO.baseUrl}/models?key=$apiKey&pageSize=1000")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            val root = json.parseToJsonElement(body).jsonObject
            root["models"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = name.removePrefix("models/")
                if (id.isBlank()) return@mapNotNull null
                AiClient.ModelInfo(
                    id = id,
                    name = obj["displayName"]?.jsonPrimitive?.content ?: id,
                )
            }
        }
    }

    /** 전역 동기화된 병합 — 새 원격 모델 추가, 활성 상태 보존 */
    private fun merge(provider: AiProvider, remote: List<AiClient.ModelInfo>): RefreshResult {
        synchronized(this) {
            val base = currentModels[provider].orEmpty()
            val baseIds = base.map { it.id }.toSet()
            val prevEnabled = enabledModels[provider].orEmpty()

            val merged = (base + remote).distinctBy { it.id }.sortedBy { it.name }
            currentModels[provider] = merged

            val mergedIds = merged.map { it.id }.toSet()
            val newEnabled = mutableSetOf<String>().apply {
                addAll(prevEnabled.filter { it in mergedIds })
                addAll(remote.filter { it.id !in prevEnabled }.map { it.id })
            }
            enabledModels[provider] = newEnabled

            return RefreshResult(
                provider = provider,
                status = RefreshStatus.OK,
                count = merged.size,
                added = remote.count { it.id !in baseIds },
            )
        }
    }

    enum class RefreshStatus { OK, FAILED, SKIPPED }

    data class RefreshResult(
        val provider: AiProvider,
        val status: RefreshStatus,
        val count: Int = 0,
        val added: Int = 0,
        val errorMessage: String? = null,
    )
}