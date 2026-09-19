package com.borasarang.common.ai

import com.borasarang.common.prefs.ModelEnabledStore
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
 * 공급자별 모델 카탈로그 — AIModelTalk ModelCatalog 패턴 이식 (R21 공통 모듈).
 * - 기본 목록: 정적 (실측 검증된 무료 모델)
 * - 런타임 갱신: 공급자 /models 조회 → 병합 (목록 갱신, 활성 상태 불변)
 * - 활성(사용) 모델 토글: 프롬프트 등록 시 모델 select에서 사용
 * - 기본 활성은 서비스가 지정한 시드 기본 모델만 (신규/원격 모델 자동 투입 없음)
 */
object ModelCatalog {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 공급자별 현재 모델 목록 (정적 기본 + 갱신 병합) */
    private val currentModels: MutableMap<AiProvider, List<AiClient.ModelInfo>> = mutableMapOf()

    /** 공급자별 정적 기본 목록 — 병합이 currentModels를 덮어써도 삭제 가드 기준으로 불변 유지 */
    private val staticBase: MutableMap<AiProvider, List<AiClient.ModelInfo>> = mutableMapOf()

    /** 공급자별 활성(사용) 모델 ID */
    private val enabledModels: MutableMap<AiProvider, MutableSet<String>> = mutableMapOf()

    /** 공급자별 직전 원격 스냅샷 (병합 삭제 가드용 — AIModelTalk refreshedIDs 패턴) */
    private val previousRemoteIds: MutableMap<AiProvider, List<AiClient.ModelInfo>> = mutableMapOf()

    /** 투입 상태 영속 저장소 (미부착 시 메모리 전용 — JVM 테스트용) */
    private var enabledStore: ModelEnabledStore? = null

    /** 초기 활성(기본 투입) 모델 — 서비스가 지정. R21: 신규 모델 자동 투입 제거 */
    private val seedDefaultModelIds: MutableSet<String> = mutableSetOf()

    fun init(seedDefaultModelIds: Set<String> = emptySet()) {
        this.seedDefaultModelIds.clear()
        this.seedDefaultModelIds.addAll(seedDefaultModelIds)
        for (provider in AiProvider.entries) {
            val base = when (provider) {
                AiProvider.OPENROUTER -> OpenRouterClient("").supportedModels
            }
            staticBase[provider] = base
            currentModels[provider] = base
            enabledModels[provider] = base.filter { it.id in seedDefaultModelIds }.map { it.id }.toMutableSet()
            previousRemoteIds.remove(provider)
        }
    }

    fun attachStore(store: ModelEnabledStore) {
        synchronized(this) {
            enabledStore = store
        }
    }

    /**
     * 저장된 투입 상태를 복원 — 저장값이 있는 공급자만 적용, 없으면 기본 유지.
     * 카탈로그에 없는 ID도 유지한다 (편집폼 stale 표시용 + persist 침식 방지).
     * enabledModelsFor()는 어차피 현 목록 기준으로 필터하므로 무해하다.
     */
    suspend fun restoreEnabled() {
        val store = synchronized(this) { enabledStore } ?: return
        val saved = mutableMapOf<AiProvider, Set<String>>()
        for (provider in AiProvider.entries) {
            store.getEnabled(provider.name)?.let { saved[provider] = it }
        }
        synchronized(this) {
            for ((provider, ids) in saved) {
                enabledModels[provider] = ids.toMutableSet()
            }
        }
    }

    private suspend fun persist(provider: AiProvider) {
        val store = synchronized(this) { enabledStore } ?: return
        val snapshot = synchronized(this) { enabledModels[provider].orEmpty().toSet() }
        try {
            store.saveEnabled(provider.name, snapshot)
        } catch (e: Exception) {
            android.util.Log.w("ModelCatalog", "투입 상태 저장 실패: ${provider.name}")
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

    suspend fun setModelEnabled(provider: AiProvider, modelId: String, enabled: Boolean) {
        synchronized(this) {
            val set = enabledModels.getOrPut(provider) { mutableSetOf() }
            if (enabled) set.add(modelId) else set.remove(modelId)
        }
        persist(provider)
    }

    /** 공급자 전체 모델 일괄 토글 — 관리 탭 모두 사용/해제용 */
    suspend fun setAllEnabled(provider: AiProvider, enabled: Boolean) {
        synchronized(this) {
            enabledModels[provider] = if (enabled) {
                currentModels[provider].orEmpty().map { it.id }.toMutableSet()
            } else {
                mutableSetOf()
            }
        }
        persist(provider)
    }

    /** 주요 공급자 목록 (관리 화면용) */
    val providers: List<AiProvider> = AiProvider.entries

    /** 공급자 모델 목록 런타임 갱신 — Status.OK/FAILED/SKIPPED */
    suspend fun refresh(
        provider: AiProvider,
        apiKey: String,
        protectedIds: Set<String> = emptySet(),
    ): RefreshResult {
        if (apiKey.isBlank()) {
            return RefreshResult(provider, RefreshStatus.SKIPPED, errorMessage = "API 키 미설정")
        }
        return try {
            val remote = fetchOpenRouter(apiKey)
            merge(provider, remote, protectedIds)
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

    /**
     * 전역 동기화된 병합 — 목록만 갱신, 활성 상태는 사용자 선택 그대로 (R21).
     * - 삭제는 "직전 원격 스냅샷에 있었는데 이번 원격에 없는 ID"에만 적용
     * - 정적 base + 프롬프트 참조 ID(protectedIds)는 어떤 경우에도 삭제 금지
     * - 신규 원격 모델은 목록에 추가하되 자동 투입하지 않음
     * - 실패·무키 시 refresh()가 merge를 호출하지 않으므로 기존 목록 유지
     */
    internal suspend fun merge(
        provider: AiProvider,
        remote: List<AiClient.ModelInfo>,
        protectedIds: Set<String> = emptySet(),
    ): RefreshResult {
        val result = synchronized(this) {
            val base = staticBase[provider].orEmpty().ifEmpty { currentModels[provider].orEmpty() }
            val baseIds = base.map { it.id }.toSet()
            val prevEnabled = enabledModels[provider].orEmpty()

            val remoteIds = remote.map { it.id }.toSet()
            val prevRemote = previousRemoteIds[provider].orEmpty()
            // 직전 스냅샷에만 있던 ID 중 보호 대상은 ModelInfo를 되살려 유지
            val kept = prevRemote.filter { it.id !in remoteIds && it.id in protectedIds }
            previousRemoteIds[provider] = remote

            val merged = (base + remote + kept).distinctBy { it.id }.sortedBy { it.name }
            currentModels[provider] = merged

            // 활성 상태 불변 — 미등록 ID의 투입 의도도 메모리에서 유지
            // (enabledModelsFor는 현 목록 기준 필터라 무해, persist가 DataStore를 침식하지 않음)
            enabledModels[provider] = prevEnabled.toMutableSet()

            RefreshResult(
                provider = provider,
                status = RefreshStatus.OK,
                count = merged.size,
                added = remote.count { it.id !in baseIds },
            )
        }
        persist(provider)
        return result
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