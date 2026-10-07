package com.example.nexora.uii

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

// ============================================================
// PREMIUM INTERACTION MODIFIER
// ============================================================

fun Modifier.nexoraClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = NexoraMotion.QuickSpec,
        label = "pressScale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Custom scale replaces default ripple for premium feel
            enabled = enabled,
            onClick = onClick
        )
}

// ============================================================
// BASE COMPONENTS
// ============================================================

enum class NexoraCardTier { Resting, Elevated }

@Composable
fun NexoraCard(
    modifier: Modifier = Modifier,
    tier: NexoraCardTier = NexoraCardTier.Resting,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = NexoraShapes.large,
        color = containerColor,
        border = BorderStroke(
            width = if (tier == NexoraCardTier.Elevated) 1.dp else 0.5.dp,
            color = if (tier == NexoraCardTier.Elevated) Clay60.copy(alpha = 0.3f) else NexoraBorder
        ),
        shadowElevation = if (tier == NexoraCardTier.Elevated) 4.dp else 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun IconBox(
    icon: ImageVector,
    containerColor: Color = Green95,
    contentColor: Color = Green40,
    size: Int = 32
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(NexoraShapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size((size * 0.6).dp)
        )
    }
}

@Composable
fun NexoraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Green10
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Green60,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun AiSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = NexoraShapes.large,
        color = Clay90,
        border = BorderStroke(0.5.dp, Clay60.copy(alpha = 0.4f)),
        content = { Column(modifier = Modifier.padding(20.dp)) { content() } }
    )
}

// ============================================================
// AI COMPONENTS
// ============================================================

