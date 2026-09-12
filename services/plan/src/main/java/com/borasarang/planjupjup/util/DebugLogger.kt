package com.borasarang.planjupjup.util

import android.util.Log
import com.borasarang.planjupjup.BuildConfig
import timber.log.Timber

/**
 * 앱 전역 로거. 모든 로그는 이 객체를 경유한다.
 * - [INFO] [FEATURE] <기능명>: 신규 기능 진입점 필수 1개 이상
 * - [ERROR] E-AND-...: 실패 경로, error_message_ko.json 매핑
 * - [PERF]/[CACHE] 레벨 포함
 */
object DebugLogger {
    fun init() {
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
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
    const val TAG = "PlanJupJup"

    fun raw(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }
}
