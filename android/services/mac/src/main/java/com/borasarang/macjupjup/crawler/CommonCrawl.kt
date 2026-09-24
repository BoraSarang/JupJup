package com.borasarang.macjupjup.crawler

/** 제목 분리 구분자 단일 진실 (R1-13). PH·HN 관례: "이름 — 태그라인" */
internal val TITLE_SEPARATORS = listOf(" — ", " – ", " | ", " - ")

internal fun splitTitle(title: String, extra: List<String> = emptyList()): String =
    title.split(*(TITLE_SEPARATORS + extra).toTypedArray()).first().trim().ifBlank { title }

/** Mac 관련 키워드 단일 진실 (R1-14). PH·HN 필터 공용 */
internal val MAC_KEYWORDS_COMMON = setOf(
    "mac", "macos", "mac os", "menu bar", "menubar", "mac app", "notch",
)

internal fun isMacRelated(text: String, extra: Set<String> = emptySet()): Boolean {
    val hay = " $text ".lowercase()
    return (MAC_KEYWORDS_COMMON + extra).any { hay.contains(it) }
}
