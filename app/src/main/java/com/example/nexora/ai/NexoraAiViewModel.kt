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
    val lastActionResult: AiActionResult? = null,
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
        _uiState.value.homeProposedAction?.let { engine.cancelProposal(it.id) }
        _uiState.update { it.copy(homeProposedAction = null) }
    }

    fun executeHomeAction(action: AiAction, onComplete: () -> Unit) {
        if (!isActionExecuting.compareAndSet(false, true)) return
        
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = engine.confirmPendingAction(action)
                _uiState.update {
                    it.copy(
                        homeProposedAction = null,
                        lastActionResult = result,
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
        if (_uiState.value.isLoading) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    dailyPlan = null
                )
            }

            try {
                val response = engine.processRequest(AiRequest(AiRequestType.DAILY_PLAN))
                val plan = engine.createDailyPlan() // Keep using the specialized plan for legacy UI

                _uiState.update {
                    it.copy(
                        dailyPlan = plan,
                        recommendations = emptyList(),
                        lastBrainResponse = response,
                        currentWorkflow = response.workflow,
                        proactiveSignals = response.proactiveSignals
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Unable to create your daily plan."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun analyzeGoals() {
        if (_uiState.value.isLoading) return
        
        scope.launch {
            _uiState.update {
                it.copy(
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
                        error = e.message ?: "Unable to analyze your goals."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
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
        if (text.isBlank()) return

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
                // 1. Handle follow-up if we have pending candidates
                if (currentState.candidateTaskIds.isNotEmpty()) {
                    handleAmbiguityFollowUp(text, currentState)
                    return@launch
                }

                // 2. AI Brain Reasoned Request
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
                        conversationalState = response.conversationContext?.toAiConversationalState() ?: AiConversationalState()
                    )
                }

                processBrainResponse(response)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Nexora Brain is having trouble reasoning."
                    )
                }
            } finally {
                _uiState.update { it.copy(isChatLoading = false) }
            }
        }
    }

    private fun processBrainResponse(response: AiResponse) {
        // Clear proposed action if the new response is explicitly CANCEL
        val isCancellation = response.decision?.type == AiDecisionType.CANCEL
        if (isCancellation) {
            _uiState.value.proposedAction?.let { engine.cancelProposal(it.id) }
            _uiState.update {
                it.copy(
                    conversationalState = AiConversationalState(),
                    proposedAction = null
                )
            }
            return
        }

        // Check if this response is an execution of an already-confirmed action
        val isConfirmedExecution = response.decision?.reason == "User explicitly confirmed pending action." ||
            (response.proposedActions.isNotEmpty() &&
             response.proposedActions.first().let { !it.requiresConfirmation && it.parameters["userConfirmed"] == true })

        if (isConfirmedExecution && response.proposedActions.isNotEmpty()) {
            val action = response.proposedActions.first()
            _uiState.update {
                it.copy(
                    conversationalState = AiConversationalState(),
                    proposedAction = null
                )
            }
            if (isActionExecuting.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        val result = engine.executeAction(action)
                        _uiState.update {
                            it.copy(
                                lastActionResult = result,
                                error = if (!result.success) (result.error ?: result.message) else null
                            )
                        }
                        if (result.success) {
                            analyze()
                            loadAutomationRules()
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                lastActionResult = AiActionResult(
                                    success = false,
                                    message = "Action failed: ${e.message ?: "Unknown error"}",
                                    error = e.message ?: "Execution exception"
                                ),
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
                        conversationalState = AiConversationalState(),
                        proposedAction = it.proposedAction
                    )
                }
            }
            else -> {
                if (response.proposedActions.isNotEmpty()) {
                    val action = response.proposedActions.first()
                    proposeAction(action)
                    
                    // Also store in conversational state for confirmation flow
                    _uiState.update {
                        it.copy(
                            conversationalState = it.conversationalState.copy(pendingAction = action)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            conversationalState = response.conversationContext?.toAiConversationalState() ?: AiConversationalState(),
                            proposedAction = it.proposedAction
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleAmbiguityFollowUp(text: String, state: AiConversationalState) {
        val context = engine.getContext()
        val candidates = context.tasks.filter { it.id in state.candidateTaskIds }
        
        val match = AiEntityResolver.resolveTask(text, candidates)
        
        when (match) {
            is ResolutionResult.Success -> {
                val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Task ID ${match.entity.id} ${text}"))
                
                val aiMessage = NexoraChatMessage(
                    text = "I've resolved the task to \"${match.entity.title}\". " + response.message,
                    isFromUser = false
                )

                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + aiMessage,
                        lastBrainResponse = response,
                        currentWorkflow = response.workflow,
                        proactiveSignals = response.proactiveSignals
                    )
                }
                
                processBrainResponse(response)
            }
            is ResolutionResult.Ambiguous -> {
                val aiMessage = NexoraChatMessage(
                    text = "I still found multiple matches among those candidates. Could you be more specific?",
                    isFromUser = false
                )
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + aiMessage,
                        conversationalState = state.copy(candidateTaskIds = match.candidates.map { it.id })
                    )
                }
            }
            is ResolutionResult.NotFound -> {
                val aiMessage = NexoraChatMessage(
                    text = "I couldn't match that to any of the candidate tasks.",
                    isFromUser = false
                )
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + aiMessage,
                        conversationalState = AiConversationalState()
                    )
                }
            }
        }
    }

    fun proposeAction(action: AiAction) {
        val current = _uiState.value.proposedAction
        if (current != null && current.id != action.id) {
            engine.cancelProposal(current.id)
        }
        val registered = if (action.requiresConfirmation || com.example.nexora.util.NexoraSecurity.isDestructiveAction(action)) {
            if (engine.isProposalPending(action.id)) {
                action
            } else {
                engine.proposeAction(action)
            }
        } else {
            action
        }
        _uiState.update {
            it.copy(
                proposedAction = registered
            )
        }
    }

    fun confirmAction() {
        val action = _uiState.value.proposedAction ?: return
        if (!isActionExecuting.compareAndSet(false, true)) return
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    proposedAction = null,
                    error = null
                )
            }
            try {
                val result = engine.confirmPendingAction(action)
                
                _uiState.update {
                    it.copy(
                        lastActionResult = result,
                        currentWorkflow = null,
                        error = if (!result.success) (result.error ?: result.message) else null
                    )
                }
                
                if (result.success) {
                    analyze()
                    loadAutomationRules()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
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

    fun dismissAction() {
        _uiState.value.proposedAction?.let { engine.cancelProposal(it.id) }
        _uiState.update {
            it.copy(
                proposedAction = null
            )
        }
    }

    fun dismissResult() {
        _uiState.update {
            it.copy(
                lastActionResult = null
            )
        }
    }
}
