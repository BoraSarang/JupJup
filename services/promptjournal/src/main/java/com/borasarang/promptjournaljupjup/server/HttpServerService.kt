package com.borasarang.promptjournaljupjup.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.R
import com.borasarang.promptjournaljupjup.Constants
import com.borasarang.promptjournaljupjup.util.DebugLogger
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
import kotlinx.coroutines.launch
import java.net.Socket

class HttpServerService : Service() {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var watchdogJob: Job? = null

    @Volatile
    internal var currentPort: Int = Constants.DEFAULT_PORT

    @Volatile
    private var isForeground = false

    @Volatile
    private var cachedIp: String? = null

    internal fun app(): PromptJournalRuntime = PromptJournalRuntime

    override fun onCreate() {
        super.onCreate()
        DebugLogger.i("서버", "프롬프트 저널 서버 생성")
        createChannel()
        startInForeground(getString(R.string.pj_notif_server_starting))
        scope.launch {
            try {
                val settings = app().preferences.getSettings()
                currentPort = settings.port
                startServer(settings.port)
                cachedIp = NetUtils.getLocalIp(this@HttpServerService)
                updateNotification(runningText(settings.port))
                startWatchdog()
                DebugLogger.i("서버", "서버 기동 완료 port=${settings.port}")
            } catch (e: Exception) {
                DebugLogger.e("서버", Constants.ERR_AI_CALL_FAILED, "서버 기동 실패: ${e.message}", e)
                updateNotification("서버 기동 실패: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        DebugLogger.i("서버", "onTaskRemoved — START_STICKY로 시스템이 재시작 예정")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?) = null

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
            DebugLogger.e("서버", Constants.ERR_AI_CALL_FAILED, "서버 재시작 실패: ${e.message}", e)
            updateNotification("서버 재시작 실패: ${e.message}")
        }
    }

    private fun startServer(port: Int) {
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            com.borasarang.common.server.LanGuard.install(this)
            com.borasarang.common.server.AdminAuth.install(this,
                { app().preferences.getAdminCredential() },
                { app().preferences.getAdminToken() })
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    DebugLogger.e(
                        "서버",
                        Constants.ERR_AI_CALL_FAILED,
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
                pjAssetRoutes(this)
                pjRoutes(this)
            }
        }.start(wait = false)
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            DebugLogger.i("서버", "Watchdog 시작")
            while (true) {
                kotlinx.coroutines.delay(30_000L)
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

    private fun startInForeground(text: String? = null) {
        if (isForeground) return
        try {
            val notification = buildNotification(text ?: runningText(currentPort))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            DebugLogger.i("서버", "포그라운드 승격 완료")
        } catch (e: Exception) {
            DebugLogger.e("서버", Constants.ERR_AI_CALL_FAILED, "FGS 승격 거부, 백그라운드 유지: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun runningText(port: Int): String {
        val ip = cachedIp ?: "IP 확인 중"
        return getString(R.string.pj_notif_server_running) + " http://$ip:$port"
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pj_app_name_full))
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
                    CHANNEL_ID,
                    getString(R.string.pj_notif_channel_server),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_RESTART = "com.borasarang.promptjournaljupjup.RESTART_SERVER"
        const val NOTIFICATION_ID = 3100
        const val CHANNEL_ID = "jupjup_pj_server"

        fun start(context: Context) {
            try {
                context.startService(Intent(context, HttpServerService::class.java))
            } catch (e: IllegalStateException) {
                DebugLogger.w("서버", "startService 거부 — FGS 재시도: ${e.message}")
                runCatching {
                    context.startForegroundService(Intent(context, HttpServerService::class.java))
                }.onFailure {
                    DebugLogger.e("서버", Constants.ERR_AI_CALL_FAILED, "FGS 시작 실패: ${it.message}")
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
                    DebugLogger.e("서버", Constants.ERR_AI_CALL_FAILED, "FGS 재시작 실패: ${it.message}")
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpServerService::class.java))
        }
    }
}
