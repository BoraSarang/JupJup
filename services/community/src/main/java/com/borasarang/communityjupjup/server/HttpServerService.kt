package com.borasarang.communityjupjup.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.communityjupjup.R
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.common.server.escapeJson
import com.borasarang.common.util.NetUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.plugins.statuspages.StatusPages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * Ktor CIO 임베디드 서버를 품은 포그라운드 서비스 (type=dataSync).
 * - START_STICKY: 시스템이 죽여도 재시작
 * - onCreate 최상단 startForeground (5초 룰)
 * - Watchdog: 주기적 로컬 포트 헬스체크로 무응답 시 자동 재시작
 * - 정적 포털은 assets/web에서 수동 서빙 (assets는 classpath가 아님)
 * - JSON은 kotlinx-serialization JsonElement 빌더 사용 (플러그인 불필요)
 *
 * M1 범위: health/apps/detail/watchlist/sync(pending)/stats/settings.
 * /api/sync 실제 수집 연동·통계 대시보드는 M2/M4에서 확장.
 */
class HttpServerService : Service() {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var watchdogJob: Job? = null

    @Volatile
    internal var currentPort: Int = Constants.DEFAULT_PORT

    @Volatile
    private var isForeground = false

    /** R5: Main 스레드 getLocalIp 금지 → IO에서 갱신된 캐시만 runningText가 사용 */
    @Volatile
    private var cachedIp: String? = null

    /** 수동 시드 백그라운드 상태 (idle/running/done …/not_found/error …) */
    @Volatile
    internal var lastSeedStatus: String = "idle"

    /** R6: 시드 시작 시각 — 상태 running의 신선도 판단용 */
    @Volatile
    internal var lastSeedStartedAt: Long = 0L

    /**
     * R7: 시드 상태 갱신 — 메모리 변수는 읽기 캐시, DataStore가 진실.
     * startedAt 미지정 시 기존값 유지 (running 진입 시만 호출자가 현재 시각 전달).
     */
    internal fun setSeedState(status: String, startedAt: Long = lastSeedStartedAt) {
        lastSeedStatus = status
        lastSeedStartedAt = startedAt
        scope.launch {
            try {
                app().preferences.saveSeedState(status, startedAt)
            } catch (e: Exception) {
                DebugLogger.e("수동시드", "E-AND-DB-0402", "시드 상태 저장 실패: ${e.message}", e)
            }
        }
    }

    /** R7: 재시작 시 최종 시드 결과 복원. running 유령 상태는 idle로 정정 */
    internal suspend fun restoreSeedState() {
        try {
            val (status, startedAt) = app().preferences.getSeedState()
            if (status == "running") {
                lastSeedStatus = "idle"
                lastSeedStartedAt = 0L
                app().preferences.saveSeedState("idle", 0L)
            } else {
                lastSeedStatus = status
                lastSeedStartedAt = startedAt
            }
        } catch (e: Exception) {
            DebugLogger.e("수동시드", "E-AND-DB-0404", "시드 상태 복원 실패: ${e.message}", e)
        }
    }

    internal fun app(): CommunityJupJupRuntime = CommunityJupJupRuntime

