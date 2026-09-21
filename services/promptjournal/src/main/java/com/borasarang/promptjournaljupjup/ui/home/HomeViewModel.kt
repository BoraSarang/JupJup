package com.borasarang.promptjournaljupjup.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.common.ai.AiProvider
import com.borasarang.promptjournaljupjup.util.DebugLogger
import com.borasarang.common.util.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isServerRunning: Boolean = false,
    val localIp: String? = null,
    val port: Int = 3030,
    val lastExecutionLabel: String? = null,
    val executionCount: Int = 0,
    val promptCount: Int = 0,
    val activePromptCount: Int = 0,
    val isExecuting: Boolean = false,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = PromptJournalRuntime
                if (!app.isInitialized) return@launch
                val settings = app.preferences.getSettings()
                val count = app.promptExecutionRepository.count()
                val recent = app.promptExecutionRepository.getRecent(1)
                val lastLabel = recent.firstOrNull()?.let {
                    val time = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(it.executedAt))
                    "$time · ${AiProvider.fromString(it.provider).displayName} · ${it.status}"
                }
                val prompts = app.promptRepository.getAll()

                _uiState.value = HomeUiState(
                    isServerRunning = NetUtils.isPortOpen(settings.port),
                    localIp = com.borasarang.common.util.NetUtils.getLocalIp(app.context),
                    port = settings.port,
                    lastExecutionLabel = lastLabel,
                    executionCount = count,
                    promptCount = prompts.size,
                    activePromptCount = prompts.count { it.enabled },
                )
            } catch (e: Exception) {
                DebugLogger.e("홈", "E-AND-DB-0404", "상태 조회 실패: ${e.message}", e)
            }
        }
    }

    fun executeNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            try {
                val first = PromptJournalRuntime.promptRepository.getEnabled().firstOrNull()
                if (first == null) {
                    DebugLogger.w("홈", "실행할 프롬프트 없음")
                } else {
                    PromptJournalRuntime.scheduler.triggerImmediate(first.id)
                    DebugLogger.i("홈", "즉시 실행 예약 완료 [${first.id}] ${first.title}")
                }
            } catch (e: Exception) {
                DebugLogger.e("홈", "E-AND-REPORT-0804", "즉시 실행 실패: ${e.message}", e)
            }
            kotlinx.coroutines.delay(1000)
            _uiState.value = _uiState.value.copy(isExecuting = false)
        }
    }
}