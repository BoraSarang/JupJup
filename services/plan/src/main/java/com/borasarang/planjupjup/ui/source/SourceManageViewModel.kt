package com.borasarang.planjupjup.ui.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.data.repository.SourceStatus
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourceManageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PlanJupJupRuntime

    val sources = app.sourceRepository.observeListItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSource(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = app.sourceRepository.getById(id) ?: return@launch
                if (current.enabled == enabled) return@launch
                app.sourceRepository.toggleEnabled(id)
                if (enabled) {
                    app.crawlScheduler.scheduleSource(current.copy(enabled = true))
                } else {
                    app.crawlScheduler.cancelSource(id)
                }
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
                DebugLogger.e("수동수집", "E-AND-CRAWL-0211", "즉시 실행 실패 id=$id: ${e.message}", e)
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
    return "$type · ${com.borasarang.planjupjup.util.TimeUtils.formatInterval(intervalMinutes)}"
}

/** 최근 로그 한 줄: 마지막 수집일 + 발견/신규/갱신 통계 */
fun com.borasarang.planjupjup.data.repository.RecentLog.describeStats(): String {
    val at = com.borasarang.planjupjup.util.TimeUtils.formatRelative(finishedAt ?: startedAt)
    return if (status == Constants.STATUS_SUCCESS) {
        "마지막 수집: $at · 발견 ${plansFound} · 신규 ${plansNew} · 갱신 ${plansUpdated}"
    } else {
        "마지막 수집: $at · 실패: ${errorMessage?.take(60) ?: "?"}"
    }
}
