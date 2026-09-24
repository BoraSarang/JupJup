package com.borasarang.communityjupjup.util

import android.content.Context
import com.borasarang.common.log.JupLog
import com.borasarang.common.log.ServiceLogger
import com.borasarang.communityjupjup.BuildConfig
import java.io.File

/**
 * 앱 전역 로거. 모든 로그는 이 객체를 경유한다.
 * 포맷(i/d/w/e/perf/raw)은 common ServiceLogger 공용 (R38).
 */
object DebugLogger : ServiceLogger("CommunityJupJup") {
    /** R2: JupLog 코어로 위임. 파일 로그는 files/logs/CommunityJupJup.log에 항상 기록 */
    fun init(context: Context) {
        JupLog.init(TAG, BuildConfig.DEBUG, File(context.applicationContext.filesDir, "logs"))
    }

    /** 릴리스 로그캣에서도 식별 가능하도록 태그 고정 */
    const val TAG = "CommunityJupJup"
}
