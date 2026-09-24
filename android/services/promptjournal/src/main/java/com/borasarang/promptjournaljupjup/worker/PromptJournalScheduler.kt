package com.borasarang.promptjournaljupjup.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.util.DebugLogger
import java.util.concurrent.TimeUnit

class PromptJournalScheduler(private val context: Context) {

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** DB의 모든 활성 프롬프트 기준 재예약 */
    suspend fun rescheduleAll(prompts: List<Prompt> = emptyList()) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG_PROMPT)
        val list = if (prompts.isNotEmpty()) prompts else getAllPrompts()
        for (p in list) {
            if (p.enabled) scheduleOne(p) else cancelOne(p.id)
        }
        DebugLogger.i("스케줄", "프롬프트 재예약 완료 — 활성 ${list.count { it.enabled }}개")
    }

    /** 단일 프롬프트 예약 (등록/수정/토글 시) */
    suspend fun scheduleOne(prompt: Prompt) {
        val wm = WorkManager.getInstance(context)
        if (!prompt.enabled) {
            cancelOne(prompt.id)
            return
        }
        wm.cancelUniqueWork(uniqueName(prompt.id))

        val delayMs = calculateDelayUntilTime(prompt.scheduleValue)
        val data = Data.Builder().putLong(KEY_PROMPT_ID, prompt.id).build()

        when (prompt.scheduleType) {
            "daily" -> {
                val req = PeriodicWorkRequestBuilder<PromptJournalWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints())
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(TAG_PROMPT)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    uniqueName(prompt.id),
                    ExistingPeriodicWorkPolicy.REPLACE,
                    req,
                )
            }
            "once" -> {
                val req = OneTimeWorkRequestBuilder<PromptJournalWorker>()
                    .setConstraints(constraints())
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(TAG_PROMPT)
                    .build()
                wm.enqueueUniqueWork(
                    uniqueName(prompt.id),
                    ExistingWorkPolicy.REPLACE,
                    req,
                )
            }
            else -> {
                DebugLogger.w("스케줄", "알 수 없는 스케줄 타입: ${prompt.scheduleType}")
            }
        }
        DebugLogger.i("스케줄", "[${prompt.id}] ${prompt.title} 예약 (${prompt.scheduleValue}, ${delayMs / 60000}분 후)")
    }

    fun cancelOne(promptId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(promptId))
    }

    /** 즉시 실행 (promptId 지정) */
    fun triggerImmediate(promptId: Long) {
        val req = OneTimeWorkRequestBuilder<PromptJournalWorker>()
            .setConstraints(constraints())
            .setInputData(Data.Builder().putLong(KEY_PROMPT_ID, promptId).build())
            .addTag(TAG_PROMPT)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pj_immediate_$promptId",
            ExistingWorkPolicy.KEEP,
            req,
        )
        DebugLogger.i("실행", "[$promptId] 즉시 실행 예약")
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PROMPT)
        DebugLogger.i("스케줄", "전체 스케줄 취소")
    }

    /**
     * 개명(프롬프트팩토리 → 프롬프트 저널) 1회 마이그레이션: 구 태그·구 unique명 Work 취소.
     * 구 Worker 클래스가 소멸했으므로 방치 시 실패 반복 — rescheduleAll() 전에 호출.
     */
    suspend fun migrateLegacyWork() {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag("promptfactory_prompt")
        val ids = runCatching {
            com.borasarang.promptjournaljupjup.PromptJournalRuntime.promptRepository.getAll()
        }.getOrDefault(emptyList()).map { it.id }
        for (id in ids) wm.cancelUniqueWork("pf_prompt_$id")
        DebugLogger.i("스케줄", "구 스케줄 정리 완료 — ${ids.size}건")
    }

    private suspend fun getAllPrompts(): List<Prompt> {
        return runCatching {
            com.borasarang.promptjournaljupjup.PromptJournalRuntime.promptRepository.getEnabled()
        }.getOrDefault(emptyList())
    }

    private fun uniqueName(promptId: Long) = "pj_prompt_$promptId"

    /**
     * 지정된 시각까지의 지연 시간 계산 (밀리초).
     * HH:mm 형식의 시간을 받아 다음 해당 시각까지의 지연 시간을 반환한다.
     */
    private fun calculateDelayUntilTime(timeValue: String): Long {
        return try {
            val parts = timeValue.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            target.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            DebugLogger.w("스케줄", "시간 파싱 실패, 기본 1시간 후로 설정: ${e.message}")
            TimeUnit.HOURS.toMillis(1)
        }
    }

    companion object {
        private const val TAG_PROMPT = "promptjournal_prompt"
        const val KEY_PROMPT_ID = "prompt_id"
    }
}