    override fun onCreate() {
        super.onCreate()
        DebugLogger.i("서버", "서비스 생성")
        createChannel()
        // FGS 승격을 가장 먼저 시도 — startForegroundService 경로의 5초 의무 창 확보
        startInForeground(getString(R.string.cm_notif_server_starting))
        scope.launch {
            try {
                restoreSeedState()
                val settings = app().preferences.getSettings()
                currentPort = settings.port
                startServer(settings.port)
                cachedIp = NetUtils.getLocalIp(this@HttpServerService)
                updateNotification(runningText(settings.port))
                startWatchdog()
                DebugLogger.i("서버", "서버 기동 완료 port=${settings.port}")
            } catch (e: Exception) {
                DebugLogger.e("서버", "E-AND-SRV-0103", "서버 기동 실패: ${e.message}", e)
                updateNotification("서버 기동 실패: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 재시작/재실행 시 알림 복구 (이미 포그라운드면 no-op)
        startInForeground()
        if (intent?.action == ACTION_RESTART) {
            scope.launch { restartServer() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        DebugLogger.i("서버", "서비스 종료 — 서버 정리")
        watchdogJob?.cancel()
        watchdogJob = null
        try {
            server?.stop(1000, 2000)
        } catch (_: Exception) {
        }
        server = null
        scope.cancel()
        super.onDestroy()
    }

    /** 사용자가 최근 앱에서 스와이프 종료해도 DB는 이미 정합 — 로그만 남긴다 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        DebugLogger.i("서버", "onTaskRemoved — START_STICKY로 시스템이 재시작 예정")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?) = null

    // ---------- 서버 생명주기 ----------

    internal suspend fun restartServer() {
        val settings = app().preferences.getSettings()
        DebugLogger.i("서버", "서버 재시작 port=$currentPort → ${settings.port}")
        try {
            server?.stop(1000, 2000)
        } catch (_: Exception) {
        }
        server = null
        currentPort = settings.port
        try {
            startServer(settings.port)
            cachedIp = NetUtils.getLocalIp(this@HttpServerService)
            updateNotification(runningText(settings.port))
        } catch (e: Exception) {
            DebugLogger.e("서버", "E-AND-SRV-0104", "서버 재시작 실패: ${e.message}", e)
            updateNotification("서버 재시작 실패: ${e.message}")
        }
    }

    private fun startServer(port: Int) {
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            com.borasarang.common.server.LanGuard.install(this)
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    DebugLogger.e(
                        "서버",
                        "E-AND-SRV-0103",
                        "API 오류 ${call.request.local.uri}: ${cause.message}",
                        cause,
                    )
                    call.respondText(
                        """{"error":"${escapeJson(cause.message ?: "internal error")}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }
            routing {
                cmAssetRoutes(this)
                cmItemRoutes(this)
                cmCollectRoutes(this)
                cmStatsRoutes(this)
                cmNotifRoutes(this)
                cmSettingsRoutes(this)
                cmBoardRoutes(this)
            }
        }.start(wait = false)
    }

    // ---------- Watchdog ----------

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            DebugLogger.i("서버", "Watchdog 시작")
            while (true) {
                val intervalSec = try {
                    app().preferences.getSettings().watchdogIntervalSec
                } catch (_: Exception) {
                    Constants.DEFAULT_WATCHDOG_INTERVAL_SEC
                }
                delay(intervalSec * 1000L)
                if (server == null || !isPortOpen(currentPort)) {
                    DebugLogger.w("서버", "Watchdog: 무응답 감지 → 자동 재시작")
                    restartServer()
                }
            }
        }
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            Socket("127.0.0.1", port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    // ---------- 포그라운드 알림 ----------

    private fun startInForeground(text: String? = null) {
        if (isForeground) return
        try {
            val notification = buildNotification(text ?: runningText(currentPort))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Constants.NOTIFICATION_ID_SERVER,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID_SERVER, notification)
            }
            isForeground = true
            DebugLogger.i("서버", "포그라운드 승격 완료")
        } catch (e: Exception) {
            // Android 12+ 백그라운드 시작 제한 — 무시하고 백그라운드로 계속 동작
            DebugLogger.e("서버", "E-AND-SRV-0101", "FGS 승격 거부, 백그라운드 유지: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Constants.NOTIFICATION_ID_SERVER, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun runningText(port: Int): String {
        val ip = cachedIp ?: "IP 확인 중"
        return getString(R.string.cm_notif_server_running) + " http://$ip:$port"
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, Constants.CHANNEL_ID_SERVER)
            .setContentTitle(getString(R.string.cm_app_name_full))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    Constants.CHANNEL_ID_SERVER,
                    getString(R.string.cm_notif_channel_server),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_RESTART = "com.borasarang.communityjupjup.RESTART_SERVER"

        fun start(context: Context) {
            // startForegroundService는 5초 내 startForeground 의무이므로
            // 일반 startService 우선 + 실패 시 FGS 폴백
            try {
                context.startService(Intent(context, HttpServerService::class.java))
            } catch (e: IllegalStateException) {
                DebugLogger.w("서버", "startService 거부 — FGS 재시도: ${e.message}")
                runCatching {
                    context.startForegroundService(Intent(context, HttpServerService::class.java))
                }.onFailure {
                    DebugLogger.e("서버", "E-AND-SRV-0102", "FGS 시작 실패: ${it.message}")
                }
            }
        }

        /** 이미 실행 중인 서버를 새 포트로 재시작 (포트 변경 적용) */
        fun restart(context: Context) {
            try {
                context.startService(
                    Intent(context, HttpServerService::class.java).setAction(ACTION_RESTART),
                )
            } catch (e: IllegalStateException) {
                DebugLogger.w("서버", "restartService 거부 — FGS 재시도: ${e.message}")
                runCatching {
                    context.startForegroundService(
                        Intent(context, HttpServerService::class.java).setAction(ACTION_RESTART),
                    )
                }.onFailure {
                    DebugLogger.e("서버", "E-AND-SRV-0102", "FGS 재시작 실패: ${it.message}")
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpServerService::class.java))
        }
    }
}
