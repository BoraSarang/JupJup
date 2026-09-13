package com.borasarang.macjupjup.ui.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.data.repository.SourceListItem
import com.borasarang.macjupjup.data.repository.SourceStatus
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SourceManageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MacJupJupRuntime

    private val _sources = MutableStateFlow<List<SourceListItem>>(emptyList())
    val sources: StateFlow<List<SourceListItem>> = _sources.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _sources.value = app.sourceRepository.getListItems()
            } catch (e: Exception) {
                DebugLogger.e("소스관리", "E-AND-DB-0404", "소스 목록 조회 실패: ${e.message}", e)
            }
        }
    }

    fun toggleSource(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = app.sourceRepository.getById(id) ?: return@launch
                if (current.enabled == enabled) return@launch
                app.sourceRepository.toggleEnabled(id, enabled)
                DebugLogger.i("소스관리", "소스 토글 $id → $enabled")
                if (enabled) {
                    app.crawlScheduler.scheduleSource(current.copy(enabled = true))
                } else {
                    app.crawlScheduler.cancelSource(id)
                }
                refresh()
            } catch (e: Exception) {
                DebugLogger.e("소스관리", "E-AND-DB-0402", "소스 토글 실패 id=$id: ${e.message}", e)
            }
        }
    }

    fun runNow(id: String) {
        viewModelScope.launch {
            DebugLogger.i("수동수집", "소스 즉시 실행 id=$id")
            try {
                app.crawlScheduler.triggerImmediate(id)
            } catch (e: Exception) {
                DebugLogger.e("수동수집", "E-AND-CRAWL-0201", "즉시 실행 실패 id=$id: ${e.message}", e)
            }
        }
    }

    /** 수집 주기 변경 후 스케줄 갱신 */
    fun setInterval(id: String, minutes: Int) {
        viewModelScope.launch {
            try {
                val rescheduled = app.sourceRepository.setIntervalMinutes(id, minutes)
                if (rescheduled) {
                    app.sourceRepository.getById(id)?.let { app.crawlScheduler.scheduleSource(it) }
                    refresh()
                }
            } catch (e: Exception) {
                DebugLogger.e("소스관리", "E-AND-DB-0402", "주기 변경 실패 id=$id: ${e.message}", e)
            }
        }
    }

    fun statusColor(status: String): Int {
        return when (status) {
            Constants.STATUS_SUCCESS -> android.graphics.Color.parseColor("#2E7D32")
            Constants.STATUS_FAILED -> android.graphics.Color.parseColor("#C62828")
            Constants.STATUS_RUNNING -> android.graphics.Color.parseColor("#EF6C00")
            else -> android.graphics.Color.parseColor("#757575")
        }
    }
}

fun SourceStatus.describe(): String {
    return "$type · ${TimeUtils.formatInterval(intervalMinutes)}"
}

/** 최근 로그 한 줄: 마지막 수집일 + 발견/신규/갱신 통계 */
fun com.borasarang.macjupjup.data.repository.RecentLog.describeStats(context: android.content.Context): String {
    val at = TimeUtils.formatRelative(finishedAt ?: startedAt)
    return if (status == Constants.STATUS_SUCCESS) {
        // R7: 본문 리소스화 (화면 표시 문구와 동일 출력)
        context.getString(
            com.borasarang.macjupjup.R.string.mac_source_stats_ok,
            at, plansFound, plansNew, plansUpdated,
        )
    } else {
        context.getString(
            com.borasarang.macjupjup.R.string.mac_source_stats_fail,
            at, errorMessage?.take(60) ?: "?",
        )
    }
}
