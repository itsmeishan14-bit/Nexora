package com.example.nexora.ai

class LocalNexoraAiService : NexoraAiService {

    private val planner = AiPlanner()

    override suspend fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation> {

        return planner.analyze(context)
    }

    override suspend fun generateDailyPlan(
        context: AiContext
    ): NexoraDailyPlan {

        return planner.createDailyPlan(context)
    }

    override suspend fun analyzeGoals(
        context: AiContext
    ): List<AiRecommendation> {

        return planner.analyzeGoals(context)
    }

    override suspend fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation> {

        return planner.analyzeProductivity(context)
    }

    override suspend fun decomposeGoal(
        context: AiContext,
        goalTitle: String,
        goalDescription: String
    ): AiGoalDecomposition {

        return planner.decomposeGoal(
            context = context,
            goalTitle = goalTitle,
            goalDescription = goalDescription
        )
    }

    override suspend fun askNexora(
        context: AiContext,
        userMessage: String
    ): String {

        return planner.chat(context, userMessage)
    }
}