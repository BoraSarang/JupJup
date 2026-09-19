package com.borasarang.common.search

/**
 * Exa 검색 결과 1건. excerpt는 컨텍스트 유형에 따라 highlights(뉴스) 또는 text(문서 본문)에서 추출.
 */
data class SearchResult(
    val url: String,
    val title: String,
    val publishedDate: String? = null,
    val excerpt: String? = null,
)