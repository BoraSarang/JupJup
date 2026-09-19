package com.borasarang.promptjournaljupjup.util

import android.content.Context
import android.util.Log
import com.borasarang.common.log.JupLog
import com.borasarang.promptjournaljupjup.BuildConfig
import java.io.File
import timber.log.Timber

/**
 * 프롬프트 저널 전역 로거.
 * - [INFO] [FEATURE] <기능명>: 신규 기능 진입점 필수 1개 이상
 * - [ERROR] E-AND-...: 실패 경로, error_message_ko.json 매핑
 */
object DebugLogger {
    fun init(context: Context) {
        JupLog.init(TAG, BuildConfig.DEBUG, File(context.applicationContext.filesDir, "logs"))
    }

    fun i(feature: String, message: String) {
        Timber.i("[INFO] [%s] %s", feature, message)
    }

    fun d(feature: String, message: String) {
        Timber.d("[%s] %s", feature, message)
    }

    fun w(feature: String, message: String) {
        Timber.w("[WARN] [%s] %s", feature, message)
    }

    fun e(feature: String, errorCode: String, message: String, throwable: Throwable? = null) {
        Timber.e(throwable, "[ERROR] %s [%s] %s", errorCode, feature, message)
    }

    fun perf(feature: String, message: String) {
        Timber.i("[PERF] [%s] %s", feature, message)
    }

    const val TAG = "PromptJournal"

    fun raw(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }
}
