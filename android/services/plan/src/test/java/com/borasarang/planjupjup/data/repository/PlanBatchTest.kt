package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.dao.PlanDao
import com.borasarang.planjupjup.data.db.dao.PlanSourceMappingDao
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** R5: getPlans 배치 조인 단위 테스트 (1+N → 2쿼리) */
class PlanBatchTest {

    private fun plan(id: String) = Plan(
        id = id, carrierName = "통신", mvnoNetwork = "SKT", planName = "요금제$id",
        price = 10000, priceAfterDiscount = null, discountMonths = null,
        dataAmount = "10GB", voice = "무제한", sms = "기본", networkType = "5G",
        eventBadge = null, collectedAt = 1000L, firstCollectedAt = 500L,
        isNew = false, sourceId = null, tags = null,
    )

    private fun mapping(planId: String, src: String) = PlanSourceMapping(
        planId = planId, sourceName = src, sourceUrl = "https://s.test",
        fetchedAt = 1000L, rawDataJson = null,
    )

    private fun repo(
        plans: List<Plan>,
        mappings: List<PlanSourceMapping>,
    ): Pair<PlanRepository, PlanSourceMappingDao> {
        val db = mockk<PlanDatabase>()
        val planDao = mockk<PlanDao>()
        val mappingDao = mockk<PlanSourceMappingDao>()
        every { db.planDao() } returns planDao
        every { db.planSourceMappingDao() } returns mappingDao
        coEvery {
            planDao.getFiltered(any(), any(), any(), any(), any())
        } returns plans
        coEvery { mappingDao.getByPlanIds(any()) } returns mappings
        return PlanRepository(db, mockk(relaxed = true)) to mappingDao
    }

    @Test
    fun getPlans_매핑배치1회_조인정확() = runBlocking {
        val (repo, mappingDao) = repo(
            listOf(plan("p1"), plan("p2")),
            listOf(mapping("p1", "S1"), mapping("p1", "S2"), mapping("p2", "S3")),
        )
        val paged = repo.getPlans(PlanFilter())
        assertEquals(2, paged.total)
        assertEquals(2, paged.plans[0].sources.size)
        assertEquals(1, paged.plans[1].sources.size)
        assertEquals("S1", paged.plans[0].sources[0].sourceName)
        coVerify(exactly = 1) { mappingDao.getByPlanIds(listOf("p1", "p2")) }
    }

    @Test
    fun getPlans_빈페이지_매핑조회없음() = runBlocking {
        val db = mockk<PlanDatabase>()
        val planDao = mockk<PlanDao>()
        val mappingDao = mockk<PlanSourceMappingDao>()
        every { db.planDao() } returns planDao
        every { db.planSourceMappingDao() } returns mappingDao
        coEvery {
            planDao.getFiltered(any(), any(), any(), any(), any())
        } returns emptyList()
        val repo = PlanRepository(db, mockk(relaxed = true))
        val paged = repo.getPlans(PlanFilter())
        assertEquals(0, paged.total)
        coVerify(exactly = 0) { mappingDao.getByPlanIds(any()) }
    }
}
