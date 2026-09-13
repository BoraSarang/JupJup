package com.borasarang.macjupjup.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.data.repository.AppFilter
import com.borasarang.macjupjup.data.repository.AppWithSourceList
import com.borasarang.macjupjup.data.repository.SettingsData
import com.borasarang.macjupjup.data.repository.SettingsView
import com.borasarang.macjupjup.data.repository.toView
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.NetUtils
import com.borasarang.macjupjup.util.maskToken
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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var currentPort: Int = Constants.DEFAULT_PORT

    @Volatile
    private var isForeground = false

    /** 수동 시드 백그라운드 상태 (idle/running/done …/not_found/error …) */
    @Volatile
    private var lastSeedStatus: String = "idle"

    private fun app(): MacJupJupRuntime = MacJupJupRuntime

    override fun onCreate() {
        super.onCreate()
        DebugLogger.i("서버", "서비스 생성")
        createChannel()
        // FGS 승격을 가장 먼저 시도 — startForegroundService 경로의 5초 의무 창 확보
        startInForeground(getString(R.string.mac_notif_server_starting))
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
                    serveAsset(call, "mac_web/index.html", ContentType.Text.Html.withCharset(Charsets.UTF_8))
                }
                get("/style.css") {
                    serveAsset(call, "mac_web/style.css", ContentType.Text.CSS.withCharset(Charsets.UTF_8))
                }
                get("/app.js") {
                    serveAsset(call, "mac_web/app.js", ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
                }
                get("/favicon.svg") {
                    serveAsset(call, "mac_web/favicon.svg", ContentType.Image.SVG)
                }
                get("/api/health") {
                    call.respondText(
                        """{"status":"ok","timestamp":${System.currentTimeMillis()}}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/apps") {
                    val params = call.queryParameters
                    val filter = AppFilter(
                        license = params["license"]?.takeIf { it.isNotBlank() },
                        category = params["category"]?.takeIf { it.isNotBlank() },
                        tag = params["tag"]?.takeIf { it.isNotBlank() },
                        q = params["q"]?.takeIf { it.isNotBlank() },
                        sort = params["sort"] ?: "newest",
                        page = params["page"]?.toIntOrNull() ?: 1,
                        pageSize = params["pageSize"]?.toIntOrNull()
                            ?.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
                            ?: Constants.API_DEFAULT_PAGE_SIZE,
                        bumped = params["bumped"]?.toBooleanStrictOrNull() ?: false,
                        updatedOnly = params["updatedOnly"]?.toBooleanStrictOrNull() ?: false,
                    )
                    val result = application.appRepository.list(filter)
                    call.respondText(
                        appsJson(result.apps.map { it }, result.total, result.page, result.pageSize),
                        ContentType.Application.Json,
                    )
                }
                get("/api/apps/{id}") {                    val id = call.pathId()
                    if (id.isNullOrBlank()) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    val found = application.appRepository.detail(id)
                    if (found == null) {
                        call.respondText(
                            """{"error":"Not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                    } else {
                        call.respondText(detailJson(found), ContentType.Application.Json)
                    }
                }
                /**
                 * T-061: 수동 시드. 차트·키워드 밖 니치 앱(SoundPaste류)을 trackId·이름으로 직접 등록.
                 * body: {"trackId":6471012328} 또는 {"name":"SoundPaste"}
                 * 백그라운드 실행 + 상태 조회 (요청 스레드 블로킹 제거).
                 */
                post("/api/apps/seed") {
                    val json = call.receiveJsonObject()
                    val trackId = json?.get("trackId")?.jsonPrimitive?.content?.toLongOrNull()
                    val name = json?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    if (trackId == null && name == null) {
                        return@post call.respondError("trackId or name required")
                    }
                    lastSeedStatus = "running"
                    val appRef = application
                    scope.launch {
                        try {
                            val query = if (trackId != null) {
                                com.borasarang.macjupjup.crawler.itunes.itunesLookupUrl("$trackId")
                            } else {
                                com.borasarang.macjupjup.crawler.itunes.itunesSearchUrl(name!!)
                            }
                            val resp = com.borasarang.macjupjup.crawler.CrawlHttp.get(query)
                            if (!resp.isOk) throw IllegalStateException("iTunes HTTP ${resp.code}")
                            val seedSource = com.borasarang.macjupjup.data.db.entity.CrawlSource(
                                id = "manual_seed", name = "수동 시드", type = "MAS_DISCOVERY",
                                baseUrl = "https://itunes.apple.com", enabled = true,
                                intervalHours = 0, intervalMinutes = 0,
                                lastRunAt = null, lastStatus = "NEVER_RUN",
                                errorMessage = null, selectorConfigJson = null,
                            )
                            val parser = com.borasarang.macjupjup.crawler.mas.MacStoreDiscoveryCrawler(seedSource)
                            // lookup/search 응답 형태 동일 → parseSearch 단일 경로
                            val hits = parser.parseSearch(resp.body)
                            val drafts = if (trackId != null) {
                                hits
                            } else {
                                val norm = com.borasarang.macjupjup.util.MergeUtils.normalizeName(name!!)
                                hits.filter {
                                    com.borasarang.macjupjup.util.MergeUtils.normalizeName(it.app.name) == norm
                                }
                            }
                            if (drafts.isEmpty()) {
                                lastSeedStatus = "not_found"
                                return@launch
                            }
                            val saved = appRef.appRepository.saveApps(
                                drafts.map { it.app },
                                drafts.flatMap { it.mappings },
                            )
                            com.borasarang.macjupjup.util.DebugLogger.i(
                                "수동시드",
                                "[FEATURE] 수동 시드 저장 created=${saved.created} updated=${saved.updated}",
                            )
                            lastSeedStatus = "done seeded=${drafts.size} created=${saved.created}"
                        } catch (e: Exception) {
                            com.borasarang.macjupjup.util.DebugLogger.e(
                                "수동시드", "E-AND-CRAWL-0201", "시드 실패: ${e.message}", e,
                            )
                            lastSeedStatus = "error ${e.message}"
                        }
                    }
                    call.respondText(
                        """{"accepted":true}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
                get("/api/apps/seed/status") {
                    call.respondText(
                        """{"status":"${escapeJson(lastSeedStatus)}"}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/watchlist") {
                    val sources = application.sourceRepository.list()
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
                    val id = call.pathId()
                    if (id.isNullOrBlank()) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                        return@post
                    }
                    val next = application.sourceRepository.toggle(id)
                    if (next == null) {
                        call.respondText(
                            """{"error":"Not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                    } else {
                        call.respondText(
                            """{"id":"${escapeJson(id)}","enabled":$next}""",
                            ContentType.Application.Json,
                        )
                    }
                }
                post("/api/sync") {
                    val sourceId = call.receiveJsonObject()
                        ?.get("sourceId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    DebugLogger.i("수동수집", "즉시 수집 요청 sourceId=$sourceId")
                    if (!sourceId.isNullOrBlank() &&
                        application.sourceRepository.getById(sourceId) == null
                    ) {
                        return@post call.respondNotFound("unknown sourceId")
                    }
                    application.crawlScheduler.triggerImmediate(application.database, sourceId)
                    call.respondText(
                        """{"accepted":true}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
                post("/api/translate") {
                    DebugLogger.i("번역", "번역 즉시 실행 요청")
                    application.crawlScheduler.triggerTranslateNow()
                    call.respondText(
                        """{"accepted":true}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
                get("/api/stats") {
                    val stats = application.appRepository.overview()
                    call.respondText(
                        buildJsonObject {
                            put("totalApps", stats.totalApps)
                            put("activeSources", stats.activeSources)
                            stats.lastCollectedAt?.let { put("lastCollectedAt", it) }
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                get("/api/stats/trends") {                    val t = application.appRepository.trends()
                    call.respondText(
                        buildJsonObject {
                            put("generatedAt", System.currentTimeMillis())
                            put("byCategory", buildJsonObject {
                                t.byCategory.forEach { (k, v) -> put(k, v) }
                            })
                            put("byLicense", buildJsonObject {
                                t.byLicense.forEach { (k, v) -> put(k, v) }
                            })
                            put("newLast7d", t.newLast7d)
                            put("updatedLast7d", t.updatedLast7d)
                            put("versionBumpsLast7d", t.versionBumpsLast7d)
                            put("aiTagCount", t.aiTagCount)
                            put("menuBarTagCount", t.menuBarTagCount)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                /** 일별 수집량 (그래프용). ?days=7/14/30, 기본 14 */
                get("/api/stats/collect") {
                    val days = call.queryParameters["days"]?.toIntOrNull()?.coerceIn(1, 30) ?: 14
                    val list = application.appRepository.collect(days)
                    call.respondText(
                        buildJsonObject {
                            put("days", buildJsonArray {
                                list.forEach { d ->
                                    add(
                                        buildJsonObject {
                                            put("day", d.day)
                                            put("found", d.found)
                                            put("new", d.newCount)
                                            put("updated", d.updated)
                                            put("runs", d.runs)
                                            put("bySource", buildJsonArray {
                                                d.bySource.forEach { s ->
                                                    add(
                                                        buildJsonObject {
                                                            put("sourceId", s.sourceId)
                                                            put("sourceName", s.sourceName)
                                                            put("found", s.found)
                                                            put("new", s.newCount)
                                                            put("updated", s.updated)
                                                        },
                                                    )
                                                }
                                            })
                                        },
                                    )
                                }
                            })
                            put("total", buildJsonObject {
                                put("found", list.sumOf { it.found })
                                put("new", list.sumOf { it.newCount })
                                put("updated", list.sumOf { it.updated })
                            })
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                /** 트렌드 인사이트 카드 목록 */
                get("/api/stats/insights") {
                    val insights = com.borasarang.macjupjup.data.repository.buildInsights(
                        application.appRepository.insightsInput(),
                    )
                    call.respondText(
                        buildJsonObject {
                            put("insights", buildJsonArray {
                                insights.forEach { i ->
                                    add(
                                        buildJsonObject {
                                            put("icon", i.icon)
                                            put("title", i.title)
                                            put("body", i.body)
                                        },
                                    )
                                }
                            })
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                get("/api/notifications") {
                    val params = call.queryParameters
                    val type = params["type"]?.takeIf { it.isNotBlank() }
                    val isRead = params["isRead"]?.toBooleanStrictOrNull()
                    val page = params["page"]?.toIntOrNull() ?: 1
                    val pageSize = params["pageSize"]?.toIntOrNull()
                        ?.coerceIn(1, 100) ?: 20
                    val repo = application.notificationRepository
                    val items = repo.getPaged(type, isRead, page, pageSize)
                    val total = repo.count(type, isRead)
                    val unread = repo.countUnread()
                    call.respondText(
                        buildJsonObject {
                            put("notifications", buildJsonArray {
                                items.forEach { n ->
                                    add(
                                        buildJsonObject {
                                            put("id", n.id)
                                            put("type", n.type)
                                            put("summary", n.summary)
                                            put("createdAt", n.createdAt)
                                            put("isRead", n.isRead)
                                        },
                                    )
                                }
                            })
                            put("total", total)
                            put("page", page)
                            put("pageSize", pageSize)
                            put("unreadCount", unread)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                get("/api/notifications/{id}") {
                    val id = call.pathIdLong()
                    val found = id?.let { application.notificationRepository.getById(it) }
                    if (found == null) {
                        call.respondText(
                            """{"error":"Not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                    } else {
                        val detailEl = try {
                            Json.parseToJsonElement(found.detailJson)
                        } catch (_: Exception) {
                            buildJsonObject { }
                        }
                        call.respondText(
                            buildJsonObject {
                                put("notification", buildJsonObject {
                                    put("id", found.id)
                                    put("type", found.type)
                                    put("summary", found.summary)
                                    put("createdAt", found.createdAt)
                                    put("isRead", found.isRead)
                                })
                                put("detail", detailEl)
                            }.toString(),
                            ContentType.Application.Json,
                        )
                    }
                }
                post("/api/notifications/{id}/read") {
                    val id = call.pathIdLong()
                    if (id == null) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    } else {
                        val updated = application.notificationRepository.markAsRead(id)
                        call.respondText(
                            """{"updated":$updated}""",
                            ContentType.Application.Json,
                        )
                    }
                }
                post("/api/notifications/read-all") {
                    val updated = application.notificationRepository.markAllAsRead()
                    call.respondText(
                        """{"updated":$updated}""",
                        ContentType.Application.Json,
                    )
                }
                delete("/api/notifications/{id}") {
                    val id = call.pathIdLong()
                    if (id == null) {
                        call.respondText(
                            """{"error":"id required"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    } else {
                        val deleted = application.notificationRepository.delete(id)
                        call.respondText(
                            """{"deleted":$deleted}""",
                            ContentType.Application.Json,
                        )
                    }
                }
                post("/api/notifications/cleanup") {                    val days = try {
                        application.preferences.getSettings().retentionDays
                    } catch (_: Exception) {
                        Constants.DEFAULT_RETENTION_DAYS
                    }
                    val before = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
                    val deleted = application.notificationRepository.deleteOlderThan(before)
                    call.respondText(
                        """{"deleted":$deleted}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/notifications/unread-count") {
                    val unread = application.notificationRepository.countUnread()
                    call.respondText(
                        """{"unreadCount":$unread}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/settings") {
                    val s = application.preferences.getSettings()
                    call.respondText(settingsJson(s.toView()), ContentType.Application.Json)
                }
                post("/api/settings") {
                    val obj = call.receiveJsonObject()
                    if (obj == null) {
                        return@post call.respondError("E-AND-VALID-0501")
                    }
                    val current = application.preferences.getSettings()
                    val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: current.port
                    if (port !in Constants.MIN_PORT..Constants.MAX_PORT) {
                        return@post call.respondError("E-AND-VALID-0502")
                    }
                    val retentionDays = obj["retentionDays"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: current.retentionDays
                    if (retentionDays !in Constants.MIN_RETENTION_DAYS..Constants.MAX_RETENTION_DAYS) {
                        return@post call.respondError("E-AND-VALID-0501")
                    }
                    val watchdogIntervalSec = obj["watchdogIntervalSec"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: current.watchdogIntervalSec
                    if (watchdogIntervalSec !in Constants.MIN_WATCHDOG_SEC..Constants.MAX_WATCHDOG_SEC) {
                        return@post call.respondError("E-AND-VALID-0501")
                    }
                    val tokenRaw = obj["githubToken"]?.jsonPrimitive?.content
                    val next = SettingsData(
                        port = port,
                        retentionDays = retentionDays,
                        autoStart = obj["autoStart"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: current.autoStart,
                        watchdogIntervalSec = watchdogIntervalSec,
                        // 토큰 키가 없으면 기존 유지, 빈 문자열이면 삭제
                        githubToken = if (obj.containsKey("githubToken")) (tokenRaw ?: "") else current.githubToken,
                        translateKo = obj["translateKo"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: current.translateKo,
                        notifCrawlComplete = obj["notifCrawlComplete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: current.notifCrawlComplete,
                        notifNewApp = obj["notifNewApp"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: current.notifNewApp,
                        notifFailure = obj["notifFailure"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: current.notifFailure,
                    )
                    application.preferences.saveSettings(next)
                    if (tokenRaw != null) {
                        DebugLogger.i("설정", "GitHub 토큰 저장됨 (${maskToken(tokenRaw)})")
                    }
                    if (next.port != currentPort) {
                        DebugLogger.i("설정", "포트 변경 감지 — 서버 재시작 예약")
                        // 라우트 스레드 블로킹(stop 최대 3s) 방지: 백그라운드 재시작
                        scope.launch { restartServer() }
                    }
                    call.respondText(settingsJson(next.toView()), ContentType.Application.Json)
                }
            }
        }.start(wait = false)
    }

    private suspend fun serveAsset(
        call: io.ktor.server.application.ApplicationCall,
        assetPath: String,
        contentType: ContentType,
    ) {
        try {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                applicationContext.assets.open(assetPath).use { it.readBytes() }
            }
            call.respondBytes(bytes, contentType)
        } catch (e: Exception) {
            DebugLogger.w("서버", "에셋 서빙 실패 $assetPath: ${e.message}")
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
        return getString(R.string.mac_notif_server_running) + " http://$ip:$port"
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, Constants.CHANNEL_ID_SERVER)
            .setContentTitle(getString(R.string.mac_app_name_full))
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
                    getString(R.string.mac_notif_channel_server),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        } catch (_: Exception) {
        }
    }

    // ---------- JSON 빌더 ----------

    private fun appsJson(
        items: List<com.borasarang.macjupjup.data.repository.AppListItem>,
        total: Int,
        page: Int,
        pageSize: Int,
    ): String {
        return buildJsonObject {
            put("apps", buildJsonArray {
                items.forEach { item ->
                    add(
                        buildJsonObject {
                            appElement(item.app).entries.forEach { (key, value) -> put(key, value) }
                            putIfNotNull("sourceName", item.sourceName)
                            putIfNotNull("sourceUrl", item.sourceUrl)
                        },
                    )
                }
            })
            put("total", total)
            put("page", page)
            put("pageSize", pageSize)
        }.toString()
    }

    private fun detailJson(item: AppWithSourceList): String {
        return buildJsonObject {
            appElement(item.app).entries.forEach { (key, value) ->
                put(key, value)
            }
            put("sources", buildJsonArray {
                item.sources.forEach { s ->
                    add(
                        buildJsonObject {
                            put("sourceName", s.sourceName)
                            s.sourceUrl?.let { put("sourceUrl", it) }
                        },
                    )
                }
            })
            put("versions", buildJsonArray {
                item.versions.forEach { v ->
                    add(
                        buildJsonObject {
                            put("version", v.version)
                            put("detectedAt", v.detectedAt)
                            v.notesSummary?.let { put("notesSummary", it) }
                            v.sourceUrl?.let { put("sourceUrl", it) }
                        },
                    )
                }
            })
        }.toString()
    }

    /** 목록·상세 공통 앱 필드 (상세 7섹션 매핑 포함) */
    private fun appElement(a: com.borasarang.macjupjup.data.db.entity.App): kotlinx.serialization.json.JsonObject {
        return buildJsonObject {
            put("id", a.id)
            put("platform", a.platform)
            put("name", a.name)
            put("developer", a.developer)
            put("license", a.license)
            put("price", a.price)
            put("currency", a.currency)
            put("category", a.category)
            putIfNotNull("tags", a.tags)
            putIfNotNull("trackId", a.trackId)
            putIfNotNull("repoFullName", a.repoFullName)
            putIfNotNull("homepageUrl", a.homepageUrl)
            putIfNotNull("version", a.version)
            putIfNotNull("prevVersion", a.prevVersion)
            // ⑤ 새로운 기능: 요약 + 원문은 버전 히스토리/출처 링크로
            putIfNotNull("releaseNotesSummary", a.releaseNotesSummary)
            putIfNotNull("releaseNotes", a.releaseNotes)
            putIfNotNull("releaseNotesKo", a.releaseNotesKo)
            putIfNotNull("releaseDate", a.releaseDate)
            // ① 소개 발췌 / ② 스크린샷(CDN 직접 표시)
            putIfNotNull("descriptionSnippet", a.descriptionSnippet)
            putIfNotNull("descriptionKo", a.descriptionKo)
            putIfNotNull("screenshotUrls", a.screenshotUrls)
            putIfNotNull("iconUrl", a.iconUrl)
            // ③ 특징
            putIfNotNull("averageRating", a.averageRating)
            putIfNotNull("ratingCount", a.ratingCount)
            putIfNotNull("stars", a.stars)
            putIfNotNull("primaryLanguage", a.primaryLanguage)
            putIfNotNull("topics", a.topics)
            putIfNotNull("sellerName", a.sellerName)
            putIfNotNull("fileSize", a.fileSize)
            putIfNotNull("minOs", a.minOs)
            putIfNotNull("contentRating", a.contentRating)
            putIfNotNull("forks", a.forks)
            putIfNotNull("issues", a.issues)
            putIfNotNull("licenseName", a.licenseName)
            put("firstSeenAt", a.firstSeenAt)
            put("lastUpdatedAt", a.lastUpdatedAt)
            put("isNew", a.isNew)
        }
    }

    private fun settingsJson(s: SettingsView): String {
        return buildJsonObject {
            put("port", s.port)
            put("retentionDays", s.retentionDays)
            put("autoStart", s.autoStart)
            put("watchdogIntervalSec", s.watchdogIntervalSec)
            // 토큰 값은 절대 반환하지 않음 — 설정 여부만
            put("githubTokenSet", s.githubTokenSet)
            put("translateKo", s.translateKo)
            put("notifCrawlComplete", s.notifCrawlComplete)
            put("notifNewApp", s.notifNewApp)
            put("notifFailure", s.notifFailure)
        }.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    /** 에러 envelope 단일 진실 (H-1). 성공 응답은 respondText 직접 사용 */
    private suspend fun io.ktor.server.application.ApplicationCall.respondError(
        msg: String,
        status: io.ktor.http.HttpStatusCode = io.ktor.http.HttpStatusCode.BadRequest,
    ) {
        respondText(
            """{"error":"${escapeJson(msg)}"}""",
            ContentType.Application.Json,
            status,
        )
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondNotFound(
        msg: String = "Not found",
    ) = respondError(msg, io.ktor.http.HttpStatusCode.NotFound)

/** nullable put 단일 진실 (H-4). appElement 25연타 축소용 */
private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    value?.let { put(key, it) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotNull(key: String, value: Long?) {
    value?.let { put(key, it) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotNull(key: String, value: Double?) {
    value?.let { put(key, it) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotNull(key: String, value: Int?) {
    value?.let { put(key, it) }
}

    /** 요청 바디 JSON 파싱 단일 진실 (H-2). 실패 시 null */
    private suspend fun io.ktor.server.application.ApplicationCall.receiveJsonObject(): JsonObject? {
        return try {
            Json.parseToJsonElement(receiveText()) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** 경로 파라미터 id 단일 진실 (H-3). 비어 있으면 null */
    private fun io.ktor.server.application.ApplicationCall.pathId(): String? =
        parameters["id"]?.takeIf { it.isNotBlank() }

    private fun io.ktor.server.application.ApplicationCall.pathIdLong(): Long? =
        parameters["id"]?.toLongOrNull()

    companion object {
        const val ACTION_RESTART = "com.borasarang.macjupjup.RESTART_SERVER"

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

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpServerService::class.java))
        }
    }
}
