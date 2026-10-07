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

    @Volatile
    private var lastActiveUserPrompt: String? = null
    @Volatile
    private var lastActiveIntent: String? = null
    @Volatile
    var lastExecutionRecord: AiExecutionRecord? = null
        internal set

    suspend fun getRecentExecutionRecords(limit: Int = 20): List<AiExecutionRecord> =
        repository.getRecentExecutionRecords(limit)

    fun observeRecentExecutionRecords(limit: Int = 20): kotlinx.coroutines.flow.Flow<List<AiExecutionRecord>> =
        repository.observeRecentExecutionRecords(limit)

    fun getBrain(): NexoraAiBrain = brain

    /**
     * Unified entry point for all AI requests.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        if (!request.userMessage.isNullOrBlank()) {
            lastActiveUserPrompt = request.userMessage
        }
        val response = brain.processRequest(request)
        if (response.decision != null) {
            lastActiveIntent = response.decision.type.name
        }
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
     * Internal single-action execution without creating a top-level plan execution record.
     */
    private suspend fun executeSingleActionInternal(action: AiAction): AiActionResult {
        brain.invalidateContext()
        val result = actionExecutor.execute(action)
        brain.invalidateContext()
        return result
    }

    /**
     * Executes an AI action.
     * Records a truthful execution record with parent-child linkage.
     * Does NOT automatically grant authorization to unconfirmed actions.
     */
    suspend fun executeAction(action: AiAction): AiActionResult {
        return executePlan(listOf(action)).firstOrNull() ?: AiActionResult(false, "No action executed")
    }

    /**
     * Executes a list of actions in a plan sequentially, respecting dependencies.
     * If an earlier prerequisite fails (e.g., CREATE_GOAL or prerequisite task), subsequent dependent actions
     * are skipped with an explanatory error.
     * Persists a complete, transparent execution record preserving order and outcomes.
     */
    suspend fun executePlan(actions: List<AiAction>): List<AiActionResult> {
        if (actions.isEmpty()) return emptyList()
        val results = mutableListOf<AiActionResult>()
        val failedGoalTitles = mutableSetOf<String>()
        val createdGoalTitles = mutableSetOf<String>()
        val failedTaskTitles = mutableSetOf<String>()
        val createdTaskTitles = mutableSetOf<String>()

        for (action in actions) {
            val goalTitle = action.parameters["goalTitle"] as? String
            val prereqTaskTitle = (action.parameters["dependsOn"] as? String)
                ?: (action.parameters["prerequisite"] as? String)
                ?: (action.parameters["prerequisiteTaskTitle"] as? String)

            // Check dependency: if this action depends on a goal that failed to be created
            if (goalTitle != null && failedGoalTitles.contains(goalTitle.trim().lowercase())) {
                val skipped = AiActionResult(
                    success = false,
                    message = "Skipped dependent action: parent goal \"$goalTitle\" creation failed.",
                    error = "Parent goal dependency failed"
                )
                results.add(skipped)
                recordSkippedOutcome(action, skipped)
                continue
            }

            // Check dependency: if this action depends on a prerequisite task that failed
            if (prereqTaskTitle != null && failedTaskTitles.contains(prereqTaskTitle.trim().lowercase())) {
                val skipped = AiActionResult(
                    success = false,
                    message = "Skipped dependent action: prerequisite task \"$prereqTaskTitle\" failed.",
                    error = "Prerequisite task dependency failed"
                )
                results.add(skipped)
                recordSkippedOutcome(action, skipped)
                continue
            }

            // If action is CREATE_TASK referencing a goal, verify parent goal exists or was created in this plan
            if (action.type == AiActionType.CREATE_TASK && goalTitle != null && goalTitle.isNotBlank()) {
                val existingGoals = repository.observeGoalsOnce()
                val goalExists = existingGoals.any { it.title.equals(goalTitle.trim(), ignoreCase = true) } ||
                    createdGoalTitles.contains(goalTitle.trim().lowercase())
                if (!goalExists) {
                    val skipped = AiActionResult(
                        success = false,
                        message = "Skipped dependent task: parent goal \"$goalTitle\" does not exist.",
                        error = "Parent goal dependency failed"
                    )
                    results.add(skipped)
                    recordSkippedOutcome(action, skipped)
                    continue
                }
            }

            val result = executeSingleActionInternal(action)
            results.add(result)

            val actionTitleKey = (action.parameters["title"] as? String ?: action.title).trim().lowercase()

            if (action.type == AiActionType.CREATE_GOAL) {
                if (result.success) {
                    createdGoalTitles.add(actionTitleKey)
                } else {
                    failedGoalTitles.add(actionTitleKey)
                }
            } else if (action.type == AiActionType.CREATE_TASK) {
                if (result.success) {
                    createdTaskTitles.add(actionTitleKey)
                } else {
                    failedTaskTitles.add(actionTitleKey)
                }
            }
        }

        brain.invalidateContext()

        // Construct and persist parent AiExecutionRecord
        recordExecutionResult(actions, results, forcedStatus = null, userConfirmed = true)

        return results
    }

    /**
     * Confirms and executes an entire pending plan of action proposals that the user approved.
     * Atomically validates that each action proposal in the plan is pending and eligible,
     * consuming their confirmation tokens before execution.
     * On rejection, records a transparent REJECTED execution record.
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
                        val allResults = authorizedPlan.map { AiActionResult(false, "Aborted", error = "Authorization aborted") } +
                            listOf(rejectedResult) + skippedResults

                        recordExecutionResult(
                            actions = actions,
                            results = allResults,
                            forcedStatus = ExecutionOverallStatus.REJECTED,
                            userConfirmed = false,
                            summaryOverride = "Authorization rejected: ${consumeResult.reason}"
                        )

                        actions.forEach { act ->
                            try {
                                repository.saveOutcome(
                                    AiOutcome(
                                        id = java.util.UUID.randomUUID().toString(),
                                        recommendationId = null,
                                        actionId = act.id,
                                        type = AiOutcomeType.REJECTED,
                                        timestamp = System.currentTimeMillis(),
                                        relatedTaskId = act.taskId,
                                        relatedGoalId = act.goalId,
                                        expectedResult = act.title,
                                        actualResult = consumeResult.reason,
                                        evidence = "Authorization rejected: ${consumeResult.error}"
                                    )
                                )
                            } catch (_: Exception) {}
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

    /**
     * Records a cancellation execution record and updates AI telemetry outcomes.
     */
    suspend fun recordCancellation(
        actions: List<AiAction>,
        userPrompt: String? = null,
        reason: String = "Action proposal cancelled by user."
    ): AiExecutionRecord {
        actions.forEach { cancelProposal(it.id) }
        val results = actions.map {
            AiActionResult(
                success = false,
                message = reason,
                error = "Cancelled by user"
            )
        }
        val record = recordExecutionResult(
            actions = actions,
            results = results,
            forcedStatus = ExecutionOverallStatus.CANCELLED,
            userConfirmed = false,
            summaryOverride = reason
        )
        actions.forEach { act ->
            try {
                repository.saveOutcome(
                    AiOutcome(
                        id = java.util.UUID.randomUUID().toString(),
                        recommendationId = null,
                        actionId = act.id,
                        type = AiOutcomeType.REJECTED,
                        timestamp = System.currentTimeMillis(),
                        relatedTaskId = act.taskId,
                        relatedGoalId = act.goalId,
                        expectedResult = act.title,
                        actualResult = reason,
                        evidence = "Cancelled by user"
                    )
                )
            } catch (_: Exception) {}
        }
        return record
    }

    suspend fun cancelPendingPlan(
        actions: List<AiAction>,
        userPrompt: String? = null,
        reason: String = "Action proposal cancelled by user."
    ): AiExecutionRecord {
        return recordCancellation(actions, userPrompt, reason)
    }

    private suspend fun recordSkippedOutcome(action: AiAction, result: AiActionResult) {
        try {
            val outcome = AiOutcome(
                id = java.util.UUID.randomUUID().toString(),
                recommendationId = null,
                actionId = action.id,
                type = AiOutcomeType.NOT_COMPLETED,
                timestamp = System.currentTimeMillis(),
                relatedTaskId = result.affectedTaskId ?: action.taskId,
                relatedGoalId = result.affectedGoalId ?: action.goalId,
                expectedResult = action.title,
                actualResult = result.message,
                evidence = "Skipped dependent action: ${result.error}"
            )
            repository.saveOutcome(outcome)
        } catch (e: Exception) {
            com.example.nexora.util.NexoraLogger.e("ENGINE", "Failed to record outcome for skipped action", e)
        }
    }

    private suspend fun recordExecutionResult(
        actions: List<AiAction>,
        results: List<AiActionResult>,
        forcedStatus: ExecutionOverallStatus? = null,
        userConfirmed: Boolean? = true,
        summaryOverride: String? = null
    ): AiExecutionRecord {
        val parentRecordId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val userPrompt = actions.firstNotNullOfOrNull { it.parameters["userPrompt"] as? String }
            ?: lastActiveUserPrompt
        val detectedIntent = actions.firstNotNullOfOrNull { it.parameters["detectedIntent"] as? String }
            ?: lastActiveIntent

        val isCancelled = forcedStatus == ExecutionOverallStatus.CANCELLED
        val isRejected = forcedStatus == ExecutionOverallStatus.REJECTED

        val childRecords = actions.mapIndexed { index, action ->
            val result = results.getOrNull(index) ?: AiActionResult(false, "Unexecuted", error = "Unexecuted")
            val status = when {
                result.success -> ActionExecutionStatus.SUCCESS
                isCancelled || result.error?.contains("dependency", ignoreCase = true) == true ||
                    result.message.startsWith("Skipped", ignoreCase = true) -> ActionExecutionStatus.SKIPPED
                else -> ActionExecutionStatus.FAILED
            }
            val failureReason = if (!result.success) {
                if (isCancelled) ActionFailureReason.CANCELLED
                else if (isRejected) ActionFailureReason.AUTHORIZATION_FAILURE
                else classifyFailureReason(result.error, result.message)
            } else null

            AiActionExecutionRecord(
                id = java.util.UUID.randomUUID().toString(),
                executionRecordId = parentRecordId,
                actionId = action.id,
                executionOrder = index + 1,
                actionType = action.type,
                actionTitle = action.title,
                status = status,
                affectedTaskId = result.affectedTaskId ?: action.taskId,
                affectedGoalId = result.affectedGoalId ?: action.goalId,
                message = result.message,
                error = result.error,
                failureReason = failureReason,
                timestamp = now
            )
        }

        val successCount = childRecords.count { it.status == ActionExecutionStatus.SUCCESS }
        val failedCount = childRecords.count { it.status == ActionExecutionStatus.FAILED }
        val skippedCount = childRecords.count { it.status == ActionExecutionStatus.SKIPPED }

        val overallStatus = forcedStatus ?: when {
            successCount == actions.size -> ExecutionOverallStatus.SUCCESS
            successCount > 0 -> ExecutionOverallStatus.PARTIAL
            else -> ExecutionOverallStatus.FAILURE
        }

        val summaryMessage = summaryOverride ?: when (overallStatus) {
            ExecutionOverallStatus.SUCCESS ->
                if (actions.size > 1) "Completed plan: all ${actions.size} actions succeeded."
                else results.firstOrNull()?.message ?: "Action completed successfully."
            ExecutionOverallStatus.PARTIAL ->
                "Partially completed: $successCount of ${actions.size} actions succeeded, ${actions.size - successCount} failed or skipped."
            ExecutionOverallStatus.FAILURE ->
                if (actions.size > 1) "Plan execution failed: none of the ${actions.size} actions succeeded."
                else results.firstOrNull()?.message ?: "Action execution failed."
            ExecutionOverallStatus.CANCELLED -> "Execution cancelled by user."
            ExecutionOverallStatus.REJECTED -> "Execution rejected: authorization error."
        }

        val executedActionCount = if (isCancelled) 0 else (actions.size - skippedCount)

        val record = AiExecutionRecord(
            id = parentRecordId,
            userPrompt = userPrompt,
            detectedIntent = detectedIntent,
            overallStatus = overallStatus,
            confirmationRequired = actions.any { it.requiresConfirmation },
            userConfirmed = userConfirmed,
            totalProposedActions = actions.size,
            executedActionCount = executedActionCount,
            successActionCount = successCount,
            failedActionCount = failedCount,
            skippedActionCount = skippedCount,
            summaryMessage = summaryMessage,
            timestamp = now,
            actionExecutions = childRecords
        )

        repository.saveExecutionRecord(record)
        lastExecutionRecord = record
        return record
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
