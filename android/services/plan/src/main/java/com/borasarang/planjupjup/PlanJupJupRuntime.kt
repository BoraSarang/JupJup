package com.borasarang.planjupjup

import android.content.Context
import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.preferences.PreferencesManager
import com.borasarang.planjupjup.data.repository.NotificationRepository
import com.borasarang.planjupjup.data.repository.NotificationService
import com.borasarang.planjupjup.data.repository.PlanRepository
import com.borasarang.planjupjup.data.repository.SourceRepository
import com.borasarang.planjupjup.data.repository.StatsRepository
import com.borasarang.planjupjup.data.seed.InitialDataSeeder
import com.borasarang.planjupjup.server.HttpServerService
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.worker.CrawlScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 요금줍줍 서비스 런타임.
 *
 * Android Application은 앱당 1개 제약 때문에 통합 JupJup 앱은 단일 [com.borasarang.jupjup.JupJupApplication]만
 * 사용한다. 서비스별 Application이 수행하던 초기화·의존성 주입·백그라운드 작업은 이 object의
 * [initialize]로 이동했다. ViewModel/Fragment/Worker/HttpServerService는 Runtime 경유로 접근한다.
 */
object PlanJupJupRuntime {

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

    lateinit var database: PlanDatabase
        private set
    lateinit var planRepository: PlanRepository
        private set
    lateinit var sourceRepository: SourceRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var notificationService: NotificationService
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
        DebugLogger.i("앱", "알뜰요금줍줍 시작")

        database = try {
            PlanDatabase.getInstance(appContext)
        } catch (e: Exception) {
            // 마이그레이션 실패 등 DB 열기 불가 → 원본 백업 후 재생성 (P0-6: 무확인 삭제 방지)
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 열기 실패, 백업 후 재생성: ${e.message}", e)
            backupDatabaseFile()
            PlanDatabase.resetInstance()
            PlanDatabase.getInstanceFallback(appContext)
        }
        statsRepository = StatsRepository(database)
        planRepository = PlanRepository(database, statsRepository)
        sourceRepository = SourceRepository(database)
        notificationRepository = NotificationRepository(database)
        preferences = PreferencesManager.getInstance(appContext)
        notificationService = NotificationService(appContext, database, planRepository, sourceRepository, preferences)
        crawlScheduler = CrawlScheduler(appContext)

        appScope.launch(Dispatchers.IO) {
            try {
                InitialDataSeeder.seedIfEmpty(database)
            } catch (e: Exception) {
                DebugLogger.e("앱", "E-AND-DB-0403", "시드 실패: ${e.message}", e)
            }
            try {
                crawlScheduler.scheduleDailySummary()
            } catch (e: Exception) {
                DebugLogger.e("스케줄", "E-AND-CRAWL-0211", "데일리 스케줄 실패: ${e.message}", e)
            }
            val settings = try {
                preferences.getSettings()
            } catch (e: Exception) {
                DebugLogger.e("설정", "E-AND-DB-0404", "설정 조회 실패: ${e.message}", e)
                return@launch
            }
            if (settings.crawlEnabled) {
                try {
                    crawlScheduler.scheduleAll()
                } catch (e: Exception) {
                    DebugLogger.e("스케줄", "E-AND-CRAWL-0211", "수집 스케줄 실패: ${e.message}", e)
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
            val src = appContext.getDatabasePath(PlanDatabase.DB_NAME)
            if (!src.exists()) return
            val dir = java.io.File(appContext.filesDir, "db-backup").apply { mkdirs() }
            val dst = java.io.File(dir, "${PlanDatabase.DB_NAME}.${System.currentTimeMillis()}.bak")
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            DebugLogger.i("앱", "DB 백업 완료 ${dst.absolutePath} (${dst.length()}B)")
        } catch (e: Exception) {
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 백업 실패: ${e.message}", e)
        }
    }
}