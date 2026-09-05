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
    ): List<AiRecommendation> {

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

        val recommendations =
            planner.analyze(context)

        return if (recommendations.isEmpty()) {

            "I don't have enough information yet to make a useful recommendation."

        } else {

            recommendations.joinToString("\n\n") {
                "${it.title}: ${it.message}"
            }
        }
    }
}