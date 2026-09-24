package com.borasarang.common.util.net

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 서비스별 네트워크 사용량 메모리 카운터 (R42a, PLAN_v18).
 * OS NetworkStatsManager 미사용 — CrawlHttp/OkHttp 초크포인트에서 실수신 바이트 직접 가산.
 * 영속은 R42b(CrawlLog rx/tx)에서 담당, 여기는 프로세스 내 집계만.
 * 순수 JVM (단위테스트 가능).
 */
object NetMeter {
    private val rx = ConcurrentHashMap<String, AtomicLong>()
    private val tx = ConcurrentHashMap<String, AtomicLong>()
    private val reqs = ConcurrentHashMap<String, AtomicLong>()

    fun record(service: String, rxBytes: Long, txBytes: Long) {
        if (rxBytes < 0 || txBytes < 0) return
        if (rxBytes == 0L && txBytes == 0L) return
        rx.getOrPut(service) { AtomicLong() }.addAndGet(rxBytes)
        tx.getOrPut(service) { AtomicLong() }.addAndGet(txBytes)
        reqs.getOrPut(service) { AtomicLong() }.incrementAndGet()
    }

    fun snapshot(): Map<String, NetUsage> {
        val keys = (rx.keys + tx.keys + reqs.keys).toSet()
        return keys.associateWith { k ->
            NetUsage(
                service = k,
                rxBytes = rx[k]?.get() ?: 0L,
                txBytes = tx[k]?.get() ?: 0L,
                requests = reqs[k]?.get() ?: 0L,
            )
        }
    }

    /** 단일 서비스 스냅샷 (워커 실행 전후 델타용) */
    fun snapshotFor(service: String): NetUsage {
        return NetUsage(
            service = service,
            rxBytes = rx[service]?.get() ?: 0L,
            txBytes = tx[service]?.get() ?: 0L,
            requests = reqs[service]?.get() ?: 0L,
        )
    }

    /** [before] 이후 해당 서비스의 증분 (수집 1회 실행분 기록용) */
    fun deltaSince(service: String, before: NetUsage): NetUsage {
        val after = snapshotFor(service)
        return NetUsage(
            service = service,
            rxBytes = (after.rxBytes - before.rxBytes).coerceAtLeast(0L),
            txBytes = (after.txBytes - before.txBytes).coerceAtLeast(0L),
            requests = (after.requests - before.requests).coerceAtLeast(0L),
        )
    }

    fun total(): NetUsage {
        val s = snapshot().values
        return NetUsage("total", s.sumOf { it.rxBytes }, s.sumOf { it.txBytes }, s.sumOf { it.requests })
    }

    fun reset(service: String? = null) {
        if (service == null) {
            rx.clear()
            tx.clear()
            reqs.clear()
        } else {
            rx.remove(service)
            tx.remove(service)
            reqs.remove(service)
        }
    }

    /** 바이트 사람 읽기 형식 (B/KB/MB/GB, 소수 1자리) */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1fKB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1fMB".format(mb)
        return "%.1fGB".format(mb / 1024.0)
    }
}

data class NetUsage(
    val service: String,
    val rxBytes: Long,
    val txBytes: Long,
    val requests: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
