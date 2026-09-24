package com.borasarang.common.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * 서비스 공용 로그 코어. 각 서비스의 `DebugLogger` 파사드가 위임한다.
 * - Logcat 트리: 디버그 빌드에서만, 프로세스당 1회
 * - 파일 트리: 항상 (디버그·릴리스), 태그별 1회, `files/logs/<tag>.log`, 512KB 로테이션
 *
 * R2: 릴리스에서 로그가 전부 소실되던 문제 해결. 파일 로그는 `adb run-as` 또는
 * 앱 내보내기 없이 버그 리포트 첨부로 회수한다.
 */
object JupLog {

    private val lock = Any()
    private var logcatPlanted = false
    private val fileTags = mutableSetOf<String>()

    fun init(tag: String, isDebug: Boolean, logDir: File?) {
        synchronized(lock) {
            if (isDebug && !logcatPlanted) {
                Timber.plant(Timber.DebugTree())
                logcatPlanted = true
            }
            if (logDir != null && fileTags.add(tag)) {
                try {
                    Timber.plant(FileTree(tag, logDir))
                } catch (_: Exception) {
                    fileTags.remove(tag)
                }
            }
        }
    }

    /** 태그별 파일 트리. 쓰기 실패는 조용히 무시 (로깅이 앱을 죽이면 안 됨) */
    private class FileTree(private val fileTag: String, logDir: File) : Timber.Tree() {
        private val file = File(logDir, "$fileTag.log")
        private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.KOREA)
        private var approxBytes = 0L

        init {
            logDir.mkdirs()
            approxBytes = try {
                if (file.exists()) file.length() else 0L
            } catch (_: Exception) {
                0L
            }
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            try {
                val level = when (priority) {
                    Log.VERBOSE -> "V"
                    Log.DEBUG -> "D"
                    Log.INFO -> "I"
                    Log.WARN -> "W"
                    Log.ERROR -> "E"
                    else -> "?"
                }
                val trace = if (t != null) "\n${Log.getStackTraceString(t)}" else ""
                val line = "${timeFormat.format(java.util.Date())} $level/${tag ?: fileTag}: $message$trace\n"
                rotateIfNeeded(line.length.toLong())
                file.appendText(line)
                approxBytes += line.length
            } catch (_: Exception) {
            }
        }

        private fun rotateIfNeeded(incoming: Long) {
            try {
                if (approxBytes + incoming < MAX_FILE_BYTES) return
                val backup = File(file.parent, "${file.name}.1")
                backup.delete()
                file.renameTo(backup)
                approxBytes = 0L
            } catch (_: Exception) {
            }
        }

        companion object {
            private const val MAX_FILE_BYTES = 512L * 1024L
        }
    }
}
