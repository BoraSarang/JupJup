package com.borasarang.promptjournaljupjup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_executions")
data class PromptExecution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val promptId: Long = 0,
    val provider: String,
    val modelId: String,
    val prompt: String,
    val response: String,
    val executedAt: Long,
    val durationMs: Long = 0,
    val status: String,
    val errorMessage: String? = null
)
