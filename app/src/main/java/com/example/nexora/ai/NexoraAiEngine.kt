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
        val contextPlan = response.conversationContext?.pendingPlan ?: emptyList()
        val registeredPendingPlan = if (contextPlan.isNotEmpty()) {
            contextPlan.map { planAction ->
                registeredActions.find { it.id == planAction.id }
                    ?: if (!com.example.nexora.util.NexoraSecurity.isAuthorized(planAction) &&
                        !com.example.nexora.util.NexoraSecurity.isProposalPending(planAction.id) &&
                        (planAction.requiresConfirmation || com.example.nexora.util.NexoraSecurity.isDestructiveAction(planAction))) {
                        com.example.nexora.util.NexoraSecurity.registerProposal(planAction)
                    } else {
                        planAction
                    }
            }
        } else {
            emptyList()
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
        val updatedContext = response.conversationContext?.copy(
            pendingAction = registeredPending,
            pendingPlan = if (registeredPendingPlan.isNotEmpty()) registeredPendingPlan else listOfNotNull(registeredPending)
        )
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
     * Executes a list of actions in a plan sequentially, respecting dependencies.
     * If an earlier prerequisite fails (e.g., CREATE_GOAL), subsequent dependent actions
     * (e.g., CREATE_TASK referencing that goal) are skipped with an explanatory error.
     */
    suspend fun executePlan(actions: List<AiAction>): List<AiActionResult> {
        if (actions.isEmpty()) return emptyList()
        val results = mutableListOf<AiActionResult>()
        val failedGoalTitles = mutableSetOf<String>()
        val createdGoalTitles = mutableSetOf<String>()

        for (action in actions) {
            val goalTitle = action.parameters["goalTitle"] as? String

            // Check dependency: if this action depends on a goal that failed to be created
            if (goalTitle != null && failedGoalTitles.contains(goalTitle.trim().lowercase())) {
                results.add(
                    AiActionResult(
                        success = false,
                        message = "Skipped dependent action: parent goal \"$goalTitle\" creation failed.",
                        error = "Parent goal dependency failed"
                    )
                )
                continue
            }

            // If action is CREATE_TASK referencing a goal, verify parent goal exists or was created in this plan
            if (action.type == AiActionType.CREATE_TASK && goalTitle != null && goalTitle.isNotBlank()) {
                val existingGoals = repository.observeGoalsOnce()
                val goalExists = existingGoals.any { it.title.equals(goalTitle.trim(), ignoreCase = true) } ||
                    createdGoalTitles.contains(goalTitle.trim().lowercase())
                if (!goalExists) {
                    results.add(
                        AiActionResult(
                            success = false,
                            message = "Skipped dependent task: parent goal \"$goalTitle\" does not exist.",
                            error = "Parent goal dependency failed"
                        )
                    )
                    continue
                }
            }

            val result = executeAction(action)
            results.add(result)

            if (action.type == AiActionType.CREATE_GOAL) {
                val title = (action.parameters["title"] as? String ?: action.title).trim().lowercase()
                if (result.success) {
                    createdGoalTitles.add(title)
                } else {
                    failedGoalTitles.add(title)
                }
            }
        }

        brain.invalidateContext()
        return results
    }

    /**
     * Confirms and executes an entire pending plan of action proposals that the user approved.
     * Atomically validates that each action proposal in the plan is pending and eligible,
     * consuming their confirmation tokens before execution.
     */
    suspend fun confirmPendingPlan(actions: List<AiAction>): List<AiActionResult> {
        if (actions.isEmpty()) return emptyList()

        // 1. Authorize all actions in the plan atomically
        val authorizedPlan = mutableListOf<AiAction>()
        for (action in actions) {
            val authorized = if (com.example.nexora.util.NexoraSecurity.isAuthorized(action)) {
                action
            } else {
                when (val consumeResult = com.example.nexora.util.NexoraSecurity.consumeAndAuthorize(action)) {
                    is com.example.nexora.util.NexoraSecurity.ConsumeResult.Success -> consumeResult.authorizedAction
                    is com.example.nexora.util.NexoraSecurity.ConsumeResult.Rejected -> {
                        // Return rejection immediately for this action, and skip remaining
                        val rejectedResult = AiActionResult(
                            success = false,
                            message = consumeResult.reason,
                            error = consumeResult.error
                        )
                        val skippedResults = actions.drop(authorizedPlan.size + 1).map {
                            AiActionResult(
                                success = false,
                                message = "Skipped action due to authorization failure on prior plan action.",
                                error = "Plan authorization aborted"
                            )
                        }
                        return listOf(rejectedResult) + skippedResults
                    }
                }
            }
            authorizedPlan.add(authorized)
        }

        // 2. Execute the authorized plan
        return executePlan(authorizedPlan)
    }

    /**
     * Confirms and executes a pending action proposal that the user has explicitly approved.
     * Validates that the proposal is still pending, unchanged, and eligible for execution.
     * Consumes the pending confirmation exactly once atomically before execution.
     */
    suspend fun confirmPendingAction(action: AiAction): AiActionResult {
        return confirmPendingPlan(listOf(action)).firstOrNull() ?: AiActionResult(false, "No action executed")
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
