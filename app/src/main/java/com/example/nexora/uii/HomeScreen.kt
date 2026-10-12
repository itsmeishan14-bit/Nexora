package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    tasks: List<PremiumTask>,
    goals: List<NexoraGoal>,
    progressHistory: List<DailyProgress>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    proactiveSignals: List<AiProactiveSignal> = emptyList(),
    aiRecommendations: List<AiRecommendation> = emptyList(),
    proposedAction: AiAction? = null,
    lastActionResult: AiActionResult? = null,
    lastExecutionRecord: AiExecutionRecord? = null,
    onApproveAction: (AiAction) -> Unit = {},
    onDismissAction: () -> Unit = {},
    onDismissResult: () -> Unit = {},
    onRecommendationAction: (AiRecommendation) -> Unit = {}
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val completedCount = remember(tasks.toList()) { tasks.count { it.completed } }
    val totalCount = tasks.size
    val progress = remember(completedCount, totalCount) {
        if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    }

    val nextTaskRec = remember(aiRecommendations) {
        aiRecommendations.find { it.type == AiRecommendationType.NEXT_TASK }
    }

    val focusTasks = remember(tasks.toList(), nextTaskRec) {
        val incomplete = tasks.filter { !it.completed }
        if (incomplete.isEmpty()) return@remember emptyList()

        val topRecommendedTaskId = nextTaskRec?.relatedTaskId
        if (topRecommendedTaskId != null) {
            val recTask = incomplete.find { it.id == topRecommendedTaskId }
            if (recTask != null) {
                listOf(recTask) + incomplete.filter { it.id != topRecommendedTaskId }.sortedByDescending { it.priority.ordinal }.take(2)
            } else {
                incomplete.sortedByDescending { it.priority.ordinal }.take(3)
            }
        } else {
            incomplete.sortedByDescending { it.priority.ordinal }.take(3)
        }
    }

    val streakCount = remember(progressHistory) { calculateCurrentStreak(progressHistory) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 1. GREETING HEADER
        item {
            HomeEntranceAnim(revealed, 0) {
                HomeHeader()
            }
        }

        // 2. AI INTELLIGENCE — Only shown when there's real signal
        val displayResult = lastActionResult ?: lastExecutionRecord?.let {
            AiActionResult(it.isCompleteSuccess, it.summaryMessage)
        }
        if (displayResult != null || proposedAction != null || proactiveSignals.isNotEmpty() || aiRecommendations.isNotEmpty()) {
            item {
                HomeEntranceAnim(revealed, 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionDivider(label = "NEXORA INTELLIGENCE")
                        if (displayResult != null) {
                            ActionResultBanner(
                                result = displayResult,
                                executionRecord = lastExecutionRecord,
                                onDismiss = onDismissResult
                            )
                        }
                        if (proposedAction != null) {
                            ProposedActionCard(
                                action = proposedAction,
                                onConfirm = { onApproveAction(proposedAction) },
                                onDismiss = onDismissAction
                            )
                        } else if (proactiveSignals.isNotEmpty()) {
                            ProactiveSignalCard(proactiveSignals.first(), onApproveAction)
                        } else if (aiRecommendations.isNotEmpty()) {
                            AiRecommendationCard(
                                recommendation = aiRecommendations.first(),
                                onAction = onRecommendationAction
                            )
                        }
                    }
                }
            }
        }

        // 3. TODAY'S FOCUS
        item {
            HomeEntranceAnim(revealed, 2) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Focus",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (tasks.isNotEmpty()) {
                            Text(
                                text = "All tasks →",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { /* Handled in parent */ }
                            )
                        }
                    }

                    if (focusTasks.isEmpty()) {
                        HomeFocusEmptyState(onAddTask)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            focusTasks.forEachIndexed { index, task ->
                                val isAiRecommended = nextTaskRec != null && task.id == nextTaskRec.relatedTaskId
                                HomeTaskCard(
                                    task = task,
                                    isAiRecommended = isAiRecommended,
                                    reasoningMessage = if (isAiRecommended) nextTaskRec?.message else null,
                                    isPrimary = index == 0,
                                    onToggle = { onToggleTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. STREAK + DAILY PROGRESS — compact, side by side
        item {
            HomeEntranceAnim(revealed, 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StreakCard(streakCount, modifier = Modifier.weight(1f))
                    ProgressCard(completedCount, totalCount, progress, modifier = Modifier.weight(1f))
                }
            }
        }

        // 5. CONSISTENCY CALENDAR
        item {
            HomeEntranceAnim(revealed, 4) {
                DailyProgressCalendar(progressHistory)
            }
        }
    }
}

// ─────────────────────────────────────────────
// HOME HEADER
// ─────────────────────────────────────────────

@Composable
private fun HomeHeader() {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────
// SECTION DIVIDER WITH LABEL
// ─────────────────────────────────────────────

@Composable
fun SectionDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTheme.customColors.textTertiary,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

// ─────────────────────────────────────────────
// STREAK CARD (compact)
// ─────────────────────────────────────────────

@Composable
private fun StreakCard(count: Int, modifier: Modifier = Modifier) {
    NexoraCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                style = NumericStyle.copy(
                    fontSize = 38.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Day Streak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (count > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(NexoraShapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Consistent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// DAILY PROGRESS CARD (compact ring)
// ─────────────────────────────────────────────

@Composable
private fun ProgressCard(
    completed: Int,
    total: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = NexoraMotion.SmoothSpec,
        label = "ringProgress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    NexoraCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = NumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Tasks Done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$completed / $total",
                style = NumericStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────
// HOME TASK CARD
// ─────────────────────────────────────────────

@Composable
private fun HomeTaskCard(
    task: PremiumTask,
    isAiRecommended: Boolean = false,
    reasoningMessage: String? = null,
    isPrimary: Boolean = false,
    onToggle: () -> Unit
) {
    val customColors = NexoraTheme.customColors
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> customColors.priorityUrgent
        TaskPriority.HIGH -> customColors.priorityHigh
        TaskPriority.MEDIUM -> customColors.priorityMedium
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .nexoraClickable { onToggle() }
    ) {
        NexoraCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (priorityColor != Color.Transparent) 4.dp else 0.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isAiRecommended) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(NexoraShapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tactile check indicator
                    val checkScale by animateFloatAsState(
                        targetValue = if (task.completed) 1.05f else 1f,
                        animationSpec = NexoraMotion.SpringSpec,
                        label = "homeCheckScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .scale(checkScale)
                            .clip(CircleShape)
                            .background(if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.completed) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Canvas(Modifier.size(22.dp)) {
                                drawCircle(
                                    color = priorityColor.takeIf { it != Color.Transparent } ?: customColors.cardBorder,
                                    radius = 10.dp.toPx(),
                                    style = Stroke(1.5.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = if (isPrimary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )

                        val meta = buildList {
                            if (task.category.isNotBlank()) add(task.category)
                            if (task.duration.isNotBlank()) add(task.duration)
                        }
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = meta.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!task.goalTitle.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = task.goalTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Priority indicator dot
                    if (!task.completed && priorityColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(priorityColor)
                        )
                    }
                }

                if (!reasoningMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reasoningMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Left edge priority accent
        if (priorityColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(priorityColor)
            )
        }
    }
}

// ─────────────────────────────────────────────
// EMPTY FOCUS STATE
// ─────────────────────────────────────────────

@Composable
private fun HomeFocusEmptyState(onAddTask: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Clear space",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Add a task when something needs your attention.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(
            onClick = onAddTask,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add task", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────
// ENTRANCE ANIMATION
// ─────────────────────────────────────────────

@Composable
private fun HomeEntranceAnim(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(300, delayMillis = index * NexoraMotion.SectionStagger)
        ) + slideInVertically(
            initialOffsetY = { 16 },
            animationSpec = tween(300, delayMillis = index * NexoraMotion.SectionStagger, easing = FastOutSlowInEasing)
        )
    ) {
        content()
    }
}
