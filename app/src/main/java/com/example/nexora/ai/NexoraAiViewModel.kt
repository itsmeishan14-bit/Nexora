package com.example.nexora.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val conversationalState: AiConversationalState = AiConversationalState()
)

class NexoraAiViewModel(
    private val engine: NexoraAiEngine,
    private val actionExecutor: AiActionExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexoraAiUiState())

    val uiState: StateFlow<NexoraAiUiState> =
        _uiState.asStateFlow()

    init {
        analyze()
    }

    fun analyze() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val context = engine.getContext()
                val recommendations = engine.analyze()
                val proactive = engine.getProactiveInsights()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = recommendations,
                    proactiveInsights = proactive,
                    memory = context.memory
                )
            } catch (e: Exception) {
                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    error = e.message ?: "Unable to analyze your data."
                )
            }
        }
    }

    fun createDailyPlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                dailyPlan = null
            )

            try {
                val plan = engine.createDailyPlan()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dailyPlan = plan,
                    recommendations = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unable to create your daily plan."
                )
            }
        }
    }

    fun analyzeGoals() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val recommendations = engine.analyzeGoals()

                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    error = e.message ?: "Unable to analyze your goals."
                )
            }
        }
    }

    fun analyzeProductivity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val recommendations = engine.analyzeProductivity()

                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    error = e.message ?: "Unable to analyze productivity."
                )
            }
        }
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            recommendations = emptyList(),
            error = null
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = NexoraChatMessage(
            text = text,
            isFromUser = true
        )

        val currentState = _uiState.value.conversationalState
        
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + userMessage,
            isChatLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                // 1. Handle follow-up if we have pending candidates
                if (currentState.candidateTaskIds.isNotEmpty()) {
                    handleAmbiguityFollowUp(text, currentState)
                    return@launch
                }

                // 2. AI Reasoning & Text Response
                val responseText = engine.ask(text)
                
                val aiMessage = NexoraChatMessage(
                    text = responseText,
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false
                )

                // 3. AI Structured Decision / Tool Calling
                val structuredResult = engine.decide(text)
                
                processStructuredResult(structuredResult)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChatLoading = false,
                    error = e.message ?: "Nexora is having trouble responding."
                )
            }
        }
    }

    private fun processStructuredResult(result: AiModelStructuredResponse) {
        when (result.decision.type) {
            AiDecisionType.AMBIGUOUS -> {
                _uiState.value = _uiState.value.copy(
                    conversationalState = AiConversationalState(
                        candidateTaskIds = result.candidateTaskIds,
                        candidateGoalIds = result.candidateGoalIds
                    )
                )
            }
            AiDecisionType.NO_ACTION -> {
                // Clear state if no action detected
                _uiState.value = _uiState.value.copy(
                    conversationalState = AiConversationalState()
                )
            }
            else -> {
                val action = if (result.actions.isNotEmpty()) {
                    result.actions.first()
                } else {
                    decisionToAction(result.decision)
                }
                proposeAction(action)
                
                // Clear conversational state as we found a definitive action
                _uiState.value = _uiState.value.copy(
                    conversationalState = AiConversationalState()
                )
            }
        }
    }

    private suspend fun handleAmbiguityFollowUp(text: String, state: AiConversationalState) {
        val context = engine.getContext()
        val candidates = context.tasks.filter { it.id in state.candidateTaskIds }
        
        val match = AiEntityResolver.resolveTask(text, candidates)
        
        when (match) {
            is ResolutionResult.Success -> {
                // Now we re-run the "ask" but with the specific task resolved?
                // Or we just proceed with whatever the pending intent was.
                // For simplicity, let's assume the user was trying to mark it complete or update it.
                // We'll just re-run the decide with a more specific query.
                val specificText = "Task ID ${match.entity.id} ${text}" // Crude but effective for local resolver
                val result = engine.decide(specificText)
                
                val aiMessage = NexoraChatMessage(
                    text = "I've resolved the task to \"${match.entity.title}\".",
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false
                )
                
                processStructuredResult(result)
            }
            is ResolutionResult.Ambiguous -> {
                val aiMessage = NexoraChatMessage(
                    text = "I still found multiple matches among those candidates. Could you be more specific?",
                    isFromUser = false
                )
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false,
                    conversationalState = state.copy(candidateTaskIds = match.candidates.map { it.id })
                )
            }
            is ResolutionResult.NotFound -> {
                val aiMessage = NexoraChatMessage(
                    text = "I couldn't match that to any of the candidate tasks.",
                    isFromUser = false
                )
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false,
                    conversationalState = AiConversationalState()
                )
            }
        }
    }

    private fun decisionToAction(decision: AiDecision): AiAction {
        return AiAction(
            type = mapDecisionTypeToActionType(decision.type),
            title = decision.title,
            description = decision.reason,
            taskId = decision.taskId,
            goalId = decision.goalId,
            priority = decision.priority,
            requiresConfirmation = true
        )
    }

    private fun mapDecisionTypeToActionType(type: AiDecisionType): AiActionType {
        return when (type) {
            AiDecisionType.START_TASK -> AiActionType.OPEN_TASK
            AiDecisionType.COMPLETE_TASK -> AiActionType.COMPLETE_TASK
            AiDecisionType.RESCHEDULE_TASK -> AiActionType.RESCHEDULE_TASK
            AiDecisionType.CREATE_TASK -> AiActionType.CREATE_TASK
            AiDecisionType.UPDATE_TASK -> AiActionType.UPDATE_TASK
            AiDecisionType.DELETE_TASK -> AiActionType.DELETE_TASK
            AiDecisionType.UPDATE_GOAL -> AiActionType.UPDATE_GOAL
            AiDecisionType.DELETE_GOAL -> AiActionType.DELETE_GOAL
            AiDecisionType.DAILY_PLAN -> AiActionType.CREATE_TASK // Or a specific planning action
            AiDecisionType.SHOW_INSIGHT -> AiActionType.SHOW_INSIGHT
            else -> AiActionType.SHOW_INSIGHT
        }
    }

    fun proposeAction(action: AiAction) {
        _uiState.value = _uiState.value.copy(
            proposedAction = action
        )
    }

    fun confirmAction() {
        val action = _uiState.value.proposedAction ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                proposedAction = null
            )
            
            val result = actionExecutor.execute(action)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                lastActionResult = result
            )
            
            if (result.success) {
                // Refresh data
                analyze()
            }
        }
    }

    fun dismissAction() {
        _uiState.value = _uiState.value.copy(
            proposedAction = null
        )
    }

    fun dismissResult() {
        _uiState.value = _uiState.value.copy(
            lastActionResult = null
        )
    }
}
