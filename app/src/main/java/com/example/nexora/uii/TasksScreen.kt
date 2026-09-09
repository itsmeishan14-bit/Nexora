package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

@Composable
fun TasksScreen(
    tasks: SnapshotStateList<PremiumTask>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit,
    onAiAction: () -> Unit = {},
    onDeleteTask: (PremiumTask) -> Unit = { task ->
        tasks.remove(task)
    }
) {
    val incompleteTasks = remember(tasks) { tasks.filter { !it.completed } }
    val completedTasks = remember(tasks) { tasks.filter { it.completed } }

    Scaffold(
        containerColor = NexoraBackground,
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.headlineLarge,
                        color = NexoraPrimaryText
                    )
                    IconButton(
                        onClick = onAiAction,
                        modifier = Modifier
                            .clip(NexoraShapes.small)
                            .background(NexoraSoftGreen)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Help",
                            tint = NexoraPrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // INCOMPLETE SECTION
            if (incompleteTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    EmptyTasksState(onAddTask = onAddTask)
                }
            } else if (incompleteTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraPrimaryText
                    )
                }
                items(
                    items = incompleteTasks,
                    key = { "task_${it.id}" }
                ) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }

            // COMPLETED SECTION
            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraMutedText
                    )
                }
                items(
                    items = completedTasks,
                    key = { "completed_${it.id}" }
                ) { task ->
                    TaskCard(
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
private fun TaskCard(
    task: PremiumTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
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
                    text = buildString {
                        append(task.category)
                        append(" • ")
                        append(task.duration)
                        task.goalTitle?.let { append(" • "); append(it) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraMutedText
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = NexoraMutedText.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTasksState(onAddTask: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Everything is organized.",
            style = MaterialTheme.typography.titleMedium,
            color = NexoraMutedText
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onAddTask) {
            Text("Create your first task", color = NexoraPrimaryGreen)
        }
    }
}
