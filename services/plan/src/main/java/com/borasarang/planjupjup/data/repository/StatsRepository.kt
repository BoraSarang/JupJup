package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.dao.PlanStatsRow
import com.borasarang.planjupjup.util.PlanMetrics
import com.borasarang.planjupjup.util.TimeUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 통계/인사이트 집계 리포지토리.
 * - 스키마 확장 없이 plans + crawl_logs 기반
 * - 집계 결과를 메모리 캐시(5분 TTL) — saveCrawlResults/cleanup 시 invalidate() 호출
 * - 브랜드/통신망/분포/추이/랭킹/건강도/인사이트를 Ktor 라우트에 공급
 */
class StatsRepository(private val db: PlanDatabase) {

    // ---------- 캐시 ----------

    private data class CacheEntry(val createdAt: Long, val value: Any)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        val now = System.currentTimeMillis()
        cache[key]?.let { e ->
            if (now - e.createdAt < CACHE_TTL_MS) {
                @Suppress("UNCHECKED_CAST")
                return e.value as T
            }
            cache.remove(key)
        }
        val value = load()
        cache[key] = CacheEntry(now, value)
        return value
    }

    fun invalidate() {
        cache.clear()
    }

    // ---------- 전역 집계 ----------

    /** 전체 요금제 프로젝션 (통계 조회 1회 fetch) */
    private suspend fun planRows(): List<PlanStatsRow> = db.planDao().getStatsProjection()

    /** 홈 KPI 오버뷰 */
    suspend fun getOverview(): OverviewStats = cached("overview") {
        val rows = planRows()
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val valid = rows.filter { it.price > 0 }
        val avgPrice = if (valid.isEmpty()) 0 else valid.map { it.price }.average().toInt()
        val minPrice = valid.minOfOrNull { it.price } ?: 0
        val maxPrice = valid.maxOfOrNull { it.price } ?: 0
        val parsed = rows.map { PlanMetrics.parseDataAmount(it.dataAmount) }
        val parsedCount = parsed.count { it.isParsed }
        val avgDataGb = if (parsedCount == 0) 0.0 else parsed.filter { it.isParsed }
            .map { it.dataGb.toDouble() }.average()
        val logs24h = db.crawlLogDao().getLogsSince(now - TimeUnit.DAYS.toMillis(1))

        OverviewStats(
            totalPlans = rows.size,
            brandCount = rows.map { it.carrierName }.distinct().size,
            networkCount = rows.map { it.mvnoNetwork }.filter { it != "UNKNOWN" }.distinct().size,
            newThisWeek = rows.count { it.isNew && it.firstCollectedAt >= weekAgo },
            avgPrice = avgPrice,
            minPrice = minPrice,
            maxPrice = maxPrice,
            unlimitedRatio = if (rows.isEmpty()) 0.0 else parsed.count { it.isUnlimited }.toDouble() / rows.size,
            g5Ratio = if (rows.isEmpty()) 0.0 else rows.count { it.networkType == "5G" }.toDouble() / rows.size,
            avgDataGb = round1(avgDataGb),
            crawlCountToday = logs24h.count { it.startedAt >= TimeUtils.startOfToday() },
            crawlFail24h = logs24h.count { it.status == "FAILED" },
            lastCollectedAt = rows.map { it.firstCollectedAt }.maxOrNull(),
        )
    }

    // ---------- 브랜드별 ----------

    suspend fun getBrands(): List<BrandStats> = cached("brands") {
        val rows = planRows()
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        rows.groupBy { it.carrierName }
            .map { (brand, group) -> aggregateBrand(brand, group, weekAgo) }
            .sortedByDescending { it.planCount }
    }

    private fun aggregateBrand(brand: String, group: List<PlanStatsRow>, weekAgo: Long): BrandStats {
        val valid = group.filter { it.price > 0 }
        val parsed = group.map { PlanMetrics.parseDataAmount(it.dataAmount) }
        val parsedValid = group.indices.filter { parsed[it].isParsed && group[it].price > 0 }
        val avgDataGb = if (parsedValid.isEmpty()) 0.0
        else parsedValid.map { parsed[it].dataGb.toDouble() }.average()
        val avgPrice = if (valid.isEmpty()) 0 else valid.map { it.price }.average().toInt()
        return BrandStats(
            brand = brand,
            mvnoNetwork = group.first().mvnoNetwork,
            planCount = group.size,
            newCount = group.count { it.firstCollectedAt >= weekAgo },
            avgPrice = avgPrice,
            minPrice = valid.minOfOrNull { it.price } ?: 0,
            maxPrice = valid.maxOfOrNull { it.price } ?: 0,
            avgDataGb = round1(avgDataGb),
            g5Count = group.count { it.networkType == "5G" },
            pricePerGb = if (parsedValid.isEmpty() || avgDataGb <= 0) null
            else (avgPrice / avgDataGb).roundToInt(),
        )
    }

    // ---------- 통신망별 ----------

    suspend fun getNetworks(): List<NetworkStats> = cached("networks") {
        val rows = planRows()
        rows.groupBy { it.mvnoNetwork }
            .filterKeys { it != "UNKNOWN" }
            .map { (network, group) -> aggregateNetwork(network, group) }
            .sortedByDescending { it.planCount }
    }

    private fun aggregateNetwork(network: String, group: List<PlanStatsRow>): NetworkStats {
        val valid = group.filter { it.price > 0 }
        val parsed = group.map { PlanMetrics.parseDataAmount(it.dataAmount) }
        val parsedCount = parsed.count { it.isParsed }
        val avgDataGb = if (parsedCount == 0) 0.0
        else parsed.filter { it.isParsed }.map { it.dataGb.toDouble() }.average()
        val avgPrice = if (valid.isEmpty()) 0 else valid.map { it.price }.average().toInt()
        val unlimitedRatio = if (group.isEmpty()) 0.0
        else parsed.count { it.isUnlimited }.toDouble() / group.size
        val g5Ratio = if (group.isEmpty()) 0.0
        else group.count { it.networkType == "5G" }.toDouble() / group.size
        return NetworkStats(
            network = network,
            planCount = group.size,
            avgPrice = avgPrice,
            minPrice = valid.minOfOrNull { it.price } ?: 0,
            maxPrice = valid.maxOfOrNull { it.price } ?: 0,
            avgDataGb = round1(avgDataGb),
            g5Ratio = round2(g5Ratio),
            unlimitedRatio = round2(unlimitedRatio),
            pricePerGb = if (parsedCount == 0 || avgDataGb <= 0) null
            else (avgPrice / avgDataGb).roundToInt(),
        )
    }

    // ---------- 분포 ----------

    suspend fun getPriceDistribution(): List<DistributionBucket> = cached("price") {
        PlanDistribution.priceBuckets(planRows().map { it.price })
    }

    suspend fun getDataDistribution(): List<DistributionBucket> = cached("data") {
        PlanDistribution.dataBuckets(planRows().map { PlanMetrics.parseDataAmount(it.dataAmount) })
    }

    // ---------- 추이 ----------

    /** 수집 추이: 일별/시간별 발견·신규·갱신·실패 (crawl_logs 기준) */
    suspend fun getCrawlTrend(days: Int, gran: String = "daily"): List<TrendPoint> {
        val hourly = gran == "hourly"
        val windowDays = if (hourly) days.coerceIn(1, 2) else days.coerceIn(1, 90)
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays.toLong())
        val logs = db.crawlLogDao().getLogsSince(since)
        return if (hourly) {
            val byBucket = logs.groupBy { startOfHour(it.startedAt) }
            val bucketMs = TimeUnit.HOURS.toMillis(1)
            (0 until windowDays * 24).map { offset ->
                val bucketStart = startOfHour(since) + bucketMs * offset
                val list = byBucket[bucketStart].orEmpty()
                TrendPoint(
                    label = hourLabel(bucketStart),
                    ts = bucketStart,
                    plansFound = list.sumOf { it.plansFound },
                    plansNew = list.sumOf { it.plansNew },
                    plansUpdated = list.sumOf { it.plansUpdated },
                    failCount = list.count { it.status == "FAILED" },
                )
            }
        } else {
            val byDay = logs.groupBy { startOfDay(it.startedAt) }
            (0 until windowDays).map { offset ->
                val dayStart = startOfDay(since) + TimeUnit.DAYS.toMillis(offset.toLong())
                val list = byDay[dayStart].orEmpty()
                TrendPoint(
                    label = dayLabel(dayStart),
                    ts = dayStart,
                    plansFound = list.sumOf { it.plansFound },
                    plansNew = list.sumOf { it.plansNew },
                    plansUpdated = list.sumOf { it.plansUpdated },
                    failCount = list.count { it.status == "FAILED" },
                )
            }
        }
    }

    /** 신규 요금제 추이: 일별/시간별 신규 수 (firstCollectedAt 기준) */
    suspend fun getNewPlanTrend(days: Int, gran: String = "daily"): List<TrendPoint> {
        val hourly = gran == "hourly"
        val windowDays = if (hourly) days.coerceIn(1, 2) else days.coerceIn(1, 90)
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays.toLong())
        val rows = planRows().filter { it.firstCollectedAt >= since }
        return if (hourly) {
            val byBucket = rows.groupBy { startOfHour(it.firstCollectedAt) }
            val bucketMs = TimeUnit.HOURS.toMillis(1)
            (0 until windowDays * 24).map { offset ->
                val bucketStart = startOfHour(since) + bucketMs * offset
                val count = byBucket[bucketStart]?.size ?: 0
                TrendPoint(hourLabel(bucketStart), bucketStart, count, count, 0, 0)
            }
        } else {
            val byDay = rows.groupBy { startOfDay(it.firstCollectedAt) }
            (0 until windowDays).map { offset ->
                val dayStart = startOfDay(since) + TimeUnit.DAYS.toMillis(offset.toLong())
                val count = byDay[dayStart]?.size ?: 0
                TrendPoint(dayLabel(dayStart), dayStart, count, count, 0, 0)
            }
        }
    }

    // ---------- 가성비 랭킹 ----------

    /** 가성비 TOP. network(5G/LTE) 필터, limit 기본 10 */
    suspend fun getValueRanking(network: String?, limit: Int): List<ValueRankItem> {
        return planRows().asSequence()
            .filter { it.price > 0 }
            .filter { network == null || it.networkType == network }
            .mapNotNull { row ->
                val da = PlanMetrics.parseDataAmount(row.dataAmount)
                if (!da.isParsed || da.dataGb <= 0) return@mapNotNull null
                val score = PlanMetrics.valueScore(
                    da.dataGb,
                    PlanMetrics.parseVoiceMinutes(row.voice),
                    PlanMetrics.parseSmsCount(row.sms),
                    row.price,
                )
                if (score <= 0) return@mapNotNull null
                ValueRankItem(
                    id = row.id,
                    carrierName = row.carrierName,
                    planName = row.planName,
                    price = row.price,
                    dataAmount = row.dataAmount,
                    voice = row.voice,
                    sms = row.sms,
                    networkType = row.networkType,
                    dataGb = da.dataGb,
                    score = score,
                    scoreLabel = String.format(java.util.Locale.KOREA, "%.1f", score),
                )
            }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 50))
            .toList()
    }

    // ---------- 수집 건강도 ----------

    suspend fun getCollectionHealth(): CollectionHealth {
        val since24h = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        val logs24h = db.crawlLogDao().getLogsSince(since24h)
        val durations = logs24h.mapNotNull { l ->
            l.finishedAt?.takeIf { it >= l.startedAt }?.let { (it - l.startedAt) / 1000.0 }
        }
        val sources = db.crawlSourceDao().getAll().map { s ->
            val recent = db.crawlLogDao().getRecentBySource(s.id, 10)
            val ok = recent.count { it.status == "SUCCESS" }
            SourceHealth(
                sourceId = s.id,
                sourceName = s.name,
                lastStatus = s.lastStatus,
                lastRunAt = s.lastRunAt,
                errorMessage = s.errorMessage,
                successRate = if (recent.isEmpty()) 1.0
                else round2(ok.toDouble() / recent.size),
            )
        }
        return CollectionHealth(
            success24h = logs24h.count { it.status == "SUCCESS" },
            fail24h = logs24h.count { it.status == "FAILED" },
            avgDurationSec = if (durations.isEmpty()) 0.0 else round1(durations.average()),
            sources = sources,
            lastCollectedAt = planRows().map { it.firstCollectedAt }.maxOrNull(),
        )
    }

    // ---------- 인사이트 ----------

    suspend fun getInsights(): List<Insight> {
        val overview = getOverview()
        val brands = getBrands()
        val networks = getNetworks()
        val ins = linkedMapOf<String, Insight>()

        if (overview.totalPlans == 0) {
            ins["empty"] = Insight(InsightType.INFO, "데이터 없음", "아직 수집된 요금제가 없습니다. 수집을 시작해 주세요.")
            return ins.values.toList()
        }

        brands.firstOrNull()?.let { b ->
            ins["topBrand"] = Insight(
                InsightType.POSITIVE,
                "브랜드 보유 1위",
                "${b.brand}${josa(b.brand)} ${b.planCount}개로 가장 많은 요금제를 보유했습니다 (평균 ${b.avgPrice}원).",
            )
        }

        brands.maxByOrNull { it.newCount }?.takeIf { it.newCount > 0 }?.let { b ->
            if (b.planCount >= 3) {
                ins["newBrand"] = Insight(
                    InsightType.POSITIVE,
                    "최근 신규 출시 활발",
                    "${b.brand}에서 최근 ${b.newCount}개의 신규 요금제가 추가되었습니다.",
                )
            }
        }

        if (overview.totalPlans > 0) {
            val g5Pct = (overview.g5Ratio * 100).roundToInt()
            ins["g5"] = Insight(
                InsightType.INFO,
                "네트워크 구성",
                "수집된 요금제의 5G 비중은 약 ${g5Pct}%입니다.",
            )
        }

        networks.firstOrNull()?.let { n ->
            n.pricePerGb?.let {
                ins["cheapestNet"] = Insight(
                    InsightType.INFO,
                    "가장 저렴한 통신망",
                    "${n.network}망 요금제의 1GB당 평균 단가가 ${it}원으로 전체 중 가장 저렴합니다.",
                )
            }
        }

        brands.filter { it.planCount >= 3 }.minByOrNull { it.avgPrice }?.let { b ->
            ins["cheapest"] = Insight(
                InsightType.POSITIVE,
                "평균 요금이 가장 저렴",
                "${b.brand}${josa(b.brand)} 평균 ${b.avgPrice}원으로 가장 저렴한 브랜드입니다.",
            )
        }

        if (overview.crawlFail24h > 0) {
            ins["fail"] = Insight(
                InsightType.WARNING,
                "수집 실패 감지",
                "최근 24시간 수집 실패가 ${overview.crawlFail24h}건 발생했습니다.",
            )
        }

        return ins.values.take(5)
    }

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0

    /** 받침 여부에 따른 "이/가" 조사 선택 */
    private fun josa(name: String): String {
        if (name.isBlank()) return ""
        val last = name.last()
        val has = (last.code - 0xAC00) % 28 != 0
        return if (has && Character.isLetter(last)) "이" else "가"
    }

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}

