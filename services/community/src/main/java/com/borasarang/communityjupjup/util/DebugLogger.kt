package com.borasarang.communityjupjup.util

import android.content.Context
import android.util.Log
import com.borasarang.common.log.JupLog
import com.borasarang.communityjupjup.BuildConfig
import java.io.File
import timber.log.Timber

/**
 * 앱 전역 로거. 모든 로그는 이 객체를 경유한다.
 * - [INFO] [FEATURE] <기능명>: 신규 기능 진입점 필수 1개 이상
 * - [ERROR] E-AND-...: 실패 경로, error_message_ko.json 매핑
 * - [PERF]/[CACHE] 레벨 포함
 */
object DebugLogger {
    /** R2: JupLog 코어로 위임. 파일 로그는 files/logs/CommunityJupJup.log에 항상 기록 */
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

    /** 릴리스 로그캣에서도 식별 가능하도록 태그 고정 */
    const val TAG = "CommunityJupJup"

    fun raw(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }
}
