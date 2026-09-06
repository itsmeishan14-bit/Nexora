package com.example.nexora.ai

class LocalNexoraAiService(
    private val providerManager: AiProviderManager = AiProviderManager(LocalAiProvider())
) : NexoraAiService {

    private val planner = AiPlanner()

    override suspend fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation> {
        // We can still use the planner for basic recommendations
        // or route through the provider for "reasoned" recommendations
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
        goalTitle: String,
        goalDescription: String
    ): AiGoalDecomposition {
        return planner.decomposeGoal(
            goalTitle = goalTitle,
            goalDescription = goalDescription
        )
    }

    override suspend fun askNexora(
        context: AiContext,
        userMessage: String
    ): AiModelStructuredResponse {
        val prompt = AiPromptBuilder.buildContextPrompt(context) + 
                     "\n\nUser Message: $userMessage"
        
        return providerManager.generateStructuredResponse(prompt, context)
    }

    override suspend fun generateProactiveInsights(
        context: AiContext
    ): List<AiRecommendation> {
        return planner.getProactiveInsights(context)
    }
}
