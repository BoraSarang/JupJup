package com.borasarang.planjupjup.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.R
import com.borasarang.planjupjup.data.repository.PlanFilter
import com.borasarang.planjupjup.data.repository.PlanWithSources
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.data.repository.BrandStats
import com.borasarang.planjupjup.data.repository.CollectionHealth
import com.borasarang.planjupjup.data.repository.DistributionBucket
import com.borasarang.planjupjup.data.repository.Insight
import com.borasarang.planjupjup.data.repository.InsightType
import com.borasarang.planjupjup.data.repository.NetworkStats
import com.borasarang.planjupjup.data.repository.OverviewStats
import com.borasarang.planjupjup.data.repository.SourceHealth
import com.borasarang.planjupjup.data.repository.TrendPoint
import com.borasarang.planjupjup.data.repository.ValueRankItem
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.NetUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.routing.delete
import io.ktor.server.plugins.statuspages.StatusPages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Ktor CIO 임베디드 서버를 품은 포그라운드 서비스 (type=dataSync).
 * - START_STICKY: 시스템이 죽여도 재시작
 * - onCreate 최상단 startForeground (5초 룰)
 * - Watchdog: 주기적 로컬 포트 헬스체크로 무응답 시 자동 재시작
 * - 정적 포털은 assets/web에서 수동 서빙 (assets는 classpath가 아님)
 * - JSON은 org.json 대신 kotlinx-serialization JsonElement 빌더 사용 (플러그인 불필요)
 */
class HttpServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var currentPort: Int = Constants.DEFAULT_PORT

    @Volatile
    private var isForeground = false

    private fun app(): PlanJupJupRuntime = PlanJupJupRuntime

    override fun onCreate() {
        super.onCreate()
        DebugLogger.i("서버", "서비스 생성")
        createChannel()
        // FGS 승격을 가장 먼저 시도 — startForegroundService 경로의 5초 의무 창 확보
        startInForeground(getString(R.string.plan_notif_server_starting))
        scope.launch {
            try {
                val settings = app().preferences.getSettings()
                currentPort = settings.port
                startServer(settings.port)
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

    private suspend fun restartServer() {
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
            updateNotification(runningText(settings.port))
        } catch (e: Exception) {
            DebugLogger.e("서버", "E-AND-SRV-0104", "서버 재시작 실패: ${e.message}", e)
            updateNotification("서버 재시작 실패: ${e.message}")
        }
    }

    private fun startServer(port: Int) {
        val application = app()
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
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
                get("/") {
                    serveAsset(call, "plan_web/index.html", ContentType.Text.Html.withCharset(Charsets.UTF_8))
                }
                get("/style.css") {
                    serveAsset(call, "plan_web/style.css", ContentType.Text.CSS.withCharset(Charsets.UTF_8))
                }
                get("/app.js") {
                    serveAsset(call, "plan_web/app.js", ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
                }
                get("/favicon.svg") {
                    serveAsset(call, "plan_web/favicon.svg", ContentType.Image.SVG)
                }
                get("/api/health") {
                    call.respondText(
                        """{"status":"ok","timestamp":${System.currentTimeMillis()}}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/plans") {
                    val params = call.queryParameters
                    val filter = PlanFilter(
                        network = params["network"]?.takeIf { it.isNotBlank() },
                        carrier = params["carrier"]?.takeIf { it.isNotBlank() },
                        minDataGb = params["minData"]?.toIntOrNull(),
                        maxPrice = params["maxPrice"]?.toIntOrNull(),
                        tag = params["tag"]?.takeIf { it.isNotBlank() },
                        sort = params["sort"] ?: "price_asc",
                        page = params["page"]?.toIntOrNull() ?: 1,
                        pageSize = params["pageSize"]?.toIntOrNull()
                            ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                            ?: Constants.API_DEFAULT_PAGE_SIZE,
                    )
                    val result = application.planRepository.getPlans(filter)
                    call.respondText(plansJson(result.plans, result.total, result.page, result.pageSize),
                        ContentType.Application.Json)
                }
                get("/api/plans/{id}") {
                    val id = call.parameters["id"]
                    if (id.isNullOrBlank()) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    val found = application.planRepository.getPlanWithSources(id)
                    if (found == null) {
                        call.respondText(
                            """{"error":"Not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                    } else {
                        call.respondText(planJson(found), ContentType.Application.Json)
                    }
                }
                get("/api/sources") {
                    val sources = application.sourceRepository.getAll()
                    val arr = buildJsonArray {
                        sources.forEach { s ->
                            add(
                                buildJsonObject {
                                    put("id", s.id)
                                    put("name", s.name)
                                    put("type", s.type)
                                    put("baseUrl", s.baseUrl)
                                    put("enabled", s.enabled)
                                    put("intervalHours", s.intervalHours)
                                    put("intervalMinutes", s.intervalMinutes)
                                    s.lastRunAt?.let { put("lastRunAt", it) }
                                    put("lastStatus", s.lastStatus)
                                    s.errorMessage?.let { put("errorMessage", it) }
                                },
                            )
                        }
                    }
                    call.respondText(arr.toString(), ContentType.Application.Json)
                }
                post("/api/sources/{id}/toggle") {
                    val id = call.parameters["id"]
                    if (id.isNullOrBlank()) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@post
                    }
                    val next = application.sourceRepository.toggleEnabled(id)
                    call.respondText("""{"id":"${escapeJson(id)}","enabled":$next}""",
                        ContentType.Application.Json)
                }
                post("/api/sync") {
                    val body = try {
                        call.receiveText()
                    } catch (_: Exception) {
                        ""
                    }
                    val sourceId = try {
                        (Json.parseToJsonElement(body) as? JsonObject)
                            ?.get("sourceId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }
                    DebugLogger.i("수동수집", "즉시 수집 요청 sourceId=$sourceId")
                    application.crawlScheduler.triggerImmediate(sourceId)
                    call.respondText(
                        """{"accepted":true}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
                get("/api/stats") {
                    val stats = application.planRepository.getStats()
                    call.respondText(
                        buildJsonObject {
                            put("totalPlans", stats.totalPlans)
                            put("activeSources", stats.activeSources)
                            stats.lastCollectedAt?.let { put("lastCollectedAt", it) }
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                // ---------- 통계 대시보드 API ----------
                get("/api/stats/overview") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        overviewJson(application.statsRepository.getOverview())
                    }
                }
                get("/api/stats/brands") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        val brand = call.queryParameters["brand"]?.takeIf { it.isNotBlank() }
                        val list = application.statsRepository.getBrands()
                            .filter { brand == null || it.brand == brand }
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("brands", buildJsonArray {
                                list.forEach { add(brandElement(it)) }
                            })
                        }.toString()
                    }
                }
                get("/api/stats/networks") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("networks", buildJsonArray {
                                application.statsRepository.getNetworks()
                                    .forEach { add(networkElement(it)) }
                            })
                        }.toString()
                    }
                }
                get("/api/stats/distribution") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        val type = call.queryParameters["type"] ?: "price"
                        val buckets = if (type == "data") {
                            application.statsRepository.getDataDistribution()
                        } else {
                            application.statsRepository.getPriceDistribution()
                        }
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("type", type)
                            put("buckets", buildJsonArray {
                                buckets.forEach { add(bucketElement(it)) }
                            })
                        }.toString()
                    }
                }
                get("/api/stats/trends") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        val params = call.queryParameters
                        val type = params["type"]?.takeIf { it == "new" } ?: "crawl"
                        val gran = params["gran"]?.takeIf { it == "hourly" } ?: "daily"
                        val days = params["days"]?.toIntOrNull() ?: 30
                        val points = if (type == "new") {
                            application.statsRepository.getNewPlanTrend(days, gran)
                        } else {
                            application.statsRepository.getCrawlTrend(days, gran)
                        }
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("type", type)
                            put("gran", gran)
                            put("points", buildJsonArray {
                                points.forEach { add(pointElement(it)) }
                            })
                        }.toString()
                    }
                }
                get("/api/stats/value-ranking") {
                    statsRoute(call, "E-AND-SRV-0106") {
                        val params = call.queryParameters
                        val network = params["network"]?.takeIf { it == "5G" || it == "LTE" }
                        val limit = params["limit"]?.toIntOrNull() ?: 10
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("items", buildJsonArray {
                                application.statsRepository.getValueRanking(network, limit)
                                    .forEachIndexed { idx, item -> add(valueItemElement(item, idx + 1)) }
                            })
                        }.toString()
                    }
                }
                get("/api/stats/collection-health") {
                    statsRoute(call, "E-AND-SRV-0105") {
                        healthJson(application.statsRepository.getCollectionHealth())
                    }
                }
                get("/api/stats/insights") {
                    statsRoute(call, "E-AND-SRV-0107") {
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("cache", "memory")
                            put("insights", buildJsonArray {
                                application.statsRepository.getInsights()
                                    .forEach { add(insightElement(it)) }
                            })
                        }.toString()
                    }
                }
                get("/api/settings") {
                    val s = application.preferences.getSettings()
                    call.respondText(settingsJson(s), ContentType.Application.Json)
                }
                post("/api/settings") {
                    val body = try {
                        call.receiveText()
                    } catch (_: Exception) {
                        ""
                    }
                    val current = application.preferences.getSettings()
                    try {
                        val obj = Json.parseToJsonElement(body) as? JsonObject
                        val next = SettingsData(
                            port = obj?.get("port")?.jsonPrimitive?.content?.toIntOrNull()
                                ?.coerceIn(Constants.MIN_PORT, Constants.MAX_PORT)
                                ?: current.port,
                            retentionDays = obj?.get("retentionDays")?.jsonPrimitive?.content
                                ?.toIntOrNull()?.takeIf { it == 30 || it == 90 }
                                ?: current.retentionDays,
                            autoStart = obj?.get("autoStart")?.jsonPrimitive?.content
                                ?.toBooleanStrictOrNull() ?: current.autoStart,
                            watchdogIntervalSec = obj?.get("watchdogIntervalSec")?.jsonPrimitive?.content
                                ?.toIntOrNull()
                                ?.coerceIn(Constants.MIN_WATCHDOG_SEC, Constants.MAX_WATCHDOG_SEC)
                                ?: current.watchdogIntervalSec,
                            notifCrawlComplete = obj?.get("notifCrawlComplete")?.jsonPrimitive?.content
                                ?.toBooleanStrictOrNull() ?: current.notifCrawlComplete,
                            notifNewPlan = obj?.get("notifNewPlan")?.jsonPrimitive?.content
                                ?.toBooleanStrictOrNull() ?: current.notifNewPlan,
                            notifFailure = obj?.get("notifFailure")?.jsonPrimitive?.content
                                ?.toBooleanStrictOrNull() ?: current.notifFailure,
                        )
                        application.preferences.saveSettings(next)
                        DebugLogger.i("설정", "설정 저장 port=${next.port} retention=${next.retentionDays}")
                        if (next.port != currentPort) {
                            restartServer()
                        }
                        call.respondText(settingsJson(next), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(
                            """{"error":"${escapeJson(e.message ?: "bad request")}"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    }
                }
                // ---------- 알림 API ----------
                get("/api/notifications") {
                    val params = call.queryParameters
                    val page = params["page"]?.toIntOrNull() ?: 1
                    val pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                    val type = params["type"]?.takeIf { it.isNotBlank() }
                    val isRead = params["isRead"]?.let {
                        when (it.lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    }
                    val list = application.notificationRepository.getPaged(type, isRead, page, pageSize)
                    val total = application.notificationRepository.count(type, isRead)
                    val unreadCount = application.notificationRepository.countUnread()
                    call.respondText(
                        buildJsonObject {
                            put("notifications", buildJsonArray { list.forEach { add(Json.parseToJsonElement(it.toJson())) } })
                            put("total", total)
                            put("page", page)
                            put("pageSize", pageSize)
                            put("unreadCount", unreadCount)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                get("/api/notifications/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    val log = application.notificationRepository.getById(id)
                    if (log == null) {
                        call.respondText(
                            """{"error":"Not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                        return@get
                    }
                    call.respondText(
                        buildJsonObject {
                            put("notification", Json.parseToJsonElement(log.toJson()))
                            put("detail", Json.parseToJsonElement(log.detailJson))
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                post("/api/notifications/{id}/read") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@post
                    }
                    val updated = application.notificationRepository.markAsRead(id)
                    call.respondText(
                        buildJsonObject { put("updated", updated) }.toString(),
                        ContentType.Application.Json,
                    )
                }
                post("/api/notifications/read-all") {
                    val updated = application.notificationRepository.markAllAsRead()
                    call.respondText(
                        buildJsonObject { put("updated", updated) }.toString(),
                        ContentType.Application.Json,
                    )
                }
                delete("/api/notifications/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@delete
                    }
                    val deleted = application.notificationRepository.delete(id)
                    call.respondText(
                        buildJsonObject { put("deleted", deleted) }.toString(),
                        ContentType.Application.Json,
                    )
                }
                post("/api/notifications/cleanup") {
                    val body = try { call.receiveText() } catch (_: Exception) { "" }
                    val days = try {
                        (Json.parseToJsonElement(body) as? JsonObject)
                            ?.get("days")?.jsonPrimitive?.content?.toIntOrNull()
                            ?: currentRetentionDays()
                    } catch (_: Exception) { currentRetentionDays() }
                    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
                    val deleted = application.notificationRepository.deleteOlderThan(cutoff)
                    call.respondText(
                        buildJsonObject { put("deleted", deleted) }.toString(),
                        ContentType.Application.Json,
                    )
                }
                get("/api/notifications/unread-count") {
                    val count = application.notificationRepository.countUnread()
                    call.respondText(
                        buildJsonObject { put("unreadCount", count) }.toString(),
                        ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)
    }

    private suspend fun currentRetentionDays(): Int {
        return try { app().preferences.getSettings().retentionDays } catch (_: Exception) { Constants.DEFAULT_RETENTION_DAYS }
    }
    
    private suspend fun serveAsset(
        call: io.ktor.server.application.ApplicationCall,
        assetPath: String,
        contentType: ContentType,
    ) {
        try {
            val bytes = applicationContext.assets.open(assetPath).use { it.readBytes() }
            call.respondBytes(bytes, contentType)
        } catch (_: Exception) {
            call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
        }
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
        val ip = NetUtils.getLocalIp(this) ?: "IP 확인 중"
        return getString(R.string.plan_notif_server_running) + " http://$ip:$port"
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, Constants.CHANNEL_ID_SERVER)
            .setContentTitle(getString(R.string.plan_app_name_full))
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
                    getString(R.string.plan_notif_channel_server),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        } catch (_: Exception) {
        }
    }

    // ---------- JSON 빌더 ----------

    private fun plansJson(plans: List<PlanWithSources>, total: Int, page: Int, pageSize: Int): String {
        return buildJsonObject {
            put("plans", buildJsonArray { plans.forEach { add(planElement(it)) } })
            put("total", total)
            put("page", page)
            put("pageSize", pageSize)
        }.toString()
    }

    private fun planJson(item: PlanWithSources): String = planElement(item).toString()

    private fun planElement(item: PlanWithSources): kotlinx.serialization.json.JsonElement {
        val fields = planFields(item)
        return buildJsonObject {
            fields.entries.forEach { (key, value) -> put(key, value) }
            put("sources", buildJsonArray {
                item.sources.forEach { s ->
                    add(
                        buildJsonObject {
                            put("sourceName", s.sourceName)
                            put("sourceUrl", s.sourceUrl)
                        },
                    )
                }
            })
        }
    }

    private fun planFields(item: PlanWithSources): kotlinx.serialization.json.JsonObject {
        val p = item.plan
        return buildJsonObject {
            put("id", p.id)
            put("carrierName", p.carrierName)
            put("mvnoNetwork", p.mvnoNetwork)
            put("planName", p.planName)
            put("price", p.price)
            p.priceAfterDiscount?.let { put("priceAfterDiscount", it) }
            p.discountMonths?.let { put("discountMonths", it) }
            put("dataAmount", p.dataAmount)
            put("voice", p.voice)
            put("sms", p.sms)
            put("networkType", p.networkType)
            p.eventBadge?.let { put("eventBadge", it) }
            put("collectedAt", p.collectedAt)
            put("firstCollectedAt", p.firstCollectedAt)
            put("isNew", p.isNew)
            p.tags?.let { put("tags", it) }
        }
    }

    private fun settingsJson(s: SettingsData): String {
        return buildJsonObject {
            put("port", s.port)
            put("retentionDays", s.retentionDays)
            put("autoStart", s.autoStart)
            put("watchdogIntervalSec", s.watchdogIntervalSec)
            put("notifCrawlComplete", s.notifCrawlComplete)
            put("notifNewPlan", s.notifNewPlan)
            put("notifFailure", s.notifFailure)
        }.toString()
    }

    // ---------- 통계 JSON 직렬화 ----------

    /** 공통 예외 처리 — 신규 통계 에러코드로 응답 (E-AND-SRV-0105/0106/0107) */
    private suspend fun statsRoute(
        call: io.ktor.server.application.ApplicationCall,
        code: String,
        block: suspend () -> String,
    ) {
        try {
            call.respondText(block(), ContentType.Application.Json)
        } catch (e: Exception) {
            DebugLogger.e("통계", code, "통계 API 오류 ${call.request.local.uri}: ${e.message}", e)
            call.respondText(
                """{"error":"$code"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }

    private fun overviewJson(o: OverviewStats): String {
        return buildJsonObject {
            put("generatedAt", System.currentTimeMillis())
            put("cache", "memory")
            put("totalPlans", o.totalPlans)
            put("brandCount", o.brandCount)
            put("networkCount", o.networkCount)
            put("newThisWeek", o.newThisWeek)
            put("avgPrice", o.avgPrice)
            put("minPrice", o.minPrice)
            put("maxPrice", o.maxPrice)
            put("unlimitedRatio", o.unlimitedRatio)
            put("g5Ratio", o.g5Ratio)
            put("avgDataGb", o.avgDataGb)
            put("crawlCountToday", o.crawlCountToday)
            put("crawlFail24h", o.crawlFail24h)
            o.lastCollectedAt?.let { put("lastCollectedAt", it) }
        }.toString()
    }

    private fun brandElement(b: BrandStats): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("brand", b.brand)
            put("mvnoNetwork", b.mvnoNetwork)
            put("planCount", b.planCount)
            put("newCount", b.newCount)
            put("avgPrice", b.avgPrice)
            put("minPrice", b.minPrice)
            put("maxPrice", b.maxPrice)
            put("avgDataGb", b.avgDataGb)
            put("g5Count", b.g5Count)
            b.pricePerGb?.let { put("pricePerGb", it) }
        }
    }

    private fun networkElement(n: NetworkStats): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("network", n.network)
            put("planCount", n.planCount)
            put("avgPrice", n.avgPrice)
            put("minPrice", n.minPrice)
            put("maxPrice", n.maxPrice)
            put("avgDataGb", n.avgDataGb)
            put("g5Ratio", n.g5Ratio)
            put("unlimitedRatio", n.unlimitedRatio)
            n.pricePerGb?.let { put("pricePerGb", it) }
        }
    }

    private fun bucketElement(b: DistributionBucket): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("label", b.label)
            put("count", b.count)
            b.min?.let { put("min", it) }
            b.max?.let { put("max", it) }
        }
    }

    private fun pointElement(p: TrendPoint): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("label", p.label)
            put("ts", p.ts)
            put("plansFound", p.plansFound)
            put("plansNew", p.plansNew)
            put("plansUpdated", p.plansUpdated)
            put("failCount", p.failCount)
        }
    }

    private fun valueItemElement(item: ValueRankItem, rank: Int): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("rank", rank)
            put("id", item.id)
            put("carrierName", item.carrierName)
            put("planName", item.planName)
            put("price", item.price)
            put("dataAmount", item.dataAmount)
            put("voice", item.voice)
            put("sms", item.sms)
            put("networkType", item.networkType)
            put("dataGb", item.dataGb)
            put("score", item.score)
            put("scoreLabel", item.scoreLabel)
        }
    }

    private fun healthJson(h: CollectionHealth): String {
        return buildJsonObject {
            put("generatedAt", System.currentTimeMillis())
            put("cache", "memory")
            put("success24h", h.success24h)
            put("fail24h", h.fail24h)
            put("avgDurationSec", h.avgDurationSec)
            h.lastCollectedAt?.let { put("lastCollectedAt", it) }
            put("sources", buildJsonArray { h.sources.forEach { add(sourceHealthElement(it)) } })
        }.toString()
    }

    private fun sourceHealthElement(s: SourceHealth): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("sourceId", s.sourceId)
            put("sourceName", s.sourceName)
            put("lastStatus", s.lastStatus)
            s.lastRunAt?.let { put("lastRunAt", it) }
            s.errorMessage?.let { put("errorMessage", it) }
            put("successRate", s.successRate)
        }
    }

    private fun insightElement(i: Insight): kotlinx.serialization.json.JsonElement {
        return buildJsonObject {
            put("type", when (i.type) {
                InsightType.POSITIVE -> "positive"
                InsightType.WARNING -> "warning"
                InsightType.INFO -> "info"
            })
            put("title", i.title)
            put("text", i.text)
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").take(300)
    }

    companion object {
        const val ACTION_RESTART = "com.borasarang.planjupjup.RESTART_SERVER"

        fun start(context: Context) {
            // startForegroundService는 5초 내 startForeground 의무이므로
            // 일반 startService 우선 + 실패 시 FGS 폴백 (DroidRelay 검증 패턴)
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

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpServerService::class.java))
        }
    }
}
