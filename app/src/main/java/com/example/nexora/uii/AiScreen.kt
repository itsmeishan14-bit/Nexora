package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

@Composable
fun AiScreen(
    engine: NexoraAiEngine,
    onOpenGoalDecomposer: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenEvaluation: () -> Unit,
    onRecommendationAction: (AiRecommendation) -> Unit,
    onTaskAction: (Long) -> Unit = {}
) {
    val viewModel: NexoraAiViewModel = viewModel(
        factory = remember(engine) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NexoraAiViewModel(engine = engine) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size + 10)
        }
    }

    Scaffold(
        containerColor = NexoraBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Intelligence",
                        style = MaterialTheme.typography.headlineLarge,
                        color = NexoraPrimaryText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenEvaluation) {
                        Icon(Icons.Default.Analytics, contentDescription = "Benchmark", tint = NexoraMutedText)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ACTIONS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.AutoAwesome,
                            label = "Plan day",
                            onClick = { viewModel.createDailyPlan() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionChip(
                            icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            label = "Next task",
                            onClick = { viewModel.analyze() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.AccountTree,
                            label = "Decompose",
                            onClick = onOpenGoalDecomposer,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionChip(
                            icon = Icons.Default.Settings,
                            label = "Rules",
                            onClick = onOpenAutomations,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // AI SYSTEM STATUS (WORKFLOWS, ACTIONS)
                uiState.lastActionResult?.let { result ->
                    item {
                        NexoraCard(containerColor = if (result.success) NexoraSoftGreen else Color(0xFFFFF4F2)) {
                            Row(modifier = Modifier.padding(16.dp).clickable { viewModel.dismissResult() }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (result.success) NexoraPrimaryGreen else NexoraError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = result.message, style = MaterialTheme.typography.bodyMedium, color = NexoraPrimaryText)
                            }
                        }
                    }
                }

                uiState.proposedAction?.let { action ->
                    item {
                        ProposedActionCard(
                            action = action,
                            onConfirm = { viewModel.confirmAction() },
                            onDismiss = { viewModel.dismissAction() }
                        )
                    }
                }

                uiState.currentWorkflow?.let { workflow ->
                    item {
                        WorkflowCard(workflow = workflow)
                    }
                }

                // AI PROACTIVE SIGNALS
                if (uiState.proactiveSignals.isNotEmpty()) {
                    items(items = uiState.proactiveSignals, key = { "signal_${it.fingerprint}" }) { signal ->
                        ProactiveSignalCard(signal = signal, onAction = { viewModel.proposeAction(it) })
                    }
                }

                // LEGACY RECOMMENDATIONS
                if (uiState.recommendations.isNotEmpty()) {
                    items(items = uiState.recommendations, key = { "rec_${it.title}_${it.type}" }) { recommendation ->
                        RecommendationCard(recommendation = recommendation, onAction = { rec ->
                            val action = recommendationToAction(rec)
                            if (action != null) viewModel.proposeAction(action) else onRecommendationAction(rec)
                        })
                    }
                }

                // CHAT CONVERSATION
                if (uiState.chatMessages.isNotEmpty()) {
                    item {
                        Text(
                            text = "Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraPrimaryText,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(items = uiState.chatMessages, key = { it.id }) { ChatMessageBubble(it) }
                }

                if (uiState.isChatLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NexoraPrimaryGreen,
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (uiState.chatMessages.isEmpty() && !uiState.isChatLoading && uiState.recommendations.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "How can I assist you?",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoraPrimaryText
                            )
                            listOf("Plan my day", "What's next?", "Check my workload", "Break down a goal").forEach { suggestion ->
                                SuggestionItem(suggestion) { viewModel.sendMessage(suggestion) }
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
            
            ChatInput(
                onSend = { viewModel.sendMessage(it) },
                enabled = !uiState.isChatLoading
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = NexoraShapes.medium,
        color = Color.White,
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexoraPrimaryGreen)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = NexoraPrimaryText)
        }
    }
}

@Composable
private fun SuggestionItem(text: String, onClick: () -> Unit) {
    NexoraCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = NexoraPrimaryText)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NexoraBorder, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ChatMessageBubble(message: NexoraChatMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isFromUser) NexoraPrimaryText else Color.White
    val textColor = if (message.isFromUser) Color.White else NexoraPrimaryText
    val shape = if (message.isFromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bgColor,
            shape = shape,
            border = if (message.isFromUser) null else BorderStroke(1.dp, NexoraBorder)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, NexoraBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(WindowInsets.ime.asPaddingValues())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Nexora...", style = MaterialTheme.typography.bodyLarge, color = NexoraMutedText) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NexoraBackground,
                    unfocusedContainerColor = NexoraBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = NexoraShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank() && enabled) {
                        onSend(text)
                        text = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }),
                enabled = enabled
            )
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() && enabled) NexoraPrimaryGreen else NexoraSoftGreen)
                    .clickable(enabled = text.isNotBlank() && enabled) {
                        onSend(text)
                        text = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank() && enabled) Color.White else NexoraMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: AiRecommendation, onAction: (AiRecommendation) -> Unit) {
    val icon = when (recommendation.type) {
        AiRecommendationType.NEXT_TASK -> Icons.AutoMirrored.Filled.PlaylistAddCheck
        AiRecommendationType.GOAL_ACTION -> Icons.Default.Flag
        AiRecommendationType.WARNING -> Icons.Default.Warning
        else -> Icons.Default.AutoAwesome
    }
    
    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NexoraPrimaryGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = recommendation.title, style = MaterialTheme.typography.titleSmall, color = NexoraPrimaryText)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraMutedText,
                lineHeight = 20.sp
            )
            if (recommendation.actionLabel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = recommendation.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraPrimaryGreen,
                    modifier = Modifier.clickable { onAction(recommendation) }
                )
            }
        }
    }
}

private fun recommendationToAction(recommendation: AiRecommendation): AiAction? {
    return when (recommendation.type) {
        AiRecommendationType.NEXT_TASK -> recommendation.relatedTaskId?.let { AiAction(type = AiActionType.COMPLETE_TASK, title = "Complete Task", description = "Mark this task as finished?", reason = recommendation.message, taskId = it) }
        AiRecommendationType.WARNING -> if (recommendation.title.contains("workload", ignoreCase = true)) AiAction(type = AiActionType.RESCHEDULE_TASK, title = "Reschedule tasks", description = "Move low-priority tasks to tomorrow?", reason = recommendation.message) else null
        else -> null
    }
}
