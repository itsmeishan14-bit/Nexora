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
    val error: String? = null,
    val chatMessages: List<NexoraChatMessage> = emptyList(),
    val isChatLoading: Boolean = false
)

class NexoraAiViewModel(
    private val engine: NexoraAiEngine
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
                val recommendations = engine.analyze()

                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    recommendations = recommendations
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChatLoading = false,
                    error = e.message ?: "Nexora is having trouble responding."
                )
            }
        }
    }
}