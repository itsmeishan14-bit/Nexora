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
    val conversationalState: AiConversationalState = AiConversationalState(),
    val lastBrainResponse: AiResponse? = null,
    val currentWorkflow: AgentWorkflow? = null
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
                val response = engine.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
                val context = engine.getContext()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = response.recommendations,
                    proactiveInsights = response.recommendations.filter { it.type == AiRecommendationType.WARNING || it.type == AiRecommendationType.GOAL_ACTION },
                    memory = context.memory,
                    lastBrainResponse = response,
                    currentWorkflow = response.workflow
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
                val response = engine.processRequest(AiRequest(AiRequestType.DAILY_PLAN))
                val plan = engine.createDailyPlan() // Keep using the specialized plan for legacy UI

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dailyPlan = plan,
                    recommendations = emptyList(),
                    lastBrainResponse = response,
                    currentWorkflow = response.workflow
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
                val response = engine.processRequest(AiRequest(AiRequestType.GOAL_ANALYSIS))

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = response.recommendations,
                    lastBrainResponse = response
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
                val response = engine.processRequest(AiRequest(AiRequestType.PRODUCTIVITY_ANALYSIS))

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = response.recommendations,
                    lastBrainResponse = response
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
            error = null,
            currentWorkflow = null
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

                // 2. AI Brain Reasoned Request
                val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = text))
                
                val aiMessage = NexoraChatMessage(
                    text = response.message,
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false,
                    lastBrainResponse = response,
                    currentWorkflow = response.workflow
                )

                processBrainResponse(response)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChatLoading = false,
                    error = e.message ?: "Nexora Brain is having trouble reasoning."
                )
            }
        }
    }

    private fun processBrainResponse(response: AiResponse) {
        when (response.responseType) {
            AiResponseType.CLARIFICATION_NEEDED -> {
                // Conversational state should ideally be in AiResponse, but for now we maintain compatibility
                // If the brain returned proposed actions that are ambiguous, handle it
            }
            AiResponseType.NO_ACTION, AiResponseType.INFORMATION -> {
                 _uiState.value = _uiState.value.copy(
                    conversationalState = AiConversationalState()
                )
            }
            else -> {
                if (response.proposedActions.isNotEmpty()) {
                    proposeAction(response.proposedActions.first())
                }
                
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
                val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Task ID ${match.entity.id} ${text}"))
                
                val aiMessage = NexoraChatMessage(
                    text = "I've resolved the task to \"${match.entity.title}\". " + response.message,
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false,
                    lastBrainResponse = response,
                    currentWorkflow = response.workflow
                )
                
                processBrainResponse(response)
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
                lastActionResult = result,
                currentWorkflow = null
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
