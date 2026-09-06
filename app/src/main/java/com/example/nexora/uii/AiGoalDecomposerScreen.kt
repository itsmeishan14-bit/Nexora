package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

private val NexoraBackground = Color(0xFFF7F8F4)
private val NexoraInk = Color(0xFF17231C)
private val NexoraGreen = Color(0xFF78A982)
private val NexoraSoftGreen = Color(0xFFE4EFE5)
private val NexoraMuted = Color(0xFF747B75)
private val NexoraBorder = Color(0xFFE1E5E1)

data class AiGoalDecomposerUiState(
    val isLoading: Boolean = false,
    val decomposition: AiGoalDecomposition? = null,
    val selectedSteps: Set<Int> = emptySet(),
    val error: String? = null,
    val successMessage: String? = null,
    val isCreatingTasks: Boolean = false
)

class AiGoalDecomposerViewModel(
    private val engine: NexoraAiEngine,
    private val repository: NexoraRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(AiGoalDecomposerUiState())

    val uiState: StateFlow<AiGoalDecomposerUiState> =
        _uiState.asStateFlow()

    fun decompose(
        goalTitle: String,
        goalDescription: String
    ) {

        if (goalTitle.isBlank()) {
            _uiState.value =
                AiGoalDecomposerUiState(
                    error = "Enter a goal first."
                )
            return
        }

        viewModelScope.launch {

            _uiState.value =
                AiGoalDecomposerUiState(
                    isLoading = true
                )

            try {

                val result =
                    engine.decomposeGoal(
                        goalTitle = goalTitle,
                        goalDescription = goalDescription
                    )

                _uiState.value =
                    AiGoalDecomposerUiState(
                        decomposition = result,
                        selectedSteps = result.steps.map { it.order }.toSet()
                    )

            } catch (e: Exception) {

                _uiState.value =
                    AiGoalDecomposerUiState(
                        error =
                            e.message
                                ?: "Unable to decompose this goal."
                    )
            }
        }
    }

    fun toggleStep(order: Int) {
        val current = _uiState.value.selectedSteps
        val next = if (current.contains(order)) {
            current - order
        } else {
            current + order
        }
        _uiState.value = _uiState.value.copy(
            selectedSteps = next
        )
    }

    fun selectAll() {
        val all = _uiState.value.decomposition?.steps?.map { it.order }?.toSet() ?: emptySet()
        _uiState.value = _uiState.value.copy(
            selectedSteps = all
        )
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(
            selectedSteps = emptySet()
        )
    }

    fun createTasks(onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val decomposition = state.decomposition ?: return
        val selectedOrders = state.selectedSteps
        
        if (selectedOrders.isEmpty()) {
            _uiState.value = state.copy(error = "No steps selected.")
            return
        }

        val selectedSteps = decomposition.steps.filter { it.order in selectedOrders }
        
        viewModelScope.launch {
            _uiState.value = state.copy(isCreatingTasks = true, error = null)
            
            try {
                val existingTasks = repository.observeTasksOnce()
                val goalTasks = existingTasks.filter { it.goalTitle == decomposition.goalTitle }
                
                var createdCount = 0
                var existingCount = 0
                
                selectedSteps.forEach { step ->
                    val alreadyExists = goalTasks.any { it.title == step.title }
                    
                    if (alreadyExists) {
                        existingCount++
                    } else {
                        val newTask = PremiumTask(
                            title = step.title,
                            category = "AI Goal Step", // Default category
                            duration = step.estimatedDuration,
                            goalTitle = decomposition.goalTitle,
                            priority = mapPriority(step.priority),
                            completed = false
                        )
                        repository.addTask(newTask)
                        createdCount++
                    }
                }
                
                if (createdCount > 0) {
                    onComplete()
                }
                
                val message = when {
                    createdCount > 0 && existingCount > 0 -> 
                        "$createdCount tasks created. $existingCount already existed."
                    createdCount > 0 -> 
                        "$createdCount tasks added to your goal."
                    existingCount > 0 -> 
                        "All selected tasks already exist for this goal."
                    else -> "No tasks were created."
                }
                
                _uiState.value = _uiState.value.copy(
                    isCreatingTasks = false,
                    successMessage = message
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingTasks = false,
                    error = "Failed to create tasks: ${e.message}"
                )
            }
        }
    }

    private fun mapPriority(aiPriority: AiPriority): TaskPriority {
        return when (aiPriority) {
            AiPriority.LOW -> TaskPriority.LOW
            AiPriority.MEDIUM -> TaskPriority.MEDIUM
            AiPriority.HIGH -> TaskPriority.HIGH
            AiPriority.CRITICAL -> TaskPriority.URGENT
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clear() {
        _uiState.value =
            AiGoalDecomposerUiState()
    }
}

@Composable
fun AiGoalDecomposerScreen(
    engine: NexoraAiEngine,
    repository: NexoraRepository,
    onBack: () -> Unit = {},
    onTasksCreated: () -> Unit = {}
) {

    val viewModel: AiGoalDecomposerViewModel =
        viewModel(
            factory = object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel>
                        create(modelClass: Class<T>): T {

                    return AiGoalDecomposerViewModel(
                        engine = engine,
                        repository = repository
                    ) as T
                }
            }
        )

    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    var goalTitle by remember {
        mutableStateOf("")
    }

    var goalDescription by remember {
        mutableStateOf("")
    }

    var showConfirmDialog by remember {
        mutableStateOf(false)
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Create Tasks",
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
            },
            text = {
                Text(
                    text = "Convert ${uiState.selectedSteps.size} steps into tasks for the goal \"${uiState.decomposition?.goalTitle}\"?",
                    color = NexoraMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.createTasks(onComplete = onTasksCreated)
                    }
                ) {
                    Text(
                        text = "Create Tasks",
                        fontWeight = FontWeight.Bold,
                        color = NexoraGreen
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = NexoraMuted)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexoraBackground)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        NexoraSoftGreen,
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Splitscreen,
                    contentDescription = null,
                    tint = NexoraGreen
                )
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Goal Decomposer",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )

                Text(
                    text = "Turn ambitious goals into clear steps.",
                    fontSize = 13.sp,
                    color = NexoraMuted
                )
            }
        }

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 30.dp
            ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            item {
                // Goal input card ...
            }

            uiState.error?.let { error ->
                item {
                    // Error card ...
                }
            }

            uiState.successMessage?.let { success ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = NexoraSoftGreen
                        ),
                        border = BorderStroke(1.dp, NexoraGreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NexoraGreen
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = success,
                                modifier = Modifier.weight(1f),
                                color = NexoraInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.clearSuccessMessage() },
                                tint = NexoraMuted
                            )
                        }
                    }
                }
            }

            uiState.decomposition?.let { decomposition ->

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Your breakdown",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexoraInk
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Select all",
                                modifier = Modifier.clickable { viewModel.selectAll() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NexoraGreen
                            )
                            Text(
                                text = "Deselect",
                                modifier = Modifier.clickable { viewModel.deselectAll() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NexoraMuted
                            )
                        }
                    }
                }

                item {
                    // Breakdown summary card ...
                }

                items(
                    items = decomposition.steps,
                    key = { it.order }
                ) { step ->
                    AiGoalStepCard(
                        step = step,
                        isSelected = uiState.selectedSteps.contains(step.order),
                        onToggle = { viewModel.toggleStep(step.order) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            showConfirmDialog = true 
                        },
                        enabled = uiState.selectedSteps.isNotEmpty() && !uiState.isCreatingTasks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexoraInk,
                            contentColor = Color.White
                        )
                    ) {
                        if (uiState.isCreatingTasks) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Creating tasks...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uiState.selectedSteps.isEmpty()) 
                                    "Create Tasks" 
                                else 
                                    "Create ${uiState.selectedSteps.size} Tasks",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiGoalStepCard(
    step: AiGoalStep,
    isSelected: Boolean,
    onToggle: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) NexoraGreen else NexoraBorder
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NexoraGreen,
                        uncheckedColor = NexoraMuted,
                        checkmarkColor = NexoraInk
                    )
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            NexoraSoftGreen,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = step.order.toString(),
                        fontWeight =
                            FontWeight.Bold,
                        color = NexoraInk
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Text(
                    text = step.title,
                    modifier =
                        Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color = NexoraInk
                )
            }

            if (step.description.isNotBlank()) {

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    color = NexoraMuted,
                    lineHeight = 20.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Surface(
                    shape =
                        RoundedCornerShape(10.dp),
                    color =
                        NexoraSoftGreen
                ) {

                    Text(
                        text =
                            step.priority.name,
                        modifier =
                            Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            ),
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        color = NexoraInk
                    )
                }

                if (step.estimatedDuration.isNotBlank()) {

                    Surface(
                        shape =
                            RoundedCornerShape(10.dp),
                        color =
                            Color(0xFFF1F3EF)
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 6.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Schedule,
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(14.dp),
                                tint = NexoraMuted
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(5.dp)
                            )

                            Text(
                                text =
                                    step.estimatedDuration,
                                fontSize = 12.sp,
                                color = NexoraMuted
                            )
                        }
                    }
                }
            }
        }
    }
}