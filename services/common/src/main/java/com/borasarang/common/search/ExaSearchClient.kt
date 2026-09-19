package com.borasarang.common.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
 * Exa 검색 클라이언트 — 무료 1,000건/월 (neural 검색). (R21)
 * 한 요청당 contents view는 1개만 허용됨: maxCharacters=0이면 highlights(뉴스),
 * 0 초과면 text 본문(카탈로그·문서)으로 처리한다.
 */
class ExaSearchClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** query 검색. 최신성 1년 내 결과를 선호하도록 startPublishedDate를 생략하고 numResults만 제한 */
    suspend fun search(
        query: String,
        numResults: Int = 5,
        maxCharacters: Int = 0,
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("type", JsonPrimitive("auto"))
                put("numResults", JsonPrimitive(numResults))
                put("contents", buildJsonObject {
                    if (maxCharacters > 0) {
                        put("text", buildJsonObject { put("maxCharacters", JsonPrimitive(maxCharacters)) })
                    } else {
                        put("highlights", JsonPrimitive(true))
                    }
                })
            }
            val request = Request.Builder()
                .url("https://api.exa.ai/search")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: throw IllegalStateException("빈 응답")
            if (!response.isSuccessful) {
                throw IllegalStateException("Exa 오류 ${response.code}: ${respBody.take(200)}")
            }
            Result.success(parseSearchResponse(respBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 응답 파싱 — 분리해 JVM 단위 테스트 가능하게 함 */
    internal fun parseSearchResponse(body: String): List<SearchResult> {
        val root = json.parseToJsonElement(body).jsonObject
        return root["results"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val url = obj["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: url
            val published = obj["publishedDate"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val excerpt = obj["highlights"]?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: obj["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            SearchResult(url, title, published, excerpt)
        }
    }
}