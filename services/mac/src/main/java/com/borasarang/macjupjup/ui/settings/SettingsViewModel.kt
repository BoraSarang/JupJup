package com.borasarang.macjupjup.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.data.repository.RecentLog
import com.borasarang.macjupjup.data.repository.SettingsData
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.maskToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MacJupJupRuntime

    private val _settings = MutableStateFlow(
        SettingsData(
            port = Constants.DEFAULT_PORT,
            retentionDays = Constants.DEFAULT_RETENTION_DAYS,
            autoStart = Constants.DEFAULT_AUTO_START,
            watchdogIntervalSec = Constants.DEFAULT_WATCHDOG_INTERVAL_SEC,
        ),
    )
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<RecentLog>>(emptyList())
    val logs: StateFlow<List<RecentLog>> = _logs.asStateFlow()

    private val _cleanupResult = MutableStateFlow<String?>(null)
    val cleanupResult: StateFlow<String?> = _cleanupResult.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _settings.value = app.preferences.getSettings()
                _logs.value = app.sourceRepository.recentLogs(50)
            } catch (e: Exception) {
                DebugLogger.e("설정", "E-AND-DB-0402", "설정 조회 실패: ${e.message}", e)
            }
        }
    }

    fun savePort(port: Int) {
        viewModelScope.launch {
            if (port !in Constants.MIN_PORT..Constants.MAX_PORT) {
                DebugLogger.w("설정", "포트 무효 값: $port (E-AND-VALID-0502)")
                return@launch
            }
            val current = _settings.value
            if (port == current.port) return@launch
            DebugLogger.i("설정", "포트 변경 ${current.port} → $port (서버 재시작)")
            app.preferences.saveSettings(current.copy(port = port))
            com.borasarang.macjupjup.server.HttpServerService.start(getApplication())
            refresh()
        }
    }

    /** 토큰 입력: 빈 문자열이면 삭제, 아니면 교체. 값은 로그에 남기지 않음 */
    fun saveToken(raw: String) {
        viewModelScope.launch {
            val token = raw.trim()
            if (token.isNotEmpty() && !token.startsWith("ghp_") && !token.startsWith("github_pat_")) {
                DebugLogger.w("설정", "토큰 형식 경고 (E-AND-VALID-0503) — 그래도 저장")
            }
            DebugLogger.i("설정", "GitHub 토큰 ${if (token.isEmpty()) "삭제" else "저장됨 (${maskToken(token)})"}")
            app.preferences.saveSettings(_settings.value.copy(githubToken = token))
            refresh()
        }
    }

    fun saveTranslateKo(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "한글 번역 → $enabled")
            app.preferences.saveSettings(_settings.value.copy(translateKo = enabled))
            refresh()
        }
    }

    fun saveRetention(days: Int) {        viewModelScope.launch {
            if (days != 30 && days != 90) return@launch
            DebugLogger.i("설정", "보관 기간 변경 → ${days}일")
            app.preferences.saveSettings(_settings.value.copy(retentionDays = days))
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

    fun saveWatchdog(sec: Int) {
        viewModelScope.launch {
            if (sec !in Constants.MIN_WATCHDOG_SEC..Constants.MAX_WATCHDOG_SEC) {
                DebugLogger.w("설정", "Watchdog 주기 무효 값: $sec")
                return@launch
            }
            DebugLogger.i("설정", "Watchdog 주기 → ${sec}초")
            app.preferences.saveSettings(_settings.value.copy(watchdogIntervalSec = sec))
            refresh()
        }
    }

    fun saveNotifCrawlComplete(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "수집 완료 알림 → $enabled")
            app.preferences.saveSettings(_settings.value.copy(notifCrawlComplete = enabled))
            refresh()
        }
    }

    fun saveNotifNewApp(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "신규 앱 알림 → $enabled")
            app.preferences.saveSettings(_settings.value.copy(notifNewApp = enabled))
            refresh()
        }
    }

    fun saveNotifFailure(enabled: Boolean) {
        viewModelScope.launch {
            DebugLogger.i("설정", "실패 알림 → $enabled")
            app.preferences.saveSettings(_settings.value.copy(notifFailure = enabled))
            refresh()
        }
    }

    fun cleanupNow() {
        viewModelScope.launch {
            DebugLogger.i("정리", "수동 정리 시작")
            try {
                val deleted = app.appRepository.cleanup(_settings.value.retentionDays)
                _cleanupResult.value = "정리 완료: 앱 ${deleted}건 삭제"
            } catch (e: Exception) {
                DebugLogger.e("정리", "E-AND-DB-0402", "수동 정리 실패: ${e.message}", e)
                _cleanupResult.value = "정리 실패: ${e.message}"
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
