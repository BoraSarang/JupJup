package com.borasarang.common.cache

/**
 * 메모리 캐시 인프라 (R4 plan → R7 공용 승격). 집계 전용 — TTL 5분, 전체 clear 무효화.
 * 시간 주입(nowMs)으로 단위 테스트 가능.
 */
class StatsCache(
    private val ttlMs: Long = CACHE_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry(val createdAt: Long, val value: Any)

    private val map = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        val now = nowMs()
        map[key]?.let { e ->
            if (now - e.createdAt < ttlMs) {
                @Suppress("UNCHECKED_CAST")
                return e.value as T
            }
            map.remove(key)
        }
        val value = load()
        map[key] = CacheEntry(now, value)
        return value
    }

    fun invalidate() {
        map.clear()
    }

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
