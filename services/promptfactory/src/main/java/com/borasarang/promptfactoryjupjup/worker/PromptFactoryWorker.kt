package com.borasarang.promptfactoryjupjup.worker

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.promptfactoryjupjup.Constants
import com.borasarang.promptfactoryjupjup.PromptFactoryRuntime
import com.borasarang.promptfactoryjupjup.ai.AiClientFactory
import com.borasarang.promptfactoryjupjup.ai.AiProvider
import com.borasarang.promptfactoryjupjup.data.db.entity.PromptExecution
import com.borasarang.promptfactoryjupjup.server.HttpServerService
import com.borasarang.promptfactoryjupjup.util.DebugLogger

class PromptFactoryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLogger.i("워커", "프롬프트 실행 워커 시작")

        val app = PromptFactoryRuntime
        if (!app.isInitialized) {
            app.initialize(applicationContext)
        }

        val promptId = inputData.getLong(PromptFactoryScheduler.KEY_PROMPT_ID, 0L)
        val prompt = if (promptId > 0) {
            app.promptRepository.getById(promptId)
        } else {
            app.promptRepository.getEnabled().firstOrNull()
        }

        if (prompt == null) {
            DebugLogger.w("워커", "실행 대상 프롬프트 없음 (id=$promptId) — 스킵")
            return Result.failure()
        }

        val apiKey = app.providerKeys.getKey(prompt.provider)
        if (apiKey.isBlank()) {
            DebugLogger.w("워커", "[${prompt.id}] ${prompt.title} — API 키 미설정, 실행 스킵")
            return Result.failure()
        }

        val provider = try {
            AiProvider.fromString(prompt.provider)
        } catch (e: Exception) {
            DebugLogger.e("워커", Constants.ERR_AI_CALL_FAILED, "지원하지 않는 공급자: ${prompt.provider}", e)
            return Result.failure()
        }

        // 이전 결과 주입 (usePreviousResult ON → 직전 SUCCESS 원문을 [어제까지 기록]에)
        val finalPrompt = if (prompt.usePreviousResult) {
            withPreviousResult(prompt.id, prompt.content, app)
        } else {
            prompt.content
        }

        val client = AiClientFactory.create(provider, apiKey)
        val startMs = System.currentTimeMillis()

        DebugLogger.i("워커", "AI 호출 시작 id=${prompt.id} title=${prompt.title} model=${prompt.modelId}")

        val result = client.complete(finalPrompt, prompt.modelId)
        val durationMs = System.currentTimeMillis() - startMs

        val execution = PromptExecution(
            promptId = prompt.id,
            provider = prompt.provider,
            modelId = prompt.modelId,
            prompt = finalPrompt,
            response = result.getOrElse { it.message ?: "실행 실패" },
            executedAt = startMs,
            durationMs = durationMs,
            status = if (result.isSuccess) "SUCCESS" else "FAILED",
            errorMessage = result.exceptionOrNull()?.message
        )

        val id = app.promptExecutionRepository.save(execution)
        DebugLogger.i("워커", "AI 호출 완료 id=$id status=${execution.status} ${durationMs}ms")

        sendNotification(prompt.title, execution, id)

        return if (result.isSuccess) Result.success() else Result.failure()
    }

    private suspend fun withPreviousResult(
        promptId: Long,
        content: String,
        app: PromptFactoryRuntime,
    ): String {
        val prev = app.promptExecutionRepository.getLastSuccess(promptId) ?: return content
        if (prev.response.isBlank()) return content
        val injected = "[어제까지 기록 — 이전 실행 결과]\n" +
            "${prev.executedAt} 실행 결과:\n${prev.response}"
        return content.replace("[어제까지 기록 — 없으면 이 줄과 아래 내용 삭제]", injected)
            .let {
                if (it == content) "$content\n\n$injected" else it
            }
    }

    private fun sendNotification(title: String, execution: PromptExecution, id: Long) {
        try {
            val nTitle = if (execution.status == "SUCCESS") "프롬프트팩토리 실행 완료" else "프롬프트팩토리 실행 실패"
            val text = if (execution.status == "SUCCESS") {
                "$title · ${execution.provider} · ${execution.modelId} · ${execution.durationMs}ms"
            } else {
                execution.errorMessage ?: "실행 실패"
            }

            val notification = android.app.Notification.Builder(
                applicationContext,
                HttpServerService.CHANNEL_ID
            )
                .setContentTitle(nTitle)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_BASE + id.toInt(), notification)
        } catch (e: Exception) {
            DebugLogger.e("워커", Constants.ERR_NOTIFICATION_FAILED, "알림 발송 실패: ${e.message}", e)
        }
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 3200
    }
}