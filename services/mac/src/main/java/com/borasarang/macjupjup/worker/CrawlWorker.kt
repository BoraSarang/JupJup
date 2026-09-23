package com.borasarang.macjupjup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.crawler.CrawlerFactory
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.common.util.NetMeter
import com.borasarang.common.util.NetUtils
import com.borasarang.common.worker.SourceLocks

/**
 * 소스 1건 수집 워커. 장시간 실행 대비 setForeground 사용.
 * 성공/실패 모두 crawl_logs 기록 + 소스 상태 갱신. 실패는 Result.retry.
 * 알림 생성은 M5 (NotificationService 연동 시).
 */
class CrawlWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID)
        if (sourceId.isNullOrBlank()) {
            return Result.failure()
        }
        val app = MacJupJupRuntime
        val source = app.sourceRepository.getById(sourceId)
        if (source == null) {
            DebugLogger.e("수집", "E-AND-CRAWL-0201", "소스 없음 id=$sourceId")
            return Result.failure()
        }
        if (!source.enabled) {
            return Result.success()
        }
        if (!NetUtils.isConnected(applicationContext)) {
            DebugLogger.w("수집", "네트워크 끊김 — 연기 source=${source.name} (E-AND-NET-0301)")
            return Result.retry()
        }

        // P0-2: 동일 소스 중복 실행 방지 (주기+즉시 겹침 시 스킵).
        // 시작 로그는 선점 성공 후에만 찍는다 (REPLACE 취소된 워커와 구분).
        if (!SourceLocks.tryAcquire(sourceId)) {
            DebugLogger.w("수집", "워커 스킵(이미 실행 중) source=${source.name}")
            return Result.success()
        }
        DebugLogger.i("수집", "워커 시작 source=${source.name}")
        try {
            return runCrawl(app, sourceId, source)
        } catch (e: Exception) {
            // REPLACE 취소 등 비정상 종료도 식별되게 기록
            DebugLogger.w("수집", "워커 종료(${e.javaClass.simpleName}) source=${source.name}")
            if (e is kotlinx.coroutines.CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    app.sourceRepository.clearRunning(sourceId)
                }
            }
            throw e
        } finally {
            SourceLocks.release(sourceId)
        }
    }

    private suspend fun runCrawl(
        app: MacJupJupRuntime,
        sourceId: String,
        source: com.borasarang.macjupjup.data.db.entity.CrawlSource,
    ): Result {
        app.sourceRepository.markRunning(sourceId)
        setForeground(createForegroundInfo(source.name))
        val startedAt = System.currentTimeMillis()
        val netBefore = NetMeter.snapshotFor("mac")

        // R32: 뉴스 RSS는 별도 경로 (AppDraft 미사용, NewsRepository 저장)
        if (source.type == Constants.TYPE_NEWS_RSS) {
            return runNewsCrawl(app, source, startedAt)
        }
        // PLAN_v23: 커뮤니티 보드는 별도 경로 (CommunityBoardCrawler)
        if (source.type == Constants.TYPE_COMMUNITY_BOARD) {
            return runCommunityCrawl(app, source, startedAt)
        }
        return try {
            val token = app.preferences.getSettings().githubToken
            // 체크포인트: 상세 보강 중 워커 취소 시에도 본문 진행량을 NonCancellable로 적재
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
                    // 저장 직전 취소로 결과 전건 유실 방지 (JupJup-dui)
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
                        "워커 완료 source=${source.name} found=${drafts.size} " +
                            "new=${saved.created} updated=${saved.updated}",
                    )
                    // 알림 생성 — 저장된 id 기준 실제 조회 (상위 50건만, IN 배치 1회)
                    val newApps = if (saved.createdIds.isEmpty()) {
                        emptyList()
                    } else {
                        app.database.appDao().getByIds(saved.createdIds.take(50))
                    }
                    if (newApps.isNotEmpty()) {
                        app.notificationService.createNewAppsNotification(newApps)
                    }
                    // 한글 번역은 TranslateWorker(별도 주기)가 담당 — 수집 경로 차단 금지
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
                    Result.success()
                },
                onFailure = { e ->
                    val net = NetMeter.deltaSince("mac", netBefore)
                    fail(app, sourceId, source.name, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
                    Result.retry()
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            DebugLogger.w("수집", "워커 취소(CancellationException) source=${source.name} — 체크포인트 본문은 유지")
            // RUNNING 고착 방지: 이전 종료 상태로 복원 (실패 스트릭 오염 금지)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                app.sourceRepository.clearRunning(sourceId)
            }
            throw e
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(app, sourceId, source.name, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            Result.retry()
        }
    }

    /**
     * 뉴스 수집 경로 (R32 PLAN_v17).
     * RSS → 저장(중복 IGNORE) → 결과 기록. 앱 알림은 생략, 완료 알림만 공통으로 발송.
     */
    private suspend fun runNewsCrawl(
        app: MacJupJupRuntime,
        source: com.borasarang.macjupjup.data.db.entity.CrawlSource,
        startedAt: Long,
    ): Result {
        val sourceId = source.id
        val sourceName = source.name
        val netBefore = NetMeter.snapshotFor("mac")
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
                "워커 완료 source=$sourceName found=${outcome.articles.size} new=${saved.created}",
            )
            // R39: 뉴스 수집 알림 (신규 + 완료 — 기존에는 알림이 전혀 없었음)
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
                // 뉴스 경로에는 App 엔티티 없음 — 건수는 detail copy로 반영, 목록은 신규 뉴스 알림이 담당
                emptyList(),
            )
            // 보관기간 초과분 정리 (기사 + 연동행)
            try {
                val purged = app.newsRepository.purge()
                if (purged > 0) DebugLogger.i("뉴스수집", "보관기간 정리 ${purged}건")
            } catch (e: Exception) {
                DebugLogger.w("뉴스수집", "정리 스킵: ${e.message}")
            }
            Result.success()
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(app, sourceId, sourceName, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            Result.retry()
        }
    }

    /**
     * 커뮤니티 보드 수집 (PLAN_v23).
     * 목록 + 신규 상세 → community_posts 저장, 로그·완료 알림.
     */
    private suspend fun runCommunityCrawl(
        app: MacJupJupRuntime,
        source: com.borasarang.macjupjup.data.db.entity.CrawlSource,
        startedAt: Long,
    ): Result {
        val sourceId = source.id
        val sourceName = source.name
        val netBefore = NetMeter.snapshotFor("mac")
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
                "워커 완료 source=$sourceName found=${outcome.posts.size} new=${outcome.created}",
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
            Result.success()
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("mac", netBefore)
            fail(app, sourceId, sourceName, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            Result.retry()
        }
    }

    /**
     * 한글 번역 (ML Kit 온디바이스). 신규·갱신 draft만, 실행당 최대 15건.
     * 설정 꺼짐·모델 없음·실패 시 조용히 스킵 (수집 성공에 영향 없음).
     * 참고: 현재 TranslateWorker(별도 주기)가 담당 — 본 함수는 미사용, 하위 호환 유지.
     */
    @Suppress("unused")
    private suspend fun translateNewOrUpdated(
        app: MacJupJupRuntime,
        apps: List<com.borasarang.macjupjup.data.db.entity.App>,
    ) {
        try {
            if (!app.preferences.getSettings().translateKo) return
            val targets = apps.filter { a ->
                (a.descriptionSnippet != null && a.descriptionKo == null) ||
                    (a.releaseNotes != null && a.releaseNotesKo == null)
            }.take(MAX_TRANSLATE_PER_RUN)
            if (targets.isEmpty()) return
            var done = 0
            for (a in targets) {
                val current = app.database.appDao().getById(a.id) ?: continue
                val descKo = current.descriptionSnippet
                    ?.takeIf { current.descriptionKo == null }
                    ?.let { com.borasarang.macjupjup.util.MacTranslator.translateAutoToKo(it) }
                val notesKo = (current.releaseNotes ?: current.releaseNotesSummary)
                    ?.takeIf { current.releaseNotesKo == null }
                    ?.let { com.borasarang.macjupjup.util.MacTranslator.translateAutoToKo(it) }
                if (descKo != null || notesKo != null) {
                    app.database.appDao().updateKo(a.id, descKo, notesKo)
                    done++
                }
            }
            DebugLogger.i("번역", "한글 번역 완료 $done/${targets.size}건")
        } catch (e: Exception) {
            DebugLogger.w("번역", "번역 스킵: ${e.message}")
        }
    }

    private suspend fun fail(
        app: MacJupJupRuntime,
        sourceId: String,
        sourceName: String,
        startedAt: Long,
        message: String,
        rxBytes: Long = 0L,
        txBytes: Long = 0L,
    ) {
        app.sourceRepository.logResult(
            sourceId = sourceId,
            sourceName = sourceName,
            startedAt = startedAt,
            status = Constants.STATUS_FAILED,
            found = 0,
            created = 0,
            updated = 0,
            error = message,
            rxBytes = rxBytes,
            txBytes = txBytes,
        )
        DebugLogger.e("수집", "E-AND-CRAWL-0201", "워커 실패 source=$sourceName: $message")
        checkFailureStreak(app, sourceId, sourceName, message)
    }

    /** 5연속 실패 시 DB 알림 저장 (E-AND-CRAWL-0204) */    private suspend fun checkFailureStreak(
        app: MacJupJupRuntime,
        sourceId: String,
        sourceName: String,
        error: String,
    ) {
        try {
            val statuses = app.sourceRepository.getRecentStatuses(sourceId, 5)
            if (!com.borasarang.common.util.CrawlStats.isFailureStreak(statuses)) return
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속 5회 수집 실패 source=$sourceName")
            app.notificationService.createFailureNotification(sourceName, error, 5)
        } catch (e: Exception) {
            // R6: 실패 기록 자체가 삼켜지면 원인 추적 불가
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속실패 기록 실패 source=$sourceName: ${e.message}", e)
        }
    }

    private fun createForegroundInfo(sourceName: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.mac_notif_crawl_running))
            .setContentText(sourceName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
                notification,
            )
        }
    }

    private fun ensureChannel() {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    // R6: 채널명 리소스화
                    applicationContext.getString(R.string.mac_notif_channel_crawl),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        private const val MAX_TRANSLATE_PER_RUN = 15
        private const val CHANNEL_ID = "macjupjup_crawl"
    }
}
