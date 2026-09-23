package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CommunityPost
import com.borasarang.macjupjup.util.Constants

data class CommunityFilter(
    val main: String? = null,
    /** 수집 소스 필터 — null이면 전체 */
    val sourceId: String? = null,
    val q: String? = null,
    val page: Int = 1,
    val pageSize: Int = Constants.API_DEFAULT_PAGE_SIZE,
)

/** 커뮤니티 게시글 저장·조회 (PLAN_v23) */
class CommunityRepository(private val db: MacDatabase) {

    data class SaveResult(val created: Int)

    suspend fun savePosts(posts: List<CommunityPost>): SaveResult {
        if (posts.isEmpty()) return SaveResult(0)
        val rowIds = db.communityPostDao().insertIgnore(posts)
        return SaveResult(rowIds.count { it != -1L })
    }

    data class Paged(
        val posts: List<CommunityPost>,
        val total: Int,
        val page: Int,
        val pageSize: Int,
    )

    suspend fun list(filter: CommunityFilter): Paged {
        val size = filter.pageSize.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
        val offset = (filter.page.coerceAtLeast(1) - 1) * size
        val q = filter.q?.takeIf { it.isNotBlank() }
        val sourceId = filter.sourceId?.takeIf { it.isNotBlank() }
        val rows = db.communityPostDao().listFiltered(filter.main, sourceId, q, size, offset)
        val total = db.communityPostDao().countFiltered(filter.main, sourceId, q)
        return Paged(rows, total, filter.page.coerceAtLeast(1), size)
    }

    suspend fun detail(id: String): CommunityPost? = db.communityPostDao().getById(id)

    suspend fun countByMain(): Map<String, Int> =
        db.communityPostDao().countByMain().associate { it.name to it.cnt }

    suspend fun countAll(): Int = db.communityPostDao().countAll()

    suspend fun purge(): Int {
        val before = System.currentTimeMillis() - Constants.DEFAULT_RETENTION_DAYS * 86_400_000L
        return db.communityPostDao().deleteOlderThan(before)
    }
}
