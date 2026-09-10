package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexora.ai.AiGoalDecomposition
import com.example.nexora.ai.AiGoalStep
import com.example.nexora.ai.AiPriority
import com.example.nexora.ai.NexoraAiEngine
import com.example.nexora.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun AiGoalDecomposerScreen(
    engine: NexoraAiEngine,
    onBack: () -> Unit = {},
    onTasksCreated: () -> Unit = {}
) {
    val viewModel: AiGoalDecomposerViewModel = viewModel(
        factory = remember(engine) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiGoalDecomposerViewModel(engine = engine) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var goalTitle by remember { mutableStateOf("") }
    var goalDescription by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NexoraBackgroundLight,
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Gray90)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Green10)
                    }
                    Text(
                        text = "Goal Decomposer",
                        style = MaterialTheme.typography.titleLarge,
                        color = Green10,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // INPUT SECTION
            if (uiState.decomposition == null) {
                item {
                    Text(
                        text = "Break down your objective.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Green10
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        TextField(
                            value = goalTitle,
                            onValueChange = { goalTitle = it },
                            placeholder = { Text("What's the goal?", style = MaterialTheme.typography.titleLarge, color = Green80) },
                            textStyle = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Green60,
                                unfocusedIndicatorColor = Gray90
                            )
                        )
                        OutlinedTextField(
                            value = goalDescription,
                            onValueChange = { goalDescription = it },
                            placeholder = { Text("Additional context (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = NexoraShapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green60,
                                unfocusedBorderColor = Gray90
                            )
                        )
                        Button(
                            onClick = { viewModel.decompose(goalTitle, goalDescription) },
                            enabled = goalTitle.isNotBlank() && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = NexoraShapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = Green10)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Generate Steps", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // RESULTS SECTION
            uiState.decomposition?.let { decomposition ->
                item {
                    Column {
                        Text(
                            text = "Nexora's Plan",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Green10
                        )
                        Text(
                            text = decomposition.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Green40
                        )
                    }
                }

                items(items = decomposition.steps, key = { it.order }) { step ->
                    StepCard(
                        step = step,
                        isSelected = uiState.selectedSteps.contains(step.order),
                        onToggle = { viewModel.toggleStep(step.order) }
                    )
                }

                item {
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = uiState.selectedSteps.isNotEmpty() && !uiState.isCreatingTasks,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = NexoraShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Green60)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Create ${uiState.selectedSteps.size} Tasks")
                    }
                }
                
                item {
                    TextButton(onClick = { viewModel.clear() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start over", color = Green40)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Create Tasks") },
            text = { Text("Add these steps to your workspace?") },
            confirmButton = {
                TextButton(onClick = { 
                    showConfirmDialog = false
                    viewModel.createTasks(onComplete = onTasksCreated) 
                }) {
                    Text("Add Tasks", color = Green60, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = Green40)
                }
            },
            containerColor = Color.White,
            shape = NexoraShapes.large
        )
    }
}

@Composable
private fun StepCard(
    step: AiGoalStep,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    NexoraCard(
        modifier = Modifier.nexoraClickable { onToggle() },
        tier = if (isSelected) NexoraCardTier.Elevated else NexoraCardTier.Resting,
        containerColor = if (isSelected) Green95 else Color.White
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Green60)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = step.title, style = MaterialTheme.typography.titleSmall, color = Green10)
                if (step.description.isNotBlank()) {
                    Text(text = step.description, style = MaterialTheme.typography.bodySmall, color = Green40)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Green90, shape = NexoraShapes.small) {
                        Text(
                            text = step.priority.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Green20
                        )
                    }
                    Text(text = step.estimatedDuration, style = MaterialTheme.typography.labelSmall, color = Green40, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// VIEWMODEL & UI STATE (Restored from previous version to maintain architecture)
data class AiGoalDecomposerUiState(
    val isLoading: Boolean = false,
    val decomposition: AiGoalDecomposition? = null,
    val selectedSteps: Set<Int> = emptySet(),
    val error: String? = null,
    val successMessage: String? = null,
    val isCreatingTasks: Boolean = false
)

class AiGoalDecomposerViewModel(
    private val engine: NexoraAiEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiGoalDecomposerUiState())
    val uiState: StateFlow<AiGoalDecomposerUiState> = _uiState.asStateFlow()

    fun decompose(goalTitle: String, goalDescription: String) {
        if (goalTitle.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = engine.decomposeGoal(goalTitle, goalDescription)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    decomposition = result,
                    selectedSteps = result.steps.map { it.order }.toSet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleStep(order: Int) {
        val current = _uiState.value.selectedSteps
        _uiState.value = _uiState.value.copy(selectedSteps = if (current.contains(order)) current - order else current + order)
    }

    fun clear() {
        _uiState.value = AiGoalDecomposerUiState()
    }

    fun createTasks(onComplete: () -> Unit) {
        val state = _uiState.value
        val steps = state.decomposition?.steps?.filter { it.order in state.selectedSteps } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingTasks = true)
            steps.forEach { step ->
                engine.executeAction(com.example.nexora.ai.AiAction(
                    type = com.example.nexora.ai.AiActionType.CREATE_TASK,
                    title = step.title,
                    description = step.description,
                    parameters = mapOf(
                        "title" to step.title,
                        "goalTitle" to state.decomposition.goalTitle,
                        "duration" to step.estimatedDuration,
                        "priority" to step.priority.name
                    ),
                    requiresConfirmation = false
                ))
            }
            onComplete()
            _uiState.value = _uiState.value.copy(isCreatingTasks = false, decomposition = null)
        }
    }
}
