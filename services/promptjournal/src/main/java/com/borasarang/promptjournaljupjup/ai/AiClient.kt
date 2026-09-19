package com.borasarang.promptjournaljupjup.ai

interface AiClient {
    /** AI 모델에 프롬프트를 전송하고 응답을 반환한다 */
    suspend fun complete(prompt: String, modelId: String): Result<String>

    /** 이 공급자가 지원하는 무료 모델 목록 */
    val supportedModels: List<ModelInfo>

    data class ModelInfo(
        val id: String,
        val name: String,
        val contextWindow: Int = 0,
        val maxOutput: Int = 0
    )
}
