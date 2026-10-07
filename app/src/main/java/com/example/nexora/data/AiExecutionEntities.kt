package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "ai_execution_record",
    indices = [Index(value = ["timestamp"])]
)
data class AiExecutionRecordEntity(
    @PrimaryKey
    val id: String,
    val userPrompt: String?,
    val detectedIntent: String?,
    val overallStatus: String,
    val confirmationRequired: Boolean,
    val userConfirmed: Boolean?,
    val totalProposedActions: Int,
    val executedActionCount: Int,
    val successActionCount: Int,
    val failedActionCount: Int,
    val skippedActionCount: Int,
    val summaryMessage: String,
    val timestamp: Long
)

@Entity(
    tableName = "ai_action_execution_record",
    indices = [
        Index(value = ["executionRecordId"]),
        Index(value = ["timestamp"])
    ]
)
data class AiActionExecutionEntity(
    @PrimaryKey
    val id: String,
    val executionRecordId: String,
    val actionId: String,
    val executionOrder: Int,
    val actionType: String,
    val actionTitle: String,
    val status: String,
    val affectedTaskId: Long?,
    val affectedGoalId: Long?,
    val message: String,
    val error: String?,
    val failureReason: String?,
    val timestamp: Long
)
