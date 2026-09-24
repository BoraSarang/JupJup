package com.borasarang.common.ai

import kotlinx.coroutines.delay
import timber.log.Timber

private const val MAX_AI_RETRIES = 2
private const val RETRY_DELAY_MS = 5_000L

/** 일시 오류 재시도 판정 힌트 (소문자 매칭) */
private val TRANSIENT_HINTS = listOf(
    "timeout", "socket", "429", "503",
    "overloaded", "temporarily", "rate limit", "too many requests",
)

fun isTransientError(e: Throwable?): Boolean {
    val msg = e?.message?.lowercase() ?: return false
    return TRANSIENT_HINTS.any { msg.contains(it) }
}

/**
 * 일시적 provider 오류(timeout/429/503/과부하)면 최대 [maxRetries]회 재시도.
 * 성공 여부와 무관하게 시도 횟수를 [onRetry]로 통지.
 */
suspend fun completeWithTransientRetry(
    client: AiClient,
    modelId: String,
    finalPrompt: String,
    maxRetries: Int = MAX_AI_RETRIES,
    retryDelayMs: Long = RETRY_DELAY_MS,
    onRetry: (Int) -> Unit = {},
): Result<String> {
    var result = client.complete(finalPrompt, modelId)
    var retries = 0
    while (result.isFailure && retries < maxRetries) {
        val err = result.exceptionOrNull()
        if (!isTransientError(err)) {
            Timber.w("AI 호출 실패(비일시 오류, 재시도 안 함): ${err?.message}")
            break
        }
        retries++
        onRetry(retries)
        Timber.w("AI 호출 일시 오류 — $retries/$maxRetries 재시도: ${err?.message}")
        delay(retryDelayMs)
        result = client.complete(finalPrompt, modelId)
    }
    return result
}
