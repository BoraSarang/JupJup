package com.borasarang.promptfactoryjupjup.data.repository

import com.borasarang.promptfactoryjupjup.data.db.PromptFactoryDatabase
import com.borasarang.promptfactoryjupjup.data.db.entity.PromptExecution

class PromptExecutionRepository(private val db: PromptFactoryDatabase) {

    private val dao get() = db.promptExecutionDao()

    suspend fun getRecent(limit: Int = 50): List<PromptExecution> = dao.getRecent(limit)

    suspend fun getById(id: Long): PromptExecution? = dao.getById(id)

    suspend fun getRecentByPrompt(promptId: Long, limit: Int = 100): List<PromptExecution> =
        dao.getRecentByPrompt(promptId, limit)

    suspend fun getLastSuccess(promptId: Long): PromptExecution? = dao.getLastSuccess(promptId)

    suspend fun save(execution: PromptExecution): Long = dao.insert(execution)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun deleteOlderThan(cutoff: Long): Int = dao.deleteOlderThan(cutoff)

    suspend fun count(): Int = dao.count()
}