package com.borasarang.macjupjup.worker

import kotlinx.coroutines.sync.Mutex

/**
 * P0-2: 동일 소스 수집 워커 상호배제.
 * 주기(`crawl_<id>`)와 즉시(`crawl_once_<id>`) 실행이 겹치면
 * 저장 경쟁(new 중복 카운트)을 만들므로, 선점된 소스는 스킵한다.
 */
object SourceLocks {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** 선점 성공 시 true. 실패(실행 중) 시 false */
    fun tryAcquire(sourceId: String): Boolean {
        return locks.getOrPut(sourceId) { Mutex() }.tryLock()
    }

    fun release(sourceId: String) {
        try {
            locks[sourceId]?.unlock()
        } catch (_: Exception) {
        }
    }
}
