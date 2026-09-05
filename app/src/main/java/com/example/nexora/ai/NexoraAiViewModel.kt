package com.example.nexora.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NexoraAiUiState(
    val isLoading: Boolean = false,
    val recommendations: List<AiRecommendation> = emptyList(),
    val error: String? = null
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
                error = null
            )

            try {
                val recommendations = engine.createDailyPlan()

                _uiState.value = NexoraAiUiState(
                    isLoading = false,
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = NexoraAiUiState(
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
        _uiState.value = NexoraAiUiState()
    }
}