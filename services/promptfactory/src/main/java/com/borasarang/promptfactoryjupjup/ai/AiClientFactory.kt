package com.borasarang.promptfactoryjupjup.ai

object AiClientFactory {
    fun create(provider: AiProvider, apiKey: String): AiClient {
        return when (provider) {
            AiProvider.OPENROUTER -> OpenRouterClient(apiKey)
            AiProvider.NIM -> NimClient(apiKey)
            AiProvider.GOOGLE_AI_STUDIO -> GoogleAiStudioClient(apiKey)
        }
    }

    fun createFromString(providerName: String, apiKey: String): AiClient {
        return create(AiProvider.fromString(providerName), apiKey)
    }

    /** 지정된 공급자의 무료 모델 목록을 반환한다 */
    fun getModels(provider: AiProvider): List<AiClient.ModelInfo> {
        return when (provider) {
            AiProvider.OPENROUTER -> OpenRouterClient("").supportedModels
            AiProvider.NIM -> NimClient("").supportedModels
            AiProvider.GOOGLE_AI_STUDIO -> GoogleAiStudioClient("").supportedModels
        }
    }

    /** 모든 공급자의 무료 모델 목록을 반환한다 */
    fun getAllModels(): Map<AiProvider, List<AiClient.ModelInfo>> {
        return AiProvider.values().associateWith { getModels(it) }
    }
}
