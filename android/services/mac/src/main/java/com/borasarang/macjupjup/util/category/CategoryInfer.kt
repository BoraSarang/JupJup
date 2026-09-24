package com.borasarang.macjupjup.util.category

/**
 * 키워드 기반 카테고리·태그 추론 (Setapp 10종 + AI-Agent/MenuBar 태그).
 * 크롤러 공통 사용. 확정 불가면 "유틸리티".
 */
object CategoryInfer {

    data class Inferred(
        val category: String,
        val tags: List<String>,
    )

    fun infer(text: String, topics: List<String> = emptyList()): Inferred {
        val hay = (text + " " + topics.joinToString(" ")).lowercase()
        val tags = mutableListOf<String>()
        if (hay.contains("ai agent") || hay.contains("ai-agent") ||
            hasWord(hay, "ai") && hay.contains("agent") ||
            "ai" in topics || "ai-agents" in topics
        ) {
            tags += AppTag.AI_AGENT
        }
        if (hay.contains("menu bar") || hay.contains("menubar") || hay.contains("menu-bar") || hay.contains("notch") ||
            "menu-bar" in topics || "menubar" in topics || "notch" in topics
        ) {
            tags += AppTag.MENU_BAR
        }
        val category = when {
            containsAny(hay, listOf("terminal", "git", "editor", "ide", "api", "developer", "code", "cli", "devops", "xcode")) ||
                topics.any { it in setOf("developer-tools", "terminal", "cli") } -> "개발"
            containsAny(hay, listOf("design", "figma", "pixel", "vector", "font", "icon", "mockup", "photo", "image", "illustrat")) -> "디자인·크리에이티브"
            containsAny(hay, listOf("video", "audio", "music", "player", "podcast", "stream", "record", "movie", "mp3", "mp4")) -> "미디어·엔터"
            containsAny(hay, listOf("security", "privacy", "vpn", "firewall", "malware", "antivirus", "password", "encrypt")) -> "보안·프라이버시"
            containsAny(hay, listOf("note", "writing", "markdown", "journal", "todo", "task", "obsidian", "memo")) -> "글쓰기·노트"
            containsAny(hay, listOf("finance", "money", "budget", "invoice", "stock", "crypto", "accounting")) -> "금융"
            containsAny(hay, listOf("chat", "message", "slack", "mail", "email", "meeting", "call", "video conferenc")) -> "커뮤니케이션"
            containsAny(hay, listOf("clean", "system", "disk", "memory", "battery", "monitor", "backup", "sync", "uninstall", "optimizer", "window manager")) -> "시스템최적화"
            containsAny(hay, listOf("productiv", "launcher", "raycast", "alfred", "shortcut", "workflow", "calendar", "reminder", "focus", "pomodoro")) -> "생산성"
            containsAny(hay, listOf("manager", "utility", "utilities", "toolbox", "assistant")) -> "유틸리티"
            else -> "유틸리티"
        }
        return Inferred(category, tags.distinct())
    }

    private fun containsAny(hay: String, keywords: List<String>): Boolean =
        keywords.any { kw ->
            // 3자 이하 키워드는 단어 경계 매칭 ("Hide"→ide, "rapid"→api 오탐 방지)
            if (kw.length <= 3) hasWord(hay, kw) else hay.contains(kw)
        }

    private fun hasWord(hay: String, word: String): Boolean {
        return try {
            Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(hay)
        } catch (_: Exception) {
            hay.contains(word)
        }
    }
}
