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
    viewModel: NexoraAiViewModel,
    onOpenGoalDecomposer: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenEvaluation: () -> Unit,
    onRecommendationAction: (AiRecommendation) -> Unit,
    onTaskAction: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size + 10)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, NexoraBorder),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Intelligence",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Green10
                        )
                        Text(
                            text = "On-device AI, private by design.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Green40
                        )
                    }
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // QUICK ACTIONS
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionDivider(label = "QUICK ACTIONS")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AiActionChip(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "Plan day",
                                onClick = { viewModel.createDailyPlan() },
                                modifier = Modifier.weight(1f)
                            )
                            AiActionChip(
                                icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                label = "Next task",
                                onClick = { viewModel.analyze() },
                                modifier = Modifier.weight(1f)
                            )
                            AiActionChip(
                                icon = Icons.Rounded.AccountTree,
                                label = "Decompose",
                                onClick = onOpenGoalDecomposer,
                                modifier = Modifier.weight(1f)
                            )
                            AiActionChip(
                                icon = Icons.Rounded.Settings,
                                label = "Rules",
                                onClick = onOpenAutomations,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ACTION RESULT BANNER
                uiState.lastActionResult?.let { result ->
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.dismissResult() },
                                shape = NexoraShapes.medium,
                                color = if (result.success) Green95 else NexoraError.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, if (result.success) Green80 else NexoraError.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = if (result.success) Green60 else NexoraError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Green10,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Rounded.Close,
                                        null,
                                        tint = Green40,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // PROPOSED ACTION
                uiState.proposedAction?.let { action ->
                    item {
                        ProposedActionCard(
                            action = action,
                            onConfirm = { viewModel.confirmAction() },
                            onDismiss = { viewModel.dismissAction() }
                        )
                    }
                }

                // WORKFLOW
                uiState.currentWorkflow?.let { workflow ->
                    item {
                        WorkflowCard(workflow = workflow)
                    }
                }

                // DAILY PLAN
                uiState.dailyPlan?.let { plan ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionDivider(label = "TODAY'S PLAN")
                            Text(
                                text = plan.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Green40,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    items(items = plan.tasks, key = { "plan_${it.task.id}" }) { plannedTask ->
                        PlannedTaskPill(
                            plannedTask = plannedTask,
                            onClick = { onTaskAction(plannedTask.task.id) }
                        )
                    }
                }

                // PROACTIVE SIGNALS
                if (uiState.proactiveSignals.isNotEmpty()) {
                    item {
                        SectionDivider(label = "OBSERVATIONS")
                    }
                    items(
                        items = uiState.proactiveSignals,
                        key = { "signal_${it.fingerprint}" }
                    ) { signal ->
                        ProactiveSignalCard(signal = signal, onAction = { viewModel.proposeAction(it) })
                    }
                }

                // AI MEMORY
                if (uiState.memory.items.isNotEmpty()) {
                    item {
                        SectionDivider(label = "LEARNED INTELLIGENCE")
                    }
                    items(
                        items = uiState.memory.items.take(3),
                        key = { "memory_${it.id}" }
                    ) { memoryItem ->
                        MemoryItemCard(item = memoryItem, onDelete = { viewModel.deleteMemory(memoryItem.id) })
                    }
                }

                // RECOMMENDATIONS
                if (uiState.recommendations.isNotEmpty()) {
                    items(
                        items = uiState.recommendations,
                        key = { it.id }
                    ) { recommendation ->
                        AiRecommendationCard(
                            recommendation = recommendation,
                            onAction = { rec ->
                                val action = rec.suggestedAction
                                if (action != null) viewModel.proposeAction(action) else onRecommendationAction(rec)
                            }
                        )
                    }
                }

                // CONVERSATION
                if (uiState.chatMessages.isNotEmpty()) {
                    item {
                        SectionDivider(label = "CONVERSATION")
                    }
                    items(items = uiState.chatMessages, key = { it.id }) { ChatMessageBubble(it) }
                }

                // LOADING
                if (uiState.isChatLoading) {
                    item {
                        AiTypingIndicator()
                    }
                }

                // EMPTY STATE / SUGGESTIONS
                if (uiState.chatMessages.isEmpty() && !uiState.isChatLoading &&
                    uiState.recommendations.isEmpty() && uiState.proactiveSignals.isEmpty()
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Ask Nexora anything",
                                style = MaterialTheme.typography.titleSmall,
                                color = Green10
                            )
                            Text(
                                text = "All processing stays on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Green40
                            )
                            Spacer(Modifier.height(4.dp))
                            listOf(
                                "Plan my day",
                                "What should I work on next?",
                                "Check my workload",
                                "Break down a goal"
                            ).forEach { suggestion ->
                                AiSuggestionRow(suggestion) { viewModel.sendMessage(suggestion) }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }

            // CHAT INPUT
            ChatInput(
                onSend = { viewModel.sendMessage(it) },
                enabled = !uiState.isChatLoading
            )
        }
    }
}

