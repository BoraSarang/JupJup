package com.borasarang.communityjupjup.crawler

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ThrottlerTest {

    @Test
    fun `hostOf_추출`() {
        assertEquals("clien.net", HostThrottler.hostOf("https://clien.net/service/board/park?a=1"))
        assertEquals("ruliweb.com", HostThrottler.hostOf("HTTP://Ruliweb.COM/best"))
        assertEquals("not a url", HostThrottler.hostOf("not a url"))
    }

    @Test
    fun `같은호스트_2회차_대기`() = runBlocking {
        val throttler = HostThrottler(minGapMs = 300L)
        throttler.waitFor("https://a.example.com/1")
        val start = System.currentTimeMillis()
        throttler.waitFor("https://a.example.com/2")
        val elapsed = System.currentTimeMillis() - start
        assertTrue("대기 부족: ${elapsed}ms", elapsed >= 200L)
    }

    @Test
    fun `다른호스트_대기없음`() = runBlocking {
        val throttler = HostThrottler(minGapMs = 10_000L)
        throttler.waitFor("https://a.example.com/1")
        val start = System.currentTimeMillis()
        throttler.waitFor("https://b.example.com/1")
        val elapsed = System.currentTimeMillis() - start
        assertTrue("다른 호스트인데 대기: ${elapsed}ms", elapsed < 2_000L)
    }

    @Test
    fun `병렬_동시성상한_순서보장`() = runBlocking {
        val throttler = HostThrottler(minGapMs = 0L)
        val live = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val urls = (1..9).map { "https://h$it.example.com/$it" }
        val out = parallelFetch(urls, throttler, 3) { url ->
            val cur = live.incrementAndGet()
            peak.updateAndGet { prev -> maxOf(prev, cur) }
            try {
                delay(50L)
                url
            } finally {
                live.decrementAndGet()
            }
        }
        assertEquals(urls, out)
        assertTrue("상한 초과: ${peak.get()}", peak.get() <= 3)
    }
}
