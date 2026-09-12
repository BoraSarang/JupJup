package com.borasarang.macjupjup

import android.content.Context
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.preferences.PreferencesManager
import com.borasarang.macjupjup.data.repository.AppRepository
import com.borasarang.macjupjup.data.repository.NotificationRepository
import com.borasarang.macjupjup.data.repository.NotificationService
import com.borasarang.macjupjup.data.repository.SourceRepository
import com.borasarang.macjupjup.data.seed.InitialDataSeeder
import com.borasarang.macjupjup.server.HttpServerService
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.worker.CrawlScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 맥줍줍 서비스 런타임.
 *
 * Android Application은 앱당 1개 제약 때문에 통합 JupJup 앱은 단일 [com.borasarang.jupjup.JupJupApplication]만
 * 사용한다. 서비스별 Application이 수행하던 초기화·의존성 주입·백그라운드 작업은 이 object의
 * [initialize]로 이동했다. ViewModel/Fragment/Worker/HttpServerService는 Runtime 경유로 접근한다.
 */
object MacJupJupRuntime {

    private val initLock = Any()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: MacDatabase
        private set
    lateinit var appRepository: AppRepository
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
        DebugLogger.init()
        DebugLogger.i("앱", "맥줍줍 시작")

        database = try {
            MacDatabase.getInstance(appContext)
        } catch (e: Exception) {
            // 마이그레이션 실패 등 DB 열기 불가 → 원본 백업 후 재생성 (P0-6: 무확인 삭제 방지)
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 열기 실패, 백업 후 재생성: ${e.message}", e)
            backupDatabaseFile()
            MacDatabase.resetInstance()
            MacDatabase.getInstanceFallback(appContext)
        }
        appRepository = AppRepository(database)
        sourceRepository = SourceRepository(database)
        preferences = PreferencesManager.getInstance(appContext)
        notificationService = NotificationService(database, preferences)
        notificationRepository = NotificationRepository(database)
        crawlScheduler = CrawlScheduler(appContext)

        appScope.launch(Dispatchers.IO) {
            InitialDataSeeder.seedIfEmpty(database)
            // Setapp 수집처 완전 제거 (사용자 결정): 예약 취소 → 소스행·매핑·고아앱·로그 삭제
            try {
                crawlScheduler.cancelSource(REMOVED_SOURCE_SETAPP)
                if (sourceRepository.removeSource(REMOVED_SOURCE_SETAPP) > 0) {
                    appRepository.purgeSource(REMOVED_SOURCE_SETAPP, REMOVED_SOURCE_SETAPP_NAME)
                }
            } catch (e: Exception) {
                DebugLogger.w("정리", "Setapp 제거 스킵: ${e.message}")
            }
            // T-161: PH·HN·MMB 수집처 완전 제거 (버전 추적 불가): Setapp과 동일 패턴
            for ((id, name) in REMOVED_SOURCES_V16) {
                try {
                    crawlScheduler.cancelSource(id)
                    if (sourceRepository.removeSource(id) > 0) {
                        appRepository.purgeSource(id, name)
                    }
                } catch (e: Exception) {
                    DebugLogger.w("정리", "수집처 제거 스킵 source=$id: ${e.message}")
                }
            }
            // isNew 정책: 7일 경과 NEW 해제 (와치리스트 진입)
            try {
                appRepository.clearStaleNewFlags()
            } catch (e: Exception) {
                DebugLogger.w("정리", "NEW 해제 스킵: ${e.message}")
            }
            // v1.1 1회성 복구: 콤마 구분자로 깨진 CDN 스크린샷 정리 (다음 lookup/상세 수집에서 복원)
            try {
                val cleared = database.appDao().clearBrokenCdnScreenshots()
                if (cleared > 0) DebugLogger.i("복구", "깨진 스크린샷 정리 ${cleared}건")
            } catch (e: Exception) {
                DebugLogger.w("복구", "스크린샷 정리 스킵: ${e.message}")
            }
            crawlScheduler.scheduleAll(database)
            crawlScheduler.scheduleDailySummary()
            crawlScheduler.scheduleTranslate()
            val settings = preferences.getSettings()
            if (settings.autoStart) {
                DebugLogger.i("앱", "자동 시작 설정 켜짐 — 서버 시작")
                HttpServerService.start(appContext)
            } else {
                DebugLogger.i("앱", "자동 시작 꺼짐 — 서버 미시작")
            }
        }
    }

    const val REMOVED_SOURCE_SETAPP = "setapp_seed"
    const val REMOVED_SOURCE_SETAPP_NAME = "Setapp 베이스라인"

    /** T-161 제거된 수집처 (PH·HN·MMB, 버전 추적 불가). id → 표시명 */
    val REMOVED_SOURCES_V16 = listOf(
        "producthunt" to "Product Hunt mac 토픽",
        "hn_show" to "Hacker News Show HN",
        "macmenubar" to "MacMenuBar 신규",
    )

    /** 파괴 폴백 전 원본 DB 백업 (files/db-backup/). 실패해도 재생성은 진행 */
    private fun backupDatabaseFile() {
        try {
            val src = appContext.getDatabasePath(MacDatabase.DB_NAME)
            if (!src.exists()) return
            val dir = java.io.File(appContext.filesDir, "db-backup").apply { mkdirs() }
            val dst = java.io.File(dir, "${MacDatabase.DB_NAME}.${System.currentTimeMillis()}.bak")
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            DebugLogger.i("앱", "DB 백업 완료 ${dst.absolutePath} (${dst.length()}B)")
        } catch (e: Exception) {
            DebugLogger.e("앱", "E-AND-DB-0403", "DB 백업 실패: ${e.message}", e)
        }
    }
}