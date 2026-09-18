package com.borasarang.promptfactoryjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borasarang.promptfactoryjupjup.data.db.entity.PromptExecution

@Dao
interface PromptExecutionDao {
    @Query("SELECT * FROM prompt_executions ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<PromptExecution>

    @Query("SELECT * FROM prompt_executions WHERE id = :id")
    suspend fun getById(id: Long): PromptExecution?

    @Query("SELECT * FROM prompt_executions WHERE promptId = :promptId ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentByPrompt(promptId: Long, limit: Int = 100): List<PromptExecution>

    @Query("SELECT * FROM prompt_executions WHERE promptId = :promptId AND status = 'SUCCESS' ORDER BY executedAt DESC LIMIT 1")
    suspend fun getLastSuccess(promptId: Long): PromptExecution?

    @Insert
    suspend fun insert(execution: PromptExecution): Long

    @Query("DELETE FROM prompt_executions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM prompt_executions WHERE promptId = :promptId")
    suspend fun deleteByPrompt(promptId: Long): Int

    @Query("DELETE FROM prompt_executions WHERE executedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM prompt_executions")
    suspend fun count(): Int
}