// ─────────────────────────────────────────────
// AI ACTION CHIP (compact grid)
// ─────────────────────────────────────────────

@Composable
private fun AiActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = NexoraShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, NexoraBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Green60)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Green10
            )
        }
    }
}

// ─────────────────────────────────────────────
// AI TYPING INDICATOR
// ─────────────────────────────────────────────

@Composable
private fun AiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "aiTyping")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse),
        label = "d1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse),
        label = "d2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse),
        label = "d3"
    )

    Surface(
        shape = NexoraShapes.large,
        color = Clay90,
        border = BorderStroke(0.5.dp, Clay60.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(alpha1, alpha2, alpha3).forEach { a ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Clay40.copy(alpha = a))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// SUGGESTION ROW
// ─────────────────────────────────────────────

@Composable
private fun AiSuggestionRow(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .nexoraClickable { onClick() },
        shape = NexoraShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, NexoraBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Green10)
            Icon(Icons.Rounded.ChevronRight, null, tint = NexoraBorder, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────
// CHAT MESSAGE BUBBLE
// ─────────────────────────────────────────────

@Composable
fun ChatMessageBubble(message: NexoraChatMessage) {
    val isUser = message.isFromUser

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isUser) {
            // User bubble — right-aligned, dark bg
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = Green10,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            // AI bubble — left-aligned, clay surface
            AiSurface(modifier = Modifier.widthIn(max = 320.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green10,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// CHAT INPUT
// ─────────────────────────────────────────────

@Composable
fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, NexoraBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(WindowInsets.ime.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask Nexora anything…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green40
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = NexoraShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank() && enabled) {
                        onSend(text); text = ""
                        keyboardController?.hide(); focusManager.clearFocus()
                    }
                }),
                enabled = enabled
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() && enabled) Green60 else Green95)
                    .clickable(enabled = text.isNotBlank() && enabled) {
                        onSend(text); text = ""
                        keyboardController?.hide(); focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank() && enabled) Color.White else Green40,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// PLANNED TASK PILL
// ─────────────────────────────────────────────

@Composable
private fun PlannedTaskPill(
    plannedTask: PlannedTask,
    onClick: () -> Unit
) {
    val task = plannedTask.task
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> NexoraError
        TaskPriority.HIGH -> Clay60
        TaskPriority.MEDIUM -> Green60
        else -> Green90
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = NexoraShapes.medium,
        color = priorityColor.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${plannedTask.recommendedOrder}.",
                style = NumericStyle.copy(fontSize = 13.sp),
                color = priorityColor.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Green10,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = priorityColor.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// MEMORY ITEM CARD
// ─────────────────────────────────────────────

@Composable
private fun MemoryItemCard(item: AiMemoryItem, onDelete: () -> Unit) {
    NexoraCard(tier = NexoraCardTier.Resting) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBox(
                icon = when (item.category) {
                    AiMemoryCategory.PRODUCTIVITY_PATTERN -> Icons.Rounded.Insights
                    AiMemoryCategory.WORKLOAD_PATTERN -> Icons.Rounded.Speed
                    AiMemoryCategory.GOAL_PATTERN -> Icons.Rounded.Flag
                    AiMemoryCategory.TASK_SIZE_PATTERN -> Icons.Rounded.Splitscreen
                    else -> Icons.Rounded.AutoAwesome
                },
                containerColor = Green95,
                contentColor = Green60,
                size = 28
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Green10,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = NexoraBorder, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = Green40,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
