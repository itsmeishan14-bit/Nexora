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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

private enum class TaskFilter { Active, Completed }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    tasks: List<PremiumTask>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    onAiAction: () -> Unit = {},
    onDeleteTask: (PremiumTask) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(TaskFilter.Active) }
    
    val filteredTasks = remember(tasks.toList(), selectedFilter) {
        when(selectedFilter) {
            TaskFilter.Active -> tasks.filter { !it.completed }.sortedBy { it.priority.ordinal }
            TaskFilter.Completed -> tasks.filter { it.completed }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = Green10,
                contentColor = Color.White,
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // HEADER
            item {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tasks", style = MaterialTheme.typography.headlineLarge, color = Green10)
                    IconBox(
                        icon = Icons.Rounded.AutoAwesome,
                        containerColor = Green95,
                        contentColor = Green60,
                        size = 40
                    )
                }
            }

            // STICKY FILTER HEADER
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TaskFilterBar(
                        selected = selectedFilter,
                        onSelect = { selectedFilter = it }
                    )
                }
            }

            // LIST
            if (filteredTasks.isEmpty()) {
                item {
                    EmptyTasksState(selectedFilter)
                }
            } else {
                items(
                    items = filteredTasks,
                    key = { task -> if (task.id != 0L) "task_${task.id}" else "temp_${task.title}" }
                ) { task ->
                    AnimatedTaskCard(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFilterBar(selected: TaskFilter, onSelect: (TaskFilter) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(NexoraShapes.medium)
            .background(Gray95)
            .padding(4.dp)
    ) {
        TaskFilter.values().forEach { filter ->
            val isSelected = selected == filter
            val bgColor by animateColorAsState(if (isSelected) Color.White else Color.Transparent)
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(NexoraShapes.small)
                    .background(bgColor)
                    .clickable { onSelect(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Green10 else Green40
                )
            }
        }
    }
}

@Composable
private fun AnimatedTaskCard(task: PremiumTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    val completionProgress by animateFloatAsState(
        targetValue = if (task.completed) 1f else 0f,
        animationSpec = NexoraMotion.SmoothSpec,
        label = "completionAnim"
    )
    
    val cardBg = lerp(Color.White, Green95, completionProgress)
    val contentAlpha = lerp(1f, 0.6f, completionProgress)
    
    val priorityColor = when(task.priority) {
        TaskPriority.URGENT -> NexoraError
        TaskPriority.HIGH -> Clay60
        else -> Green60
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        NexoraCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp)
                .nexoraClickable { onToggle() },
            containerColor = cardBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scale-Bounce Icon
                Box(contentAlignment = Alignment.Center) {
                    val scale by animateFloatAsState(
                        targetValue = if (task.completed) 1.2f else 1f,
                        animationSpec = NexoraMotion.SpringSpec,
                        label = "iconScale"
                    )
                    Icon(
                        imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (task.completed) Green60 else Green80,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scale)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Box {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Green10
                        )
                        // Custom Animated Strikethrough
                        Canvas(modifier = Modifier.matchParentSize()) {
                            if (completionProgress > 0f) {
                                drawLine(
                                    color = Green60,
                                    start = Offset(0f, size.height / 2 + 2.dp.toPx()),
                                    end = Offset(
                                        size.width * completionProgress,
                                        size.height / 2 + 2.dp.toPx()
                                    ),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = Green40
                        )
                        if (task.duration.isNotBlank()) {
                            Text(" • ", color = Green80)
                            Text(
                                text = task.duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = Green40
                            )
                        }
                    }

                    if (!task.goalTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
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

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = Green80,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Left edge priority bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(priorityColor)
        )
    }
}

@Composable
private fun EmptyTasksState(filter: TaskFilter) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (filter == TaskFilter.Active) Icons.Rounded.Inbox else Icons.Rounded.CheckCircleOutline,
            null, tint = Gray90, modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (filter == TaskFilter.Active) "You're all caught up" else "No completed tasks yet",
            style = MaterialTheme.typography.titleMedium, color = Green40
        )
    }
}
