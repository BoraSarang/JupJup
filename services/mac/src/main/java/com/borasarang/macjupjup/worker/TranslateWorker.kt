package com.borasarang.macjupjup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.MacTranslator

/**
 * 한글 번역 전용 워커 (ML Kit 온디바이스).
 * 수집 워커와 분리 — 모델 다운로드(~30MB 1회) + 장시간 번역을 독립 생명주기로 처리.
 * 미번역 행을 최대 100건/실행씩 처리 (T-150: 적체 해소용 상향). 설정 꺼짐·실패 시 조용히 스킵.
 */
class TranslateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = MacJupJupRuntime
        return try {
            if (!app.preferences.getSettings().translateKo) {
                return Result.success()
            }
            val targets = app.database.appDao().getUntranslated(MAX_PER_RUN)
            if (targets.isEmpty()) {
                return Result.success()
            }
            DebugLogger.i("번역", "번역 워커 시작 대상=${targets.size}건")
            var done = 0
            for (a in targets) {
                // 중단 요청 시 즉시 종료 (다음 실행에 이어서)
                if (isStopped) {
                    DebugLogger.i("번역", "중단 요청 — 진행 $done/${targets.size}건 저장 후 종료")
                    break
                }
                // T-131: 미번역 + 개행 소실(원문 여러 줄·번역 한 줄) 복구 대상
                val needsDescRepair = a.descriptionSnippet?.contains("\n") == true &&
                    (a.descriptionKo == null || !a.descriptionKo.contains("\n"))
                val descKo = a.descriptionSnippet
                    ?.takeIf { a.descriptionKo == null || needsDescRepair }
                    ?.let { MacTranslator.translateAutoToKo(it) }
                val notesKo = (a.releaseNotes ?: a.releaseNotesSummary)
                    ?.takeIf { a.releaseNotesKo == null }
                    ?.let { MacTranslator.translateAutoToKo(it) }
                if (descKo != null || notesKo != null) {
                    app.database.appDao().updateKo(a.id, descKo, notesKo)
                    done++
                }
                // gtx 레이트 제한 예의 대기
                kotlinx.coroutines.delay(500)
            }
            DebugLogger.i("번역", "번역 워커 완료 $done/${targets.size}건")
            // R41·R50: 뉴스 제목·요약 번역 (미번역 대상, 최신 순, 최대 80건/실행)
            translateNews(app)
            Result.success()
        } catch (e: Exception) {
            DebugLogger.w("번역", "번역 워커 실패, 재시도: ${e.message}")
            Result.retry()
        }
    }

    /** 뉴스 번역 2단계 (앱 번역과 동일 예의· 중단 처리) */
    private suspend fun translateNews(app: MacJupJupRuntime) {
        val targets = try {
            app.database.newsArticleDao().getUntranslated(MAX_NEWS_PER_RUN)
        } catch (e: Exception) {
            DebugLogger.w("번역", "뉴스 대상 조회 스킵: ${e.message}")
            return
        }
        if (targets.isEmpty()) return
        DebugLogger.i("번역", "뉴스 번역 시작 대상=${targets.size}건")
        var done = 0
        for (n in targets) {
            if (isStopped) {
                DebugLogger.i("번역", "중단 요청 — 뉴스 진행 $done/${targets.size}건 저장 후 종료")
                break
            }
            try {
                val titleKo = MacTranslator.translateAutoToKo(n.title)
                val summaryKo = n.summary?.let { MacTranslator.translateAutoToKo(it) }
                if (titleKo != null || summaryKo != null) {
                    app.database.newsArticleDao().updateKo(n.id, titleKo, summaryKo)
                    done++
                }
            } catch (e: Exception) {
                DebugLogger.w("번역", "뉴스 번역 스킵 ${n.id.take(8)}: ${e.message}")
            }
            kotlinx.coroutines.delay(500)
        }
        DebugLogger.i("번역", "뉴스 번역 완료 $done/${targets.size}건")
    }

    companion object {
        const val MAX_PER_RUN = 100
        const val MAX_NEWS_PER_RUN = 80
    }
}
