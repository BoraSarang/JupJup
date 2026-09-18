package com.borasarang.promptfactoryjupjup.ai

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

class GoogleAiStudioClient(private val apiKey: String) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val supportedModels: List<AiClient.ModelInfo> = listOf(
        AiClient.ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192),
        AiClient.ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", 1048576, 65536),
        AiClient.ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", 1048576, 65536),
    )

    override suspend fun complete(prompt: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", JsonPrimitive(prompt))
                            })
                        })
                    })
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${modelId}:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("빈 응답"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API 오류 ${response.code}: $body"))
            }

            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val content = jsonResponse["candidates"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?: return@withContext Result.failure(Exception("응답 파싱 실패"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
