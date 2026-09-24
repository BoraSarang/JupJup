package com.borasarang.common.search

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Exa 여러 쿼리 병렬 수집 → URL 중복 제거 → 마크다운 근거 블록.
 * 키 미설정이거나 전부 실패면 [GroundingFormatter.UNAVAILABLE_BLOCK] 반환.
 * 개별 쿼리 실패는 격리.
 */
object GroundingCollector {

    data class Spec(val query: String, val numResults: Int, val maxCharacters: Int)

    suspend fun collect(
        exaKey: String,
        specs: List<Spec>,
        logTag: String = "검색",
    ): String {
        if (exaKey.isBlank()) {
            Timber.w("[$logTag] Exa 키 미설정 — 근거 블록 미주입")
            return GroundingFormatter.UNAVAILABLE_BLOCK
        }
        val client = ExaSearchClient(exaKey)
        val collected: List<Pair<String, List<SearchResult>>> = coroutineScope {
            specs.map { spec ->
                async {
                    val results = try {
                        client.search(spec.query, spec.numResults, spec.maxCharacters)
                            .getOrElse { emptyList() }
                    } catch (e: Exception) {
                        Timber.w("[$logTag] 쿼리 실패 격리: ${spec.query} — ${e.message}")
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
        Timber.i(
            "[$logTag] 근거 수집: 쿼리 ${collected.count { it.second.isNotEmpty() }}/${specs.size}, " +
                "문서 ${deduped.sumOf { it.second.size }}건, ${block.length}자",
        )
        return block
    }
}
