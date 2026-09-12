package com.borasarang.planjupjup.data.repository

import androidx.room.withTransaction
import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import java.util.concurrent.TimeUnit

class PlanRepository(
    private val db: PlanDatabase,
    private val statsRepository: StatsRepository,
) {

    /** 목록 조회. SQL은 동등·범위 조건, minData·정렬·페이징은 코틀린에서 처리 */
    suspend fun getPlans(filter: PlanFilter): PagedPlans {
        // "신규출시"는 tags 컬럼이 아닌 isNew 플래그로 조회
        val onlyNew = if (filter.tag == Constants.TAG_NEW) 1 else 0
        val tagParam = if (onlyNew == 1) null else filter.tag?.ifBlank { null }
        val rows = db.planDao().getFiltered(
            network = filter.network?.ifBlank { null },
            carrier = filter.carrier?.ifBlank { null },
            tag = tagParam,
            maxPrice = filter.maxPrice,
            onlyNew = onlyNew,
        )
        val withMinData = filter.minDataGb?.let { min ->
            rows.filter { parseDataGb(it.dataAmount) >= min }
        } ?: rows
        val sorted = when (filter.sort) {
            "price_desc" -> withMinData.sortedByDescending { it.price }
            "data_desc" -> withMinData.sortedByDescending { parseDataGb(it.dataAmount) }
            "newest" -> withMinData.sortedByDescending { it.collectedAt }
            else -> withMinData.sortedBy { it.price }
        }
        val page = filter.page.coerceAtLeast(1)
        val pageSize = filter.pageSize.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
        val from = ((page - 1) * pageSize).coerceAtMost(sorted.size)
        val to = (from + pageSize).coerceAtMost(sorted.size)
        val pageItems = if (from >= to) emptyList() else sorted.subList(from, to)
        val mappingDao = db.planSourceMappingDao()
        val items = pageItems.map { plan ->
            PlanWithSources(plan, mappingDao.getByPlanId(plan.id))
        }
        return PagedPlans(items, sorted.size, page, pageSize)
    }

    suspend fun getPlanWithSources(id: String): PlanWithSources? {
        val plan = db.planDao().getById(id) ?: return null
        return PlanWithSources(plan, db.planSourceMappingDao().getByPlanId(id))
    }

    /** 크롤 결과 원자 저장 + 신규/업데이트 집계. 신규 id는 isNew=1 표시 */
    suspend fun saveCrawlResults(plans: List<Plan>, mappings: List<PlanSourceMapping>): SaveResult {
        val createdIds = mutableListOf<String>()
        var updated = 0
        db.withTransaction {
            val dao = db.planDao()
            val toSave = plans.map { plan ->
                val existing = dao.getById(plan.id)
                if (existing == null) {
                    createdIds += plan.id
                    plan.copy(firstCollectedAt = plan.collectedAt)
                } else {
                    updated++
                    // 갱신 시 최초 수집 시각 유지, 최근 수집만 갱신
                    plan.copy(firstCollectedAt = existing.firstCollectedAt)
                }
            }
            dao.upsertAll(toSave)
            db.planSourceMappingDao().upsertAll(mappings)
            if (createdIds.isNotEmpty()) {
                dao.markNew(createdIds)
            }
        }
        // 통계 캐시 무효화 (신규/갱신 반영)
        statsRepository.invalidate()
        return SaveResult(createdIds, updated)
    }

    suspend fun getStats(): PlanStats {
        return PlanStats(
            totalPlans = db.planDao().countAll(),
            activeSources = db.crawlSourceDao().countEnabled(),
            lastCollectedAt = db.planDao().getLatestCollectedAt(),
        )
    }

    /** 오늘 수집/신규 집계 — 일일 요약(9시) 알림 데이터 */
    suspend fun getDailySummary(cutoff: Long): DailySummary {
        val news = db.planDao().getNewSince(cutoff, 100)
        return DailySummary(
            collectedToday = db.planDao().countCollectedSince(cutoff),
            newPlans = news,
        )
    }

    /** 보관 기간 초과 데이터 정리. 반환: 삭제된 요금제 수 */
    suspend fun cleanupOldPlans(retentionDays: Int): Int {
        DebugLogger.i("정리", "보관 기간 초과 데이터 정리 시작 retentionDays=$retentionDays")
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val deleted = db.withTransaction {
            val count = db.planDao().deleteOlderThan(cutoff)
            db.planSourceMappingDao().deleteOrphans()
            db.crawlLogDao().prune(200)
            // 7일 지난 신규 플래그 해제
            db.planDao().clearStaleNewFlags(
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7),
            )
            count
        }
        // 정리로 인한 통계 캐시 무효화
        statsRepository.invalidate()
        DebugLogger.i("정리", "정리 완료 deleted=$deleted")
        return deleted
    }

    companion object {
        /** "15GB", "15.5GB", "무제한+5Mbps" 등에서 GB 숫자 추출. 무제한 단독 표기는 Int.MAX_VALUE */
        fun parseDataGb(text: String): Int {
            val t = text.replace(" ", "").uppercase()
            if (t.startsWith("무제한") && !t.contains("GB")) return Int.MAX_VALUE
            val match = Regex("""(\d+(?:\.\d+)?)\s*GB""").find(t) ?: return 0
            return match.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
        }
    }
}

/** 신규 생성 요금제 id 목록 포함 — 알림용 신규 요금제 조회에 사용 */
data class SaveResult(val createdIds: List<String>, val updated: Int) {
    val created: Int get() = createdIds.size
}

data class DailySummary(
    val collectedToday: Int,
    val newPlans: List<Plan>,
)
