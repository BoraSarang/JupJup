package com.borasarang.promptjournaljupjup.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.promptjournaljupjup.Constants
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.data.preferences.PjSettings
import com.borasarang.promptjournaljupjup.server.HttpServerService
import com.borasarang.promptjournaljupjup.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PromptJournalRuntime

    private val _settings = MutableStateFlow(PjSettings())
    val settings: StateFlow<PjSettings> = _settings.asStateFlow()

    private val _exaApiKey = MutableStateFlow("")
    val exaApiKey: StateFlow<String> = _exaApiKey.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _settings.value = app.preferences.getSettings()
                _exaApiKey.value = app.preferences.getExaApiKey()
            } catch (e: Exception) {
                DebugLogger.e("설정", Constants.ERR_SETTINGS_SAVE_FAILED, "설정 조회 실패: ${e.message}", e)
            }
        }
    }

    fun savePort(port: Int) {
        viewModelScope.launch {
            if (port !in Constants.MIN_PORT..Constants.MAX_PORT) {
                DebugLogger.w("설정", "포트 무효 값: $port")
                return@launch
            }
            val current = _settings.value
            if (port == current.port) return@launch
            DebugLogger.i("설정", "포트 변경 ${current.port} → $port (서버 재시작)")
            app.preferences.saveSettings(current.copy(port = port))
            HttpServerService.restart(getApplication())
            refresh()
        }
    }

    fun saveAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "자동 시작 → $enabled")
            app.preferences.saveSettings(_settings.value.copy(autoStart = enabled))
            refresh()
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        } catch (_: Exception) {
            false
        }
    }

    /** Exa API 키 저장 (빈 문자열 삭제) */
    fun saveExaApiKey(raw: String) {
        viewModelScope.launch {
            val trimmed = raw.trim()
            DebugLogger.i("설정", "Exa API 키 ${if (trimmed.isEmpty()) "삭제" else "저장됨"}")
            app.preferences.saveExaApiKey(trimmed)
            refresh()
        }
    }
}