package com.borasarang.promptjournaljupjup.worker

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.common.ai.AiClientFactory
import com.borasarang.common.ai.AiProvider
import com.borasarang.common.search.ExaSearchClient
import com.borasarang.common.search.GroundingFormatter
import com.borasarang.common.search.SearchResult
import com.borasarang.promptjournaljupjup.Constants
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.data.db.entity.PromptExecution
import com.borasarang.promptjournaljupjup.server.HttpServerService
import com.borasarang.promptjournaljupjup.util.DebugLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class PromptJournalWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLogger.i("워커", "프롬프트 실행 워커 시작")

        val app = PromptJournalRuntime
        if (!app.isInitialized) {
            app.initialize(applicationContext)
        }

        val promptId = inputData.getLong(PromptJournalScheduler.KEY_PROMPT_ID, 0L)
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

        // 취재 데이터 주입: 이전 결과 + 웹 검색(Exa) 근거
        val finalPrompt = buildPrompt(prompt, app)

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

    private suspend fun buildPrompt(prompt: Prompt, app: PromptJournalRuntime): String {
        var text = prompt.content

        // 1) 지난 호 결과 주입 (usePreviousResult ON → 직전 SUCCESS 원문을 [어제까지 기록]에)
        if (prompt.usePreviousResult) {
            val prev = app.promptExecutionRepository.getLastSuccess(prompt.id)
            if (prev != null && prev.response.isNotBlank()) {
                val injected = "[어제까지 기록 — 이전 실행 결과]\n" +
                    "${prev.executedAt} 실행 결과:\n${prev.response}"
                text = replaceMarker(text, PREV_RESULT_MARKER, injected)
            }
        }

        // 2) 웹 검색(Exa) 근거 주입 — 키 미설정/실패 시 [사용 불가] 폴백, 개별 쿼리 실패는 격리
        text = replaceMarker(text, GroundingFormatter.MARKER, collectSearchGrounding(app))
        return text
    }

    private fun replaceMarker(text: String, marker: String, block: String): String {
        return if (marker in text) text.replace(marker, block) else text
    }

    /**
     * R21: Exa 6쿼리 병렬 수집 → URL 중복 제거 → 마크다운 근거 블록.
     * 키 미설정이거나 전부 실패면 GroundingFormatter.UNAVAILABLE_BLOCK 반환 (리포트는 계속 생성).
     */
    private suspend fun collectSearchGrounding(app: PromptJournalRuntime): String {
        val exaKey = app.preferences.getExaApiKey()
        if (exaKey.isBlank()) {
            DebugLogger.w("검색", "Exa 키 미설정 — [웹 검색 결과] 미주입")
            return GroundingFormatter.UNAVAILABLE_BLOCK
        }
        val client = ExaSearchClient(exaKey)
        val collected: List<Pair<String, List<SearchResult>>> = coroutineScope {
            SEARCH_QUERIES.map { spec ->
                async {
                    val results = try {
                        client.search(spec.query, spec.numResults, spec.maxCharacters)
                            .getOrElse { emptyList() }
                    } catch (e: Exception) {
                        DebugLogger.w("검색", "쿼리 실패 격리: ${spec.query} — ${e.message}")
                        emptyList()
                    }
                    spec.query to results
                }
            }.map { it.await() }
        }

        val seen = mutableSetOf<String>()
        val deduped = collected.mapNotNull { (query, results) ->
            val kept = results.filter { seen.add(it.url) }
            if (kept.isEmpty()) null else query to kept
        }
        val block = GroundingFormatter.format(deduped)
        DebugLogger.i(
            "검색",
            "웹 근거 수집: 쿼리 ${collected.count { it.second.isNotEmpty() }}/${SEARCH_QUERIES.size}, " +
                "문서 ${deduped.sumOf { it.second.size }}건, ${block.length}자"
        )
        return block
    }

    private fun sendNotification(title: String, execution: PromptExecution, id: Long) {
        try {
            val nTitle = if (execution.status == "SUCCESS") "프롬프트 저널 실행 완료" else "프롬프트 저널 실행 실패"
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
        private const val PREV_RESULT_MARKER = "[어제까지 기록 — 없으면 이 줄과 아래 내용 삭제]"

        private data class SearchSpec(val query: String, val numResults: Int, val maxCharacters: Int)

        /** R21: 워커 6쿼리 — maxCharacters=0 → highlights(뉴스), >0 → text(카탈로그 본문) */
        private val SEARCH_QUERIES = listOf(
            SearchSpec("OpenRouter 무료 모델 :free 전환 및 제거 최근 변경", 6, 0),
            SearchSpec("OpenCode Zen 무료 AI 코딩 크레딧 최신", 5, 0),
            SearchSpec("NVIDIA NIM build.nvidia.com 무료 API 크레딧 2026", 5, 0),
            SearchSpec("Groq Cerebras SambaNova Together AI 무료 티어 LLM API", 6, 0),
            SearchSpec("신규 무료 LLM API 공개 최근 7일", 6, 0),
            SearchSpec("OpenRouter 무료 모델 공식 카탈로그 max_price=0", 3, 3000),
        )
    }
}