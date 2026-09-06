package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiLearningDao {

    // Recommendation History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendation(recommendation: AiRecommendationHistoryEntity)

    @Query("SELECT * FROM ai_recommendation_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecommendations(limit: Int): List<AiRecommendationHistoryEntity>

    @Query("SELECT * FROM ai_recommendation_history WHERE relatedTaskId = :taskId")
    suspend fun getRecommendationsForTask(taskId: Long): List<AiRecommendationHistoryEntity>

    // Outcomes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: AiOutcomeEntity)

    @Query("SELECT * FROM ai_outcome ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOutcomes(limit: Int): List<AiOutcomeEntity>

    @Query("SELECT * FROM ai_outcome WHERE recommendationId = :recommendationId")
    suspend fun getOutcomeForRecommendation(recommendationId: String): AiOutcomeEntity?

    // Evaluations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(evaluation: AiEvaluationEntity)

    @Query("SELECT * FROM ai_evaluation ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvaluations(limit: Int): List<AiEvaluationEntity>
}
