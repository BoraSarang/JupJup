package com.borasarang.common.search

/**
 * 검색 결과 → 프롬프트 주입용 마크다운 블록 포맷터. (R21)
 * 시드 프롬프트의 `[웹 검색 결과 — 없으면 이 줄과 아래 내용 삭제]` 자리에 삽입된다.
 */
object GroundingFormatter {

    const val MARKER = "[웹 검색 결과 — 없으면 이 줄과 아래 내용 삭제]"

    const val UNAVAILABLE_BLOCK = "[웹 검색 결과: 사용 불가 — Exa 키 미설정 또는 검색 실패]" +
        "\n검색으로 확인된 내용이 없으므로, 보고서는 주입된 자료만으로 판단하고" +
        " 미확인 항목은 '검색 결과 없음'으로 명시할 것."

    private const val DEFAULT_LIMIT_CHARS = 16_000

    /**
     * 쿼리별 결과를 정리해 하나의 근거 블록으로 변환.
     * 빈 결과 쿼리는 제외, 전체 크기는 limitChars로 제한. URL 중복은 호출부에서 정리한다.
     */
    fun format(queries: List<Pair<String, List<SearchResult>>>, limitChars: Int = DEFAULT_LIMIT_CHARS): String {
        val sb = StringBuilder()
        sb.append("[웹 검색 결과 — Exa 실측]")
        for ((query, results) in queries) {
            if (results.isEmpty()) continue
            sb.append("\n\n### 검색어: ").append(query)
            for (r in results) {
                sb.append("\n- ").append(r.title)
                r.publishedDate?.let { sb.append(" (").append(it.take(10)).append(")") }
                sb.append("\n  URL: ").append(r.url)
                r.excerpt?.let {
                    sb.append("\n  발췌: ").append(it.replace(Regex("\\s+"), " ").trim().take(300))
                }
            }
        }
        if (sb.length > limitChars) sb.setLength(limitChars)
        val body = sb.toString().trim()
        return if (body.endsWith("[웹 검색 결과 — Exa 실측]")) {
            UNAVAILABLE_BLOCK
        } else {
            body
        }
    }
}