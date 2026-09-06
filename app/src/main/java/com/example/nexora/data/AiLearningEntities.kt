package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "ai_recommendation_history")
data class AiRecommendationHistoryEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val relatedTaskId: Long?,
    val relatedGoalId: Long?,
    val confidence: String
)

@Entity(tableName = "ai_outcome")
data class AiOutcomeEntity(
    @PrimaryKey
    val id: String,
    val recommendationId: String?,
    val actionId: String?,
    val type: String,
    val timestamp: Long,
    val relatedTaskId: Long?,
    val relatedGoalId: Long?,
    val expectedResult: String?,
    val actualResult: String?,
    val confidence: String,
    val evidence: String?
)

@Entity(tableName = "ai_evaluation")
data class AiEvaluationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val whatWasExpected: String,
    val whatActuallyHappened: String,
    val outcome: String,
    val improvementSignal: String?,
    val timestamp: Long,
    val confidence: String
)
