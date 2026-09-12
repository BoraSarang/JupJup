package com.borasarang.macjupjup.util

/**
 * Setapp 10카테고리 + 라이선스 3분류 + 횡단 태그.
 * PLAN_v1.0 2장·4장 고정값 — 변경 시 PLAN·DESIGN·TODO 함께 갱신.
 */
object AppCategory {

    val ALL: List<String> = listOf(
        "생산성",
        "유틸리티",
        "보안·프라이버시",
        "미디어·엔터",
        "개발",
        "디자인·크리에이티브",
        "금융",
        "글쓰기·노트",
        "시스템최적화",
        "커뮤니케이션",
    )

    fun isValid(category: String): Boolean = category in ALL
}

/** 라이선스 3분류: 1.오픈소스 2.프리 3.유료 */
enum class AppLicense { OSS, FREE, PAID }

/** 카테고리가 아닌 횡단 조류 태그 */
object AppTag {
    const val AI_AGENT = "AI-Agent"
    const val MENU_BAR = "MenuBar"

    val ALL: Set<String> = setOf(AI_AGENT, MENU_BAR)

    fun isValid(tag: String): Boolean = tag in ALL
}

/** 라이선스 자동분류: repo 존재 → OSS, price>0 → PAID, 그 외 FREE */
fun classifyLicense(repoFullName: String?, price: Double): AppLicense =
    when {
        !repoFullName.isNullOrBlank() -> AppLicense.OSS
        price > 0 -> AppLicense.PAID
        else -> AppLicense.FREE
    }

/** GitHub 토큰 마스킹: 뒤 4자리만 노출 (`ghp_****1234`) */
fun maskToken(token: String): String =
    if (token.length <= 4) "****" else "****" + token.takeLast(4)
