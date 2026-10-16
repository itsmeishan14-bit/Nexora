package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun HomeScreen(
    tasks: List<PremiumTask>,
    goals: List<NexoraGoal>,
    progressHistory: List<DailyProgress>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    onNavigateToTasks: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToGoalDetails: (NexoraGoal) -> Unit = {},
    onNavigateToAi: () -> Unit = {},
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

    val completedCount = remember(tasks) { tasks.count { it.completed } }
    val totalCount = tasks.size
    val progress = remember(completedCount, totalCount) {
        if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    }

    val incompleteTasks = remember(tasks) { tasks.filter { !it.completed } }

    val nextTaskRec = remember(aiRecommendations) {
        aiRecommendations.find { it.type == AiRecommendationType.NEXT_TASK }
    }

    // Identify the single highest-priority next action
    val nextActionTask = remember(incompleteTasks, nextTaskRec) {
        val aiPicked = nextTaskRec?.relatedTaskId?.let { id -> incompleteTasks.find { it.id == id } }
        aiPicked ?: incompleteTasks.maxByOrNull { it.priority.ordinal }
    }

    // Remaining focus tasks after extracting the next action
    val remainingTasks = remember(incompleteTasks, nextActionTask) {
        if (nextActionTask == null) emptyList()
        else incompleteTasks.filter { it.id != nextActionTask.id }.sortedByDescending { it.priority.ordinal }.take(4)
    }

    val streakCount = remember(progressHistory) { calculateCurrentStreak(progressHistory) }
    val activeGoals = remember(goals) { goals.filter { it.progress < 1f }.take(4) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. APPLE EDITORIAL HEADER & DATE STAMP
        item {
            HomeEntranceAnim(revealed, 0) {
                ExecutiveHeader(
                    onAddTask = onAddTask,
                    onOpenAi = onNavigateToAi
                )
            }
        }

        // 2. HERO: DAILY PROGRESS & MOMENTUM CARD
        item {
            HomeEntranceAnim(revealed, 1) {
                DailyMomentumCard(
                    completed = completedCount,
                    total = totalCount,
                    progress = progress,
                    streak = streakCount,
                    progressHistory = progressHistory
                )
            }
        }

        // 3. AI ACTION OR SIGNAL (if active)
        val displayResult = lastActionResult ?: lastExecutionRecord?.let {
            AiActionResult(it.isCompleteSuccess, it.summaryMessage)
        }
        if (displayResult != null || proposedAction != null || proactiveSignals.isNotEmpty() || aiRecommendations.isNotEmpty()) {
            item {
                HomeEntranceAnim(revealed, 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("NEXORA INTELLIGENCE", onActionClick = onNavigateToAi, actionText = "Assistant →")
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

        // 4. SPOTLIGHT: NEXT ACTION HERO CARD
        item {
            HomeEntranceAnim(revealed, 3) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("NEXT ACTION", onActionClick = onNavigateToTasks, actionText = if (incompleteTasks.isNotEmpty()) "All (${incompleteTasks.size}) →" else null)
                    if (nextActionTask != null) {
                        NextActionHeroCard(
                            task = nextActionTask,
                            aiReason = if (nextTaskRec?.relatedTaskId == nextActionTask.id) nextTaskRec.message else null,
                            onToggle = { onToggleTask(nextActionTask) }
                        )
                    } else {
                        AllCaughtUpCard(onAddTask = onAddTask)
                    }
                }
            }
        }

        // 5. COMPACT GROUPED FOCUS TASKS (Apple Grouped Inset List)
        if (remainingTasks.isNotEmpty()) {
            item {
                HomeEntranceAnim(revealed, 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("REMAINING FOCUS", onActionClick = onNavigateToTasks, actionText = "See all →")
                        GroupedTasksInsetCard(
                            tasks = remainingTasks,
                            onToggle = onToggleTask
                        )
                    }
                }
            }
        }

        // 6. ACTIVE OBJECTIVES (GOALS) CAROUSEL
        if (activeGoals.isNotEmpty()) {
            item {
                HomeEntranceAnim(revealed, 5) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("ACTIVE OBJECTIVES", onActionClick = onNavigateToGoals, actionText = "All Goals →")
                        ActiveGoalsCarousel(
                            goals = activeGoals,
                            onSelectGoal = onNavigateToGoalDetails
                        )
                    }
                }
            }
        }

        // 7. ACTIVITY RHYTHM CALENDAR HEATMAP
        item {
            HomeEntranceAnim(revealed, 6) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("ACTIVITY RHYTHM")
                    DailyProgressCalendar(progressHistory)
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────
// 1. EXECUTIVE HEADER & DATE BADGE
// ─────────────────────────────────────────────

@Composable
private fun ExecutiveHeader(
    onAddTask: () -> Unit,
    onOpenAi: () -> Unit
) {
    val now = remember { LocalDate.now() }
    val monthShort = remember { now.format(DateTimeFormatter.ofPattern("MMM")).uppercase() }
    val dayOfMonth = remember { now.dayOfMonth.toString() }
    val dayOfWeek = remember { now.format(DateTimeFormatter.ofPattern("EEEE")) }
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Apple-style Calendar Date Stamp Badge
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.width(46.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = monthShort,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = dayOfMonth,
                        style = NumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Title & Day Greeting
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dayOfWeek · Nexora OS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Action Buttons: AI Hub & Quick Add
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenAi,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "Intelligence",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onAddTask,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add Task",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// 2. DAILY MOMENTUM & ACTIVITY RIBBON
// ─────────────────────────────────────────────

@Composable
private fun DailyMomentumCard(
    completed: Int,
    total: Int,
    progress: Float,
    streak: Int,
    progressHistory: List<DailyProgress>
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = NexoraMotion.SmoothSpec,
        label = "heroProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Upper row: Circular gauge + Key momentum stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dual-ring Progress Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(68.dp)
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    val progressColor = MaterialTheme.colorScheme.primary

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        if (animatedProgress > 0f) {
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = NumericStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (total == 0) "No tasks scheduled today" else "$completed of $total tasks completed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            total == 0 -> "Tap + to plan your first focus item"
                            completed == total -> "All tasks completed · Excellent focus"
                            progress >= 0.5f -> "More than halfway done today"
                            else -> "Stay deliberate and knock out your next action"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Streak Badge
                if (streak > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = streak.toString(),
                                    style = NumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "STREAK",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))

            // Lower row: Apple Activity-style Week Ribbon (Mon - Sun)
            WeekActivityRibbon(progressHistory = progressHistory)
        }
    }
}

@Composable
private fun WeekActivityRibbon(progressHistory: List<DailyProgress>) {
    val today = remember { LocalDate.now() }
    val monday = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dayLabels.forEachIndexed { index, label ->
            val date = monday.plusDays(index.toLong())
            val progressItem = progressHistory.find { it.date == date }
            val isFullyCompleted = progressItem != null && progressItem.tasksPlanned > 0 && progressItem.tasksCompleted >= progressItem.tasksPlanned
            val isPartiallyCompleted = progressItem != null && progressItem.tasksCompleted > 0 && !isFullyCompleted
            val isToday = date == today
            val isFuture = date.isAfter(today)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFullyCompleted -> MaterialTheme.colorScheme.primary
                                isPartiallyCompleted -> MaterialTheme.colorScheme.primaryContainer
                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFullyCompleted) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (isPartiallyCompleted) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    } else if (!isFuture && progressItem != null && progressItem.tasksPlanned > 0) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 3. NEXT ACTION HERO SPOTLIGHT
// ─────────────────────────────────────────────

@Composable
private fun NextActionHeroCard(
    task: PremiumTask,
    aiReason: String?,
    onToggle: () -> Unit
) {
    val customColors = NexoraTheme.customColors
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> customColors.priorityUrgent
        TaskPriority.HIGH -> customColors.priorityHigh
        TaskPriority.MEDIUM -> customColors.priorityMedium
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Tag: "PRIMARY FOCUS" + Priority badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "PRIMARY FOCUS",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = priorityColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = task.priority.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = priorityColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Big Bold Task Title
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Metadata Chips (Category, Duration, Goal link)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.category.isNotBlank()) {
                    Text(
                        text = task.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.duration.isNotBlank()) {
                    Text(text = "·", color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = task.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!task.goalTitle.isNullOrBlank()) {
                    Text(text = "·", color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = task.goalTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // AI Insight Explanation (if available)
            if (!aiReason.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = aiReason,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Large Tactile Completion Button
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Mark as Complete",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// 4. GROUPED INSET FOCUS TASKS (Apple Inset Table)
// ─────────────────────────────────────────────

@Composable
private fun GroupedTasksInsetCard(
    tasks: List<PremiumTask>,
    onToggle: (PremiumTask) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column {
            tasks.forEachIndexed { index, task ->
                CompactTaskRow(
                    task = task,
                    onToggle = { onToggle(task) }
                )
                if (index < tasks.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTaskRow(
    task: PremiumTask,
    onToggle: () -> Unit
) {
    val customColors = NexoraTheme.customColors
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> customColors.priorityUrgent
        TaskPriority.HIGH -> customColors.priorityHigh
        TaskPriority.MEDIUM -> customColors.priorityMedium
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tactile Spring Check Circle
        val checkScale by animateFloatAsState(
            targetValue = if (task.completed) 1.08f else 1f,
            animationSpec = NexoraMotion.SpringSpec,
            label = "compactCheck"
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .scale(checkScale)
                .clip(CircleShape)
                .background(if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    BorderStroke(
                        1.5.dp,
                        if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (task.completed) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val meta = buildList {
                if (task.category.isNotBlank()) add(task.category)
                if (task.duration.isNotBlank()) add(task.duration)
                if (!task.goalTitle.isNullOrBlank()) add(task.goalTitle)
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (priorityColor != Color.Transparent) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(priorityColor)
            )
        }
    }
}

// ─────────────────────────────────────────────
// 5. ACTIVE GOALS CAROUSEL
// ─────────────────────────────────────────────

@Composable
private fun ActiveGoalsCarousel(
    goals: List<NexoraGoal>,
    onSelectGoal: (NexoraGoal) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(goals, key = { it.id }) { goal ->
            val progressPercent = (goal.progress * 100).toInt()
            Surface(
                modifier = Modifier
                    .width(190.dp)
                    .clickable { onSelectGoal(goal) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (goal.category.isNotBlank()) {
                        Text(
                            text = goal.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { goal.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$progressPercent%",
                            style = NumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// ALL CAUGHT UP STATE
// ─────────────────────────────────────────────

@Composable
private fun AllCaughtUpCard(onAddTask: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "All caught up",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "You have completed all pending focus tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddTask,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Task", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────
// REUSABLE SECTION HEADER WITH ACTION
// ─────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    onActionClick: (() -> Unit)? = null,
    actionText: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTheme.customColors.textTertiary,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        if (onActionClick != null && actionText != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

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
