package com.borasarang.communityjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.communityjupjup.data.db.entity.CommunityPost

@Dao
interface CommunityPostDao {
    /** originalUrl UNIQUE — 중복 수집 시 무시 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(posts: List<CommunityPost>): List<Long>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getById(id: Long): CommunityPost?

    /** 알림용 일괄 조회 (N+1 제거) */
    @Query("SELECT * FROM posts WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CommunityPost>

    @Query("SELECT * FROM posts WHERE originalUrl IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<CommunityPost>

    @Query("SELECT * FROM posts WHERE canonicalUrl IN (:urls)")
    suspend fun getByCanonicalUrls(urls: List<String>): List<CommunityPost>

    /** 재수집 시 카운트·수집시각 갱신 (행은 유지, 중복 생성 없음) */
    @Query(
        """UPDATE posts SET viewCount = :views, likeCount = :likes, commentCount = :comments,
        collectedAt = :now WHERE canonicalUrl = :canonical"""
    )
    suspend fun updateCountsByCanonical(
        canonical: String,
        views: Int?,
        likes: Int?,
        comments: Int?,
        now: Long,
    ): Int

    /** 상세 백필 대상: 요약·썸네일 없는 게시글 URL (오래된 순 — 신규 글은 수집 시 처리) */
    @Query(
        """SELECT originalUrl FROM posts
        WHERE summary IS NULL OR thumbnailUrl IS NULL
        ORDER BY collectedAt ASC LIMIT :limit"""
    )
    suspend fun getMissingDetailUrls(limit: Int): List<String>

    @Query(
        """UPDATE posts SET summary = COALESCE(:summary, summary),
        thumbnailUrl = COALESCE(:thumbnail, thumbnailUrl),
        imageUrls = COALESCE(:images, imageUrls)
        WHERE originalUrl = :url"""
    )
    suspend fun updateDetailByUrl(url: String, summary: String?, thumbnail: String?, images: String? = null): Int

    /** 휘발 파라미터가 달라도 동일 게시글에 갱신 (canonical 매칭, null은 유지) */
    @Query(
        """UPDATE posts SET summary = COALESCE(:summary, summary),
        thumbnailUrl = COALESCE(:thumbnail, thumbnailUrl),
        imageUrls = COALESCE(:images, imageUrls)
        WHERE canonicalUrl = :canonical"""
    )
    suspend fun updateDetailByCanonical(
        canonical: String,
        summary: String?,
        thumbnail: String?,
        images: String? = null,
    ): Int

    @Query(
        """SELECT * FROM posts
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR title LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 'popular' THEN COALESCE(likeCount, 0) ELSE publishedAt END DESC,
          collectedAt DESC
        LIMIT :limit OFFSET :offset"""
    )
    suspend fun listFiltered(
        categoryId: Int?,
        sourceId: String?,
        q: String?,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<CommunityPost>

    @Query(
        """SELECT COUNT(*) FROM posts
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR title LIKE '%' || :q || '%')"""
    )
    suspend fun countFiltered(categoryId: Int?, sourceId: String?, q: String?): Int

    @Query(
        """SELECT * FROM posts
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND sourceId IN (:sourceIds)
          AND (:q IS NULL OR title LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 'popular' THEN COALESCE(likeCount, 0) ELSE publishedAt END DESC,
          collectedAt DESC
        LIMIT :limit OFFSET :offset"""
    )
    suspend fun listFilteredIds(
        categoryId: Int?,
        sourceIds: List<String>,
        q: String?,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<CommunityPost>

    @Query(
        """SELECT COUNT(*) FROM posts
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND sourceId IN (:sourceIds)
          AND (:q IS NULL OR title LIKE '%' || :q || '%')"""
    )
    suspend fun countFilteredIds(categoryId: Int?, sourceIds: List<String>, q: String?): Int

    @Query("SELECT categoryId, COUNT(*) AS cnt FROM posts GROUP BY categoryId")
    suspend fun countByCategory(): List<CategoryCounter>

    @Query("SELECT sourceId, COUNT(*) AS cnt FROM posts GROUP BY sourceId")
    suspend fun countBySource(): List<SourceCounter>

    @Query("SELECT COUNT(*) FROM posts WHERE collectedAt >= :since")
    suspend fun countNewSince(since: Long): Int

    @Query("SELECT * FROM posts WHERE collectedAt >= :since ORDER BY COALESCE(likeCount, 0) DESC LIMIT :limit")
    suspend fun topByLikesSince(since: Long, limit: Int): List<CommunityPost>

    /** TTL 정리: 카테고리별 보관일 초과 삭제 */
    @Query("DELETE FROM posts WHERE categoryId = :categoryId AND collectedAt < :before")
    suspend fun deleteOlderThanByCategory(categoryId: Int, before: Long): Int

    @Query("DELETE FROM posts WHERE collectedAt < :before AND categoryId NOT IN (:excluded)")
    suspend fun deleteOlderThanExcept(before: Long, excluded: List<Int>): Int

    @Query("DELETE FROM posts WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String): Int
}

data class CategoryCounter(val categoryId: Int, val cnt: Int)
data class SourceCounter(val sourceId: String, val cnt: Int)
