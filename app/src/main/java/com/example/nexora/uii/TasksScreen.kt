package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PremiumTask(
    val title: String,
    val category: String,
    val duration: String,
    var completed: Boolean = false
)

@Composable
fun TasksScreen() {

    val tasks = remember {
        mutableStateListOf(
            PremiumTask(
                title = "Complete Java assignment",
                category = "Academic",
                duration = "30 minutes",
                completed = true
            ),
            PremiumTask(
                title = "Study Operating Systems",
                category = "Academic",
                duration = "45 minutes"
            ),
            PremiumTask(
                title = "Build Nexora",
                category = "Personal project",
                duration = "60 minutes"
            ),
            PremiumTask(
                title = "Read 20 pages",
                category = "Personal",
                duration = "25 minutes"
            )
        )
    }

    val completedCount = tasks.count { it.completed }
    val progress = if (tasks.isEmpty()) 0f
    else completedCount.toFloat() / tasks.size.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8F5))
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Tasks",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF18201B)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Stay focused. Get things done.",
                    fontSize = 15.sp,
                    color = Color(0xFF737873)
                )

                Spacer(modifier = Modifier.height(22.dp))

                ProgressCard(
                    completed = completedCount,
                    total = tasks.size,
                    progress = progress
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "Today's tasks",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF18201B),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$completedCount/${tasks.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6FA477)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            items(
                items = tasks,
                key = { it.title }
            ) { task ->

                TaskCard(
                    task = task,
                    onClick = {
                        val index = tasks.indexOf(task)

                        if (index >= 0) {
                            tasks[index] = task.copy(
                                completed = !task.completed
                            )
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                tasks.add(
                    PremiumTask(
                        title = "New task",
                        category = "Personal",
                        duration = "30 minutes"
                    )
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp),
            containerColor = Color(0xFF1E2722),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add task"
            )
        }
    }
}

@Composable
private fun ProgressCard(
    completed: Int,
    total: Int,
    progress: Float
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2722)
        )
    ) {

        Column(
            modifier = Modifier.padding(22.dp)
        ) {

            Text(
                text = "TODAY'S PROGRESS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB9C0BA)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {

                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "$completed of $total tasks completed",
                    fontSize = 14.sp,
                    color = Color(0xFFB9C0BA),
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF424A45))
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF8FC596))
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: PremiumTask,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
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
                .padding(
                    horizontal = 18.dp,
                    vertical = 17.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.completed)
                            Color(0xFF82B889)
                        else
                            Color(0xFFE7EAE7)
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (task.completed) {

                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(15.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.completed)
                        Color(0xFF747A75)
                    else
                        Color(0xFF18201B)
                )

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = task.category,
                        fontSize = 13.sp,
                        color = Color(0xFF7A807B)
                    )

                    Text(
                        text = "  •  ",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B5B1)
                    )

                    Text(
                        text = task.duration,
                        fontSize = 13.sp,
                        color = Color(0xFF7A807B)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open task",
                tint = Color(0xFF9BA19D),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}