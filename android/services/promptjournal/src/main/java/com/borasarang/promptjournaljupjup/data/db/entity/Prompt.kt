package com.borasarang.promptjournaljupjup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompts")
data class Prompt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val provider: String,
    val modelId: String,
    val scheduleType: String = "daily",
    val scheduleValue: String = "09:00",
    val enabled: Boolean = true,
    val usePreviousResult: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)