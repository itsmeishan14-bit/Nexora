package com.example.nexora.ai

class NexoraAiCore(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService
) {

    suspend fun analyze(): List<AiRecommendation> {

        val context =
            contextBuilder.build()

        return aiService.generateRecommendations(
            context
        )
    }

    suspend fun createDailyPlan(): NexoraDailyPlan {

        val context =
            contextBuilder.build()

        return aiService.generateDailyPlan(
            context
        )
    }

    suspend fun analyzeGoals(): List<AiRecommendation> {

        val context =
            contextBuilder.build()

        return aiService.analyzeGoals(
            context
        )
    }

    suspend fun analyzeProductivity(): List<AiRecommendation> {

        val context =
            contextBuilder.build()

        return aiService.analyzeProductivity(
            context
        )
    }

    suspend fun ask(
        userMessage: String
    ): String {

        val context =
            contextBuilder.build()

        return aiService.askNexora(
            context = context,
            userMessage = userMessage
        )
    }
}