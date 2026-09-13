package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.dao.PlanDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** R4: StatsCache 단위 테스트 (TTL·히트·무효화) */
class StatsCacheTest {

    @Test
    fun cached_두번째호출_로더1회() = runBlocking {
        val cache = StatsCache()
        var loads = 0
        repeat(2) { cache.cached("k") { loads++; "v" } }
        assertEquals(1, loads)
    }

    @Test
    fun invalidate_이후_재로딩() = runBlocking {
        val cache = StatsCache()
        var loads = 0
        cache.cached("k") { loads++; "v1" }
        cache.invalidate()
        val v = cache.cached("k") { loads++; "v2" }
        assertEquals(2, loads)
        assertEquals("v2", v)
    }

    @Test
    fun ttl_만료_재로딩_TTL내_히트() = runBlocking {
        var now = 0L
        val cache = StatsCache(ttlMs = 1000L, nowMs = { now })
        var loads = 0
        cache.cached("k") { loads++; "v" }
        now = 999L
        cache.cached("k") { loads++; "v" }
        assertEquals(1, loads)
        now = 1000L
        cache.cached("k") { loads++; "v" }
        assertEquals(2, loads)
    }

    @Test
    fun cached_키별_분리() = runBlocking {
        val cache = StatsCache()
        cache.cached("a") { "va" }
        val v = cache.cached("b") { "vb" }
        assertEquals("vb", v)
        assertEquals("va", cache.cached("a") { "other" })
    }

    @Test
    fun valueRanking_두번째호출_DB1회() = runBlocking {
        val db = mockk<PlanDatabase>()
        val planDao = mockk<PlanDao>()
        val crawlLogDao = mockk<com.borasarang.planjupjup.data.db.dao.CrawlLogDao>()
        every { db.planDao() } returns planDao
        every { db.crawlLogDao() } returns crawlLogDao
        coEvery { planDao.getStatsProjection() } returns emptyList()
        val repo = StatsRepository(db)
        repeat(2) { repo.getValueRanking(null, 10) }
        coVerify(exactly = 1) { planDao.getStatsProjection() }
    }

    @Test
    fun collectionHealth_두번째호출_DB히트() = runBlocking {
        val db = mockk<PlanDatabase>()
        val planDao = mockk<PlanDao>()
        val crawlLogDao = mockk<com.borasarang.planjupjup.data.db.dao.CrawlLogDao>()
        val sourceDao = mockk<com.borasarang.planjupjup.data.db.dao.CrawlSourceDao>()
        every { db.planDao() } returns planDao
        every { db.crawlLogDao() } returns crawlLogDao
        every { db.crawlSourceDao() } returns sourceDao
        coEvery { crawlLogDao.getLogsSince(any()) } returns emptyList()
        coEvery { sourceDao.getAll() } returns emptyList()
        coEvery { planDao.getStatsProjection() } returns emptyList()
        val repo = StatsRepository(db)
        repeat(2) { repo.getCollectionHealth() }
        coVerify(exactly = 1) { crawlLogDao.getLogsSince(any()) }
    }
}
