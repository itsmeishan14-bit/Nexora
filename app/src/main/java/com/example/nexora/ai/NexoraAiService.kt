package com.example.nexora.ai

interface NexoraAiService {

    suspend fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun generateDailyPlan(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun analyzeGoals(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation>
}