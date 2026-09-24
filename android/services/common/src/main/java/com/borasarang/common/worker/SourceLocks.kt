package com.borasarang.common.worker

import kotlinx.coroutines.sync.Mutex

/**
 * 동일 소스 수집 워커 상호배제 (services/mac SourceLocks + services/plan SourceLocks 통합, R2).
 * 주기와 즉시 실행이 겹치면 저장 경쟁을 만들므로, 선점된 소스는 스킵한다.
 */
object SourceLocks {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** 선점 성공 시 true. 실패(실행 중) 시 false */
    fun tryAcquire(sourceId: String): Boolean {
        return locks.getOrPut(sourceId) { Mutex() }.tryLock()
    }

    fun release(sourceId: String) {
        val mutex = locks[sourceId] ?: return
        try {
            if (mutex.isLocked) mutex.unlock()
        } catch (_: IllegalMonitorStateException) {
            // 미보유 해제 시도 — 무시 (의도적)
        } finally {
            // R6: 유휴 락 제거 — 삭제된 소스 키 영구 잔존·무한증가 방지.
            // 원자적 remove(sourceId, mutex): 다른 워커가 이미 재선점한 맵 항목은 제거하지 않음
            if (!mutex.isLocked) {
                locks.remove(sourceId, mutex)
            }
        }
    }
}
