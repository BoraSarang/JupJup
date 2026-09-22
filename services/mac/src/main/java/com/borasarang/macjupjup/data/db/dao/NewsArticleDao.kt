package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle

@Dao
interface NewsArticleDao {
    /** originalUrl UNIQUE — 중복은 무시 (-1L) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(articles: List<NewsArticle>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelations(relations: List<NewsAppRelation>): List<Long>

    @Query("SELECT * FROM news_articles WHERE id = :id")
    suspend fun getById(id: String): NewsArticle?

    /** 신규 수집 알림용 일괄 조회 (N+1 제거) */
    @Query("SELECT * FROM news_articles WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<NewsArticle>

    /** 원문 fetch 전 기존 저장분 제외용 (IN 배치 1회) */
    @Query("SELECT id FROM news_articles WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query("SELECT appId FROM news_app_relation WHERE newsId = :newsId")
    suspend fun getAppIdsByNewsId(newsId: String): List<String>

    @Query(
        """SELECT * FROM news_articles
        WHERE (:main IS NULL OR `main` = :main)
          AND (:sub IS NULL OR sub = :sub)
          AND (:q IS NULL OR title LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%')
        ORDER BY publishedAt DESC
        LIMIT :limit OFFSET :offset"""
    )
    suspend fun listFiltered(
        main: String?,
        sub: String?,
        q: String?,
        limit: Int,
        offset: Int,
    ): List<NewsArticle>

    @Query(
        """SELECT COUNT(*) FROM news_articles
        WHERE (:main IS NULL OR `main` = :main)
          AND (:sub IS NULL OR sub = :sub)
          AND (:q IS NULL OR title LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%')"""
    )
    suspend fun countFiltered(
        main: String?,
        sub: String?,
        q: String?,
    ): Int

    /** 대시보드용 main별 최신 N건 */
    @Query(
        """SELECT * FROM news_articles WHERE `main` = :main
        ORDER BY publishedAt DESC LIMIT :limit"""
    )
    suspend fun recentByMain(main: String, limit: Int): List<NewsArticle>

    /** main별 전체 건수 (하이라이트 카드용) */
    @Query("SELECT `main` AS name, COUNT(*) AS cnt FROM news_articles GROUP BY `main`")
    suspend fun countByMain(): List<NameCount>

    /** 오늘 수집 건수 (히어로 LIVE용) */
    @Query("SELECT COUNT(*) FROM news_articles WHERE collectedAt >= :since")
    suspend fun countCollectedSince(since: Long): Int

    /** 보관기간 정리. 연동 행 먼저 삭제 후 본문 삭제 */
    @Query("DELETE FROM news_app_relation WHERE newsId IN (SELECT id FROM news_articles WHERE publishedAt < :before)")
    suspend fun deleteRelationsOlderThan(before: Long): Int

    @Query("DELETE FROM news_articles WHERE publishedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