@Composable
fun ProposedActionCard(
    action: AiAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NexoraShapes.extraLarge,
        color = Green10,
        border = BorderStroke(1.dp, Clay60.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Clay60)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "NEXORA INTELLIGENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Clay60,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val confirmLabel = when (action.type) {
                AiActionType.CREATE_TASK -> "Create Task"
                AiActionType.COMPLETE_TASK -> "Complete Task"
                AiActionType.DELETE_TASK -> "Delete Task"
                AiActionType.UPDATE_TASK -> "Update Task"
                AiActionType.CREATE_GOAL -> "Create Goal"
                AiActionType.UPDATE_GOAL -> "Update Goal"
                AiActionType.DELETE_GOAL -> "Delete Goal"
                else -> "Confirm Action"
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = Clay60, contentColor = Green10),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 13.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(confirmLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 13.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ProactiveSignalCard(
    signal: AiProactiveSignal,
    onAction: (AiAction) -> Unit
) {
    NexoraCard(tier = NexoraCardTier.Resting) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBox(
                icon = when (signal.type) {
                    ProactiveSignalType.OVERLOAD -> Icons.Rounded.Speed
                    ProactiveSignalType.NEGLECTED_GOAL -> Icons.Rounded.Flag
                    else -> Icons.Rounded.Insights
                },
                containerColor = Clay90,
                contentColor = Clay40
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signal.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Green10
                )
                Text(
                    text = signal.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green40,
                    lineHeight = 18.sp
                )
                signal.suggestedAction?.let { action ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Clay40,
                        modifier = Modifier.clickable { onAction(action) },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WorkflowCard(workflow: AgentWorkflow) {
    NexoraCard(containerColor = Green10) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "EXECUTING WORKFLOW",
                style = MaterialTheme.typography.labelSmall,
                color = Green60,
                letterSpacing = 1.5.sp
            )
            Text(
                text = workflow.objective,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            workflow.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (step.status) {
                        StepStatus.COMPLETED -> Green60
                        StepStatus.FAILED -> NexoraError
                        else -> Green40
                    }
                    val statusIcon = when (step.status) {
                        StepStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        StepStatus.FAILED -> Icons.Rounded.Error
                        else -> Icons.Rounded.Circle
                    }
                    
                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.status == StepStatus.PENDING) Green40 else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AiRecommendationCard(
    recommendation: AiRecommendation,
    onAction: (AiRecommendation) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (recommendation.type) {
        AiRecommendationType.NEXT_TASK -> Icons.Rounded.CheckCircle
        AiRecommendationType.GOAL_ACTION -> Icons.Rounded.Flag
        AiRecommendationType.WARNING -> Icons.Rounded.Warning
        AiRecommendationType.PRODUCTIVITY_INSIGHT -> Icons.Rounded.Insights
        else -> Icons.Rounded.AutoAwesome
    }

    NexoraCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(icon, Green95, Green60, size = 28)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Green10
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Green40,
                lineHeight = 20.sp
            )

            if (recommendation.evidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                ReasoningList(factors = recommendation.evidence)
            }

            if (!recommendation.actionLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = { onAction(recommendation) },
                    shape = NexoraShapes.small,
                    color = Green95
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recommendation.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Green60,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Green60,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PredictiveBadge(
    prediction: AiPrediction,
    modifier: Modifier = Modifier
) {
    val containerColor = when (prediction.riskLevel) {
        AiPriority.CRITICAL -> NexoraError.copy(alpha = 0.15f)
        AiPriority.HIGH -> Clay60.copy(alpha = 0.15f)
        else -> Green95
    }
    val contentColor = when (prediction.riskLevel) {
        AiPriority.CRITICAL -> NexoraError
        AiPriority.HIGH -> Clay40
        else -> Green60
    }

    Surface(
        modifier = modifier,
        shape = NexoraShapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Insights,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = prediction.prediction,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PredictiveCard(
    prediction: AiPrediction,
    modifier: Modifier = Modifier
) {
    NexoraCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(Icons.Rounded.Insights, Green95, Green60, size = 28)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = prediction.targetTitle ?: "Predictive Intelligence",
                        style = MaterialTheme.typography.titleSmall,
                        color = Green10
                    )
                    Text(
                        text = "Confidence: ${prediction.confidence.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Green40
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = prediction.prediction,
                style = MaterialTheme.typography.bodyMedium,
                color = Green10,
                fontWeight = FontWeight.Medium
            )
            if (prediction.evidence.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = prediction.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = Green40,
                    lineHeight = 16.sp
                )
            }
            if (prediction.contributingFactors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ReasoningList(factors = prediction.contributingFactors)
            }
        }
    }
}

@Composable
fun ReasoningList(factors: List<ReasoningFactor>) {
    if (factors.isEmpty()) return
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "WHY THIS CHOICE?",
            style = MaterialTheme.typography.labelSmall,
            color = Gray90,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        factors.forEach { factor ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                val icon = when (factor.impact) {
                    ReasoningImpact.POSITIVE -> Icons.Rounded.CheckCircle
                    ReasoningImpact.CRITICAL -> Icons.Rounded.PriorityHigh
                    ReasoningImpact.NEGATIVE -> Icons.Rounded.RemoveCircle
                    ReasoningImpact.NEUTRAL -> Icons.Rounded.Info
                }
                val color = when (factor.impact) {
                    ReasoningImpact.POSITIVE -> Green60
                    ReasoningImpact.CRITICAL -> NexoraError
                    ReasoningImpact.NEGATIVE -> Clay40
                    ReasoningImpact.NEUTRAL -> Gray90
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = factor.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = Green40,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ============================================================
// ACTION RESULT BANNER
// ============================================================

@Composable
fun ActionResultBanner(
    result: AiActionResult,
    executionRecord: AiExecutionRecord? = null,
    onDismiss: () -> Unit
) {
    val isSuccess = executionRecord?.isCompleteSuccess ?: result.success
    val isPartial = executionRecord?.isPartial == true
    val isCancelled = executionRecord?.isCancelled == true
    val isRejected = executionRecord?.isRejected == true

    val backgroundColor = when {
        isSuccess -> Green95
        isPartial -> Clay90.copy(alpha = 0.35f)
        isCancelled -> Color.LightGray.copy(alpha = 0.2f)
        else -> NexoraError.copy(alpha = 0.08f)
    }

    val borderColor = when {
        isSuccess -> Green80
        isPartial -> Clay40.copy(alpha = 0.5f)
        isCancelled -> Color.Gray.copy(alpha = 0.3f)
        else -> NexoraError.copy(alpha = 0.3f)
    }

    val primaryTint = when {
        isSuccess -> Green60
        isPartial -> Clay40
        isCancelled -> Color.DarkGray
        else -> NexoraError
    }

    val headerIcon = when {
        isSuccess -> Icons.Rounded.CheckCircle
        isPartial -> Icons.Rounded.Warning
        isCancelled -> Icons.Rounded.Cancel
        else -> Icons.Rounded.Error
    }

    val headerTitle = when {
        isSuccess -> if ((executionRecord?.actionExecutions?.size ?: 0) > 1) "Completed plan" else result.message
        isPartial -> "Partially completed"
        isCancelled -> "Plan cancelled"
        isRejected -> "Execution rejected"
        else -> if ((executionRecord?.actionExecutions?.size ?: 0) > 1) "Plan failed" else result.message
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .nexoraClickable { onDismiss() },
        shape = NexoraShapes.medium,
        color = backgroundColor,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = primaryTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Green10,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.Close, null, tint = Green40, modifier = Modifier.size(14.dp))
            }

            // Compact transparent breakdown for multi-action plan or detailed records
            if (executionRecord != null && executionRecord.actionExecutions.isNotEmpty()) {
                val lines = executionRecord.actionExecutions.sortedBy { it.executionOrder }
                if (lines.size > 1 || !isSuccess) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.padding(start = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lines.forEach { child ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (childIcon, childTint) = when (child.status) {
                                    ActionExecutionStatus.SUCCESS -> Pair(Icons.Rounded.Check, Green60)
                                    ActionExecutionStatus.SKIPPED -> Pair(Icons.Rounded.Redo, Clay40)
                                    ActionExecutionStatus.FAILED -> Pair(Icons.Rounded.Close, NexoraError)
                                }
                                Icon(
                                    imageVector = childIcon,
                                    contentDescription = null,
                                    tint = childTint,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                val reasonSuffix = when {
                                    child.status == ActionExecutionStatus.SKIPPED -> " — ${child.error ?: "prerequisite failed"}"
                                    child.status == ActionExecutionStatus.FAILED -> " — ${child.error ?: child.failureReason ?: child.message}"
                                    else -> ""
                                }
                                Text(
                                    text = "${child.actionTitle}$reasonSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Green20,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

