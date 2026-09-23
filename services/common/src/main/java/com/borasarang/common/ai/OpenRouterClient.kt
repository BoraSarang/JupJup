package com.borasarang.common.ai

import com.borasarang.common.util.net.NetMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override val supportedModels: List<AiClient.ModelInfo> = listOf(
        AiClient.ModelInfo("deepseek/deepseek-chat-v3-0324:free", "DeepSeek V3 (Free)", 131072, 8192),
        AiClient.ModelInfo("meta-llama/llama-4-maverick:free", "Llama 4 Maverick (Free)", 1048576, 8192),
        AiClient.ModelInfo("qwen/qwen3-235b-a22b:free", "Qwen3 235B (Free)", 40960, 8192),
        AiClient.ModelInfo("microsoft/mai-ds-r1:free", "MAI DS R1 (Free)", 131072, 8192),
        // 시드 기본값 — 정적 보호 대상 (갱신 병합에서 삭제 금지, 편집폼 항상 선택 가능)
        AiClient.ModelInfo("nvidia/nemotron-3-super-120b-a12b:free", "Nemotron 3 Super 120B (Free)"),
    )

    override suspend fun complete(prompt: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put("max_tokens", JsonPrimitive(8192))
                put("reasoning", buildJsonObject {
                    put("effort", JsonPrimitive("none"))
                })
                put("thinking", buildJsonObject {
                    put("type", JsonPrimitive("disabled"))
                })
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

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("빈 응답"))
                NetMeter.record(
                    "ai",
                    body.toByteArray(Charsets.UTF_8).size.toLong(),
                    requestBody.toString().toByteArray(Charsets.UTF_8).size.toLong() + 300L,
                )

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("API 오류 ${response.code}: $body"))
                }

                val jsonResponse = try {
                    json.parseToJsonElement(body).jsonObject
                } catch (e: Exception) {
                    return@withContext Result.failure(Exception("응답 파싱 실패 — 본문: ${body.take(300)}"))
                }

                // OpenRouter는 일부 상류(provider) 오류를 HTTP 200 + error 필드로 반환
                val errObj = jsonResponse["error"]
                if (errObj != null) {
                    val errMessage = (errObj as? JsonObject)
                        ?.get("message")?.jsonPrimitive?.content
                        ?: errObj.toString()
                    val errCode = jsonResponse["code"]?.jsonPrimitive?.content
                    return@withContext Result.failure(
                        Exception("API 오류${errCode?.let { " $it" } ?: ""}: $errMessage")
                    )
                }

                val message = jsonResponse["choices"]
                    ?.jsonArray?.get(0)
                    ?.jsonObject?.get("message")
                    ?.jsonObject
                val content = extractContentText(message)
                if (content.isBlank()) {
                    val reason = message?.get("reasoning")?.jsonPrimitive?.content
                        ?.take(200)?.replace("\n", " ")
                        ?.let { " — 사고 내용만 반환: $it…" }
                        .orEmpty()
                    return@withContext Result.failure(Exception("응답 파싱 실패 — 최종 답변(content) 없음${reason}본문: ${body.take(200)}"))
                }

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** OpenRouter message.content은 문자열(기본)·reasoning 파트 배열(추론형)·null일 수 있음.
     *  최종 답변(= content)만 보고 본문으로 사용하며, 추론 흔적(reasoning)은 길이 0으로 처리해 실패 유도. */
    private fun extractContentText(message: JsonObject?): String {
        if (message == null) return ""
        val content = message["content"] ?: return ""
        return when (content) {
            is JsonNull -> ""
            is JsonPrimitive -> content.content
            is JsonObject -> (content["text"] ?: content["content"])?.jsonPrimitive?.content.orEmpty()
            is JsonArray -> content.joinToString("\n") { part ->
                val obj = part as? JsonObject ?: return@joinToString ""
                when (val text = obj["text"]) {
                    is JsonPrimitive -> text.content
                    is JsonArray -> text.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" ")
                    else -> ""
                }
            }
            else -> ""
        }
    }
}