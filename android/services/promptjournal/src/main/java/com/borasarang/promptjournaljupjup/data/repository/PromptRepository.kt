package com.borasarang.promptjournaljupjup.data.repository

import com.borasarang.promptjournaljupjup.data.db.PromptJournalDatabase
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.data.db.entity.PromptExecution

class PromptRepository(private val db: PromptJournalDatabase) {

    private val promptDao get() = db.promptDao()
    private val executionDao get() = db.promptExecutionDao()

    suspend fun getAll(): List<Prompt> = promptDao.getAll()

    suspend fun getById(id: Long): Prompt? = promptDao.getById(id)

    suspend fun getEnabled(): List<Prompt> = promptDao.getEnabled()

    suspend fun save(prompt: Prompt): Long {
        return if (prompt.id == 0L) {
            promptDao.insert(prompt)
        } else {
            promptDao.update(prompt)
            prompt.id
        }
    }

    suspend fun deleteById(id: Long): Boolean {
        executionDao.deleteByPrompt(id)
        return promptDao.deleteById(id) > 0
    }

    /** 폐기 공급자 데이터 마이그레이션용 — 해당 프롬프트 삭제 (실행기록은 별도) */
    suspend fun deleteByProvider(provider: String): Int = promptDao.deleteByProvider(provider)

    suspend fun count(): Int = promptDao.count()

    suspend fun getRecentExecutions(promptId: Long, limit: Int = 100): List<PromptExecution> =
        executionDao.getRecentByPrompt(promptId, limit)

    suspend fun getLastSuccess(promptId: Long): PromptExecution? = executionDao.getLastSuccess(promptId)
}