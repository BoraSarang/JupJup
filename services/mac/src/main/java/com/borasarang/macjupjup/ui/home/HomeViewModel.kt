package com.borasarang.macjupjup.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.server.HttpServerService
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.common.util.NetUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isServerRunning: Boolean = false,
    val totalApps: Int = 0,
    val activeSources: Int = 0,
    val lastCollectedAt: Long? = null,
    val isCrawling: Boolean = false,
    val crawlEnabled: Boolean = true,
    val localIp: String? = null,
    val port: Int = 3000,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MacJupJupRuntime

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            DebugLogger.i("홈", "홈 상태 새로고침")
            try {
                // 소켓 접속은 메인 스레드 금지 → IO에서 조회
                // R5: getLocalIp도 블로킹(바인더+NIC)이라 IO 합류
                val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val settings = app.preferences.getSettings()
                    val stats = app.appRepository.overview()
                    Triple(settings, stats, NetUtils.getLocalIp(getApplication()))
                }
                val (settings, stats, ip) = fresh
                val port = settings.port
                // 서버 기동 지연 대비 최대 5회·1초 간격으로 기동 완료를 폴링
                val running = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var up = false
                    for (i in 1..5) {
                        up = isServiceRunning(port)
                        if (up) {
                            if (i > 1) DebugLogger.i("홈", "서버 기동 감지 (${i - 1}회 폴링)")
                            return@withContext up
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                    up
                }
                _uiState.value = HomeUiState(
                    isServerRunning = running,
                    totalApps = stats.totalApps,
                    activeSources = stats.activeSources,
                    lastCollectedAt = stats.lastCollectedAt,
                    isCrawling = false,
                    crawlEnabled = settings.crawlEnabled,
                    localIp = ip,
                    port = port,
                )
            } catch (e: Exception) {
                DebugLogger.e("홈", "E-AND-DB-0404", "홈 상태 조회 실패: ${e.message}", e)
            }
        }
    }

    fun triggerManualCrawl() {
        viewModelScope.launch {
            if (!_uiState.value.crawlEnabled) {
                DebugLogger.w("수동수집", "수집 일시정지 상태 — 수동 수집 스킵")
                return@launch
            }
            DebugLogger.i("수동수집", "수동 수집 버튼 클릭")
            _uiState.value = _uiState.value.copy(isCrawling = true)
            try {
                app.crawlScheduler.triggerImmediate(null)
            } catch (e: Exception) {
                DebugLogger.e("수동수집", "E-AND-CRAWL-0201", "수동 수집 예약 실패: ${e.message}", e)
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

    /** 수집 일시정지 토글. 중지 시 실행/예약 취소, 재개 시 주기 스케줄 재예약 */
    fun toggleCrawl() {
        viewModelScope.launch {
            val enabled = !_uiState.value.crawlEnabled
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.preferences.setCrawlEnabled(enabled)
                }
                if (enabled) {
                    app.crawlScheduler.scheduleAll()
                    DebugLogger.i("수동수집", "수집 재개 — 주기 스케줄 재예약")
                } else {
                    app.crawlScheduler.cancelAll()
                    DebugLogger.i("수동수집", "수집 일시정지 — 실행/예약 수집 취소")
                }
            } catch (e: Exception) {
                DebugLogger.e("수동수집", "E-AND-CRAWL-0221", "수집 중지/재개 저장 실패: ${e.message}", e)
            }
            refresh()
        }
    }

    /** 로컬 포트 개방 여부. 반드시 백그라운드 스레드에서 호출 (R5: 타임아웃 내장 공용 헬퍼) */
    private fun isServiceRunning(port: Int): Boolean = NetUtils.isPortOpen(port)
}
