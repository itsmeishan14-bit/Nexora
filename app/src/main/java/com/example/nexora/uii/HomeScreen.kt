package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import java.time.LocalDate

private val Background = Color(0xFFF7F8F4)
private val Ink = Color(0xFF17231C)
private val Muted = Color(0xFF747B75)
private val Green = Color(0xFF78A982)
private val SoftGreen = Color(0xFFE4EFE5)
private val Border = Color(0xFFE1E5E1)

@Composable
fun HomeScreen(
    tasks: SnapshotStateList<PremiumTask>,
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

    val completed = tasks.count { it.completed }
    val total = tasks.size

    val progress = if (total == 0) 0f else completed.toFloat() / total

    val priorityOrder = mapOf(
        TaskPriority.URGENT to 0,
        TaskPriority.HIGH to 1,
        TaskPriority.MEDIUM to 2,
        TaskPriority.LOW to 3
    )

    val focusTasks = tasks
        .sortedWith(
            compareBy<PremiumTask> { if (it.completed) 1 else 0 }
                .thenBy { priorityOrder[it.priority] ?: 2 }
        )
        .take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(26.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Good day.", fontSize = 14.sp, color = Muted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Your day, organized.", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                IconButton(onClick = onAddTask) {
                    Icon(imageVector = Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = Ink)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(text = "TODAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Color(0xFFB8C1BA))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(text = "${(progress * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(text = "$completed of $total tasks completed", fontSize = 13.sp, color = Color(0xFFB8C1BA), modifier = Modifier.padding(bottom = 7.dp))
                    }
                    Spacer(modifier = Modifier.height(17.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Green, trackColor = Color(0xFF3B453F))
                }
            }
        }

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
                Text(text = "Nexora noticed", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 8.dp))
            }
            items(items = proactiveSignals.take(2)) { signal ->
                HomeProactiveCard(signal = signal, onAction = { onApproveAction(it) })
            }
        } else if (proactiveInsight != null) {
            item {
                HomeLegacyInsightCard(insight = proactiveInsight)
            }
        } else if (topPattern != null) {
             item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = topPattern, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                    }
                }
             }
        }

        item {
            Text(text = "Today's focus", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        if (tasks.isEmpty()) {
            item { EmptyHomeCard(onAddTask = onAddTask) }
        } else {
            items(items = focusTasks) { task ->
                HomeTaskCard(task = task, onToggle = { onToggleTask(task) })
            }
        }

        item {
            Text(text = "Your consistency", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Your progress over time", fontSize = 13.sp, color = Muted)
        }

        item {
            DailyProgressCalendar(progressHistory = progressHistory)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Plan your next task", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = "Keep your momentum going.", fontSize = 12.sp, color = Muted)
                    }
                    IconButton(onClick = onAddTask) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add task", tint = Ink)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HomeTaskCard(task: PremiumTask, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(if (task.completed) Green else Border), contentAlignment = Alignment.Center) {
                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    if (task.completed) Icon(imageVector = Icons.Default.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(modifier = Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (task.completed) Muted else Ink)
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = buildString { append(task.category); append(" • "); append(task.duration); task.goalTitle?.let { append(" • "); append(it) } }, fontSize = 11.sp, color = Muted)
            }
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = if (task.completed) Green else Border, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun EmptyHomeCard(onAddTask: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(SoftGreen), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Green, modifier = Modifier.size(25.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Nothing planned yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(modifier = Modifier.height(5.dp))
            Text(text = "Add your first task for today.", fontSize = 13.sp, color = Muted)
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(onClick = onAddTask) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add task", tint = Green)
            }
        }
    }
}
