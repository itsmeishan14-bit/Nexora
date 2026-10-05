package com.example.nexora.ai

/**
 * Main entry point for Nexora AI capabilities.
 * Powered by the Nexora AI Brain orchestration layer.
 */
class NexoraAiEngine(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService,
    private val providerManager: AiProviderManager,
    private val actionExecutor: AiActionExecutor,
    toolRegistry: AiToolRegistry,
    private val repository: com.example.nexora.data.NexoraRepository,
    val automationSystem: NexoraAutomationSystem = actionExecutor.getAutomationSystem()
) {
    private val brain = NexoraAiBrain(
        contextBuilder = contextBuilder,
        aiService = aiService,
        providerManager = providerManager,
        toolRegistry = toolRegistry,
        repository = repository,
        automationSystem = automationSystem
    )

    init {
        actionExecutor.onActionExecuted = {
            brain.invalidateContext()
        }
    }

    fun getBrain(): NexoraAiBrain = brain

    /**
     * Unified entry point for all AI requests.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        val response = brain.processRequest(request)
        // Automatically register proposed actions requiring confirmation as pending proposals if not already registered or authorized
        val registeredActions = response.proposedActions.map { action ->
            if (!com.example.nexora.util.NexoraSecurity.isAuthorized(action) &&
                !com.example.nexora.util.NexoraSecurity.isProposalPending(action.id) &&
                (action.requiresConfirmation || com.example.nexora.util.NexoraSecurity.isDestructiveAction(action))) {
                com.example.nexora.util.NexoraSecurity.registerProposal(action)
            } else {
                action
            }
        }
        val registeredPending = response.conversationContext?.pendingAction?.let { pending ->
            registeredActions.find { it.id == pending.id }
                ?: if (!com.example.nexora.util.NexoraSecurity.isAuthorized(pending) &&
                    !com.example.nexora.util.NexoraSecurity.isProposalPending(pending.id) &&
                    (pending.requiresConfirmation || com.example.nexora.util.NexoraSecurity.isDestructiveAction(pending))) {
                    com.example.nexora.util.NexoraSecurity.registerProposal(pending)
                } else {
                    pending
                }
        }
        val updatedContext = if (registeredPending != null) {
            response.conversationContext?.copy(pendingAction = registeredPending)
        } else {
            response.conversationContext
        }
        return response.copy(
            proposedActions = registeredActions,
            conversationContext = updatedContext
        )
    }

    /**
     * Issues and registers a pending action proposal through the trusted application flow.
     */
    fun proposeAction(action: AiAction, ttlMs: Long = com.example.nexora.util.NexoraSecurity.PROPOSAL_TTL_MS): AiAction {
        return com.example.nexora.util.NexoraSecurity.registerProposal(action, ttlMs)
    }

    /**
     * Cancels an existing pending action proposal so it can no longer be confirmed.
     */
    fun cancelProposal(actionId: String) {
        com.example.nexora.util.NexoraSecurity.cancelProposal(actionId)
    }

    /**
     * Checks if an action proposal is currently pending and eligible for confirmation.
     */
    fun isProposalPending(actionId: String): Boolean {
        return com.example.nexora.util.NexoraSecurity.isProposalPending(actionId)
    }

    /**
     * Executes an AI action.
     * Does NOT automatically grant authorization to unconfirmed actions.
     * Only actions that do not require confirmation (e.g. safe/read-only actions)
     * or actions already authorized by a genuine user confirmation flow will be executed.
     */
    suspend fun executeAction(action: AiAction): AiActionResult {
        brain.invalidateContext()
        val result = actionExecutor.execute(action)
        brain.invalidateContext()
        return result
    }

    /**
     * Confirms and executes a pending action proposal that the user has explicitly approved.
     * Validates that the proposal is still pending, unchanged, and eligible for execution.
     * Consumes the pending confirmation exactly once atomically before execution.
     */
    suspend fun confirmPendingAction(action: AiAction): AiActionResult {
        val authorizedAction = if (com.example.nexora.util.NexoraSecurity.isAuthorized(action)) {
            action
        } else {
            when (val consumeResult = com.example.nexora.util.NexoraSecurity.consumeAndAuthorize(action)) {
                is com.example.nexora.util.NexoraSecurity.ConsumeResult.Success -> consumeResult.authorizedAction
                is com.example.nexora.util.NexoraSecurity.ConsumeResult.Rejected -> {
                    return AiActionResult(
                        success = false,
                        message = consumeResult.reason,
                        error = consumeResult.error
                    )
                }
            }
        }
        return executeAction(authorizedAction)
    }

    fun invalidateContext() {
        brain.invalidateContext()
    }

    fun observeAutomationRules(): kotlinx.coroutines.flow.Flow<List<AiAutomationRule>> {
        return brain.observeAutomationRules()
    }

    fun getAutomationRules(): List<AiAutomationRule> {
        return brain.getAutomationRules()
    }

    suspend fun updateAutomationRule(rule: AiAutomationRule): Boolean {
        return brain.updateAutomationRule(rule)
    }

    suspend fun addAutomationRule(rule: AiAutomationRule): Boolean {
        return brain.addAutomationRule(rule)
    }

    suspend fun deleteAutomationRule(idOrName: String): Boolean {
        return brain.deleteAutomationRule(idOrName)
    }

    suspend fun toggleAutomationRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? {
        return brain.toggleAutomationRule(idOrName, enabled)
    }

    fun explainAutomationRun(query: String? = null): String {
        return brain.explainAutomationRun(query)
    }

    suspend fun analyze(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
        return response.recommendations
    }

    suspend fun getProactiveInsights(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
        return response.recommendations
    }

    suspend fun getContext(): AiContext {
        return contextBuilder.build()
    }

    suspend fun getTopInsight(): String? {
        val context = contextBuilder.build()
        return context.memory.items.firstOrNull()?.content ?: context.memory.legacyPatterns.firstOrNull()?.description
    }

    suspend fun createDailyPlan(): NexoraDailyPlan {
        brain.processRequest(AiRequest(AiRequestType.DAILY_PLAN))
        val context = contextBuilder.build()
        return aiService.generateDailyPlan(context)
    }

    suspend fun analyzeGoals(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.GOAL_ANALYSIS))
        return response.recommendations
    }

    suspend fun analyzeProductivity(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.PRODUCTIVITY_ANALYSIS))
        return response.recommendations
    }

    suspend fun decomposeGoal(
        goalTitle: String,
        goalDescription: String = "",
        category: String = "Personal"
    ): AiGoalDecomposition {
        brain.processRequest(
            AiRequest(
                type = AiRequestType.GOAL_DECOMPOSITION,
                parameters = mapOf("title" to goalTitle, "description" to goalDescription, "category" to category)
            )
        )
        return aiService.decomposeGoal(
            goalTitle = goalTitle,
            goalDescription = goalDescription,
            category = category
        )
    }

    suspend fun ask(
        userMessage: String
    ): AiModelStructuredResponse {
        brain.processRequest(
            AiRequest(
                type = AiRequestType.CHAT,
                userMessage = userMessage
            )
        )
        val context = contextBuilder.build()
        return aiService.askNexora(
            context = context,
            userMessage = userMessage
        )
    }

    suspend fun deleteMemory(id: String) {
        repository.deleteMemory(id)
    }

    suspend fun clearAllMemory() {
        repository.clearAllMemory()
    }
}
