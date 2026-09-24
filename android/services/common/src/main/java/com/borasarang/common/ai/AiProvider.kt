package com.borasarang.common.ai

/**
 * 서비스 공통 AI 공급자. Google AI Studio는 브라우징 툴 미지원/모델 사멸로 제거.
 * (R21: OpenRouter 단일 공급자 — 프롬프트저널 → 공통 모듈 이관)
 */
enum class AiProvider(val displayName: String, val baseUrl: String) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1");

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OPENROUTER
        }
    }
}