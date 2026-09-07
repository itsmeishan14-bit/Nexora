package com.example.nexora.ai.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexora.ai.NexoraAiEngine
import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiEvaluationUiState(
    val isLoading: Boolean = false,
    val report: AiEvaluationReport? = null,
    val error: String? = null
)

class AiEvaluationViewModel(
    private val repository: NexoraRepository,
    private val engine: NexoraAiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiEvaluationUiState())
    val uiState: StateFlow<AiEvaluationUiState> = _uiState.asStateFlow()

    private val evaluationEngine = AiEvaluationEngine(
        repository = repository,
        aiService = LocalAiServiceAccessor.getService(engine),
        actionExecutor = LocalAiServiceAccessor.getExecutor(engine),
        toolRegistry = LocalAiServiceAccessor.getRegistry(engine)
    )

    fun runFullBenchmark() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cases = AiEvaluationSuite.getAllCases()
                val report = evaluationEngine.runBenchmark(cases)
                _uiState.value = _uiState.value.copy(isLoading = false, report = report)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

/**
 * Internal helper to access private engine components for evaluation.
 * In a real project with DI, these would be provided directly.
 */
object LocalAiServiceAccessor {
    fun getService(engine: NexoraAiEngine): com.example.nexora.ai.NexoraAiService {
        val field = engine.javaClass.getDeclaredField("aiService")
        field.isAccessible = true
        return field.get(engine) as com.example.nexora.ai.NexoraAiService
    }
    
    fun getExecutor(engine: NexoraAiEngine): com.example.nexora.ai.AiActionExecutor {
        val field = engine.javaClass.getDeclaredField("actionExecutor")
        field.isAccessible = true
        return field.get(engine) as com.example.nexora.ai.AiActionExecutor
    }
    
    fun getRegistry(engine: NexoraAiEngine): com.example.nexora.ai.AiToolRegistry {
        // Find brain first
        val brainField = engine.javaClass.getDeclaredField("brain")
        brainField.isAccessible = true
        val brain = brainField.get(engine)
        
        val registryField = brain.javaClass.getDeclaredField("toolRegistry")
        registryField.isAccessible = true
        return registryField.get(brain) as com.example.nexora.ai.AiToolRegistry
    }
}