// ---------- 통계 모델 ----------

data class OverviewStats(
    val totalPlans: Int,
    val brandCount: Int,
    val networkCount: Int,
    val newThisWeek: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val unlimitedRatio: Double,
    val g5Ratio: Double,
    val avgDataGb: Double,
    val crawlCountToday: Int,
    val crawlFail24h: Int,
    val lastCollectedAt: Long?,
)

data class BrandStats(
    val brand: String,
    val mvnoNetwork: String,
    val planCount: Int,
    val newCount: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val avgDataGb: Double,
    val g5Count: Int,
    val pricePerGb: Int?,
)

data class NetworkStats(
    val network: String,
    val planCount: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val avgDataGb: Double,
    val g5Ratio: Double,
    val unlimitedRatio: Double,
    val pricePerGb: Int?,
)

/** 가격대/데이터 용량 구간 분포. 버킷은 label + [min, max) */
data class DistributionBucket(
    val label: String,
    val count: Int,
    val min: Int?,
    val max: Int?,
)

object PlanDistribution {
    /** 가격대 버킷 경계 (원). 마지막은 개방 */
    private val PRICE_BUCKETS = listOf(
        0 to 10000, 10000 to 20000, 20000 to 30000,
        30000 to 50000, 50000 to 100000, 100000 to null,
    )

