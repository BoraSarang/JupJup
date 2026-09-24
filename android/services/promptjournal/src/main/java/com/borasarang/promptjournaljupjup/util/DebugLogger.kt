package com.borasarang.promptjournaljupjup.util

import android.content.Context
import com.borasarang.common.log.JupLog
import com.borasarang.common.log.ServiceLogger
import com.borasarang.promptjournaljupjup.BuildConfig
import java.io.File

/**
 * 프롬프트 저널 전역 로거.
 * 포맷(i/d/w/e/perf/raw)은 common ServiceLogger 공용 (R38).
 */
object DebugLogger : ServiceLogger("PromptJournal") {
    fun init(context: Context) {
        JupLog.init(TAG, BuildConfig.DEBUG, File(context.applicationContext.filesDir, "logs"))
    }

    const val TAG = "PromptJournal"
}
