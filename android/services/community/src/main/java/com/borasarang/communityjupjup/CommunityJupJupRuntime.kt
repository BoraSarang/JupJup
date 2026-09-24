package com.borasarang.communityjupjup

import android.content.Context
import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.preferences.PreferencesManager
import com.borasarang.communityjupjup.data.repository.CommunityRepository
import com.borasarang.communityjupjup.data.repository.NotificationRepository
import com.borasarang.communityjupjup.data.repository.NotificationService
import com.borasarang.communityjupjup.data.repository.SourceRepository
import com.borasarang.communityjupjup.data.seed.InitialDataSeeder
import com.borasarang.communityjupjup.server.HttpServerService
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.worker.CrawlScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 커뮤니티 뉴스 서비스 런타임.
 *
 * Android Application은 앱당 1개 제약 때문에 통합 JupJup 앱은 단일 [com.borasarang.jupjup.JupJupApplication]만
 * 사용한다. 서비스별 Application이 수행하던 초기화는 이 object의 [initialize]로 이동했다.
 */
object CommunityJupJupRuntime {

    private val initLock = Any()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    val appScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, e ->
                DebugLogger.e("앱", "E-AND-SRV-0103", "appScope 미처리 예외: ${e.message}", e)
            },
    )

    lateinit var database: CommunityDatabase
        private set
    lateinit var communityRepository: CommunityRepository
        private set
    lateinit var sourceRepository: SourceRepository
        private set
    lateinit var notificationService: NotificationService
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var preferences: PreferencesManager
        private set
    lateinit var crawlScheduler: CrawlScheduler
        private set

    val context: Context
        get() = appContext

    val isInitialized: Boolean
        get() = initialized

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            initialized = true
        }
        DebugLogger.init(appContext)
        DebugLogger.i("앱", "커뮤니티줍줍 시작")

        database = try {
            CommunityDatabase.getInstance(appContext)
        } catch (e: Exception) {
            // DB 열기 불가 → 원본 백업 후 재생성 (P0-6: 무확인 삭제 방지)
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 열기 실패, 백업 후 재생성: ${e.message}", e)
            backupDatabaseFile()
            CommunityDatabase.resetInstance()
            CommunityDatabase.getInstanceFallback(appContext)
        }
        communityRepository = CommunityRepository(database)
        sourceRepository = SourceRepository(database)
        preferences = PreferencesManager.getInstance(appContext)
        notificationService = NotificationService(appContext, database, preferences)
        notificationRepository = NotificationRepository(database)
        crawlScheduler = CrawlScheduler(appContext)

        appScope.launch(Dispatchers.IO) {
            try {
                InitialDataSeeder.seedIfEmpty(database)
            } catch (e: Exception) {
                DebugLogger.e("앱", "E-AND-DB-0403", "시드 실패: ${e.message}", e)
            }
            // TTL 정리 (핫딜 3일·중고 7일·그 외 retentionDays)
            try {
                val retention = preferences.getSettings().retentionDays
                val purged = communityRepository.purgeExpired(retention)
                if (purged > 0) DebugLogger.i("정리", "만료 게시글 정리 ${purged}건")
            } catch (e: Exception) {
                DebugLogger.w("정리", "TTL 정리 스킵: ${e.message}")
            }
            try {
                crawlScheduler.scheduleDailySummary()
            } catch (e: Exception) {
                DebugLogger.e("스케줄", "E-AND-CRAWL-0201", "데일리 스케줄 실패: ${e.message}", e)
            }
            val settings = try {
                preferences.getSettings()
            } catch (e: Exception) {
                DebugLogger.e("설정", "E-AND-DB-0404", "설정 조회 실패: ${e.message}", e)
                return@launch
            }
            // R27: 보드 단위 스케줄 1회 전환 (구 소스 작업명 잔재 정리)
            try {
                if (!preferences.isWorkV4Done()) {
                    crawlScheduler.cancelAll()
                    preferences.setWorkV4Done()
                    DebugLogger.i("스케줄", "보드 단위 스케줄로 1회 전환")
                }
            } catch (e: Exception) {
                DebugLogger.w("스케줄", "V4 전환 스킵: ${e.message}")
            }
            if (settings.crawlEnabled) {
                try {
                    crawlScheduler.scheduleAll()
                } catch (e: Exception) {
                    DebugLogger.e("스케줄", "E-AND-CRAWL-0201", "수집 스케줄 실패: ${e.message}", e)
                }
            } else {
                DebugLogger.i("수집", "수집 일시정지 상태 — 주기 스케줄 생략")
            }
            if (settings.autoStart) {
                DebugLogger.i("앱", "자동 시작 설정 켜짐 — 서버 시작")
                HttpServerService.start(appContext)
            } else {
                DebugLogger.i("앱", "자동 시작 꺼짐 — 서버 미시작")
            }
        }
    }

    /** 파괴 폴백 전 원본 DB 백업 (files/db-backup/). 실패해도 재생성은 진행 */
    private fun backupDatabaseFile() {
        try {
            val src = appContext.getDatabasePath(CommunityDatabase.DB_NAME)
            if (!src.exists()) return
            val dir = java.io.File(appContext.filesDir, "db-backup").apply { mkdirs() }
            val dst = java.io.File(dir, "${CommunityDatabase.DB_NAME}.${System.currentTimeMillis()}.bak")
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            DebugLogger.i("앱", "DB 백업 완료 ${dst.absolutePath} (${dst.length()}B)")
        } catch (e: Exception) {
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 백업 실패: ${e.message}", e)
        }
    }
}
