package com.example.nexora.uii

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

private enum class GoalTabFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    ACHIEVED("Achieved")
}

@Composable
fun GoalScreen(
    goals: List<NexoraGoal>,
    personalContext: AiPersonalContext? = null,
    onAddGoal: () -> Unit,
    onEditGoal: (NexoraGoal) -> Unit,
    onOpenGoal: (NexoraGoal) -> Unit
) {
    val activeGoals = remember(goals) { goals.filter { it.progress < 1f } }
    val completedGoals = remember(goals) { goals.filter { it.progress >= 1f } }
    var selectedFilter by remember { mutableStateOf(GoalTabFilter.ALL) }

    val filteredGoals = remember(goals, selectedFilter) {
        when (selectedFilter) {
            GoalTabFilter.ALL -> goals
            GoalTabFilter.ACTIVE -> activeGoals
            GoalTabFilter.ACHIEVED -> completedGoals
        }
    }

    val overallAvgProgress = remember(goals) {
        if (goals.isEmpty()) 0
        else (goals.map { it.progress }.average() * 100).toInt()
    }

    val atRiskCount = remember(goals, personalContext) {
        personalContext?.goalHealth?.count { it.state == GoalHealthState.AT_RISK } ?: 0
    }
    val needsAttentionCount = remember(goals, personalContext) {
        personalContext?.goalHealth?.count { it.state == GoalHealthState.NEEDS_ATTENTION } ?: 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Objective",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // EDITORIAL SCREEN HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Objectives",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = when {
                                goals.isEmpty() -> "Define what matters to you."
                                activeGoals.isEmpty() -> "All milestones achieved."
                                activeGoals.size == 1 -> "1 active objective · $overallAvgProgress% overall"
                                else -> "${activeGoals.size} active objectives · $overallAvgProgress% overall"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Target badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${goals.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // EXECUTIVE STRATEGY SUMMARY CARD (Apple ring + metrics)
            if (goals.isNotEmpty()) {
                item {
                    AppleStrategicSummaryCard(
                        totalCount = goals.size,
                        activeCount = activeGoals.size,
                        completedCount = completedGoals.size,
                        avgProgress = overallAvgProgress,
                        atRiskCount = atRiskCount,
                        needsAttentionCount = needsAttentionCount
                    )
                }
            }

            // APPLE SEGMENTED CAPSULE FILTER
            if (goals.isNotEmpty()) {
                item {
                    AppleGoalSegmentedFilter(
                        selectedFilter = selectedFilter,
                        totalCount = goals.size,
                        activeCount = activeGoals.size,
                        completedCount = completedGoals.size,
                        onSelectFilter = { selectedFilter = it }
                    )
                }
            }

            // EMPTY STATE
            if (goals.isEmpty()) {
                item {
                    AppleGoalEmptyState(onAddGoal = onAddGoal)
                }
            } else if (filteredGoals.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No ${selectedFilter.label.lowercase()} objectives",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Switch tabs or create a new objective to see it here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // GOAL CARDS
                items(
                    items = filteredGoals,
                    key = { "apple_goal_${it.id}" }
                ) { goal ->
                    val health = personalContext?.goalHealth?.find { it.goalId == goal.id }
                    AppleGoalRowCard(
                        goal = goal,
                        healthState = health?.state,
                        onOpen = { onOpenGoal(goal) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// APPLE STRATEGIC SUMMARY CARD
// ─────────────────────────────────────────────

@Composable
private fun AppleStrategicSummaryCard(
    totalCount: Int,
    activeCount: Int,
    completedCount: Int,
    avgProgress: Int,
    atRiskCount: Int,
    needsAttentionCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular progress gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                val progressFraction = (avgProgress / 100f).coerceIn(0f, 1f)

                androidx.compose.foundation.Canvas(modifier = Modifier.size(68.dp)) {
                    drawArc(
                        color = outlineColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progressFraction,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$avgProgress%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Right side breakdown
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GoalMetricItem(
                        label = "Active",
                        value = "$activeCount",
                        color = MaterialTheme.colorScheme.primary
                    )
                    GoalMetricItem(
                        label = "Achieved",
                        value = "$completedCount",
                        color = NexoraTheme.customColors.priorityLow
                    )
                    GoalMetricItem(
                        label = "Total",
                        value = "$totalCount",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // AI Health Summary Banner
                val healthBadgeColor = when {
                    atRiskCount > 0 -> MaterialTheme.colorScheme.error
                    needsAttentionCount > 0 -> NexoraTheme.customColors.priorityHigh
                    else -> NexoraTheme.customColors.priorityLow
                }
                val healthBadgeText = when {
                    atRiskCount > 0 -> "$atRiskCount objective at risk"
                    needsAttentionCount > 0 -> "$needsAttentionCount needs focus"
                    else -> "All objectives on schedule"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(healthBadgeColor.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(healthBadgeColor)
                    )
                    Text(
                        text = healthBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = healthBadgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────
// APPLE SEGMENTED CAPSULE FILTER
// ─────────────────────────────────────────────

@Composable
private fun AppleGoalSegmentedFilter(
    selectedFilter: GoalTabFilter,
    totalCount: Int,
    activeCount: Int,
    completedCount: Int,
    onSelectFilter: (GoalTabFilter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(3.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GoalTabFilter.values().forEach { tab ->
                val isSelected = selectedFilter == tab
                val count = when (tab) {
                    GoalTabFilter.ALL -> totalCount
                    GoalTabFilter.ACTIVE -> activeCount
                    GoalTabFilter.ACHIEVED -> completedCount
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectFilter(tab) },
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    border = if (isSelected) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// APPLE GOAL ROW CARD
// ─────────────────────────────────────────────

@Composable
private fun AppleGoalRowCard(
    goal: NexoraGoal,
    healthState: GoalHealthState? = null,
    onOpen: () -> Unit
) {
    val progressPercent = (goal.progress * 100).toInt()
    val isCompleted = goal.progress >= 1f

    val trackColor = when {
        isCompleted -> NexoraTheme.customColors.priorityLow
        healthState == GoalHealthState.AT_RISK -> MaterialTheme.colorScheme.error
        healthState == GoalHealthState.NEEDS_ATTENTION -> NexoraTheme.customColors.priorityHigh
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpen() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top row: category pill + progress text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (goal.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = goal.category.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Achieved",
                            tint = NexoraTheme.customColors.priorityLow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) NexoraTheme.customColors.priorityLow else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Title
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = trackColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                strokeCap = StrokeCap.Round
            )

            // Bottom metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Target date or Health status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (goal.targetDate.isNotBlank() && goal.targetDate != "No date") {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = goal.targetDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (healthState != null && !isCompleted) {
                        val healthColor = when (healthState) {
                            GoalHealthState.HEALTHY -> NexoraTheme.customColors.priorityLow
                            GoalHealthState.NEEDS_ATTENTION -> NexoraTheme.customColors.priorityHigh
                            GoalHealthState.AT_RISK -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val healthText = when (healthState) {
                            GoalHealthState.HEALTHY -> "On track"
                            GoalHealthState.NEEDS_ATTENTION -> "Needs attention"
                            GoalHealthState.AT_RISK -> "At risk"
                            else -> ""
                        }

                        if (healthText.isNotBlank()) {
                            Text(text = "·", color = MaterialTheme.colorScheme.outline)
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(healthColor)
                            )
                            Text(
                                text = healthText,
                                style = MaterialTheme.typography.labelSmall,
                                color = healthColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Subtle chevron navigation affordance
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// APPLE GOAL EMPTY STATE
// ─────────────────────────────────────────────

@Composable
private fun AppleGoalEmptyState(onAddGoal: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Define Your First Objective",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create milestones to link your daily tasks to long-term progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Button(
                onClick = onAddGoal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "New Objective",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
