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
        containerColor = NexoraBackgroundLight,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = Green10,
                contentColor = Color.White,
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // GOAL HEADER & PROGRESS
            item {
                Column {
                    Text(
                        text = goal.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Green60,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Green10
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { goal.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = Green60,
                                trackColor = Gray90,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$progress%",
                            style = NumericStyle.copy(fontSize = 18.sp, color = Green60)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // INTERACTIVE PROGRESS CONTROL
                    Surface(
                        shape = NexoraShapes.medium,
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Gray90),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Goal Progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Green10
                                )
                                Text(
                                    text = "$progress%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Green60,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Slider(
                                value = goal.progress,
                                onValueChange = { onUpdateProgress(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Green60,
                                    activeTrackColor = Green60,
                                    inactiveTrackColor = Gray95
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
            }

            // AI INSIGHT SECTION
            item {
                AiSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBox(Icons.Rounded.AutoAwesome, Clay60, Color.White, 28)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Intelligence Insight",
                            style = MaterialTheme.typography.labelLarge,
                            color = Clay40
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = health?.evidence ?: "You're making steady progress toward this objective.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Green10,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Break down next milestone",
                        style = MaterialTheme.typography.labelLarge,
                        color = Clay60,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDecomposeGoal() }
                    )
                }
            }

            // TASKS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NexoraSectionHeader(title = "Related tasks")
                    
                    if (relatedTasks.isEmpty()) {
                        Text(
                            text = "No tasks linked to this goal yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Green40
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            relatedTasks.forEach { task ->
                                GoalTaskCard(task = task, onToggle = { onToggleTask(task) })
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

@Composable
private fun GoalTaskCard(task: PremiumTask, onToggle: () -> Unit) {
    NexoraCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .nexoraClickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(
                icon = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                containerColor = if (task.completed) Green60 else Gray95,
                contentColor = if (task.completed) Color.White else Green40,
                size = 24
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
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
