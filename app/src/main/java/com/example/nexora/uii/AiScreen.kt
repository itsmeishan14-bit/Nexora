package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

// ─────────────────────────────────────────────────────────────
// MAIN AI SCREEN — PERSONAL INTELLIGENCE CONSOLE
// ─────────────────────────────────────────────────────────────

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

    // Auto-scroll to latest message when conversation grows
    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size + 20)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IntelligenceHeader(onOpenEvaluation = onOpenEvaluation) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isAnyLoading = uiState.isChatLoading || uiState.isLoading

            // ── Scrollable content ──────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // INTELLIGENCE CONTEXT (subtle text insight from real data)
                uiState.personalContext?.let { ctx ->
                    val contextText = buildIntelligenceContextText(ctx)
                    if (!contextText.isNullOrBlank()) {
                        item {
                            IntelligenceContextLine(contextText)
                            Spacer(Modifier.height(28.dp))
                        }
                    }
                }

                // QUICK ACTIONS ROW
                item {
                    IntelligenceActionRow(
                        onPlanDay = { viewModel.createDailyPlan() },
                        onNextTask = { viewModel.recommendNextTask() },
                        onDecompose = onOpenGoalDecomposer,
                        onRules = onOpenAutomations
                    )
                    Spacer(Modifier.height(28.dp))
                }

                // ACTION RESULT BANNER
                val displayResult = uiState.lastActionResult ?: uiState.lastExecutionRecord?.let {
                    AiActionResult(
                        success = it.isCompleteSuccess,
                        message = it.summaryMessage
                    )
                } ?: uiState.error?.let {
                    AiActionResult(
                        success = false,
                        message = it,
                        error = it
                    )
                }
                displayResult?.let { result ->
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200))
                        ) {
                            ActionResultBanner(
                                result = result,
                                executionRecord = uiState.lastExecutionRecord,
                                onDismiss = { viewModel.dismissResult() }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
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
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // WORKFLOW CARD
                uiState.currentWorkflow?.let { workflow ->
                    item {
                        WorkflowCard(workflow = workflow)
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // TODAY'S PLAN
                uiState.dailyPlan?.let { plan ->
                    item {
                        SectionDivider(label = "TODAY'S PLAN")
                        Spacer(Modifier.height(14.dp))
                        PremiumDailyPlanHeader(plan = plan)
                        Spacer(Modifier.height(12.dp))
                    }
                    items(items = plan.tasks, key = { "plan_${it.task.id}" }) { plannedTask ->
                        PlannedTaskPill(
                            plannedTask = plannedTask,
                            onClick = { onTaskAction(plannedTask.task.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }

                // OBSERVATIONS / PROACTIVE SIGNALS
                if (uiState.proactiveSignals.isNotEmpty()) {
                    item {
                        SectionDivider(label = "OBSERVATIONS")
                        Spacer(Modifier.height(14.dp))
                    }
                    items(
                        items = uiState.proactiveSignals,
                        key = { "signal_${it.fingerprint}" }
                    ) { signal ->
                        ProactiveSignalCard(signal = signal, onAction = { viewModel.proposeAction(it) })
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }

                // LEARNED INTELLIGENCE
                if (uiState.memory.items.isNotEmpty()) {
                    item {
                        SectionDivider(label = "LEARNED INTELLIGENCE")
                        Spacer(Modifier.height(14.dp))
                    }
                    items(
                        items = uiState.memory.items.take(3),
                        key = { "memory_${it.id}" }
                    ) { memoryItem ->
                        MemoryItemCard(item = memoryItem, onDelete = { viewModel.deleteMemory(memoryItem.id) })
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }

                // RECOMMENDATIONS — INSIGHT / WHY / NEXT ACTION hierarchy
                if (uiState.recommendations.isNotEmpty()) {
                    item {
                        SectionDivider(label = "INTELLIGENCE")
                        Spacer(Modifier.height(14.dp))
                    }
                    items(
                        items = uiState.recommendations,
                        key = { it.id }
                    ) { recommendation ->
                        PremiumRecommendationCard(
                            recommendation = recommendation,
                            onAction = { rec ->
                                val action = rec.suggestedAction
                                if (action != null) viewModel.proposeAction(action) else onRecommendationAction(rec)
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }

                // CONVERSATION
                if (uiState.chatMessages.isNotEmpty()) {
                    item {
                        SectionDivider(label = "CONVERSATION")
                        Spacer(Modifier.height(14.dp))
                    }
                    items(items = uiState.chatMessages, key = { it.id }) { msg ->
                        PremiumChatMessageBubble(msg)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // LOADING INDICATOR
                if (isAnyLoading) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        IntelligenceThinkingIndicator()
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // EMPTY STATE
                if (uiState.chatMessages.isEmpty() && !isAnyLoading &&
                    uiState.recommendations.isEmpty() && uiState.proactiveSignals.isEmpty() &&
                    uiState.dailyPlan == null
                ) {
                    item {
                        IntelligenceEmptyState(onSuggestionClick = { viewModel.sendMessage(it) })
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // ── COMPOSER ───────────────────────────────────────────────
            IntelligenceComposer(
                onSend = { viewModel.sendMessage(it) },
                enabled = !isAnyLoading
            )
        }
    }
}

// ─────────────────────────────────────────────
// HEADER — Intelligence Console
// ─────────────────────────────────────────────

@Composable
private fun IntelligenceHeader(onOpenEvaluation: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, NexoraBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Intelligence",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = Green10
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "On-device AI · Private by design",
                    style = MaterialTheme.typography.bodySmall,
                    color = Green40
                )
            }
            IconButton(onClick = onOpenEvaluation) {
                Icon(
                    Icons.Rounded.Analytics,
                    contentDescription = "Benchmark",
                    tint = Green40,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// INTELLIGENCE CONTEXT LINE (from real data)
// ─────────────────────────────────────────────

fun buildIntelligenceContextText(context: AiPersonalContext): String? {
    return when (context.dayState) {
        CurrentDayState.OVERLOADED -> "Your workload is above your usual capacity."
        CurrentDayState.BEHIND -> "You're behind your typical pace for this time of day."
        CurrentDayState.AHEAD -> "You're ahead of schedule today."
        CurrentDayState.ON_TRACK -> "Your day is progressing as expected."
        else -> when (context.workload.state) {
            WorkloadState.VERY_HIGH -> "You have a high number of tasks today."
            WorkloadState.HIGH -> "Your workload is elevated."
            WorkloadState.LOW, WorkloadState.VERY_LOW -> "Your schedule looks clear."
            else -> null
        }
    }
}

@Composable
private fun IntelligenceContextLine(contextText: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Green60)
        )
        Text(
            text = contextText,
            style = MaterialTheme.typography.bodyMedium,
            color = Green40,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────
// QUICK ACTION ROW
// ─────────────────────────────────────────────

@Composable
private fun IntelligenceActionRow(
    onPlanDay: () -> Unit,
    onNextTask: () -> Unit,
    onDecompose: () -> Unit,
    onRules: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IntelligenceActionChip(Icons.Rounded.AutoAwesome, "Plan day", onPlanDay, Modifier.weight(1f))
        IntelligenceActionChip(Icons.AutoMirrored.Rounded.PlaylistAddCheck, "Next task", onNextTask, Modifier.weight(1f))
        IntelligenceActionChip(Icons.Rounded.AccountTree, "Decompose", onDecompose, Modifier.weight(1f))
        IntelligenceActionChip(Icons.Rounded.Settings, "Rules", onRules, Modifier.weight(1f))
    }
}

@Composable
private fun IntelligenceActionChip(
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = Green10)
        }
    }
}

// ─────────────────────────────────────────────
// ─────────────────────────────────────────────

// ─────────────────────────────────────────────
// PREMIUM DAILY PLAN HEADER
// ─────────────────────────────────────────────

@Composable
private fun PremiumDailyPlanHeader(plan: NexoraDailyPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (plan.summary.isNotBlank()) {
            Text(
                text = plan.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Green40,
                lineHeight = 20.sp
            )
        }
        if (plan.tasks.isNotEmpty()) {
            Text(
                text = "${plan.tasks.size} task${if (plan.tasks.size == 1) "" else "s"} · ${plan.date}",
                style = MaterialTheme.typography.labelSmall,
                color = NexoraBorder,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
// PREMIUM RECOMMENDATION CARD
// (INSIGHT / WHY / NEXT ACTION hierarchy)
// ─────────────────────────────────────────────

@Composable
private fun PremiumRecommendationCard(
    recommendation: AiRecommendation,
    onAction: (AiRecommendation) -> Unit
) {
    val isErrorResponse = recommendation.message.startsWith("Goal not found:") ||
        recommendation.message.startsWith("Task not found:") ||
        recommendation.message.startsWith("Error:")

    if (isErrorResponse) {
        IntelligenceSystemFeedback(message = recommendation.message)
        return
    }

    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            // INSIGHT label + title
            Text(
                text = "INSIGHT",
                style = MaterialTheme.typography.labelSmall,
                color = Green60,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleSmall,
                color = Green10
            )
            if (recommendation.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = recommendation.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green40,
                    lineHeight = 20.sp
                )
            }

            // WHY — reasoning factors
            if (recommendation.evidence.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = NexoraBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "WHY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Green40,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                recommendation.evidence.forEach { factor ->
                    IntelligenceEvidenceFactor(factor = factor)
                    Spacer(Modifier.height(4.dp))
                }
            }

            // NEXT ACTION
            if (!recommendation.actionLabel.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = NexoraBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "NEXT ACTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Green40,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { onAction(recommendation) },
                    shape = NexoraShapes.small,
                    color = Green95,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = recommendation.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Green40,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            null,
                            tint = Green60,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntelligenceEvidenceFactor(factor: ReasoningFactor) {
    val icon = when (factor.impact) {
        ReasoningImpact.POSITIVE -> Icons.Rounded.CheckCircle
        ReasoningImpact.CRITICAL -> Icons.Rounded.PriorityHigh
        ReasoningImpact.NEGATIVE -> Icons.Rounded.RemoveCircle
        ReasoningImpact.NEUTRAL -> Icons.Rounded.Info
    }
    val color = when (factor.impact) {
        ReasoningImpact.POSITIVE -> Green60
        ReasoningImpact.CRITICAL -> NexoraError
        ReasoningImpact.NEGATIVE -> Clay40
        ReasoningImpact.NEUTRAL -> Gray90
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
        )
        Text(
            text = factor.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = Green40,
            lineHeight = 16.sp
        )
    }
}

// ─────────────────────────────────────────────
// SYSTEM FEEDBACK CARD (error / not-found)
// ─────────────────────────────────────────────

@Composable
private fun IntelligenceSystemFeedback(message: String) {
    val colonIdx = message.indexOf(':')
    val title = if (colonIdx > 0) message.substring(0, colonIdx).trim() else "System Notice"
    val detail = if (colonIdx > 0) message.substring(colonIdx + 1).trim() else message

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NexoraShapes.medium,
        color = NexoraError.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, NexoraError.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    null,
                    tint = NexoraError.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraError.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Green40,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// PREMIUM CHAT MESSAGE BUBBLE
// (no tails — information system style)
// ─────────────────────────────────────────────

@Composable
fun PremiumChatMessageBubble(message: NexoraChatMessage) {
    val isUser = message.isFromUser

    val isError = !isUser && (
        message.text.startsWith("Goal not found:") ||
            message.text.startsWith("Task not found:") ||
            message.text.startsWith("Error:")
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isUser) {
            // User — right-aligned, dark charcoal, no tail
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = Green10,
                    shape = RoundedCornerShape(18.dp),
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
        } else if (isError) {
            // Error — system feedback style, not a chat bubble
            val colonIdx = message.text.indexOf(':')
            val title = if (colonIdx > 0) message.text.substring(0, colonIdx).trim() else "Notice"
            val detail = if (colonIdx > 0) message.text.substring(colonIdx + 1).trim() else message.text
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                color = NexoraError.copy(alpha = 0.05f),
                border = BorderStroke(0.5.dp, NexoraError.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info, null,
                            tint = NexoraError.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoraError.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Green40,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        } else {
            // AI — warm off-white clay surface, clean rectangular, no tail
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(18.dp),
                color = Clay90,
                border = BorderStroke(0.5.dp, Clay60.copy(alpha = 0.28f))
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green10,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// Legacy alias for backward compatibility
@Composable
fun ChatMessageBubble(message: NexoraChatMessage) = PremiumChatMessageBubble(message)

// ─────────────────────────────────────────────
// INTELLIGENCE THINKING INDICATOR
// (subtle, minimal — not flashy)
// ─────────────────────────────────────────────

@Composable
private fun IntelligenceThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val labelAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "labelAlpha"
    )
    val dot1 by rememberInfiniteTransition(label = "d1").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by rememberInfiniteTransition(label = "d2").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 150), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by rememberInfiniteTransition(label = "d3").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 300), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Clay90,
            border = BorderStroke(0.5.dp, Clay60.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(dot1, dot2, dot3).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Clay40.copy(alpha = a))
                    )
                }
            }
        }
        Text(
            text = "Nexora is thinking\u2026",
            style = MaterialTheme.typography.bodySmall,
            color = Green40.copy(alpha = labelAlpha)
        )
    }
}

// Legacy alias for backward compatibility
@Composable
private fun AiTypingIndicator() = IntelligenceThinkingIndicator()

// ─────────────────────────────────────────────
// INTELLIGENCE EMPTY STATE
// (OS-layer opening feel)
// ─────────────────────────────────────────────

@Composable
private fun IntelligenceEmptyState(onSuggestionClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = "Nexora Intelligence",
            style = MaterialTheme.typography.titleLarge,
            color = Green10
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your work, understood.",
            style = MaterialTheme.typography.bodyMedium,
            color = Green40
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "All processing stays on your device.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoraBorder
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "TRY ASKING",
            style = MaterialTheme.typography.labelSmall,
            color = NexoraBorder,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            "Plan my day",
            "What should I work on next?",
            "Check my workload",
            "Break down a goal"
        ).forEach { suggestion ->
            IntelligenceSuggestionRow(text = suggestion, onClick = { onSuggestionClick(suggestion) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IntelligenceSuggestionRow(text: String, onClick: () -> Unit) {
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Green10)
            Icon(Icons.Rounded.ChevronRight, null, tint = NexoraBorder, modifier = Modifier.size(16.dp))
        }
    }
}

// ─────────────────────────────────────────────
// INTELLIGENCE COMPOSER
// (premium command field, not a generic chat input)
// ─────────────────────────────────────────────

@Composable
fun IntelligenceComposer(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, NexoraBorder),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(WindowInsets.ime.asPaddingValues())
                .fillMaxWidth()
        ) {
            // Optional Command Suggestions (compact, horizontally scrollable)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Plan my day",
                    "What should I focus on?",
                    "Why am I behind?",
                    "Review my goals"
                )
                items(suggestions) { suggestion ->
                    Surface(
                        onClick = {
                            if (enabled) {
                                onSend(suggestion)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(0.5.dp, NexoraBorder)
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            color = Green40,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input field & Send button
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Input field — off-white surface, 20dp radius, generous padding
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(0.5.dp, NexoraBorder)
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Ask Nexora anything\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Green40
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (text.isNotBlank() && enabled) {
                                onSend(text); text = ""
                                keyboardController?.hide(); focusManager.clearFocus()
                            }
                        }),
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Green10)
                    )
                }

                // Send button — Nexora green, compact circle
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
}

// Legacy aliases for any external references
@Composable
fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) = IntelligenceComposer(onSend, enabled)

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
