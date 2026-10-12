package com.borasarang.macjupjup.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 클라우드 push 재시도 워커 (PLAN_v27 T-27-2).
 * 입력은 id 목록만 (페이로드는 DB에서 재조회 — WorkManager 10KB 제한 회피).
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        val ids = parseIds(inputData.getString(KEY_IDS).orEmpty()).take(MAX_IDS)
        if (ids.isEmpty()) return Result.success()
        return try {
            val app = MacJupJupRuntime
            val body = when (kind) {
                KIND_APPS -> {
                    val rows = app.database.appDao().getByIds(ids)
                    if (rows.isEmpty()) return Result.success()
                    val maps = app.database.appSourceMappingDao().getByApps(rows.map { it.id })
                    CloudUploader.appsPayload(rows, maps)
                }
                KIND_NEWS -> {
                    val rows = app.database.newsArticleDao().getByIds(ids)
                    if (rows.isEmpty()) return Result.success()
                    val rels = app.database.newsArticleDao().getRelationsByNewsIds(ids)
                    CloudUploader.newsPayload(rows, rels)
                }
                KIND_COMMUNITY -> {
                    val rows = app.database.communityPostDao().getByIds(ids)
                    if (rows.isEmpty()) return Result.success()
                    CloudUploader.communityPayload(rows)
                }
                else -> return Result.failure()
            }
            if (pushNow(kind, body)) {
                Result.success()
            } else if (runAttemptCount >= MAX_ATTEMPTS) {
                DebugLogger.e("업로드", "E-AND-NET-0304", "클라우드 push 재시도 초과 kind=$kind ids=${ids.size}")
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            DebugLogger.e("업로드", "E-AND-NET-0302", "push 워커 실패: ${e.message}")
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KIND_APPS = "apps"
        const val KIND_NEWS = "news"
        const val KIND_COMMUNITY = "community"
        private const val KEY_KIND = "kind"
        private const val KEY_IDS = "ids"
        private const val MAX_IDS = 200
        private const val MAX_ATTEMPTS = 3

        private fun parseIds(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(raw)
                List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** 즉시 push 시도 — 설정 없음이면 noop 성공, 실패면 false */
        suspend fun pushNow(kind: String, body: String): Boolean {
            val app = MacJupJupRuntime
            val (url, token) = try {
                app.preferences.getCloud()
            } catch (e: Exception) {
                DebugLogger.e("업로드", "E-AND-DB-0404", "클라우드 설정 조회 실패: ${e.message}")
                return false
            }
            if (url.isBlank() || token.isBlank()) return true
            val res = withContext(Dispatchers.IO) {
                CloudUploader.post(url, token, kind, body)
            }
            if (res.ok) {
                DebugLogger.i("업로드", "클라우드 push 성공 kind=$kind ${body.length}B")
                return true
            }
            val code = if (res.code == 401) "E-AND-NET-0303" else "E-AND-NET-0302"
            DebugLogger.e("업로드", code, "클라우드 push 실패 kind=$kind code=${res.code}: ${res.body}")
            return false
        }

        /** 실패분 재시도 예약 (id만 전달) */
        fun enqueueRetry(kind: String, ids: List<String>) {
            if (ids.isEmpty()) return
            try {
                val req = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(workDataOf(
                        KEY_KIND to kind,
                        KEY_IDS to org.json.JSONArray(ids.take(MAX_IDS)).toString(),
                    ))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(MacJupJupRuntime.context).enqueueUniqueWork(
                    "cloud-upload-$kind",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    req,
                )
            } catch (e: Exception) {
                DebugLogger.e("업로드", "E-AND-NET-0302", "재시도 예약 실패: ${e.message}")
            }
        }
    }
}
