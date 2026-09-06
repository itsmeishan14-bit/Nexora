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
    val dailyPlan: NexoraDailyPlan? = null,
    val memory: AiMemory = AiMemory(),
    val error: String? = null,
    val chatMessages: List<NexoraChatMessage> = emptyList(),
    val isChatLoading: Boolean = false,
    val proposedAction: AiAction? = null,
    val lastActionResult: AiActionResult? = null
)

class NexoraAiViewModel(
    private val engine: NexoraAiEngine,
    private val actionExecutor: AiActionExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexoraAiUiState())

    val uiState: StateFlow<NexoraAiUiState> =
        _uiState.asStateFlow()

    fun analyze() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val context = engine.getContext()
                val recommendations = engine.analyze()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    recommendations = recommendations,
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

        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + userMessage,
            isChatLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val responseText = engine.ask(text)
                
                val aiMessage = NexoraChatMessage(
                    text = responseText,
                    isFromUser = false
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiMessage,
                    isChatLoading = false
                )

                // Check if we should propose an action based on chat response
                checkForProposedAction(responseText)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChatLoading = false,
                    error = e.message ?: "Nexora is having trouble responding."
                )
            }
        }
    }

    private fun checkForProposedAction(aiResponse: String) {
        val lower = aiResponse.lowercase()
        
        // Simple mock of AI decision logic
        when {
            lower.contains("create a task") || lower.contains("add a task") -> {
                val titleMatch = Regex("\"([^\"]*)\"").find(aiResponse)
                val title = titleMatch?.groupValues?.get(1) ?: "New AI Task"
                
                proposeAction(AiAction(
                    type = AiActionType.CREATE_TASK,
                    title = "Propose: Create Task",
                    description = "Should I create the task \"$title\"?",
                    parameters = mapOf("title" to title),
                    requiresConfirmation = true
                ))
            }
            
            lower.contains("mark") && lower.contains("complete") -> {
                // Propose completing the next best task as a guess
                viewModelScope.launch {
                    val context = engine.getContext()
                    val nextTask = context.incompleteTasks.firstOrNull()
                    if (nextTask != null) {
                        proposeAction(AiAction(
                            type = AiActionType.COMPLETE_TASK,
                            title = "Propose: Complete Task",
                            description = "Mark \"${nextTask.title}\" as complete?",
                            taskId = nextTask.id,
                            reason = "User expressed desire to complete a task.",
                            requiresConfirmation = true
                        ))
                    }
                }
            }

            lower.contains("delete task") -> {
                // Propose deleting a task (requires confirmation)
                viewModelScope.launch {
                    val context = engine.getContext()
                    val taskToDelete = context.incompleteTasks.firstOrNull()
                    if (taskToDelete != null) {
                        proposeAction(AiAction(
                            type = AiActionType.DELETE_TASK,
                            title = "Propose: Delete Task",
                            description = "Delete \"${taskToDelete.title}\" permanently?",
                            taskId = taskToDelete.id,
                            priority = AiPriority.HIGH,
                            requiresConfirmation = true
                        ))
                    }
                }
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
