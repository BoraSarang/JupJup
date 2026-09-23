package com.borasarang.communityjupjup.data.repository

import androidx.room.withTransaction
import com.borasarang.common.cache.StatsCache
import com.borasarang.common.util.net.NetBudget
import com.borasarang.common.util.net.NetMeter
import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.category.CommunityCategories
import java.util.concurrent.TimeUnit


/** 게시글 저장·조회·TTL 정리 */
class CommunityRepository(private val db: CommunityDatabase) {

    /** 통계 캐시 (서버 폴링용, TTL 5분) — 저장·정리 시 키별 무효화 */
    val statsCache = StatsCache()

    data class SaveResult(
        val created: Int,
        val updated: Int,
        val createdIds: List<Long>,
    )

    /** canonicalUrl UNIQUE insert — 중복은 무시하고 카운트만 갱신 (피드 중복 방지) */
    suspend fun savePosts(posts: List<CommunityPost>): SaveResult {
        if (posts.isEmpty()) return SaveResult(0, 0, emptyList())
        return db.withTransaction {
            val rowIds = db.postDao().insertIgnore(posts)
            val createdIds = rowIds.filter { it != -1L }
            // IGNORE된 기존 행은 조회수·추천·댓글·수집시각만 갱신
            val now = System.currentTimeMillis()
            var updated = 0
            val canonicals = posts.map { it.canonicalUrl }.distinct()
            val existing = db.postDao().getByCanonicalUrls(canonicals).associateBy { it.canonicalUrl }
            val byCanonical = posts.groupBy { it.canonicalUrl }
            for ((canonical, group) in byCanonical) {
                if (canonical !in existing) continue
                val latest = group.maxBy { it.collectedAt }
                updated += db.postDao().updateCountsByCanonical(
                    canonical, latest.viewCount, latest.likeCount, latest.commentCount, now,
                )
            }
            SaveResult(
                created = createdIds.size,
                updated = updated,
                createdIds = createdIds,
            )
        }.also {
            // 신규·갱신 반영 — 전체 clear 대신 통계 키만 무효화
            invalidateStats()
        }
    }

    suspend fun list(filter: PostFilter): PagedPosts {
        val q = filter.q?.takeIf { it.isNotBlank() }
        val size = filter.pageSize.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
        val offset = (filter.page.coerceAtLeast(1) - 1) * size
        val ids = filter.sourceIds.takeIf { it.isNotEmpty() }?.toList()
        val rows = if (ids != null) {
            db.postDao().listFilteredIds(
                categoryId = filter.categoryId,
                sourceIds = ids,
                q = q,
                sort = filter.sort,
                limit = size,
                offset = offset,
            )
        } else {
            db.postDao().listFiltered(
                categoryId = filter.categoryId,
                sourceId = filter.sourceId?.takeIf { it.isNotBlank() },
                q = q,
                sort = filter.sort,
                limit = size,
                offset = offset,
            )
        }
        val total = if (ids != null) {
            db.postDao().countFilteredIds(
                categoryId = filter.categoryId,
                sourceIds = ids,
                q = q,
            )
        } else {
            db.postDao().countFiltered(
                categoryId = filter.categoryId,
                sourceId = filter.sourceId?.takeIf { it.isNotBlank() },
                q = q,
            )
        }
        val sources = if (rows.isEmpty()) {
            emptyMap()
        } else {
            // R35: 매 요청 getAll 전건 스캔 → 화면에 필요한 id만 일괄 조회
            db.crawlSourceDao().getByIds(rows.map { it.sourceId }.distinct()).associateBy { it.id }
        }
        val boards = if (rows.isEmpty()) {
            emptyMap()
        } else {
            db.siteBoardDao().getByIds(rows.map { it.boardId }.distinct()).associateBy { it.id }
        }
        return PagedPosts(
            posts = rows.map { p ->
                PostListItem(
                    post = p,
                    sourceName = sources[p.sourceId]?.name,
                    boardName = boards[p.boardId]?.boardName,
                    categoryName = CommunityCategories.nameOf(p.categoryId),
                )
            },
            total = total,
            page = filter.page.coerceAtLeast(1),
            pageSize = size,
        )
    }

