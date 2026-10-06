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
        if (lastActionResult != null || proposedAction != null || proactiveSignals.isNotEmpty() || aiRecommendations.isNotEmpty()) {
            item {
                HomeEntranceAnim(revealed, 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionDivider(label = "NEXORA INTELLIGENCE")
                        if (lastActionResult != null) {
                            ActionResultBanner(result = lastActionResult, onDismiss = onDismissResult)
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
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's focus",
                            style = MaterialTheme.typography.titleLarge,
                            color = Green10
                        )
                        if (tasks.isNotEmpty()) {
                            Text(
                                text = "All tasks →",
                                style = MaterialTheme.typography.labelLarge,
                                color = Green60,
                                modifier = Modifier.clickable { /* Navigation handled in parent */ }
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

        // 4. STREAK + DAILY PROGRESS  — compact, side by side
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
            in 0..11 -> "Good morning."
            in 12..17 -> "Good afternoon."
            else -> "Good evening."
        }
    }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = Green10
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = Green40
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
            color = NexoraMutedTextLight,
            letterSpacing = 1.2.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = NexoraBorder,
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
        containerColor = Green10
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                style = NumericStyle.copy(fontSize = 40.sp, color = Green60)
            )
            Text(
                text = "day streak",
                style = MaterialTheme.typography.bodySmall,
                color = Green70
            )
            if (count > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Consistent.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Green50
                )
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

    NexoraCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Green95,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = Green60,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = NumericStyle.copy(fontSize = 13.sp),
                    color = Green10
                )
            }
            Text(
                text = "Tasks done",
                style = MaterialTheme.typography.bodySmall,
                color = Green40
            )
            Text(
                text = "$completed / $total",
                style = NumericStyle.copy(fontSize = 16.sp),
                color = Green10
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
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> NexoraError
        TaskPriority.HIGH -> Clay60
        else -> Green60
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
                .padding(start = 6.dp),
            containerColor = if (isPrimary && !task.completed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isAiRecommended) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(NexoraShapes.small)
                                .background(Green95)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Green60)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Green40
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Completion indicator
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (task.completed) Green60 else Green95),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.completed) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = if (isPrimary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (task.completed) Green40 else Green10
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
                                color = Green40
                            )
                        }

                        if (!task.goalTitle.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Green60
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = task.goalTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Green60
                                )
                            }
                        }
                    }

                    // Priority indicator dot
                    if (!task.completed && (task.priority == TaskPriority.URGENT || task.priority == TaskPriority.HIGH)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(priorityColor)
                        )
                    }
                }

                if (!reasoningMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = NexoraBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = reasoningMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Green40,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Left edge priority bar — thinner, more refined
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(priorityColor)
        )
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
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Clear space.",
            style = MaterialTheme.typography.titleMedium,
            color = Green40
        )
        Text(
            text = "Add a task when something needs your attention.",
            style = MaterialTheme.typography.bodyMedium,
            color = NexoraMutedTextLight,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(
            onClick = onAddTask,
            colors = ButtonDefaults.textButtonColors(contentColor = Green60)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add task", style = MaterialTheme.typography.labelLarge)
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
