package com.borasarang.promptfactoryjupjup.ai

enum class AiProvider(val displayName: String, val baseUrl: String) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    NIM("NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
    GOOGLE_AI_STUDIO("Google AI Studio", "https://generativelanguage.googleapis.com/v1beta");

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OPENROUTER
        }
    }
}
