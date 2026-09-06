package com.example.nexora.ai

interface NexoraAiService {

    suspend fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun generateDailyPlan(
        context: AiContext
    ): NexoraDailyPlan

    suspend fun analyzeGoals(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation>

    suspend fun decomposeGoal(
        goalTitle: String,
        goalDescription: String = "",
        category: String = "Personal"
    ): AiGoalDecomposition

    suspend fun askNexora(
        context: AiContext,
        userMessage: String
    ): AiModelStructuredResponse

    suspend fun generateProactiveInsights(
        context: AiContext
    ): List<AiRecommendation>
}
