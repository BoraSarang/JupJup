package com.borasarang.planjupjup.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PlanJupJupRuntime

    private val _settings = MutableStateFlow(
        SettingsData(
            port = Constants.DEFAULT_PORT,
            retentionDays = Constants.DEFAULT_RETENTION_DAYS,
            autoStart = Constants.DEFAULT_AUTO_START,
            watchdogIntervalSec = Constants.DEFAULT_WATCHDOG_INTERVAL_SEC,
        ),
    )
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _settings.value = app.preferences.getSettings()
            } catch (e: Exception) {
                DebugLogger.e("설정", "E-AND-DB-0404", "설정 조회 실패: ${e.message}", e)
            }
        }
    }

    fun saveAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "자동 시작 → $enabled")
            try {
                app.preferences.saveSettings(_settings.value.copy(autoStart = enabled))
            } catch (e: Exception) {
                DebugLogger.e("설정", "E-AND-DB-0404", "자동 시작 저장 실패: ${e.message}", e)
            }
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
}