    suspend fun detail(id: Long): PostWithSourceList? {
        val post = db.postDao().getById(id) ?: return null
        return PostWithSourceList(
            post = post,
            source = db.crawlSourceDao().getById(post.sourceId),
            board = db.siteBoardDao().getById(post.boardId),
        )
    }

    suspend fun stats(): CommunityStats {
        val total = db.postDao().countFiltered(null, null, null)
        val active = db.crawlSourceDao().countEnabled()
        val last = db.crawlLogDao().recent(1).firstOrNull()?.finishedAt
        return CommunityStats(totalPosts = total, activeSources = active, lastCollectedAt = last)
    }

    /** 기간 네트워크 합산 (대시보드·/api/stats용, 캐시 5분). 초과 시 WARN만 */
    suspend fun netTotals(days: Int = 30): Pair<Long, Long> {
        val d = days.coerceIn(1, 90)
        return statsCache.cached("net:$d") {
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(d.toLong())
            val rows = db.crawlLogDao().collectByDay(since)
            val rx = rows.sumOf { it.rxBytes }
            val tx = rows.sumOf { it.txBytes }
            val byDay = rows.groupBy { it.day }.mapValues { (_, rs) -> rs.sumOf { it.rxBytes + it.txBytes } }
            val lastDay = byDay.toSortedMap().values.lastOrNull() ?: 0L
            if (com.borasarang.common.util.net.NetBudget.isDailyOver(lastDay)) {
                com.borasarang.communityjupjup.util.DebugLogger.w(
                    "트래픽",
                    "일일 사용량 초과(200MB) community ${com.borasarang.common.util.net.NetMeter.formatBytes(lastDay)}",
                )
            }
            rx to tx
        }
    }

    /** TTL 정리: 핫딜 3일·중고 7일·그 외 retentionDays */
    suspend fun purgeExpired(retentionDays: Int): Int {
        val now = System.currentTimeMillis()
        var deleted = 0
        deleted += db.postDao().deleteOlderThanByCategory(
            CommunityCategories.HOTDEAL,
            now - TimeUnit.DAYS.toMillis(Constants.RETENTION_HOTDEAL_DAYS.toLong()),
        )
        deleted += db.postDao().deleteOlderThanByCategory(
            CommunityCategories.USED,
            now - TimeUnit.DAYS.toMillis(Constants.RETENTION_USED_DAYS.toLong()),
        )
        deleted += db.postDao().deleteOlderThanExcept(
            now - TimeUnit.DAYS.toMillis(retentionDays.coerceAtLeast(1).toLong()),
            listOf(CommunityCategories.HOTDEAL, CommunityCategories.USED),
        )
        if (deleted > 0) invalidateStats()
        return deleted
    }

    /** 수집처 완전 제거 시 게시글·보드·로그 일괄 삭제 */
    suspend fun purgeSource(sourceId: String): Int {
        db.siteBoardDao().deleteBySource(sourceId)
        db.crawlLogDao().deleteBySource(sourceId)
        return db.postDao().deleteBySource(sourceId).also {
            if (it > 0) invalidateStats()
        }
    }

    /** 게시글만 비우기 (보드·소스 설정 유지, 재생성용) */
    suspend fun purgePosts(sourceId: String): Int {
        return db.postDao().deleteBySource(sourceId).also {
            if (it > 0) invalidateStats()
        }
    }

    private fun invalidateStats() {
        statsCache.invalidatePrefix("stats")
        statsCache.invalidatePrefix("overview")
        statsCache.invalidatePrefix("collect")
        statsCache.invalidatePrefix("trends")
        statsCache.invalidatePrefix("net")
    }
}
