package com.borasarang.promptjournaljupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY createdAt ASC")
    suspend fun getAll(): List<Prompt>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getById(id: Long): Prompt?

    @Query("SELECT * FROM prompts WHERE enabled = 1")
    suspend fun getEnabled(): List<Prompt>

    @Insert
    suspend fun insert(prompt: Prompt): Long

    @Update
    suspend fun update(prompt: Prompt): Int

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM prompts")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int
}