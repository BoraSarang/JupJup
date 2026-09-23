package com.borasarang.macjupjup.util.category

/**
 * Apple Store 카테고리 → Setapp 10카테고리 매핑.
 * RSS `category.attributes.label` 기준.
 */
object AppleCategoryMap {

    fun map(appleCategory: String): String {
        val c = appleCategory.lowercase()
        return when {
            c.contains("developer") || c.contains("developers") -> "개발"
            c.contains("graphic") || c.contains("design") || c.contains("photo") -> "디자인·크리에이티브"
            c.contains("music") || c.contains("video") || c.contains("entertainment") || c.contains("photo & video") -> "미디어·엔터"
            c.contains("business") || c.contains("finance") -> "금융"
            c.contains("social") || c.contains("communication") -> "커뮤니케이션"
            c.contains("productivity") -> "생산성"
            c.contains("utilities") -> "유틸리티"
            c.contains("lifestyle") || c.contains("health") || c.contains("fitness") -> "유틸리티"
            c.contains("education") || c.contains("reference") || c.contains("book") -> "글쓰기·노트"
            c.contains("news") || c.contains("weather") || c.contains("travel") -> "유틸리티"
            c.contains("games") -> "미디어·엔터"
            c.contains("security") || c.contains("privacy") -> "보안·프라이버시"
            else -> "유틸리티"
        }
    }
}
