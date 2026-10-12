package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

private enum class TaskFilter { Active, Completed }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    tasks: List<PremiumTask>,
    topRecommendation: AiRecommendation? = null,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    onAiAction: () -> Unit = {},
    onDeleteTask: (PremiumTask) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(TaskFilter.Active) }

    val filteredTasks = remember(tasks.toList(), selectedFilter) {
        when (selectedFilter) {
            TaskFilter.Active -> tasks.filter { !it.completed }.sortedByDescending { it.priority.ordinal }
            TaskFilter.Completed -> tasks.filter { it.completed }
        }
    }

    val activeCount = remember(tasks.toList()) { tasks.count { !it.completed } }
    val completedCount = remember(tasks.toList()) { tasks.count { it.completed } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = NexoraShapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // HEADER
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 40.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            activeCount == 0 -> "Nothing pending"
                            activeCount == 1 -> "1 task remaining"
                            else -> "$activeCount tasks remaining"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // TOP AI RECOMMENDATION BANNER (compact inline)
            if (topRecommendation != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        AiRecommendationCard(
                            recommendation = topRecommendation,
                            onAction = { onAiAction() }
                        )
                    }
                }
            }

            // STICKY FILTER BAR
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PremiumFilterBar(
                        selected = selectedFilter,
                        activeCount = activeCount,
                        completedCount = completedCount,
                        onSelect = { selectedFilter = it }
                    )
                }
            }

            // TASK LIST
            if (filteredTasks.isEmpty()) {
                item {
                    PremiumEmptyTasksState(selectedFilter, onAddTask)
                }
            } else {
                items(
                    items = filteredTasks,
                    key = { task -> if (task.id != 0L) "task_${task.id}" else "temp_${task.title}" }
                ) { task ->
                    val isRecommended = topRecommendation?.relatedTaskId == task.id
                    PremiumTaskRow(
                        task = task,
                        isAiRecommended = isRecommended,
                        reasoningMessage = if (isRecommended) topRecommendation?.message else null,
                        onToggle = { onToggleTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// PREMIUM FILTER BAR
// ─────────────────────────────────────────────

@Composable
private fun PremiumFilterBar(
    selected: TaskFilter,
    activeCount: Int,
    completedCount: Int,
    onSelect: (TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        PremiumFilterTab(
            label = "Active",
            count = activeCount,
            isSelected = selected == TaskFilter.Active,
            onClick = { onSelect(TaskFilter.Active) },
            modifier = Modifier.weight(1f)
        )
        PremiumFilterTab(
            label = "Completed",
            count = completedCount,
            isSelected = selected == TaskFilter.Completed,
            onClick = { onSelect(TaskFilter.Completed) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PremiumFilterTab(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "filterTabColor"
    )

    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = textColor
            )
            if (count > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Animated underline indicator
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(2.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

// ─────────────────────────────────────────────
// PREMIUM TASK ROW
// ─────────────────────────────────────────────

@Composable
private fun PremiumTaskRow(
    task: PremiumTask,
    isAiRecommended: Boolean = false,
    reasoningMessage: String? = null,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val contentAlpha = if (task.completed) 0.55f else 1f

    val customColors = NexoraTheme.customColors
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> customColors.priorityUrgent
        TaskPriority.HIGH -> customColors.priorityHigh
        TaskPriority.MEDIUM -> customColors.priorityMedium
        else -> Color.Transparent
    }

    val priorityBarWidth = when (task.priority) {
        TaskPriority.URGENT, TaskPriority.HIGH -> 3.dp
        else -> 0.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .nexoraClickable { onToggle() }
        ) {
            // Card body
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = priorityBarWidth + if (priorityBarWidth > 0.dp) 4.dp else 0.dp),
                shape = NexoraShapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isAiRecommended) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                Text(
                                    text = "Focus now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(contentAlpha)
                    ) {
                        // Animated check box
                        val iconScale by animateFloatAsState(
                            targetValue = if (task.completed) 1.05f else 1f,
                            animationSpec = NexoraMotion.SpringSpec,
                            label = "checkScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .scale(iconScale)
                                .clip(CircleShape)
                                .background(if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.completed) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Canvas(Modifier.size(22.dp)) {
                                    drawCircle(
                                        color = priorityColor.takeIf { it != Color.Transparent } ?: customColors.cardBorder,
                                        radius = 10.dp.toPx(),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
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
                                        Icons.Rounded.Flag,
                                        null,
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

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!reasoningMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = reasoningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Left priority bar
            if (priorityBarWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(priorityBarWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(priorityColor)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────

@Composable
private fun PremiumEmptyTasksState(filter: TaskFilter, onAddTask: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (filter == TaskFilter.Active) {
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
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddTask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Task", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                text = "Nothing completed yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Check off tasks to see them archived here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
