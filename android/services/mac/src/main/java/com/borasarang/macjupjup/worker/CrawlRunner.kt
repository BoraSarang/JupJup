package com.borasarang.macjupjup.worker

import androidx.work.ListenableWorker
import com.borasarang.common.util.net.NetMeter
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.crawler.CrawlerFactory

/**
 * 소스 1건 수집 실행부 — CrawlWorker(즉시)·PipelineWorker(주기) 공용.
 * 락·슬롯·예산은 호출부가 관리한다.
 */
class CrawlRunner(
    private val app: MacJupJupRuntime,
    private val onStart: suspend (sourceName: String) -> Unit = {},
    private val attempt: () -> Int = { 1 },
    private val maxAttempts: Int = 3,
) {

    suspend fun execute(source: CrawlSource): ListenableWorker.Result {
        val sourceId = source.id
        onStart(source.name)
        app.sourceRepository.markRunning(sourceId)
        val startedAt = System.currentTimeMillis()
        val netBefore = NetMeter.snapshotFor("mac")

        if (source.type == Constants.TYPE_NEWS_RSS) {
            return runNewsCrawl(source, startedAt, netBefore)
        }
        if (source.type == Constants.TYPE_COMMUNITY_BOARD) {
            return runCommunityCrawl(source, startedAt, netBefore)
        }
        return try {
            val token = app.preferences.getSettings().githubToken
            val crawler = CrawlerFactory(app.database, token).create(source) { drafts ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    app.appRepository.saveApps(
                        drafts.map { it.app },
                        drafts.flatMap { it.mappings },
                    )
                }
            }
            val outcome = crawler.crawl()
            outcome.fold(
                onSuccess = { drafts ->
                    val apps = drafts.map { it.app }
                    val mappings = drafts.flatMap { it.mappings }
                    val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        app.appRepository.saveApps(apps, mappings)
                    }
                    val net = NetMeter.deltaSince("mac", netBefore)
                    app.sourceRepository.logResult(
                        sourceId = sourceId,
                        sourceName = source.name,
                        startedAt = startedAt,
                        status = Constants.STATUS_SUCCESS,
                        found = drafts.size,
                        created = saved.created,
                        updated = saved.updated,
                        error = null,
                        rxBytes = net.rxBytes,
                        txBytes = net.txBytes,
                    )
                    DebugLogger.i(
                        "수집",
                        "수집 완료 source=${source.name} found=${drafts.size} " +
                            "new=${saved.created} updated=${saved.updated}",
                    )
                    val newApps = if (saved.createdIds.isEmpty()) {
                        emptyList()
                    } else {
                        app.database.appDao().getByIds(saved.createdIds.take(50))
                    }
                    if (newApps.isNotEmpty()) {
                        app.notificationService.createNewAppsNotification(newApps)
                    }
                    app.notificationService.createCrawlCompleteNotification(
                        com.borasarang.macjupjup.data.repository.CrawlResult(
                            sourceName = source.name,
                            found = drafts.size,
                            created = saved.created,
                            updated = saved.updated,
                            startedAt = startedAt,
                        ),
                        newApps,
                    )
                    ListenableWorker.Result.success()
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val net = NetMeter.deltaSince("mac", netBefore)
                    fail(source, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
                    retryOrFail()
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            DebugLogger.w("수집", "수집 취소(CancellationException) source=${source.name}")
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                app.sourceRepository.clearRunning(sourceId)
            }
            throw e
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(source, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            retryOrFail()
        }
    }

    private suspend fun runNewsCrawl(
        source: CrawlSource,
        startedAt: Long,
        netBefore: com.borasarang.common.util.net.NetUsage,
    ): ListenableWorker.Result {
        val sourceId = source.id
        val sourceName = source.name
        return try {
            val crawler = com.borasarang.macjupjup.crawler.news.NewsRssCrawler(source, app.database)
            val outcome = crawler.crawlNews().getOrThrow()
            val saved = app.newsRepository.saveArticles(outcome.articles, outcome.relations)
            val net = NetMeter.deltaSince("mac", netBefore)
            app.sourceRepository.logResult(
                sourceId = sourceId,
                sourceName = sourceName,
                startedAt = startedAt,
                status = Constants.STATUS_SUCCESS,
                found = outcome.articles.size,
                created = saved.created,
                updated = 0,
                error = null,
                rxBytes = net.rxBytes,
                txBytes = net.txBytes,
            )
            DebugLogger.i(
                "뉴스수집",
                "수집 완료 source=$sourceName found=${outcome.articles.size} new=${saved.created}",
            )
            if (saved.createdIds.isNotEmpty()) {
                val newNews = app.database.newsArticleDao().getByIds(saved.createdIds.take(50))
                if (newNews.isNotEmpty()) {
                    app.notificationService.createNewNewsNotification(newNews)
                }
            }
            app.notificationService.createCrawlCompleteNotification(
                com.borasarang.macjupjup.data.repository.CrawlResult(
                    sourceName = sourceName,
                    found = outcome.articles.size,
                    created = saved.created,
                    updated = 0,
                    startedAt = startedAt,
                ),
                emptyList(),
            )
            try {
                val purged = app.newsRepository.purge()
                if (purged > 0) DebugLogger.i("뉴스수집", "보관기간 정리 ${purged}건")
            } catch (e: Exception) {
                DebugLogger.w("뉴스수집", "정리 스킵: ${e.message}")
            }
            ListenableWorker.Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(source, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            retryOrFail()
        }
    }

    private suspend fun runCommunityCrawl(
        source: CrawlSource,
        startedAt: Long,
        netBefore: com.borasarang.common.util.net.NetUsage,
    ): ListenableWorker.Result {
        val sourceId = source.id
        val sourceName = source.name
        return try {
            val crawler = com.borasarang.macjupjup.crawler.community.CommunityBoardCrawler(source, app.database)
            val outcome = crawler.crawl().getOrThrow()
            val net = NetMeter.deltaSince("mac", netBefore)
            app.sourceRepository.logResult(
                sourceId = sourceId,
                sourceName = sourceName,
                startedAt = startedAt,
                status = Constants.STATUS_SUCCESS,
                found = outcome.posts.size,
                created = outcome.created,
                updated = 0,
                error = null,
                rxBytes = net.rxBytes,
                txBytes = net.txBytes,
            )
            DebugLogger.i(
                "커뮤니티수집",
                "수집 완료 source=$sourceName found=${outcome.posts.size} new=${outcome.created}",
            )
            app.notificationService.createCrawlCompleteNotification(
                com.borasarang.macjupjup.data.repository.CrawlResult(
                    sourceName = sourceName,
                    found = outcome.posts.size,
                    created = outcome.created,
                    updated = 0,
                    startedAt = startedAt,
                ),
                emptyList(),
            )
            try {
                val purged = app.communityRepository.purge()
                if (purged > 0) DebugLogger.i("커뮤니티수집", "보관기간 정리 ${purged}건")
            } catch (e: Exception) {
                DebugLogger.w("커뮤니티수집", "정리 스킵: ${e.message}")
            }
            ListenableWorker.Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(source, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            retryOrFail()
        }
    }

    private fun retryOrFail(): ListenableWorker.Result {
        return if (attempt() >= maxAttempts) {
            ListenableWorker.Result.failure()
        } else {
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun fail(
        source: CrawlSource,
        startedAt: Long,
        message: String,
        rxBytes: Long = 0L,
        txBytes: Long = 0L,
    ) {
        app.sourceRepository.logResult(
            sourceId = source.id,
            sourceName = source.name,
            startedAt = startedAt,
            status = Constants.STATUS_FAILED,
            found = 0,
            created = 0,
            updated = 0,
            error = message,
            rxBytes = rxBytes,
            txBytes = txBytes,
        )
        DebugLogger.e("수집", "E-AND-CRAWL-0201", "수집 실패 source=${source.name}: $message")
        checkFailureStreak(source.id, source.name, message)
    }

    private suspend fun checkFailureStreak(sourceId: String, sourceName: String, error: String) {
        try {
            val statuses = app.sourceRepository.getRecentStatuses(sourceId, 5)
            if (!com.borasarang.common.util.CrawlStats.isFailureStreak(statuses)) return
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속 5회 수집 실패 source=$sourceName")
            app.notificationService.createFailureNotification(sourceName, error, 5)
        } catch (e: Exception) {
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속실패 기록 실패 source=$sourceName: ${e.message}", e)
        }
    }
}
