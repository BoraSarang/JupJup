package com.borasarang.promptjournaljupjup.data.repository

import com.borasarang.promptjournaljupjup.data.db.PromptJournalDatabase
import com.borasarang.promptjournaljupjup.data.db.entity.PromptExecution

class PromptExecutionRepository(private val db: PromptJournalDatabase) {

    private val dao get() = db.promptExecutionDao()

    suspend fun getRecent(limit: Int = 50): List<PromptExecution> = dao.getRecent(limit)

    suspend fun getById(id: Long): PromptExecution? = dao.getById(id)

    suspend fun getRecentByPrompt(promptId: Long, limit: Int = 100): List<PromptExecution> =
        dao.getRecentByPrompt(promptId, limit)

    suspend fun getLastSuccess(promptId: Long): PromptExecution? = dao.getLastSuccess(promptId)

    suspend fun save(execution: PromptExecution): Long = dao.insert(execution)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteOlderThan(cutoff: Long): Int = dao.deleteOlderThan(cutoff)

    /** 폐기 공급자 데이터 마이그레이션용 — 해당 실행기록 삭제 */
    suspend fun deleteByProvider(provider: String): Int = dao.deleteByProvider(provider)

    suspend fun count(): Int = dao.count()
}