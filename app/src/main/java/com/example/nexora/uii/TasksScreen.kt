package com.example.nexora.uii

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            TaskFilter.Active -> tasks.filter { !it.completed }.sortedByDescending { it.priority.ordinal }
            TaskFilter.Completed -> tasks.filter { it.completed }
        }
    }

    val activeCount = remember(tasks) { tasks.count { !it.completed } }
    val completedCount = remember(tasks) { tasks.count { it.completed } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. SCREEN HEADER
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$activeCount pending · $completedCount completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. TOP AI RECOMMENDATION (if active)
            if (topRecommendation != null && selectedFilter == TaskFilter.Active) {
                item {
                    AiRecommendationCard(
                        recommendation = topRecommendation,
                        onAction = { onAiAction() }
                    )
                }
            }

            // 3. APPLE SEGMENTED CAPSULE FILTER
            item {
                AppleSegmentedFilter(
                    selected = selectedFilter,
                    activeCount = activeCount,
                    completedCount = completedCount,
                    onSelect = { selectedFilter = it }
                )
            }

            // 4. GROUPED INSET TASK LIST OR EMPTY STATE
            if (filteredTasks.isEmpty()) {
                item {
                    AppleEmptyTasksState(selectedFilter, onAddTask)
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp
                    ) {
                        Column {
                            filteredTasks.forEachIndexed { index, task ->
                                val isRecommended = topRecommendation?.relatedTaskId == task.id
                                InsetTaskRow(
                                    task = task,
                                    isAiRecommended = isRecommended,
                                    reasoningMessage = if (isRecommended) topRecommendation?.message else null,
                                    onToggle = { onToggleTask(task) },
                                    onDelete = { onDeleteTask(task) }
                                )
                                if (index < filteredTasks.size - 1) {
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
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// APPLE SEGMENTED CONTROL FILTER
// ─────────────────────────────────────────────

@Composable
private fun AppleSegmentedFilter(
    selected: TaskFilter,
    activeCount: Int,
    completedCount: Int,
    onSelect: (TaskFilter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentItem(
                label = "Pending",
                count = activeCount,
                isSelected = selected == TaskFilter.Active,
                onClick = { onSelect(TaskFilter.Active) },
                modifier = Modifier.weight(1f)
            )
            SegmentItem(
                label = "Completed",
                count = completedCount,
                isSelected = selected == TaskFilter.Completed,
                onClick = { onSelect(TaskFilter.Completed) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (isSelected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// INSET TASK ROW
// ─────────────────────────────────────────────

@Composable
private fun InsetTaskRow(
    task: PremiumTask,
    isAiRecommended: Boolean = false,
    reasoningMessage: String? = null,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val contentAlpha = if (task.completed) 0.5f else 1f
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
            label = "tasksCheck"
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

        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha)
        ) {
            if (isAiRecommended) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "AI Recommended",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = buildList {
                if (task.category.isNotBlank()) add(task.category)
                if (task.duration.isNotBlank()) add(task.duration)
                if (!task.goalTitle.isNullOrBlank()) add(task.goalTitle)
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Priority Badge or Dot
        if (!task.completed && priorityColor != Color.Transparent) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = priorityColor.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = task.priority.name,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = priorityColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Delete Action
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp).padding(start = 4.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// EMPTY TASKS STATE
// ─────────────────────────────────────────────

@Composable
private fun AppleEmptyTasksState(filter: TaskFilter, onAddTask: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBox(
                icon = if (filter == TaskFilter.Active) Icons.Rounded.DoneAll else Icons.Rounded.CheckCircleOutline,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                size = 36
            )
            Text(
                text = if (filter == TaskFilter.Active) "All caught up" else "No completed tasks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (filter == TaskFilter.Active)
                    "You have no pending tasks right now."
                else
                    "Tasks you complete will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filter == TaskFilter.Active) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAddTask,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Task", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
