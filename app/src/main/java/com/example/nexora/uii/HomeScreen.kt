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
    tasks: SnapshotStateList<PremiumTask>,
    goals: SnapshotStateList<NexoraGoal>,
    progressHistory: List<DailyProgress>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    proactiveSignals: List<AiProactiveSignal> = emptyList(),
    proposedAction: AiAction? = null,
    onApproveAction: (AiAction) -> Unit = {},
    onDismissAction: () -> Unit = {}
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val completedCount = remember(tasks.toList()) { tasks.count { it.completed } }
    val totalCount = tasks.size
    val progress = remember(completedCount, totalCount) { 
        if (totalCount == 0) 0f else completedCount.toFloat() / totalCount 
    }

    val focusTasks = remember(tasks.toList()) {
        tasks.filter { !it.completed }
            .sortedBy { it.priority.ordinal }
            .take(3)
    }

    val streakCount = remember(progressHistory) { calculateCurrentStreak(progressHistory) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. GREETING & CLOCK
        item {
            StaggeredEntrance(revealed, 0) {
                HomeHeader()
            }
        }

        // 2. STREAK HERO
        item {
            StaggeredEntrance(revealed, 1) {
                StreakHero(streakCount)
            }
        }

        // 3. AI INTELLIGENCE (Contextual)
        if (proposedAction != null || proactiveSignals.isNotEmpty()) {
            item {
                StaggeredEntrance(revealed, 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Nexora Noticed", style = MaterialTheme.typography.titleMedium, color = Green10)
                        if (proposedAction != null) {
                            ProposedActionCard(
                                action = proposedAction,
                                onConfirm = { onApproveAction(proposedAction) },
                                onDismiss = onDismissAction
                            )
                        } else {
                            ProactiveSignalCard(proactiveSignals.first(), onApproveAction)
                        }
                    }
                }
            }
        }

        // 4. TODAY'S FOCUS
        item {
            StaggeredEntrance(revealed, 3) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NexoraSectionHeader(
                        title = "Today's focus",
                        actionLabel = if (tasks.isNotEmpty()) "View all" else null,
                        onAction = { /* Navigation handled in parent */ }
                    )
                    
                    if (focusTasks.isEmpty()) {
                        EmptyFocusState(onAddTask)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            focusTasks.forEach { task ->
                                HomeTaskCard(task, { onToggleTask(task) })
                            }
                        }
                    }
                }
            }
        }

        // 5. PROGRESS RING CARD
        item {
            StaggeredEntrance(revealed, 4) {
                ProgressRingCard(completedCount, totalCount, progress)
            }
        }

        // 6. GITHUB HEATMAP
        item {
            StaggeredEntrance(revealed, 5) {
                DailyProgressCalendar(progressHistory)
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineLarge, color = Green10)
            Text("Your day, intelligently prioritized.", style = MaterialTheme.typography.bodyLarge, color = Green40)
        }
        LiveMomentChip()
    }
}

@Composable
private fun LiveMomentChip() {
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")) }

    Surface(
        color = Green95,
        shape = NexoraShapes.medium,
        border = BorderStroke(1.dp, Green80)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Green60))
            Spacer(Modifier.width(8.dp))
            Text("$time • $date", style = NumericStyle.copy(fontSize = 12.sp), color = Green20)
        }
    }
}

@Composable
private fun StreakHero(count: Int) {
    NexoraCard(containerColor = Green10) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = count.toString(),
                style = NumericStyle.copy(fontSize = 48.sp, color = Green60)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Day Streak", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Your consistency is scaling.", style = MaterialTheme.typography.bodySmall, color = Green80)
            }
            Spacer(Modifier.weight(1f))
            IconBox(Icons.Rounded.Whatshot, Green20, Green60, size = 48)
        }
    }
}

@Composable
private fun HomeTaskCard(task: PremiumTask, onToggle: () -> Unit) {
    val priorityColor = when(task.priority) {
        TaskPriority.URGENT -> NexoraError
        TaskPriority.HIGH -> Clay60
        else -> Green60
    }

    Box(modifier = Modifier.fillMaxWidth().nexoraClickable { onToggle() }) {
        NexoraCard(
            modifier = Modifier.padding(start = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBox(
                    icon = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    containerColor = if (task.completed) Green60 else Gray95,
                    contentColor = if (task.completed) Color.White else Green40,
                    size = 28
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, color = Green10)
                    Text(task.category, style = MaterialTheme.typography.labelMedium, color = Green40)
                }
                Text(task.duration, style = NumericStyle.copy(fontSize = 12.sp), color = Green40)
            }
        }
        // Left edge priority bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(priorityColor)
        )
    }
}

@Composable
private fun ProgressRingCard(completed: Int, total: Int, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = NexoraMotion.SmoothSpec,
        label = "ringProgress"
    )

    NexoraCard(containerColor = Gray95) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawArc(Color.White, -90f, 360f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Green60, -90f, animatedProgress * 360f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("${(progress * 100).toInt()}%", style = NumericStyle.copy(fontSize = 14.sp), color = Green10)
            }
            Spacer(Modifier.width(24.dp))
            Column {
                Text("Daily progress", style = MaterialTheme.typography.titleMedium, color = Green10)
                Text("$completed of $total tasks completed", style = MaterialTheme.typography.bodyMedium, color = Green40)
            }
        }
    }
}

@Composable
private fun StaggeredEntrance(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(400, delayMillis = index * NexoraMotion.SectionStagger, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(400, delayMillis = index * NexoraMotion.SectionStagger)
        )
    ) {
        content()
    }
}

@Composable
private fun EmptyFocusState(onAddTask: () -> Unit) {
    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.FilterTiltShift, null, tint = Green80, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Clear space.", style = MaterialTheme.typography.titleMedium, color = Green40)
            TextButton(onClick = onAddTask) {
                Text("+ Add task", color = Green60)
            }
        }
    }
}
