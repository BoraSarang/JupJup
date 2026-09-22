package com.borasarang.macjupjup.data.repository

import androidx.room.withTransaction
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import com.borasarang.macjupjup.util.Constants

/** 뉴스 필터 (main/sub 미지정·"전체"는 null로 정규화) */
data class NewsFilter(
    val main: String? = null,
    val sub: String? = null,
    val q: String? = null,
    val page: Int = 1,
    val pageSize: Int = Constants.API_DEFAULT_PAGE_SIZE,
)

/** 뉴스 저장·조회 (R32 PLAN_v17) */
class NewsRepository(private val db: MacDatabase) {

    data class SaveResult(
        val created: Int,
        val createdIds: List<String>,
    )

    /** originalUrl UNIQUE insert — 중복 무시. 연동 행도 함께 저장 */
    suspend fun saveArticles(
        articles: List<NewsArticle>,
        relations: List<NewsAppRelation>,
    ): SaveResult {
        if (articles.isEmpty()) return SaveResult(0, emptyList())
        return db.withTransaction {
            val dao = db.newsArticleDao()
            val rowIds = dao.insertIgnore(articles)
            val created = articles.filterIndexed { i, _ -> rowIds.getOrNull(i) != -1L }
            if (relations.isNotEmpty()) dao.insertRelations(relations)
            SaveResult(created.size, created.map { it.id })
        }
    }

    data class PagedNews(
        val articles: List<NewsArticle>,
        val total: Int,
        val page: Int,
        val pageSize: Int,
    )

    suspend fun list(filter: NewsFilter): PagedNews {
        val size = filter.pageSize.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
        val offset = (filter.page.coerceAtLeast(1) - 1) * size
        val q = filter.q?.takeIf { it.isNotBlank() }
        val rows = db.newsArticleDao().listFiltered(filter.main, filter.sub, q, size, offset)
        val total = db.newsArticleDao().countFiltered(filter.main, filter.sub, q)
        return PagedNews(rows, total, filter.page.coerceAtLeast(1), size)
    }

    data class NewsDetail(
        val article: NewsArticle,
        val sourceName: String,
        val relatedApps: List<com.borasarang.macjupjup.data.db.entity.App>,
    )

    suspend fun detail(id: String): NewsDetail? {
        val article = db.newsArticleDao().getById(id) ?: return null
        val appIds = db.newsArticleDao().getAppIdsByNewsId(id)
        val apps = if (appIds.isEmpty()) emptyList() else db.appDao().getByIds(appIds)
        return NewsDetail(article, article.sourceName, apps)
    }

    /** 대시보드 main별 최신 N건 */
    suspend fun recentByMain(main: String, limit: Int): List<NewsArticle> =
        db.newsArticleDao().recentByMain(main, limit.coerceIn(1, 20))

    /** main별 전체 건수 */
    suspend fun countByMain(): Map<String, Int> =
        db.newsArticleDao().countByMain().associate { it.name to it.cnt }

    /** 오늘 수집 건수 (히어로 LIVE) */
    suspend fun countToday(): Int {
        val dayStart = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return db.newsArticleDao().countCollectedSince(dayStart)
    }

    /** 보관기간(기본 30일) 초과 정리. 반환 = 삭제 기사 수 */
    suspend fun purge(retentionDays: Int = Constants.DEFAULT_RETENTION_DAYS): Int {
        val before = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000L
        val dao = db.newsArticleDao()
        dao.deleteRelationsOlderThan(before)
        return dao.deleteOlderThan(before)
    }
}