    fun priceBuckets(prices: List<Int>): List<DistributionBucket> {
        val counts = IntArray(PRICE_BUCKETS.size)
        prices.forEach { p ->
            if (p <= 0) return@forEach
            val idx = PRICE_BUCKETS.indexOfFirst { (lo, hi) ->
                p >= lo && (hi == null || p < hi)
            }
            if (idx >= 0) counts[idx]++
        }
        return PRICE_BUCKETS.mapIndexed { i, (lo, hi) ->
            DistributionBucket(
                label = when {
                    lo == 0 && hi != null -> "1만원 미만"
                    hi == null && lo == 100000 -> "10만원 이상"
                    hi != null -> "${lo / 10000}~${hi / 10000}만원"
                    else -> "10만원 이상"
                },
                count = counts[i],
                min = lo,
                max = hi,
            )
        }
    }

    /** 데이터 용량 구간 분포 (GB). 파싱 불가는 별도 버킷 */
    fun dataBuckets(amounts: List<PlanMetrics.DataAmount>): List<DistributionBucket> {
        val buckets = listOf(
            "1GB 이하" to 0..1, "2~5GB" to 2..5, "6~15GB" to 6..15,
            "16~50GB" to 16..50, "51~100GB" to 51..100, "100GB 이상" to 101..Int.MAX_VALUE,
        )
        val counts = IntArray(buckets.size)
        var unparsed = 0
        amounts.forEach { a ->
            if (!a.isParsed || a.dataGb <= 0) { unparsed++; return@forEach }
            val idx = buckets.indexOfFirst { (_, r) -> a.dataGb in r }
            if (idx >= 0) counts[idx]++ else unparsed++
        }
        return buckets.mapIndexed { i, (label, range) ->
            DistributionBucket(label, counts[i], range.first, range.last)
        } + DistributionBucket("파싱 불가", unparsed, null, null)
    }
}

