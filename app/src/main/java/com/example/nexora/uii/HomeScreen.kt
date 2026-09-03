package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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

@Composable
fun HomeScreen(
    tasks: SnapshotStateList<PremiumTask>,
    onAddTask: () -> Unit
) {
    val completed = tasks.count { it.completed }
    val total = tasks.size
    val progress = if (total == 0) 0f else completed.toFloat() / total

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 28.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "TUESDAY • SEPTEMBER 1",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = Muted
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        Text(
                            text = "Good evening 👋",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Let's make today count.",
                            fontSize = 15.sp,
                            color = Muted
                        )
                    }

                    IconButton(
                        onClick = onAddTask
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = Ink
                        )
                    }
                }
            }

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Ink
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "TODAY'S PROGRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = Color(0xFFB8C1BA)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {

                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 43.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.size(12.dp))

                            Text(
                                text = "$completed of $total tasks completed",
                                fontSize = 13.sp,
                                color = Color(0xFFB8C1BA),
                                modifier = Modifier.padding(bottom = 7.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

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

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "Today's focus",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onAddTask
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add task",
                            tint = Green
                        )
                    }
                }
            }

            items(
                items = tasks,
                key = { it.title }
            ) { task ->

                TaskRow(
                    task = task,
                    onToggle = {

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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SoftGreen
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {

                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Green,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.size(12.dp))

                            Text(
                                text = "Nexora suggests",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Study Operating Systems for 45 minutes.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Ink
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "A focused session is your best next move.",
                            fontSize = 13.sp,
                            color = Muted
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "Start focus",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green
                            )

                            Spacer(modifier = Modifier.size(5.dp))

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Green,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            item {

                Column {

                    Text(
                        text = "A THOUGHT FOR TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = Muted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Consistency beats intensity.",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: PremiumTask,
    onToggle: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onToggle()
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(
                    if (task.completed) {
                        Green
                    } else {
                        Border
                    }
                ),
            contentAlignment = Alignment.Center
        ) {

            if (task.completed) {

                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (task.completed) Muted else Ink
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${task.category} • ${task.duration}",
                fontSize = 12.sp,
                color = Muted
            )
        }

        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = Color(0xFFB0B6B1),
            modifier = Modifier.size(18.dp)
        )
    }
}