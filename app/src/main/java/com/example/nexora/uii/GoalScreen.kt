package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
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
        containerColor = NexoraBackgroundLight,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = Green10,
                contentColor = Color.White,
                shape = NexoraShapes.medium
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
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
                    color = Green10
                )
            }

            if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
                item {
                    EmptyGoalsState(onAddGoal = onAddGoal)
                }
            } else if (activeGoals.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Objectives",
                        style = MaterialTheme.typography.titleMedium,
                        color = Green10
                    )
                }
                items(
                    items = activeGoals,
                    key = { "goal_${it.id}" }
                ) { goal ->
                    val health = personalContext?.goalHealth?.find { it.goalId == goal.id }
                    GoalStrategyCard(
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
                        color = Green40
                    )
                }
                items(
                    items = completedGoals,
                    key = { "completed_goal_${it.id}" }
                ) { goal ->
                    GoalStrategyCard(
                        goal = goal,
                        onOpen = { onOpenGoal(goal) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalStrategyCard(
    goal: NexoraGoal,
    healthState: GoalHealthState? = null,
    onOpen: () -> Unit
) {
    val healthColor = when (healthState) {
        GoalHealthState.HEALTHY -> Green60
        GoalHealthState.NEEDS_ATTENTION -> NexoraWarning
        GoalHealthState.AT_RISK -> NexoraError
        else -> Green40
    }

    NexoraCard(
        modifier = Modifier.nexoraClickable { onOpen() }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Green60,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Green10
                    )
                    
                    if (healthState != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(healthColor))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = healthState.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = healthColor
                            )
                        }
                    }
                }
                
                Text(
                    text = "${(goal.progress * 100).toInt()}%",
                    style = NumericStyle.copy(fontSize = 24.sp, color = Green60)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (healthState == GoalHealthState.AT_RISK) NexoraError else Green60,
                trackColor = Gray95,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.Rounded.Event, Gray95, Green40, 24)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = goal.targetDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green40
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Gray90,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyGoalsState(onAddGoal: () -> Unit) {
    NexoraCard(modifier = Modifier.padding(top = 40.dp)) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Flag, null, tint = Gray95, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Give Nexora something meaningful to work toward.",
                style = MaterialTheme.typography.bodyLarge,
                color = Green40,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAddGoal,
                colors = ButtonDefaults.buttonColors(containerColor = Green60),
                shape = NexoraShapes.medium
            ) {
                Text("Define a Goal")
            }
        }
    }
}
