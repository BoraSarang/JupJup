package com.borasarang.promptjournaljupjup.worker

import com.borasarang.common.ai.AiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientRetryTest {

    private class FakeClient(
        var results: ArrayDeque<Result<String>>,
    ) : AiClient {
        var calls = 0
        override suspend fun complete(prompt: String, modelId: String): Result<String> {
            calls++
            return results.removeFirst()
        }

        override val supportedModels: List<AiClient.ModelInfo> = emptyList()
    }

    @Test
    fun `일시 오류 후 성공이면 재시도해 성공 반환`() = runBlocking {
        val client = FakeClient(ArrayDeque(listOf(
            Result.failure(Exception("timeout")),
            Result.success("정상 답변"),
        )))
        val retries = mutableListOf<Int>()

        val result = completeWithTransientRetry(client, "m", "p", retryDelayMs = 0L) { retries += it }

        assertTrue(result.isSuccess)
        assertEquals("정상 답변", result.getOrNull())
        assertEquals(2, client.calls)
        assertEquals(listOf(1), retries)
    }

    @Test
    fun `일시 오류가 반복되면 최대 2회 재시도 후 실패 반환`() = runBlocking {
        val client = FakeClient(ArrayDeque(List(3) {
            Result.failure(Exception("Service temporarily overloaded"))
        }))
        val retries = mutableListOf<Int>()

        val result = completeWithTransientRetry(client, "m", "p", retryDelayMs = 0L) { retries += it }

        assertTrue(result.isFailure)
        assertEquals(3, client.calls)
        assertEquals(listOf(1, 2), retries)
    }

    @Test
    fun `비일시 오류면 재시도하지 않음`() = runBlocking {
        val client = FakeClient(ArrayDeque(listOf(
            Result.failure(Exception("API 오류 403: free tier can only be used from within OpenCode")),
        )))

        val result = completeWithTransientRetry(client, "m", "p", retryDelayMs = 0L)

        assertTrue(result.isFailure)
        assertEquals(1, client.calls)
    }

    @Test
    fun `첫 시도 성공이면 재시도 없음`() = runBlocking {
        val client = FakeClient(ArrayDeque(listOf(Result.success("정상"))))

        val result = completeWithTransientRetry(client, "m", "p", retryDelayMs = 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, client.calls)
    }

    @Test
    fun `일시 오류 판정`() {
        assertTrue(isTransientError(Exception("timeout")))
        assertTrue(isTransientError(Exception("java.net.SocketTimeoutException")))
        assertTrue(isTransientError(Exception("API 오류 429: too many requests")))
        assertTrue(isTransientError(Exception("API 오류 503: Upstream error from Nvidia: Service temporarily overloaded")))
        assertTrue(isTransientError(Exception("rate limit")))
        assertFalse(isTransientError(Exception("API 오류 403: free tier gated")))
        assertFalse(isTransientError(Exception("응답 파싱 실패 — 최종 답변(content) 없음")))
        assertFalse(isTransientError(null))
    }
}