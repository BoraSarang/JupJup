package com.borasarang.promptjournaljupjup.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenCode Zen 클라이언트 — OpenAI 호환 /chat/completions.
 * 키는 opencode.ai 로그인 후 /connect 흐름이 아닌, 웹 취재원 관리에서 직접 등록한 Zen API 키 사용.
 */
class ZenClient(private val apiKey: String) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Zen 무료 모델 (https://opencode.ai/zen/v1/models 실측, -free) */
    override val supportedModels: List<AiClient.ModelInfo> = listOf(
        AiClient.ModelInfo("big-pickle", "Big Pickle (Free)"),
        AiClient.ModelInfo("deepseek-v4-flash-free", "DeepSeek V4 Flash (Free)"),
        AiClient.ModelInfo("mimo-v2.5-free", "MiMo V2.5 (Free)"),
        AiClient.ModelInfo("ling-3.0-flash-fin-free", "Ling 3.0 Flash Fin (Free)"),
        AiClient.ModelInfo("nemotron-3-ultra-free", "Nemotron 3 Ultra (Free)"),
        AiClient.ModelInfo("nemotron-3.5-lightning-free", "Nemotron 3.5 Lightning (Free)"),
        AiClient.ModelInfo("muse-spark-1.3-contributor-free", "Muse Spark 1.3 Contributor (Free)"),
        AiClient.ModelInfo("muse-spark-1.2-contributor-free", "Muse Spark 1.2 Contributor (Free)"),
    )

    override suspend fun complete(prompt: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(prompt))
                    })
                })
            }

            val request = Request.Builder()
                .url("${AiProvider.OPENCODE_ZEN.baseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("빈 응답"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API 오류 ${response.code}: $body"))
            }

            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val content = jsonResponse["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: return@withContext Result.failure(Exception("응답 파싱 실패"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
