package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFFF7F8F4)
private val Ink = Color(0xFF17231C)
private val Muted = Color(0xFF747B75)
private val Green = Color(0xFF78A982)
private val SoftGreen = Color(0xFFE4EFE5)
private val Border = Color(0xFFE1E5E1)

data class PremiumTask(
    val title: String,
    val category: String,
    val duration: String,
    val goalTitle: String? = null,
    var completed: Boolean = false
)

@Composable
fun TasksScreen(
    tasks: SnapshotStateList<PremiumTask>,
    onAddTask: () -> Unit,
    onToggleTask: (PremiumTask) -> Unit
){
    val completed = tasks.count { it.completed }
    val total = tasks.size

    val progress = if (total == 0) {
        0f
    } else {
        completed.toFloat() / total
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp,
                top = 26.dp,
                end = 22.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Tasks",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Focus on what matters today.",
                            fontSize = 15.sp,
                            color = Muted
                        )
                    }

                    IconButton(
                        onClick = onAddTask
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add task",
                            tint = Ink
                        )
                    }
                }
            }

            // Progress card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Ink
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            "TODAY'S PROGRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = Color(0xFFB8C1BA)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.size(12.dp))

                            Text(
                                "$completed of $total completed",
                                fontSize = 13.sp,
                                color = Color(0xFFB8C1BA),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = Green,
                            trackColor = Color(0xFF3B453F)
                        )
                    }
                }
            }

            // Section title
            item {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Your tasks",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            // Empty state
            if (tasks.isEmpty()) {

                item {
                    EmptyTasksCard()
                }

            } else {

                // Tasks
                items(
                    items = tasks,
                    key = { task ->
                        task.title + task.category + task.duration
                    }
                ) { task ->

                    TaskCard(
                        task = task,

                        onToggle = {
                            onToggleTask(task)
                        },

                        onDelete = {
                            tasks.remove(task)
                        }
                    )
                }
            }
        }

        // Floating button
        FloatingActionButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp),
            containerColor = Ink,
            contentColor = Color.White
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add task"
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: PremiumTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.completed) Green else Border
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (task.completed) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.completed) Muted else Ink
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    buildString {
                        append(task.category)
                        append(" • ")
                        append(task.duration)

                        if (task.goalTitle != null) {
                            append(" • ")
                            append(task.goalTitle)
                        }
                    },
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete task",
                    tint = Color(0xFF9A9F9B)
                )
            }
        }
    }
}

@Composable
private fun EmptyTasksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(SoftGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "No tasks yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                "Add a task and start making progress.",
                fontSize = 13.sp,
                color = Muted
            )
        }
    }
}