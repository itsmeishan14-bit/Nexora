package com.example.nexora.uii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.AiPersonalContext
import com.example.nexora.ui.theme.*

@Composable
fun GoalDetailsScreen(
    goal: NexoraGoal,
    relatedTasks: List<PremiumTask>,
    personalContext: AiPersonalContext? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    onAddTask: () -> Unit,
    onDecomposeGoal: () -> Unit,
    onUpdateProgress: (Float) -> Unit = {}
) {
    val health = personalContext?.goalHealth?.find { it.goalId == goal.id }
    val progress = (goal.progress * 100).toInt()

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, NexoraBorder),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Green10)
                    }
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Green40)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = NexoraError)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = Green10,
                contentColor = Color.White,
                shape = NexoraShapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // GOAL HEADER
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Category label
                    if (goal.category.isNotBlank()) {
                        Text(
                            text = goal.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Green60,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Green10
                    )

                    // Progress bar + percent
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { goal.progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = Green60,
                            trackColor = NexoraBorder,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "$progress%",
                            style = NumericStyle.copy(fontSize = 16.sp, color = Green60)
                        )
                    }
                }
            }

            // PROGRESS CONTROL
            item {
                Surface(
                    shape = NexoraShapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, NexoraBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Update progress",
                                style = MaterialTheme.typography.titleSmall,
                                color = Green10
                            )
                            Text(
                                text = "$progress%",
                                style = NumericStyle.copy(fontSize = 14.sp, color = Green60)
                            )
                        }

                        Slider(
                            value = goal.progress,
                            onValueChange = { onUpdateProgress(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Green60,
                                activeTrackColor = Green60,
                                inactiveTrackColor = NexoraBorder
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { pct ->
                                val isSelected = kotlin.math.abs(goal.progress - pct) < 0.05f
                                Surface(
                                    onClick = { onUpdateProgress(pct) },
                                    shape = CircleShape,
                                    color = if (isSelected) Green60 else Green95,
                                    contentColor = if (isSelected) Color.White else Green40
                                ) {
                                    Text(
                                        text = "${(pct * 100).toInt()}%",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI INSIGHT SECTION
            item {
                AiSurface {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Clay60)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Intelligence Insight",
                            style = MaterialTheme.typography.labelSmall,
                            color = Clay40,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = health?.evidence ?: "You're making steady progress toward this objective.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Green10,
                        lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Break down next milestone →",
                        style = MaterialTheme.typography.labelLarge,
                        color = Clay60,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onDecomposeGoal() }
                    )
                }
            }

            // RELATED TASKS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionDivider(label = "RELATED TASKS")

                    if (relatedTasks.isEmpty()) {
                        Text(
                            text = "No tasks linked to this goal yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoraMutedTextLight
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            relatedTasks.forEach { task ->
                                GoalDetailTaskRow(task = task, onToggle = { onToggleTask(task) })
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// GOAL DETAIL TASK ROW
// ─────────────────────────────────────────────

@Composable
private fun GoalDetailTaskRow(task: PremiumTask, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .nexoraClickable { onToggle() },
        shape = NexoraShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, NexoraBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (task.completed) Green60 else Green95),
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.completed) Green40 else Green10,
                modifier = Modifier.weight(1f)
            )

            if (task.priority == TaskPriority.URGENT) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NexoraError)
                )
            }
        }
    }
}
