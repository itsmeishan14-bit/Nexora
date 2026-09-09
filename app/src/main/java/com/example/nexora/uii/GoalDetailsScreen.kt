package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onDecomposeGoal: () -> Unit
) {
    val health = personalContext?.goalHealth?.find { it.goalId == goal.id }
    val progress = (goal.progress * 100).toInt()

    Scaffold(
        containerColor = NexoraBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NexoraPrimaryText)
                    }
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NexoraMutedText)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = NexoraError)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = NexoraPrimaryText,
                contentColor = Color.White,
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // GOAL HEADER
            item {
                Column {
                    Text(
                        text = goal.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoraPrimaryGreen,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NexoraPrimaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { goal.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = NexoraPrimaryGreen,
                                trackColor = NexoraBorder,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraPrimaryGreen
                        )
                    }
                }
            }

            // AI INSIGHT
            item {
                NexoraCard(containerColor = NexoraSoftGreen, border = null) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NexoraPrimaryGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nexora insight",
                                style = MaterialTheme.typography.labelLarge,
                                color = NexoraPrimaryGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = health?.evidence ?: "You're making steady progress toward this objective.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoraPrimaryText
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Decompose into tasks",
                            style = MaterialTheme.typography.labelLarge,
                            color = NexoraPrimaryGreen,
                            modifier = Modifier.clickable { onDecomposeGoal() }
                        )
                    }
                }
            }

            // TASKS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Related tasks",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraPrimaryText
                    )
                    
                    if (relatedTasks.isEmpty()) {
                        Text(
                            text = "No tasks linked to this goal yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoraMutedText
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
                Spacer(modifier = Modifier.height(40.dp))
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (task.completed) NexoraPrimaryGreen else NexoraSoftGreen)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (task.completed) NexoraMutedText else NexoraPrimaryText,
                modifier = Modifier.weight(1f)
            )
            
            if (task.priority == TaskPriority.URGENT) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NexoraError)
                )
            }
        }
    }
}
