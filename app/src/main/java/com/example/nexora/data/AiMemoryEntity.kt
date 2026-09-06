package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "ai_memory")
data class AiMemoryEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val confidence: String,
    val importance: String,
    val relatedTaskId: Long?,
    val relatedGoalId: Long?,
    val createdAt: Long,
    val lastUsedAt: Long,
    val expiration: Long?,
    val metadata: String // Simplified as String for now
)
