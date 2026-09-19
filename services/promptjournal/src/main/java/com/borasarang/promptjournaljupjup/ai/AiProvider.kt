package com.borasarang.promptjournaljupjup.ai

enum class AiProvider(val displayName: String, val baseUrl: String) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GOOGLE_AI_STUDIO("Google AI Studio", "https://generativelanguage.googleapis.com/v1beta"),
    OPENCODE_ZEN("OpenCode Zen", "https://opencode.ai/zen/v1");

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OPENROUTER
        }
    }
}
