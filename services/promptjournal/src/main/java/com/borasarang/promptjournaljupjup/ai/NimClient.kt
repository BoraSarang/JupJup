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

class NimClient(private val apiKey: String) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val supportedModels: List<AiClient.ModelInfo> = listOf(
        AiClient.ModelInfo("nvidia/nemotron-3-ultra-495b-v1", "Nemotron 3 Ultra", 4096, 4096),
        AiClient.ModelInfo("nvidia/nemotron-3-lightning-8b-v1", "Nemotron 3 Lightning", 4096, 4096),
        AiClient.ModelInfo("nvidia/llama-3.1-nemotron-70b-instruct", "Nemotron 70B", 4096, 4096),
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
                put("temperature", JsonPrimitive(0.7))
                put("max_tokens", JsonPrimitive(4096))
            }

            val request = Request.Builder()
                .url("${AiProvider.NIM.baseUrl}/chat/completions")
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
