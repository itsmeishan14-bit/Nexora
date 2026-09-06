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
    private val engine: NexoraAiEngine
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
                _uiState.value = _uiState.value.copy(
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

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
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

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
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

                // 2. AI Reasoning, Text Response & Decision in one pass
                val result = engine.ask(text)
                
                val aiMessage = NexoraChatMessage(
                    text = result.textResponse ?: "I'm not sure how to respond.",
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false
                )

                processStructuredResult(result)

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
                
                // If it's just showing an insight with no actual action, don't propose
                if (action.type != AiActionType.SHOW_INSIGHT || result.decision.type == AiDecisionType.WARNING) {
                    proposeAction(action)
                }
                
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
                val specificText = "Task ID ${match.entity.id} ${text}"
                val result = engine.ask(specificText)
                
                val aiMessage = NexoraChatMessage(
                    text = "I've resolved the task to \"${match.entity.title}\". " + (result.textResponse ?: ""),
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
            reason = decision.evidence,
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
            
            val result = engine.executeAction(action)
            
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
