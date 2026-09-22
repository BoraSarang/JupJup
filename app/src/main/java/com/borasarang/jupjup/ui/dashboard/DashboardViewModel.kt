package com.borasarang.jupjup.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.borasarang.common.util.NetUtils
import com.borasarang.jupjup.ui.nav.Service
import com.borasarang.jupjup.ui.nav.ServiceRegistry
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardServiceUi(
    val isServerRunning: Boolean = false,
    val address: String = "",
    val statValue1: Int = 0,
    val statValue2: Int = 0,
    val lastCollectedLabel: String = "",
    val isCrawling: Boolean = false,
    val crawlEnabled: Boolean = true,
    /** 네트워크 사용량 1줄 (R42b, 30일 합산·plan 24h·pj 프로세스 누적) */
    val netLabel: String = "",
)

data class DashboardUiState(
    val isChecking: Boolean = true,
    val mac: DashboardServiceUi = DashboardServiceUi(),
    val plan: DashboardServiceUi = DashboardServiceUi(),
    val pj: DashboardServiceUi = DashboardServiceUi(),
    val cm: DashboardServiceUi = DashboardServiceUi(),
) {
    fun forService(service: Service): DashboardServiceUi = when (service) {
        Service.MAC -> mac
        Service.PLAN -> plan
        Service.PROMPTJOURNAL -> pj
        Service.COMMUNITY -> cm
    }
}

/**
 * 줍줍 시리즈 대시보드 — 네 서비스 상태를 한 화면에서 병렬 조회.
 * R3: 서비스별 분기는 ServiceAdapter가 소유, 여기서는 Service 키로만 다룬다.
 * 테스트용 어댑터 주입을 위해 팩토리 경유 생성 ([Factory]).
 */
class DashboardViewModel(
    app: Application,
    private val adapters: Map<Service, ServiceAdapter> = ServiceRegistry.adapters,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(app) {

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
                val ip = withContext(ioDispatcher) {
                    NetUtils.getLocalIp(getApplication()) ?: ""
                }
                val fresh = withContext(ioDispatcher) {
                    // 4개 서비스 순차 조회는 소켓 타임아웃(500ms)이 합산돼 p95를 초과한다 → 병렬 조회
                    coroutineScope {
                        listOf(
                            async { adapters.getValue(Service.MAC).loadState(ip) },
                            async { adapters.getValue(Service.PLAN).loadState(ip) },
                            async { adapters.getValue(Service.PROMPTJOURNAL).loadState(ip) },
                            async { adapters.getValue(Service.COMMUNITY).loadState(ip) },
                        ).awaitAll()
                    }
                }
                _uiState.value = DashboardUiState(
                    isChecking = false,
                    mac = fresh[0],
                    plan = fresh[1],
                    pj = fresh[2],
                    cm = fresh[3],
                )
                val anyStopped = fresh.any { !it.isServerRunning }
                if (retry && anyStopped) {
                    MacDebugLogger.i("대시보드", "서버 미기동 감지 — ${SERVER_SETTLE_MS}ms 뒤 1회 재조회")
                    delay(SERVER_SETTLE_MS)
                    refresh(retry = false)
                }
            } catch (e: Exception) {
                MacDebugLogger.e("대시보드", "E-AND-DB-0404", "시리즈 대시보드 조회 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isChecking = false)
            }
        }
    }

    /** 지금 수집 */
    fun triggerCrawl(service: Service) {
        viewModelScope.launch {
            val adapter = adapters[service] ?: return@launch
            setCrawling(service, true)
            try {
                adapter.triggerCrawlIfEnabled(_uiState.value.forService(service).crawlEnabled)
            } finally {
                setCrawling(service, false)
                refresh()
            }
        }
    }

    /** 수집 중지/재개 */
    fun toggleCrawl(service: Service) {
        viewModelScope.launch {
            val adapter = adapters[service] ?: return@launch
            val enabled = !_uiState.value.forService(service).crawlEnabled
            withContext(ioDispatcher) { adapter.setCrawlEnabled(enabled) }
            refresh()
        }
    }

    /** 서버 시작/중지 */
    fun toggleServer(service: Service) {
        val adapter = adapters[service] ?: return
        val context = getApplication<Application>()
        adapter.setServerRunning(context, _uiState.value.forService(service).isServerRunning)
        refresh()
    }

    private fun setCrawling(service: Service, crawling: Boolean) {
        _uiState.value = when (service) {
            Service.MAC -> _uiState.value.copy(mac = _uiState.value.mac.copy(isCrawling = crawling))
            Service.PLAN -> _uiState.value.copy(plan = _uiState.value.plan.copy(isCrawling = crawling))
            Service.PROMPTJOURNAL -> _uiState.value.copy(pj = _uiState.value.pj.copy(isCrawling = crawling))
            Service.COMMUNITY -> _uiState.value.copy(cm = _uiState.value.cm.copy(isCrawling = crawling))
        }
    }

    class Factory(
        private val app: Application,
        private val adapters: Map<Service, ServiceAdapter> = ServiceRegistry.adapters,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(app, adapters, ioDispatcher) as T
        }
    }

    companion object {
        /** 서버 비동기 기동 정착 대기 (실측 기동 ~0.4s + 여유) */
        private const val SERVER_SETTLE_MS = 2000L
    }
}
