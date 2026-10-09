package com.example.nexora.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class NexoraChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class NexoraAiUiState(
    val isLoading: Boolean = false,
    val recommendations: List<AiRecommendation> = emptyList(),
    val proactiveInsights: List<AiRecommendation> = emptyList(),
    val dailyPlan: NexoraDailyPlan? = null,
    val memory: AiMemory = AiMemory(),
    val error: String? = null,
    val chatMessages: List<NexoraChatMessage> = emptyList(),
    val isChatLoading: Boolean = false,
    val proposedAction: AiAction? = null,
    val proposedPlan: List<AiAction> = emptyList(),
    val lastActionResult: AiActionResult? = null,
    val lastPlanResults: List<AiActionResult> = emptyList(),
    val lastExecutionRecord: AiExecutionRecord? = null,
    val executionHistory: List<AiExecutionRecord> = emptyList(),
    val conversationalState: AiConversationalState = AiConversationalState(),
    val lastBrainResponse: AiResponse? = null,
    val currentWorkflow: AgentWorkflow? = null,
    val proactiveSignals: List<AiProactiveSignal> = emptyList(),
    val automationRules: List<AiAutomationRule> = emptyList(),
    val personalContext: AiPersonalContext? = null,
    val homeProposedAction: AiAction? = null
)

