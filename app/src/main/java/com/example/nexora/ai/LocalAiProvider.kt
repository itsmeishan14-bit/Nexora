package com.example.nexora.ai

/**
 * Local implementation of AI model provider using Nexora's built-in heuristic engine.
 */
class LocalAiProvider(
    private val planner: AiPlanner = AiPlanner(),
    private val intentResolver: LocalAiIntentResolver = LocalAiIntentResolver()
) : AiModelProvider {

    override val providerName: String = "Nexora Local AI"

    override suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse {
        val userMessage = prompt.split("\n\nUser Message: ").lastOrNull() ?: prompt
        val result = intentResolver.resolve(userMessage, context)
        
        return AiModelResponse(
            text = result.textResponse ?: "I'm not sure how to respond to that.",
            modelName = "nexora-local-v1"
        )
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext
    ): AiModelStructuredResponse {
        val userMessage = prompt.split("\n\nUser Message: ").lastOrNull() ?: 
                          prompt.split("\n\nDecision Query: ").lastOrNull() ?: 
                          prompt

        val result = intentResolver.resolve(userMessage, context)
        
        if (result.decision.type != AiDecisionType.NO_ACTION) {
            return result
        }

        // Fallback to autonomous analysis if no explicit intent found
        val recommendations = planner.analyze(context)
        val bestRec = recommendations.firstOrNull()
        
        val decision = if (bestRec != null) {
            AiDecision(
                type = mapRecTypeToDecisionType(bestRec.type),
                title = bestRec.title,
                reason = bestRec.message,
                taskId = bestRec.relatedTaskId,
                goalId = bestRec.relatedGoalId,
                actionLabel = bestRec.actionLabel
            )
        } else {
            AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "No immediate actions",
                reason = "Nexora local analysis didn't identify urgent changes."
            )
        }

        return result.copy(
            decision = decision,
            modelName = "nexora-local-v1"
        )
    }

    override suspend fun isAvailable(): Boolean = true

    private fun mapRecTypeToDecisionType(type: AiRecommendationType): AiDecisionType {
        return when (type) {
            AiRecommendationType.NEXT_TASK -> AiDecisionType.START_TASK
            AiRecommendationType.DAILY_PLAN -> AiDecisionType.DAILY_PLAN
            AiRecommendationType.GOAL_ACTION -> AiDecisionType.UPDATE_GOAL
            AiRecommendationType.PRODUCTIVITY_INSIGHT -> AiDecisionType.SHOW_INSIGHT
            AiRecommendationType.WARNING -> AiDecisionType.WARNING
            else -> AiDecisionType.NO_ACTION
        }
    }
}
