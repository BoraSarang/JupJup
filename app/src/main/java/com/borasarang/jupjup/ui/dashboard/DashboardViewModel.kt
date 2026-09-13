package com.borasarang.jupjup.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.server.HttpServerService as MacHttpServerService
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import com.borasarang.macjupjup.util.NetUtils as MacNetUtils
import com.borasarang.macjupjup.util.TimeUtils as MacTimeUtils
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.server.HttpServerService as PlanHttpServerService
import com.borasarang.planjupjup.util.DebugLogger as PlanDebugLogger
import com.borasarang.planjupjup.util.NetUtils as PlanNetUtils
import com.borasarang.planjupjup.util.TimeUtils as PlanTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket

data class DashboardServiceUi(
    val isServerRunning: Boolean = false,
    val address: String = "",
    val statValue1: Int = 0,
    val statValue2: Int = 0,
    val lastCollectedLabel: String = "",
    val isCrawling: Boolean = false,
    val crawlEnabled: Boolean = true,
)

data class DashboardUiState(
    val isChecking: Boolean = true,
    val mac: DashboardServiceUi = DashboardServiceUi(),
    val plan: DashboardServiceUi = DashboardServiceUi(),
)

/** 줍줍 시리즈 — 맥줍줍·요금줍줍 두 서비스 상태를 한 화면에서 병렬 조회 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 시리즈 상태 새로고침.
     * 서버 기동은 비동기(포트 바인드까지 수백 ms)라 첫 조회가 "중지됨"으로 잡힐 수 있다.
     * 중지 표시가 하나라도 있으면 [SERVER_SETTLE_MS] 뒤 1회만 재조회한다 (재귀 없음).
     */
    fun refresh(retry: Boolean = true) {
        viewModelScope.launch {
            MacDebugLogger.i("대시보드", "시리즈 대시보드 새로고침")
            _uiState.value = _uiState.value.copy(isChecking = true)
            try {
                val fresh = withContext(Dispatchers.IO) {
                    val mac = loadMacState()
                    val plan = loadPlanState()
                    mac to plan
                }
                _uiState.value = DashboardUiState(isChecking = false, mac = fresh.first, plan = fresh.second)
                if (retry && (!fresh.first.isServerRunning || !fresh.second.isServerRunning)) {
                    MacDebugLogger.i("대시보드", "서버 미기동 감지 — ${SERVER_SETTLE_MS}ms 뒤 재조회")
                    delay(SERVER_SETTLE_MS)
                    refresh(retry = false)
                }
            } catch (e: Exception) {
                MacDebugLogger.e("대시보드", "E-AND-DB-0402", "시리즈 대시보드 조회 실패: ${e.message}", e)
                PlanDebugLogger.e("대시보드", "E-AND-DB-0402", "시리즈 대시보드 조회 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isChecking = false)
            }
        }
    }

    private suspend fun loadMacState(): DashboardServiceUi {
        val app = MacJupJupRuntime
        val settings = app.preferences.getSettings()
        val stats = app.appRepository.overview()
        val running = isServiceRunning(settings.port)
        val ip = MacNetUtils.getLocalIp(getApplication()) ?: ""
        return DashboardServiceUi(
            isServerRunning = running,
            address = "http://$ip:${settings.port}",
            statValue1 = stats.totalApps,
            statValue2 = stats.activeSources,
            lastCollectedLabel = MacTimeUtils.formatRelative(stats.lastCollectedAt),
            crawlEnabled = settings.crawlEnabled,
        )
    }

    private suspend fun loadPlanState(): DashboardServiceUi {
        val app = PlanJupJupRuntime
        val settings = app.preferences.getSettings()
        val stats = app.planRepository.getStats()
        val running = isServiceRunning(settings.port)
        val ip = PlanNetUtils.getLocalIp(getApplication()) ?: ""
        return DashboardServiceUi(
            isServerRunning = running,
            address = "http://$ip:${settings.port}",
            statValue1 = stats.totalPlans,
            statValue2 = stats.activeSources,
            lastCollectedLabel = PlanTimeUtils.formatRelative(stats.lastCollectedAt),
            crawlEnabled = settings.crawlEnabled,
        )
    }

    /** 맥줍줍 — 지금 수집 */
    fun triggerMacCrawl() {
        viewModelScope.launch {
            val app = MacJupJupRuntime
            if (!_uiState.value.mac.crawlEnabled) {
                MacDebugLogger.w("수동수집", "수집 일시정지 상태 — 대시보드 수동 수집 스킵(mac)")
                return@launch
            }
            MacDebugLogger.i("수동수집", "대시보드 수동 수집 클릭(mac)")
            _uiState.value = _uiState.value.copy(mac = _uiState.value.mac.copy(isCrawling = true))
            try {
                app.crawlScheduler.triggerImmediate(app.database, null)
            } catch (e: Exception) {
                MacDebugLogger.e("수동수집", "E-AND-CRAWL-0201", "대시보드 수동 수집 예약 실패(mac): ${e.message}", e)
            } finally {
                _uiState.value = _uiState.value.copy(mac = _uiState.value.mac.copy(isCrawling = false))
                refresh()
            }
        }
    }

    /** 요금줍줍 — 지금 수집 */
    fun triggerPlanCrawl() {
        viewModelScope.launch {
            val app = PlanJupJupRuntime
            if (!_uiState.value.plan.crawlEnabled) {
                PlanDebugLogger.w("수동수집", "수집 일시정지 상태 — 대시보드 수동 수집 스킵(plan)")
                return@launch
            }
            PlanDebugLogger.i("수동수집", "대시보드 수동 수집 클릭(plan)")
            _uiState.value = _uiState.value.copy(plan = _uiState.value.plan.copy(isCrawling = true))
            try {
                app.crawlScheduler.triggerImmediate(null)
            } catch (e: Exception) {
                PlanDebugLogger.e("수동수집", "E-AND-CRAWL-0211", "대시보드 수동 수집 예약 실패(plan): ${e.message}", e)
            } finally {
                _uiState.value = _uiState.value.copy(plan = _uiState.value.plan.copy(isCrawling = false))
                refresh()
            }
        }
    }

    /** 맥줍줍 — 수집 중지/재개 */
    fun toggleMacCrawl() {
        viewModelScope.launch {
            val app = MacJupJupRuntime
            val enabled = !_uiState.value.mac.crawlEnabled
            try {
                withContext(Dispatchers.IO) { app.preferences.setCrawlEnabled(enabled) }
                if (enabled) {
                    app.crawlScheduler.scheduleAll(app.database)
                    MacDebugLogger.i("수동수집", "대시보드 수집 재개 — 주기 스케줄 재예약(mac)")
                } else {
                    app.crawlScheduler.cancelAll()
                    MacDebugLogger.i("수동수집", "대시보드 수집 일시정지 — 실행/예약 수집 취소(mac)")
                }
            } catch (e: Exception) {
                MacDebugLogger.e("수동수집", "E-AND-CRAWL-0221", "대시보드 수집 중지/재개 저장 실패(mac): ${e.message}", e)
            }
            refresh()
        }
    }

    /** 요금줍줍 — 수집 중지/재개 */
    fun togglePlanCrawl() {
        viewModelScope.launch {
            val app = PlanJupJupRuntime
            val enabled = !_uiState.value.plan.crawlEnabled
            try {
                withContext(Dispatchers.IO) { app.preferences.setCrawlEnabled(enabled) }
                if (enabled) {
                    app.crawlScheduler.scheduleAll()
                    PlanDebugLogger.i("수동수집", "대시보드 수집 재개 — 주기 스케줄 재예약(plan)")
                } else {
                    app.crawlScheduler.cancelAll()
                    PlanDebugLogger.i("수동수집", "대시보드 수집 일시정지 — 실행/예약 수집 취소(plan)")
                }
            } catch (e: Exception) {
                PlanDebugLogger.e("수동수집", "E-AND-CRAWL-0221", "대시보드 수집 중지/재개 저장 실패(plan): ${e.message}", e)
            }
            refresh()
        }
    }

    /** 맥줍줍 서버 시작/중지 */
    fun toggleMacServer() {
        val context = getApplication<Application>()
        if (_uiState.value.mac.isServerRunning) {
            MacDebugLogger.i("서버", "대시보드 서버 중지(mac)")
            MacHttpServerService.stop(context)
        } else {
            MacDebugLogger.i("서버", "대시보드 서버 시작(mac)")
            MacHttpServerService.start(context)
        }
        refresh()
    }

    /** 요금줍줍 서버 시작/중지 */
    fun togglePlanServer() {
        val context = getApplication<Application>()
        if (_uiState.value.plan.isServerRunning) {
            PlanDebugLogger.i("서버", "대시보드 서버 중지(plan)")
            PlanHttpServerService.stop(context)
        } else {
            PlanDebugLogger.i("서버", "대시보드 서버 시작(plan)")
            PlanHttpServerService.start(context)
        }
        refresh()
    }

    private fun isServiceRunning(port: Int): Boolean {
        return try {
            Socket("127.0.0.1", port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** 서버 비동기 기동 정착 대기 (실측 기동 ~0.4s + 여유) */
        private const val SERVER_SETTLE_MS = 2000L
    }
}
