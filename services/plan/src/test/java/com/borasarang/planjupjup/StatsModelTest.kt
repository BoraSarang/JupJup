package com.borasarang.planjupjup

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.dao.PlanDao
import com.borasarang.planjupjup.data.db.dao.PlanStatsRow
import com.borasarang.planjupjup.data.repository.PlanDistribution
import com.borasarang.planjupjup.data.repository.StatsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsModelTest {

    // ---------- PlanDistribution ----------

    @Test
    fun priceBuckets_groupsPricesAndIgnoresZero() {
        val buckets = PlanDistribution.priceBuckets(listOf(5000, 15000, 25000, 99999, 150000, 0))
        val byLabel = buckets.associateBy { it.label }
        assertEquals(1, byLabel["1만원 미만"]!!.count)      // 5000
        assertEquals(1, byLabel["1~2만원"]!!.count)        // 15000
        assertEquals(1, byLabel["2~3만원"]!!.count)        // 25000
        assertEquals(1, byLabel["5~10만원"]!!.count)       // 99999
        assertEquals(1, byLabel["10만원 이상"]!!.count)    // 150000
        assertEquals(6, buckets.size)
    }

    @Test
    fun priceBuckets_empty_returnsAllZero() {
        val buckets = PlanDistribution.priceBuckets(emptyList())
        assertTrue(buckets.all { it.count == 0 })
    }

    @Test
    fun dataBuckets_groupsGbRanges() {
        val buckets = PlanDistribution.dataBuckets(
            listOf(
                com.borasarang.planjupjup.util.PlanMetrics.parseDataAmount("1GB"),
                com.borasarang.planjupjup.util.PlanMetrics.parseDataAmount("5GB"),
                com.borasarang.planjupjup.util.PlanMetrics.parseDataAmount("100GB+3Mbps"),
                com.borasarang.planjupjup.util.PlanMetrics.parseDataAmount("150GB"),
                com.borasarang.planjupjup.util.PlanMetrics.parseDataAmount("300분"),
            ),
        )
        val byLabel = buckets.associateBy { it.label }
        assertEquals(1, byLabel["1GB 이하"]!!.count)
        assertEquals(1, byLabel["2~5GB"]!!.count)
        assertEquals(1, byLabel["100GB 이상"]!!.count)     // 150GB
        assertEquals(1, byLabel["파싱 불가"]!!.count)       // 300분, 100GB+3Mbps는 100(51~100?)
        // 무제한+GB 있는 100GB는 "51~100GB" 버킷
        assertEquals(1, buckets.count { it.label == "51~100GB" && it.count == 1 })
    }

    // ---------- StatsRepository.getValueRanking ----------

    @Test
    fun valueRanking_sortsByScoreDescAndFiltersNetwork() = runBlocking {
        val now = System.currentTimeMillis()
        val rows = listOf(
            row("p1", "브랜드A", "SKT", "5G 20GB", 20000, "20GB", "300분", "300건", "5G", now),
            row("p2", "브랜드B", "KT", "LTE 30GB", 10000, "30GB", "무제한", "무제한", "LTE", now),
            row("p3", "브랜드C", "LGU+", "LTE 10GB", 5000, "10GB", "300분", "300건", "LTE", now),
            row("p4", "브랜드D", "SKT", "LTE 0원", 0, "5GB", "200분", "100건", "LTE", now),
        )
        val repo = repoWith(rows, emptyList())

        val ranking5g = repo.getValueRanking("5G", 10)
        assertEquals(1, ranking5g.size)
        assertEquals("p1", ranking5g[0].id)

        val all = repo.getValueRanking(null, 10)
        assertEquals(3, all.size) // p4는 price 0 제외
        assertTrue(all.all { it.price > 0 })
        assertEquals("p2", all[0].id) // 스코어 최대

        val limited = repo.getValueRanking(null, 1)
        assertEquals(1, limited.size)
        assertEquals("p2", limited[0].id)
    }

    @Test
    fun valueRanking_unparseableData_excluded() = runBlocking {
        val now = System.currentTimeMillis()
        val rows = listOf(
            row("p1", "A", "SKT", "플랜", 10000, "설치비 면제", "-", "-", "LTE", now),
            row("p2", "B", "KT", "플랜2", 10000, "10GB", "300분", "100건", "LTE", now),
        )
        val repo = repoWith(rows, emptyList())
        val all = repo.getValueRanking(null, 10)
        assertEquals(1, all.size)
        assertEquals("p2", all[0].id)
    }

    @Test
    fun insights_emptyData_returnsInfoCard() = runBlocking {
        val repo = repoWith(emptyList(), emptyList())
        val insights = repo.getInsights()
        assertEquals(1, insights.size)
        assertEquals("데이터 없음", insights[0].title)
    }

    @Test
    fun insights_basic_data_returnsPicks() = runBlocking {
        val now = System.currentTimeMillis()
        val rows = (1..5).map { i ->
            row(
                "plan$i", "브랜드$i", if (i % 2 == 0) "KT" else "SKT",
                "플랜$i", 1000 * i, "${i + 10}GB", "300분", "300건",
                if (i % 2 == 0) "5G" else "LTE", now,
            )
        }
        val repo = repoWith(rows, emptyList())
        val insights = repo.getInsights()
        assertTrue(insights.size in 1..5)
        assertTrue(insights.all { it.text.length <= 100 })
    }

    // ---------- helpers ----------

    private fun row(
        id: String,
        carrierName: String,
        mvnoNetwork: String,
        planName: String,
        price: Int,
        dataAmount: String,
        voice: String,
        sms: String,
        networkType: String,
        firstCollectedAt: Long,
    ) = PlanStatsRow(
        id = id,
        carrierName = carrierName,
        mvnoNetwork = mvnoNetwork,
        planName = planName,
        price = price,
        dataAmount = dataAmount,
        voice = voice,
        sms = sms,
        networkType = networkType,
        firstCollectedAt = firstCollectedAt,
        isNew = true,
        tags = null,
    )

    private fun repoWith(
        plans: List<PlanStatsRow>,
        logs: List<com.borasarang.planjupjup.data.db.dao.CrawlLogStatsRow>,
    ): StatsRepository {
        val db = mockk<PlanDatabase>()
        val planDao = mockk<PlanDao>()
        val crawlLogDao = mockk<com.borasarang.planjupjup.data.db.dao.CrawlLogDao>()
        every { db.planDao() } returns planDao
        every { db.crawlLogDao() } returns crawlLogDao
        coEvery { planDao.getStatsProjection() } returns plans
        coEvery { crawlLogDao.getLogsSince(any()) } returns logs
        return StatsRepository(db)
    }
}