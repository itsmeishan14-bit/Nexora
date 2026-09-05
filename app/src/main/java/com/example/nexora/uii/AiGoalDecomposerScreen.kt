package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.nexora.ai.NexoraAiEngine
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
    val error: String? = null
)

class AiGoalDecomposerViewModel(
    private val engine: NexoraAiEngine
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
                        decomposition = result
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

    fun clear() {
        _uiState.value =
            AiGoalDecomposerUiState()
    }
}

@Composable
fun AiGoalDecomposerScreen(
    engine: NexoraAiEngine,
    onBack: () -> Unit = {}
) {

    val viewModel: AiGoalDecomposerViewModel =
        viewModel(
            factory = object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel>
                        create(modelClass: Class<T>): T {

                    return AiGoalDecomposerViewModel(
                        engine = engine
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NexoraInk
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NexoraGreen,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(9.dp)
                            )

                            Text(
                                text = "What do you want to achieve?",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        OutlinedTextField(
                            value = goalTitle,
                            onValueChange = { goalTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "e.g. Learn Java full stack",
                                    color = Color(0xFF9EAAA1)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = NexoraGreen,
                                focusedBorderColor = NexoraGreen,
                                unfocusedBorderColor = Color(0xFF718075),
                                focusedPlaceholderColor = Color(0xFF9EAAA1),
                                unfocusedPlaceholderColor = Color(0xFF9EAAA1)
                            )
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        OutlinedTextField(
                            value = goalDescription,
                            onValueChange = {
                                goalDescription = it
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            placeholder = {
                                Text(
                                    text =
                                        "Optional: add context about your goal"
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.decompose(
                                    goalTitle =
                                        goalTitle,
                                    goalDescription =
                                        goalDescription
                                )
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexoraGreen,
                                contentColor = NexoraInk
                            )
                        ) {

                            if (uiState.isLoading) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NexoraInk
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(9.dp)
                                )

                                Text(
                                    text = "Thinking..."
                                )

                            } else {

                                Icon(
                                    imageVector =
                                        Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier =
                                        Modifier.size(18.dp)
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(8.dp)
                                )

                                Text(
                                    text = "Decompose goal",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            uiState.error?.let { error ->

                item {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(18.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color(0xFFFFF4F2)
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                Color(0xFFE8D3CF)
                            )
                    ) {

                        Text(
                            text = error,
                            modifier =
                                Modifier.padding(18.dp),
                            color = NexoraInk,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            uiState.decomposition?.let { decomposition ->

                item {

                    Text(
                        text = "Your breakdown",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk
                    )
                }

                item {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                NexoraBorder
                            )
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(20.dp)
                        ) {

                            Text(
                                text =
                                    decomposition.goalTitle,
                                fontSize = 19.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color = NexoraInk
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )

                            Text(
                                text =
                                    decomposition.summary,
                                fontSize = 14.sp,
                                color = NexoraMuted,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }

                items(
                    items = decomposition.steps,
                    key = {
                        it.order
                    }
                ) { step ->

                    AiGoalStepCard(
                        step = step
                    )
                }
            }
        }
    }
}

@Composable
private fun AiGoalStepCard(
    step: AiGoalStep
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            NexoraBorder
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

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