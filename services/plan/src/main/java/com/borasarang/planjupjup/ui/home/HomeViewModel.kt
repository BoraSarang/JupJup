package com.borasarang.planjupjup.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.server.HttpServerService
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.NetUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isServerRunning: Boolean = false,
    val totalPlans: Int = 0,
    val activeSources: Int = 0,
    val lastCollectedAt: Long? = null,
    val isCrawling: Boolean = false,
    val localIp: String? = null,
    val port: Int = 3000,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PlanJupJupRuntime

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            DebugLogger.i("홈", "홈 상태 새로고침")
            try {
                // 소켓 접속은 메인 스레드 금지 → IO에서 조회
                val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val settings = app.preferences.getSettings()
                    val stats = app.planRepository.getStats()
                    Triple(settings, stats, isServiceRunning(settings.port))
                }
                val (settings, stats, running) = fresh
                _uiState.value = HomeUiState(
                    isServerRunning = running,
                    totalPlans = stats.totalPlans,
                    activeSources = stats.activeSources,
                    lastCollectedAt = stats.lastCollectedAt,
                    isCrawling = false,
                    localIp = NetUtils.getLocalIp(getApplication()),
                    port = settings.port,
                )
            } catch (e: Exception) {
                DebugLogger.e("홈", "E-AND-DB-0402", "홈 상태 조회 실패: ${e.message}", e)
            }
        }
    }

    fun triggerManualCrawl() {
        viewModelScope.launch {
            DebugLogger.i("수동수집", "수동 수집 버튼 클릭")
            _uiState.value = _uiState.value.copy(isCrawling = true)
            try {
                app.crawlScheduler.triggerImmediate(null)
            } catch (e: Exception) {
                DebugLogger.e("수동수집", "E-AND-CRAWL-0211", "수동 수집 예약 실패: ${e.message}", e)
            } finally {
                _uiState.value = _uiState.value.copy(isCrawling = false)
                refresh()
            }
        }
    }

    fun startServer() {
        HttpServerService.start(getApplication())
        refresh()
    }

    fun stopServer() {
        HttpServerService.stop(getApplication())
        refresh()
    }

    /** 로컬 포트 개방 여부. 반드시 백그라운드 스레드에서 호출 */
    private fun isServiceRunning(port: Int): Boolean {
        return try {
            java.net.Socket("127.0.0.1", port).use { true }
        } catch (_: Exception) {
            false
        }
    }
}
