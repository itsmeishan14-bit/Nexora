package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nexora.ai.AiPersonalContext
import com.example.nexora.ai.GoalHealthState
import com.example.nexora.ui.theme.*

@Composable
fun GoalScreen(
    goals: SnapshotStateList<NexoraGoal>,
    personalContext: AiPersonalContext? = null,
    onAddGoal: () -> Unit,
    onEditGoal: (NexoraGoal) -> Unit,
    onOpenGoal: (NexoraGoal) -> Unit
) {
    val activeGoals = remember(goals.toList()) { goals.filter { it.progress < 1f } }
    val completedGoals = remember(goals.toList()) { goals.filter { it.progress >= 1f } }

    Scaffold(
        containerColor = NexoraBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = NexoraPrimaryText,
                contentColor = Color.White,
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add goal")
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
            item {
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NexoraPrimaryText
                )
            }

            if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
                item {
                    EmptyGoalsState(onAddGoal = onAddGoal)
                }
            } else if (activeGoals.isNotEmpty()) {
                item {
                    Text(
                        text = "Strategic focus",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraPrimaryText
                    )
                }
                items(
                    items = activeGoals,
                    key = { "goal_${it.id}" }
                ) { goal ->
                    val health = personalContext?.goalHealth?.find { it.goalId == goal.id }
                    GoalCard(
                        goal = goal,
                        healthState = health?.state,
                        onOpen = { onOpenGoal(goal) }
                    )
                }
            }

            if (completedGoals.isNotEmpty()) {
                item {
                    Text(
                        text = "Achieved",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoraMutedText
                    )
                }
                items(
                    items = completedGoals,
                    key = { "completed_goal_${it.id}" }
                ) { goal ->
                    GoalCard(
                        goal = goal,
                        onOpen = { onOpenGoal(goal) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: NexoraGoal,
    healthState: GoalHealthState? = null,
    onOpen: () -> Unit
) {
    val healthColor = when (healthState) {
        GoalHealthState.HEALTHY -> NexoraPrimaryGreen
        GoalHealthState.NEEDS_ATTENTION -> NexoraWarning
        GoalHealthState.AT_RISK -> NexoraError
        else -> NexoraMutedText
    }

    NexoraCard {
        Column(
            modifier = Modifier
                .clickable { onOpen() }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(NexoraShapes.small)
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = NexoraPrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = NexoraPrimaryText
                    )
                    Text(
                        text = "${goal.category} • ${goal.targetDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexoraMutedText
                    )
                }

                Text(
                    text = "${(goal.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraPrimaryGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = NexoraPrimaryGreen,
                trackColor = NexoraBorder,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            if (healthState != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(healthColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = healthState.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = healthColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGoalsState(onAddGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Aim high.",
            style = MaterialTheme.typography.titleMedium,
            color = NexoraMutedText
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onAddGoal) {
            Text("Create your first goal", color = NexoraPrimaryGreen)
        }
    }
}
