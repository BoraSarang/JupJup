package com.borasarang.communityjupjup.util.text

/**
 * 게시글 URL正規화 (중복 판정 단일 진실).
 * 목록 수집 때마다 바뀌는 휘발 파라미터(po/od/追跡系)를 제거해 동일 게시글을 한 행으로 수렴.
 * 식별 파라미터(document_srl·wr_id 등)는 유지.
 */
object UrlCanonical {

    private val volatileKeys = setOf(
        "po", "od", "groupcd", "category", "m", "t",
        "page", "pg", "cp", "offset", "divpage",
        "s_no", "no_tag", "_dcbest",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
    )

    fun normalize(url: String): String {
        val noFrag = url.substringBefore("#").trim()
        if (noFrag.isBlank()) return noFrag
        val qIndex = noFrag.indexOf('?')
        if (qIndex < 0) return noFrag
        val path = noFrag.substring(0, qIndex)
        val query = noFrag.substring(qIndex + 1)
        if (query.isBlank()) return path
        val kept = query.split("&").mapNotNull { pair ->
            val key = pair.substringBefore("=").trim().lowercase()
            if (key.isEmpty() || key in volatileKeys) null else pair
        }.sorted()
        return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
    }
}
