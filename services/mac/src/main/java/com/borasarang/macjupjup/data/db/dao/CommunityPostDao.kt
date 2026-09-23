package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.macjupjup.data.db.entity.CommunityPost

@Dao
interface CommunityPostDao {
    /** originalUrl UNIQUE — 중복은 무시 (-1L) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(posts: List<CommunityPost>): List<Long>

    @Query("SELECT id FROM community_posts WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query("SELECT * FROM community_posts WHERE id = :id")
    suspend fun getById(id: String): CommunityPost?

    /** 신규 상세 백필 대상 (contentHtml 없는 최근 N건) */
    @Query(
        """SELECT * FROM community_posts
        WHERE contentHtml IS NULL
        ORDER BY collectedAt DESC LIMIT :limit""",
    )
    suspend fun getMissingContent(limit: Int): List<CommunityPost>

    @Query(
        """UPDATE community_posts
        SET contentHtml = :contentHtml, summary = COALESCE(:summary, summary),
            thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl)
        WHERE id = :id""",
    )
    suspend fun updateDetail(
        id: String,
        contentHtml: String?,
        summary: String?,
        thumbnailUrl: String?,
    )

    @Query(
        """SELECT * FROM community_posts
        WHERE (:main IS NULL OR main = :main)
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR title LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%')
        ORDER BY publishedAt DESC
        LIMIT :limit OFFSET :offset""",
    )
    suspend fun listFiltered(
        main: String?,
        sourceId: String?,
        q: String?,
        limit: Int,
        offset: Int,
    ): List<CommunityPost>

    @Query(
        """SELECT COUNT(*) FROM community_posts
        WHERE (:main IS NULL OR main = :main)
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR title LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%')""",
    )
    suspend fun countFiltered(main: String?, sourceId: String?, q: String?): Int

    @Query("SELECT main AS name, COUNT(*) AS cnt FROM community_posts GROUP BY main")
    suspend fun countByMain(): List<NameCount>

    @Query("SELECT COUNT(*) FROM community_posts")
    suspend fun countAll(): Int

    @Query("DELETE FROM community_posts WHERE collectedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
