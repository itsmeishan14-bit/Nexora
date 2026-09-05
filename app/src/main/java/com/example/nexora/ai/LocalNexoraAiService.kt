package com.example.nexora.ai

class LocalNexoraAiService : NexoraAiService {

    override suspend fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation> {
        return AiPlanner.generateRecommendations(context)
    }

    override suspend fun generateDailyPlan(
        context: AiContext
    ): List<AiRecommendation> {
        return AiPlanner.generateRecommendations(context)
            .filter {
                it.type == AiRecommendationType.NEXT_TASK ||
                        it.type == AiRecommendationType.GOAL_ACTION
            }
    }

    override suspend fun analyzeGoals(
        context: AiContext
    ): List<AiRecommendation> {
        return AiPlanner.generateRecommendations(context)
            .filter {
                it.type == AiRecommendationType.GOAL_ACTION
            }
    }

    override suspend fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation> {
        return AiPlanner.generateRecommendations(context)
            .filter {
                it.type == AiRecommendationType.PRODUCTIVITY_INSIGHT ||
                        it.type == AiRecommendationType.WARNING
            }
    }
}