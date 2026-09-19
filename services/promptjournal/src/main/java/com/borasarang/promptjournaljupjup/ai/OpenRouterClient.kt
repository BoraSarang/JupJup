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

class OpenRouterClient(private val apiKey: String) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val supportedModels: List<AiClient.ModelInfo> = listOf(
        AiClient.ModelInfo("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash (Free)", 1048576, 8192),
        AiClient.ModelInfo("deepseek/deepseek-chat-v3-0324:free", "DeepSeek V3 (Free)", 131072, 8192),
        AiClient.ModelInfo("meta-llama/llama-4-maverick:free", "Llama 4 Maverick (Free)", 1048576, 8192),
        AiClient.ModelInfo("qwen/qwen3-235b-a22b:free", "Qwen3 235B (Free)", 40960, 8192),
        AiClient.ModelInfo("microsoft/mai-ds-r1:free", "MAI DS R1 (Free)", 131072, 8192),
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
                .url("${AiProvider.OPENROUTER.baseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/BoraSarang/JupJup")
                .addHeader("X-Title", "PromptJournal JupJup")
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