/** 시계열 점 (일별 수집/신규 추이) */
data class TrendPoint(
    val label: String,
    val ts: Long,
    val plansFound: Int,
    val plansNew: Int,
    val plansUpdated: Int,
    val failCount: Int,
)

/** 가성비 랭킹 1행 */
data class ValueRankItem(
    val id: String,
    val carrierName: String,
    val planName: String,
    val price: Int,
    val dataAmount: String,
    val voice: String,
    val sms: String,
    val networkType: String,
    val dataGb: Int,
    val score: Double,
    val scoreLabel: String,
)

/** 수집 건강도 */
data class CollectionHealth(
    val success24h: Int,
    val fail24h: Int,
    val avgDurationSec: Double,
    val sources: List<SourceHealth>,
    val lastCollectedAt: Long?,
)

data class SourceHealth(
    val sourceId: String,
    val sourceName: String,
    val lastStatus: String,
    val lastRunAt: Long?,
    val errorMessage: String?,
    val successRate: Double,
)

enum class InsightType { POSITIVE, WARNING, INFO }

data class Insight(
    val type: InsightType,
    val title: String,
    val text: String,
)

private fun startOfDay(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun startOfHour(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun dayLabel(dayStart: Long): String =
    java.text.SimpleDateFormat("MM-dd", java.util.Locale.KOREA).format(java.util.Date(dayStart))

private fun hourLabel(hourStart: Long): String =
    java.text.SimpleDateFormat("MM-dd HH시", java.util.Locale.KOREA).format(java.util.Date(hourStart))