package com.borasarang.common.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.URI

/**
 * 호스트별 최소 요청 간격 강제 (수집 예의 1초, 병렬 시에도 동일 호스트 연타 금지).
 * 서로 다른 호스트는 대기 없이 통과. R35(community)·R36(mac) 중복분 승격 (R37).
 * 순수 JVM (단위테스트 가능).
 */
class HostThrottler(private val minGapMs: Long = 1000L) {
    private val mutex = Mutex()
    private val lastHit = mutableMapOf<String, Long>()

    suspend fun waitFor(url: String) {
        val host = hostOf(url)
        while (true) {
            val wait = mutex.withLock {
                val now = System.currentTimeMillis()
                val prev = lastHit[host]
                if (prev == null || now - prev >= minGapMs) {
                    lastHit[host] = now
                    0L
                } else {
                    minGapMs - (now - prev)
                }
            }
            if (wait <= 0) return
            delay(wait)
        }
    }

    companion object {
        fun hostOf(url: String): String {
            return try {
                URI(url).host?.lowercase() ?: url
            } catch (_: Exception) {
                url
            }
        }
    }
}

/**
 * 항목 일괄 병렬 수집 헬퍼.
 * [concurrency] 상한 세마포어 + [throttler] 호스트 예의 강제.
 * 반환 순서는 items와 동일. 순수 코루틴 (단위테스트 가능).
 */
suspend fun <T, R> parallelFetch(
    items: List<T>,
    throttler: HostThrottler,
    concurrency: Int,
    urlOf: (T) -> String,
    fetcher: suspend (T) -> R,
): List<R> = coroutineScope {
    val sem = Semaphore(concurrency.coerceAtLeast(1))
    items.map { item ->
        async(Dispatchers.IO) {
            sem.withPermit {
                throttler.waitFor(urlOf(item))
                fetcher(item)
            }
        }
    }.awaitAll()
}
