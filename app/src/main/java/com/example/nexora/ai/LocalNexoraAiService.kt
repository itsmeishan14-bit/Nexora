package com.example.nexora.ai

class LocalNexoraAiService(
    private val providerManager: AiProviderManager = AiProviderManager(LocalAiProvider())
) : NexoraAiService {

    private val planner = AiPlanner()
    private val proactiveEngine = NexoraProactiveEngine()

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
        goalTitle: String,
        goalDescription: String,
        category: String
    ): AiGoalDecomposition {
        return planner.decomposeGoal(
            goalTitle = goalTitle,
            goalDescription = goalDescription,
            category = category
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
        val signals = proactiveEngine.detectSignals(context)
        return signals.map { signal ->
             AiRecommendation(
                id = signal.fingerprint,
                type = mapSignalToRecType(signal.type),
                title = signal.title,
                message = signal.message,
                priority = signal.severity,
                confidence = signal.confidence,
                evidence = emptyList(),
                relatedTaskId = signal.relatedTaskId,
                relatedGoalId = signal.relatedGoalId,
                actionLabel = signal.suggestedAction?.title
            )
        }
    }

    private fun mapSignalToRecType(type: ProactiveSignalType): AiRecommendationType {
        return when (type) {
            ProactiveSignalType.WORKLOAD_RISK, 
            ProactiveSignalType.PLAN_MISMATCH -> AiRecommendationType.WARNING
            
            ProactiveSignalType.GOAL_NEGLECT, 
            ProactiveSignalType.MISSING_NEXT_ACTION,
            ProactiveSignalType.GOAL_PROGRESS_OPPORTUNITY -> AiRecommendationType.GOAL_ACTION
            
            ProactiveSignalType.CARRY_FORWARD_PATTERN,
            ProactiveSignalType.PRODUCTIVITY_DROP,
            ProactiveSignalType.PRODUCTIVITY_IMPROVEMENT,
            ProactiveSignalType.WORKLOAD_BALANCED -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            
            ProactiveSignalType.HIGH_PRIORITY_CONFLICT,
            ProactiveSignalType.TASK_TOO_LARGE -> AiRecommendationType.WARNING
            
            else -> AiRecommendationType.GENERAL
        }
    }
}
