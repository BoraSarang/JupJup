package com.borasarang.common.log

import android.util.Log
import timber.log.Timber

/**
 * 서비스 공용 로거 베이스. 각 서비스는 TAG만 달리해 상속한다 (R38):
 * `object DebugLogger : ServiceLogger("MacJupJup")`.
 * 모든 로그는 이 객체를 경유한다.
 * - [INFO] [FEATURE] <기능명>: 신규 기능 진입점 필수 1개 이상
 * - [ERROR] E-AND-...: 실패 경로, error_message_ko.json 매핑
 * - [PERF] 레벨 포함
 */
open class ServiceLogger(private val tag: String) {
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
    fun raw(priority: Int, message: String) {
        Log.println(priority, tag, message)
    }
}
