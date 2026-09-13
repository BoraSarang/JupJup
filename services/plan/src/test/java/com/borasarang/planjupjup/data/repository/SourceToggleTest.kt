package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.dao.CrawlSourceDao
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R4 D1: toggle null(404) 단위 테스트 (mac 패턴) */
class SourceToggleTest {

    private fun repoWith(source: CrawlSource?): Pair<SourceRepository, CrawlSourceDao> {
        val db = mockk<PlanDatabase>()
        val dao = mockk<CrawlSourceDao>()
        every { db.crawlSourceDao() } returns dao
        coEvery { dao.getById("s1") } returns source
        return SourceRepository(db) to dao
    }

    private fun source(enabled: Boolean) = CrawlSource(
        id = "s1", name = "S", type = "COMPARE_SITE", baseUrl = "https://s.test",
        enabled = enabled, intervalHours = 1, intervalMinutes = 60,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null,
        selectorConfigJson = null,
    )

    @Test
    fun toggle_없음_null() = runBlocking {
        val (repo, _) = repoWith(null)
        assertNull(repo.toggle("s1"))
        // 기존 동작 유지: toggleEnabled는 false
        assertEquals(false, repo.toggleEnabled("s1"))
    }

    @Test
    fun toggle_있음_반전_DB기록() = runBlocking {
        val (repo, dao) = repoWith(source(true))
        coEvery { dao.setEnabled("s1", false) } returns Unit
        assertEquals(false, repo.toggle("s1"))
        coVerify { dao.setEnabled("s1", false) }
    }
}