class NexoraAiViewModel(
    private val engine: NexoraAiEngine,
    private val applicationContext: android.content.Context? = null,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(NexoraAiUiState())

    val uiState: StateFlow<NexoraAiUiState> =
        _uiState.asStateFlow()

    private val isActionExecuting = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        analyze()
        loadAutomationRules()
        loadInitialHomeState()
        scope.launch {
            engine.observeAutomationRules().collect { rules ->
                _uiState.update { it.copy(automationRules = rules) }
            }
        }
        scope.launch {
            engine.observeRecentExecutionRecords().collect { history ->
                _uiState.update { it.copy(executionHistory = history) }
            }
        }
    }

    private fun loadInitialHomeState() {
        scope.launch {
            try {
                val context = engine.getContext()
                val response = engine.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
                
                val signals = response.proactiveSignals.toMutableList()
                if (context.incompleteTasks.size >= 8 && signals.none { it.type == ProactiveSignalType.WORKLOAD_RISK }) {
                    signals.add(
                        0,
                        AiProactiveSignal(
                            type = ProactiveSignalType.WORKLOAD_RISK,
                            title = "High Workload Detected",
                            message = "You have ${context.incompleteTasks.size} incomplete tasks. Focusing on top priority items is recommended.",
                            severity = AiPriority.HIGH,
                            confidence = AiConfidence.HIGH,
                            evidence = "Incomplete tasks: ${context.incompleteTasks.size}",
                            fingerprint = "home_workload_advisory_${context.incompleteTasks.size}",
                            suggestedAction = null
                        )
                    )
                }

                val candidateAction = response.proposedActions.firstOrNull()
                val homeAction = if (candidateAction != null && candidateAction.requiresConfirmation) {
                    if (engine.isProposalPending(candidateAction.id)) candidateAction else engine.proposeAction(candidateAction)
                } else null

                _uiState.update {
                    it.copy(
                        personalContext = context.personalContext,
                        proactiveSignals = (signals + it.proactiveSignals).distinctBy { s -> s.fingerprint },
                        homeProposedAction = homeAction
                    )
                }

                // Trigger notifications for new high-priority signals
                applicationContext?.let { appContext ->
                    signals.forEach { signal ->
                        if (signal.severity >= AiPriority.HIGH) {
                            com.example.nexora.util.NexoraNotificationManager.showProactiveNotification(appContext, signal)
                        }
                    }
                }
            } catch (_: Exception) {
                // Silent fail for background proactive check
            }
        }
    }

    fun dismissHomeAction() {
        _uiState.value.homeProposedAction?.let { action ->
            scope.launch {
                val record = engine.recordCancellation(listOf(action))
                _uiState.update {
                    it.copy(
                        homeProposedAction = null,
                        lastExecutionRecord = record
                    )
                }
            }
        } ?: run {
            _uiState.update { it.copy(homeProposedAction = null) }
        }
    }

    fun executeHomeAction(action: AiAction, onComplete: () -> Unit) {
        if (!isActionExecuting.compareAndSet(false, true)) return
        
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = engine.confirmPendingAction(action)
                val record = engine.lastExecutionRecord
                _uiState.update {
                    it.copy(
                        homeProposedAction = null,
                        lastActionResult = result,
                        lastExecutionRecord = record,
                        error = if (!result.success) (result.error ?: result.message) else null
                    )
                }
                if (result.success) {
                    onComplete()
                    analyze()
                    loadAutomationRules()
                    loadInitialHomeState()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        homeProposedAction = null,
                        lastActionResult = AiActionResult(
                            success = false,
                            message = "Action failed: ${e.message ?: "Unknown error"}",
                            error = e.message ?: "Execution exception"
                        ),
                        error = e.message ?: "Action execution failed"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                isActionExecuting.set(false)
            }
        }
    }

    fun loadAutomationRules() {
        scope.launch(Dispatchers.IO) {
            val rules = engine.getAutomationRules()
            _uiState.update { it.copy(automationRules = rules) }
        }
    }

    fun toggleAutomationRule(rule: AiAutomationRule) {
        scope.launch(Dispatchers.IO) {
            val updated = rule.copy(enabled = !rule.enabled)
            engine.updateAutomationRule(updated)
            val rules = engine.getAutomationRules()
            _uiState.update { it.copy(automationRules = rules) }
        }
    }

    fun deleteAutomationRule(idOrName: String): Boolean {
        scope.launch(Dispatchers.IO) {
            val deleted = engine.deleteAutomationRule(idOrName)
            if (deleted) {
                val rules = engine.getAutomationRules()
                _uiState.update { it.copy(automationRules = rules) }
            }
        }
        return true
    }

    fun analyze() {
        if (_uiState.value.isLoading) return

        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
                val context = engine.getContext()

                _uiState.update {
                    it.copy(
                        recommendations = response.recommendations,
                        proactiveInsights = response.recommendations.filter { rec ->
                            rec.type == AiRecommendationType.WARNING || rec.type == AiRecommendationType.GOAL_ACTION
                        },
                        memory = context.memory,
                        lastBrainResponse = response,
                        currentWorkflow = response.workflow,
                        proactiveSignals = (response.proactiveSignals + it.proactiveSignals).distinctBy { s -> s.fingerprint }
                    )
                }

                // Trigger notifications for new high-priority signals
                applicationContext?.let { appContext ->
                    response.proactiveSignals.forEach { signal ->
                        if (signal.severity >= AiPriority.HIGH) {
                            com.example.nexora.util.NexoraNotificationManager.showProactiveNotification(appContext, signal)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to analyze your data."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createDailyPlan() {
        if (_uiState.value.isChatLoading) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isChatLoading = true,
                    isLoading = true,
                    error = null,
                    dailyPlan = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.DAILY_PLAN))
                val plan = response.dailyPlan ?: engine.createDailyPlan()

                _uiState.update {
                    it.copy(
                        dailyPlan = plan,
                        recommendations = if (response.recommendations.isNotEmpty()) response.recommendations else it.recommendations,
                        lastBrainResponse = response,
                        currentWorkflow = response.workflow,
                        proactiveSignals = response.proactiveSignals
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to create your daily plan.",
                        lastActionResult = AiActionResult(false, e.message ?: "Unable to create your daily plan.", error = e.message)
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, isChatLoading = false) }
            }
        }
    }

    fun recommendNextTask() {
        if (_uiState.value.isChatLoading) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isChatLoading = true,
                    isLoading = true,
                    error = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.NEXT_TASK))
                _uiState.update {
                    it.copy(
                        recommendations = response.recommendations,
                        proposedAction = response.proposedActions.firstOrNull(),
                        lastBrainResponse = response,
                        proactiveSignals = response.proactiveSignals,
                        conversationalState = response.conversationContext?.toAiConversationalState() ?: it.conversationalState
                    )
                }
                if (response.proposedActions.isNotEmpty()) {
                    proposePlan(response.proposedActions)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to recommend next task.",
                        lastActionResult = AiActionResult(false, e.message ?: "Unable to recommend next task.", error = e.message)
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, isChatLoading = false) }
            }
        }
    }

    fun analyzeGoals() {
        if (_uiState.value.isChatLoading) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isChatLoading = true,
                    isLoading = true,
                    error = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.GOAL_ANALYSIS))

                _uiState.update {
                    it.copy(
                        recommendations = response.recommendations,
                        lastBrainResponse = response,
                        proactiveSignals = response.proactiveSignals
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to analyze your goals.",
                        lastActionResult = AiActionResult(false, e.message ?: "Unable to analyze your goals.", error = e.message)
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, isChatLoading = false) }
            }
        }
    }

    fun analyzeProductivity() {
        if (_uiState.value.isLoading) return

        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.PRODUCTIVITY_ANALYSIS))

                _uiState.update {
                    it.copy(
                        recommendations = response.recommendations,
                        lastBrainResponse = response,
                        proactiveSignals = response.proactiveSignals
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to analyze productivity."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearResults() {
        _uiState.update {
            it.copy(
                recommendations = emptyList(),
                error = null,
                currentWorkflow = null
            )
        }
    }

    fun deleteMemory(id: String) {
        scope.launch {
            engine.deleteMemory(id)
            refreshMemory()
        }
    }

    fun clearAllMemory() {
        scope.launch {
            engine.clearAllMemory()
            refreshMemory()
        }
    }

    private suspend fun refreshMemory() {
        val context = engine.getContext()
        _uiState.update {
            it.copy(
                memory = context.memory
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isChatLoading) return

        val userMessage = NexoraChatMessage(
            text = text,
            isFromUser = true
        )

        val currentState = _uiState.value.conversationalState
        
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMessage,
                isChatLoading = true,
                error = null
            )
        }

        scope.launch {
            try {
                // 1. AI Brain Reasoned Request
                val response = engine.processRequest(AiRequest(
                    type = AiRequestType.CHAT, 
                    userMessage = text,
                    conversationContext = currentState.toConversationContext()
                ))
                
                val aiMessage = NexoraChatMessage(
                    text = response.message,
                    isFromUser = false
                )

                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + aiMessage,
                        lastBrainResponse = response,
                        currentWorkflow = response.workflow,
                        proactiveSignals = response.proactiveSignals,
                        conversationalState = response.conversationContext?.toAiConversationalState() ?: AiConversationalState(),
                        dailyPlan = response.dailyPlan ?: it.dailyPlan,
                        recommendations = if (response.recommendations.isNotEmpty()) response.recommendations else it.recommendations
                    )
                }

                processBrainResponse(response)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Nexora Brain is having trouble reasoning.",
                        lastActionResult = AiActionResult(false, e.message ?: "Nexora Brain error", error = e.message)
                    )
                }
            } finally {
                _uiState.update { it.copy(isChatLoading = false) }
            }
        }
    }

    private fun processBrainResponse(response: AiResponse) {
        // Clear proposed action/plan if the new response is explicitly CANCEL
        val isCancellation = response.decision?.type == AiDecisionType.CANCEL
        if (isCancellation) {
            val planToCancel = _uiState.value.proposedPlan.ifEmpty { listOfNotNull(_uiState.value.proposedAction) }
            planToCancel.forEach { engine.cancelProposal(it.id) }
            _uiState.update {
                it.copy(
                    conversationalState = AiConversationalState(),
                    proposedAction = null,
                    proposedPlan = emptyList()
                )
            }
            return
        }

        // Check if this response is an execution of an already-confirmed action/plan
        val isConfirmedExecution = response.decision?.reason?.contains("confirmed pending") == true ||
            (response.proposedActions.isNotEmpty() &&
             response.proposedActions.all { !it.requiresConfirmation && it.parameters["userConfirmed"] == true })

        if (isConfirmedExecution && response.proposedActions.isNotEmpty()) {
            val actions = response.proposedActions
            _uiState.update {
                it.copy(
                    conversationalState = AiConversationalState(),
                    proposedAction = null,
                    proposedPlan = emptyList()
                )
            }
            if (isActionExecuting.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        val results = engine.executePlan(actions)
                        val record = engine.lastExecutionRecord
                        val successCount = results.count { it.success }
                        val failureCount = results.count { !it.success }
                        val firstFailed = results.find { !it.success }
                        val allSuccess = failureCount == 0 && results.isNotEmpty()

                        val summaryResult = if (allSuccess) {
                            AiActionResult(
                                success = true,
                                message = if (results.size > 1) "Successfully executed all ${results.size} actions." else results.first().message,
                                affectedTaskId = results.firstNotNullOfOrNull { it.affectedTaskId },
                                affectedGoalId = results.firstNotNullOfOrNull { it.affectedGoalId }
                            )
                        } else if (successCount > 0) {
                            AiActionResult(
                                success = false,
                                message = "Partially executed: $successCount of ${results.size} actions succeeded, $failureCount failed.",
                                error = firstFailed?.error ?: firstFailed?.message,
                                affectedTaskId = results.firstNotNullOfOrNull { it.affectedTaskId },
                                affectedGoalId = results.firstNotNullOfOrNull { it.affectedGoalId }
                            )
                        } else {
                            firstFailed ?: AiActionResult(false, "Plan execution failed", error = "Execution failed")
                        }

                        _uiState.update {
                            it.copy(
                                lastActionResult = summaryResult,
                                lastPlanResults = results,
                                lastExecutionRecord = record,
                                error = if (!summaryResult.success) (summaryResult.error ?: summaryResult.message) else null
                            )
                        }
                        if (successCount > 0) {
                            analyze()
                            loadAutomationRules()
                            loadInitialHomeState()
                        }
                    } catch (e: Exception) {
                        val errorResult = AiActionResult(
                            success = false,
                            message = "Action failed: ${e.message ?: "Unknown error"}",
                            error = e.message ?: "Execution exception"
                        )
                        _uiState.update {
                            it.copy(
                                lastActionResult = errorResult,
                                lastPlanResults = listOf(errorResult),
                                error = e.message ?: "Action execution failed"
                            )
                        }
                    } finally {
                        isActionExecuting.set(false)
                    }
                }
            }
            return
        }
        
        when (response.responseType) {
            AiResponseType.CLARIFICATION_NEEDED -> {
                // Ambiguous request; no mutation or action proposed
            }
            AiResponseType.NO_ACTION, AiResponseType.INFORMATION -> {
                _uiState.update {
                    it.copy(
                        conversationalState = response.conversationContext?.toAiConversationalState() ?: AiConversationalState(),
                        proposedAction = it.proposedAction,
                        proposedPlan = it.proposedPlan
                    )
                }
            }
            AiResponseType.RECOMMENDATION, AiResponseType.PLAN -> {
                _uiState.update {
                    it.copy(
                        conversationalState = response.conversationContext?.toAiConversationalState()
                            ?: it.conversationalState.copy(
                                lastTaskId = response.relatedTaskId,
                                lastGoalId = response.relatedGoalId
                            ),
                        proposedAction = it.proposedAction,
                        proposedPlan = it.proposedPlan
                    )
                }
            }
            else -> {
                if (response.proposedActions.isNotEmpty()) {
                    proposePlan(response.proposedActions)
                    
                    // Also store in conversational state for confirmation flow
                    _uiState.update {
                        it.copy(
                            conversationalState = it.conversationalState.copy(
                                pendingAction = response.proposedActions.first(),
                                pendingPlan = response.proposedActions
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            conversationalState = response.conversationContext?.toAiConversationalState() ?: AiConversationalState(),
                            proposedAction = it.proposedAction,
                            proposedPlan = it.proposedPlan
                        )
                    }
                }
            }
        }
    }

    fun proposePlan(actions: List<AiAction>) {
        val currentPlan = _uiState.value.proposedPlan.ifEmpty { listOfNotNull(_uiState.value.proposedAction) }
        val newIds = actions.map { it.id }.toSet()
        currentPlan.filter { it.id !in newIds }.forEach {
            engine.cancelProposal(it.id)
        }
        val registeredPlan = actions.map { action ->
            if (action.requiresConfirmation || com.example.nexora.util.NexoraSecurity.isDestructiveAction(action)) {
                if (engine.isProposalPending(action.id)) {
                    action
                } else {
                    engine.proposeAction(action)
                }
            } else {
                action
            }
        }
        _uiState.update {
            it.copy(
                proposedAction = registeredPlan.firstOrNull(),
                proposedPlan = registeredPlan
            )
        }
    }

    fun proposeAction(action: AiAction) {
        proposePlan(listOf(action))
    }

    fun confirmAction() {
        val plan = _uiState.value.proposedPlan.ifEmpty { listOfNotNull(_uiState.value.proposedAction) }
        if (plan.isEmpty()) return
        if (!isActionExecuting.compareAndSet(false, true)) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    proposedAction = null,
                    proposedPlan = emptyList(),
                    error = null
                )
            }
            try {
                val results = engine.confirmPendingPlan(plan)
                val record = engine.lastExecutionRecord
                val successCount = results.count { it.success }
                val failureCount = results.count { !it.success }
                val firstFailed = results.find { !it.success }
                val allSuccess = failureCount == 0 && results.isNotEmpty()

                val summaryResult = if (allSuccess) {
                    AiActionResult(
                        success = true,
                        message = if (results.size > 1) "Successfully executed all ${results.size} actions." else results.first().message,
                        affectedTaskId = results.firstNotNullOfOrNull { it.affectedTaskId },
                        affectedGoalId = results.firstNotNullOfOrNull { it.affectedGoalId }
                    )
                } else if (successCount > 0) {
                    AiActionResult(
                        success = false,
                        message = "Partially executed: $successCount of ${results.size} actions succeeded, $failureCount failed.",
                        error = firstFailed?.error ?: firstFailed?.message,
                        affectedTaskId = results.firstNotNullOfOrNull { it.affectedTaskId },
                        affectedGoalId = results.firstNotNullOfOrNull { it.affectedGoalId }
                    )
                } else {
                    firstFailed ?: AiActionResult(false, "Plan execution failed", error = "Execution failed")
                }

                _uiState.update {
                    it.copy(
                        lastActionResult = summaryResult,
                        lastPlanResults = results,
                        lastExecutionRecord = record,
                        currentWorkflow = null,
                        error = if (!summaryResult.success) (summaryResult.error ?: summaryResult.message) else null
                    )
                }
                
                if (successCount > 0) {
                    analyze()
                    loadAutomationRules()
                    loadInitialHomeState()
                }
            } catch (e: Exception) {
                val errorResult = AiActionResult(
                    success = false,
                    message = "Plan failed: ${e.message ?: "Unknown error"}",
                    error = e.message ?: "Execution exception"
                )
                _uiState.update {
                    it.copy(
                        lastActionResult = errorResult,
                        lastPlanResults = listOf(errorResult),
                        error = e.message ?: "Plan execution failed"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                isActionExecuting.set(false)
            }
        }
    }

    fun dismissAction() {
        val plan = _uiState.value.proposedPlan.ifEmpty { listOfNotNull(_uiState.value.proposedAction) }
        if (plan.isNotEmpty()) {
            scope.launch {
                val record = engine.recordCancellation(plan)
                _uiState.update {
                    it.copy(
                        proposedAction = null,
                        proposedPlan = emptyList(),
                        lastExecutionRecord = record
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    proposedAction = null,
                    proposedPlan = emptyList()
                )
            }
        }
    }

    fun dismissResult() {
        _uiState.update {
            it.copy(
                lastActionResult = null,
                lastPlanResults = emptyList(),
                lastExecutionRecord = null,
                error = null
            )
        }
    }
}
