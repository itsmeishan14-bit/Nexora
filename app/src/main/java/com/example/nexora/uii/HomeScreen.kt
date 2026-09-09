package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    tasks: SnapshotStateList<PremiumTask>,
    goals: SnapshotStateList<NexoraGoal>,
    progressHistory: List<DailyProgress>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    topPattern: String? = null,
    proactiveInsight: AiRecommendation? = null,
    proactiveSignals: List<AiProactiveSignal> = emptyList(),
    proposedAction: AiAction? = null,
    onApproveAction: (AiAction) -> Unit = {},
    onDismissAction: () -> Unit = {}
) {
    val greeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val completed = remember(tasks) { tasks.count { it.completed } }
    val total = tasks.size
    val progress = remember(completed, total) { if (total == 0) 0f else completed.toFloat() / total }

    val priorityOrder = mapOf(
        TaskPriority.URGENT to 0,
        TaskPriority.HIGH to 1,
        TaskPriority.MEDIUM to 2,
        TaskPriority.LOW to 3
    )

    val focusTasks = remember(tasks) {
        tasks
            .filter { !it.completed }
            .sortedBy { priorityOrder[it.priority] ?: 2 }
            .take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexoraBackground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // GREETING
        item {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    color = NexoraPrimaryText
                )
                Text(
                    text = "Here's what matters today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NexoraMutedText
                )
            }
        }

        // AI ACTIONS / SIGNALS
        if (proposedAction != null) {
            item {
                ProposedActionCard(
                    action = proposedAction,
                    onConfirm = { onApproveAction(proposedAction) },
                    onDismiss = onDismissAction
                )
            }
        } else if (proactiveSignals.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Nexora noticed",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraPrimaryText
                    )
                    proactiveSignals.take(1).forEach { signal ->
                        HomeProactiveCard(signal = signal, onAction = { onApproveAction(it) })
                    }
                }
            }
        }

        // TODAY'S FOCUS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NexoraSectionHeader(
                    title = "Today's focus",
                    actionLabel = if (tasks.isNotEmpty()) "View all" else null,
                    onAction = { /* Navigate to tasks */ }
                )
                
                if (focusTasks.isEmpty()) {
                    EmptyFocusCard(onAddTask = onAddTask)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        focusTasks.forEach { task ->
                            HomeTaskCard(task = task, onToggle = { onToggleTask(task) })
                        }
                    }
                }
            }
        }

        // PROGRESS SUMMARY
        item {
            NexoraCard(containerColor = NexoraSoftGreen, border = null) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraPrimaryText
                        )
                        Text(
                            text = "$completed of $total tasks completed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoraMutedText
                        )
                    }
                    
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(56.dp),
                            color = NexoraPrimaryGreen,
                            trackColor = NexoraBorder,
                            strokeWidth = 6.dp,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoraPrimaryGreen
                        )
                    }
                }
            }
        }

        // RECENT GOALS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NexoraSectionHeader(title = "Goals")
                
                if (goals.isEmpty()) {
                    Text(
                        text = "Give Nexora something meaningful to work toward.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraMutedText
                    )
                } else {
                    Text(
                        text = "You are working toward ${goals.size} active goals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraMutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTaskCard(task: PremiumTask, onToggle: () -> Unit) {
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
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.completed) NexoraMutedText else NexoraPrimaryText
                )
                Text(
                    text = "${task.category} • ${task.duration}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraMutedText
                )
            }

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

@Composable
private fun EmptyFocusCard(onAddTask: () -> Unit) {
    NexoraCard(border = BorderStroke(1.dp, NexoraBorder.copy(alpha = 0.5f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Clear space.",
                style = MaterialTheme.typography.titleMedium,
                color = NexoraMutedText
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddTask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraPrimaryGreen,
                    contentColor = Color.White
                ),
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add task", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
