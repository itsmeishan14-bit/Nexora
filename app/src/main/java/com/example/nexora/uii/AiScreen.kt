package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
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
        containerColor = NexoraBackgroundLight,
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Gray90)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Intelligence",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Green10,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenEvaluation) {
                        Icon(Icons.Rounded.Analytics, contentDescription = "Benchmark", tint = Green40)
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
                // QUICK ACTIONS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "Plan day",
                            onClick = { viewModel.createDailyPlan() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionChip(
                            icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
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
                            icon = Icons.Rounded.AccountTree,
                            label = "Decompose",
                            onClick = onOpenGoalDecomposer,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionChip(
                            icon = Icons.Rounded.Settings,
                            label = "Rules",
                            onClick = onOpenAutomations,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // AI SYSTEM STATUS (WORKFLOWS, ACTIONS)
                uiState.lastActionResult?.let { result ->
                    item {
                        NexoraCard(containerColor = if (result.success) Green95 else Color(0xFFFFF4F2)) {
                            Row(modifier = Modifier.padding(16.dp).clickable { viewModel.dismissResult() }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = if (result.success) Green60 else NexoraError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = result.message, style = MaterialTheme.typography.bodyMedium, color = Green10)
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

                // RECOMMENDATIONS
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
                            color = Green10,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(items = uiState.chatMessages, key = { it.id }) { ChatMessageBubble(it) }
                }

                if (uiState.isChatLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Green60,
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (uiState.chatMessages.isEmpty() && !uiState.isChatLoading && uiState.recommendations.isEmpty() && uiState.proactiveSignals.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "How can I assist you?",
                                style = MaterialTheme.typography.titleMedium,
                                color = Green10
                            )
                            listOf("Plan my day", "What should I work on next?", "Check my workload", "Break down a goal").forEach { suggestion ->
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
        border = BorderStroke(1.dp, Gray90)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Green60)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Green10)
        }
    }
}

@Composable
private fun SuggestionItem(text: String, onClick: () -> Unit) {
    NexoraCard(modifier = Modifier.nexoraClickable { onClick() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = Green10)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Gray90, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ChatMessageBubble(message: NexoraChatMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val shape = if (message.isFromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (message.isFromUser) {
            Surface(
                color = Green10,
                shape = shape
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 22.sp
                )
            }
        } else {
            AiSurface {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Green10,
                    lineHeight = 22.sp
                )
            }
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
        border = BorderStroke(1.dp, Gray90),
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
                placeholder = { Text("Ask Nexora...", style = MaterialTheme.typography.bodyLarge, color = Green40) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Green98,
                    unfocusedContainerColor = Green98,
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
                    .background(if (text.isNotBlank() && enabled) Green60 else Green95)
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
                    tint = if (text.isNotBlank() && enabled) Color.White else Green40,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: AiRecommendation, onAction: (AiRecommendation) -> Unit) {
    val icon = when (recommendation.type) {
        AiRecommendationType.NEXT_TASK -> Icons.AutoMirrored.Rounded.PlaylistAddCheck
        AiRecommendationType.GOAL_ACTION -> Icons.Rounded.Flag
        AiRecommendationType.WARNING -> Icons.Rounded.Warning
        else -> Icons.Rounded.AutoAwesome
    }
    
    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(icon, Green95, Green60, size = 24)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = recommendation.title, style = MaterialTheme.typography.titleSmall, color = Green10)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Green40,
                lineHeight = 20.sp
            )
            if (recommendation.actionLabel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = recommendation.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Green60,
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
