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
    goals: List<NexoraGoal>,
    personalContext: AiPersonalContext? = null,
    onAddGoal: () -> Unit,
    onEditGoal: (NexoraGoal) -> Unit,
    onOpenGoal: (NexoraGoal) -> Unit
) {
    val activeGoals = remember(goals.toList()) { goals.filter { it.progress < 1f } }
    val completedGoals = remember(goals.toList()) { goals.filter { it.progress >= 1f } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = Green10,
                contentColor = Color.White,
                shape = NexoraShapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // SCREEN HEADER
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Goals",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Green10
                    )
                    Text(
                        text = when {
                            activeGoals.isEmpty() && completedGoals.isEmpty() -> "Define what matters to you."
                            activeGoals.isEmpty() -> "All objectives achieved."
                            activeGoals.size == 1 -> "1 active objective"
                            else -> "${activeGoals.size} active objectives"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green40
                    )
                }
            }

            // EMPTY STATE
            if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
                item {
                    PremiumGoalEmptyState(onAddGoal = onAddGoal)
                }
            }

            // ACTIVE GOALS
            if (activeGoals.isNotEmpty()) {
                item {
                    SectionDivider(label = "ACTIVE OBJECTIVES")
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

            // COMPLETED GOALS
            if (completedGoals.isNotEmpty()) {
                item {
                    SectionDivider(label = "ACHIEVED")
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

// ─────────────────────────────────────────────
// GOAL CARD
// ─────────────────────────────────────────────

@Composable
private fun GoalCard(
    goal: NexoraGoal,
    healthState: GoalHealthState? = null,
    onOpen: () -> Unit
) {
    val progressPercent = (goal.progress * 100).toInt()
    val isCompleted = goal.progress >= 1f

    val trackColor = when (healthState) {
        GoalHealthState.AT_RISK -> NexoraError
        GoalHealthState.NEEDS_ATTENTION -> NexoraWarning
        else -> Green60
    }

    NexoraCard(
        modifier = Modifier.nexoraClickable { onOpen() }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Category + progress percent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    if (goal.category.isNotBlank()) {
                        Text(
                            text = goal.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCompleted) Green40 else Green60,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCompleted) Green40 else Green10
                    )
                }

                Text(
                    text = "$progressPercent%",
                    style = NumericStyle.copy(fontSize = 22.sp, color = if (isCompleted) Green60 else Green60)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = if (isCompleted) Green60 else trackColor,
                trackColor = NexoraBorder,
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(14.dp))

            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Health indicator or date
                if (healthState != null && !isCompleted) {
                    val healthColor = when (healthState) {
                        GoalHealthState.HEALTHY -> Green60
                        GoalHealthState.NEEDS_ATTENTION -> NexoraWarning
                        GoalHealthState.AT_RISK -> NexoraError
                        else -> Green40
                    }
                    val healthLabel = when (healthState) {
                        GoalHealthState.HEALTHY -> "On track"
                        GoalHealthState.NEEDS_ATTENTION -> "Needs attention"
                        GoalHealthState.AT_RISK -> "At risk"
                        else -> ""
                    }
                    if (healthLabel.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(healthColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = healthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = healthColor
                            )
                        }
                    } else {
                        GoalDateChip(goal.targetDate)
                    }
                } else {
                    GoalDateChip(goal.targetDate)
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = NexoraBorder,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GoalDateChip(targetDate: String) {
    if (targetDate.isBlank() || targetDate == "No date") return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Event,
            null,
            modifier = Modifier.size(12.dp),
            tint = Green40
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = targetDate,
            style = MaterialTheme.typography.labelSmall,
            color = Green40
        )
    }
}

// ─────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────

@Composable
private fun PremiumGoalEmptyState(onAddGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Define what matters.",
            style = MaterialTheme.typography.titleMedium,
            color = Green40
        )
        Text(
            text = "Give Nexora something meaningful to work toward.",
            style = MaterialTheme.typography.bodyMedium,
            color = NexoraMutedTextLight,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onAddGoal,
            colors = ButtonDefaults.buttonColors(containerColor = Green10),
            shape = NexoraShapes.medium,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Define a Goal", style = MaterialTheme.typography.labelLarge)
        }
    }
}
