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
import androidx.compose.material.icons.automirrored.rounded.Redo
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
    val borderColor = if (tier == NexoraCardTier.Elevated) {
        NexoraTheme.customColors.aiBorder.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier,
        shape = NexoraShapes.large,
        color = containerColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = borderColor
        ),
        shadowElevation = if (tier == NexoraCardTier.Elevated) 2.dp else 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun IconBox(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
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
            modifier = Modifier.size((size * 0.58).dp)
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
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
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
        color = NexoraTheme.customColors.aiSurface,
        border = BorderStroke(0.5.dp, NexoraTheme.customColors.aiBorder),
        content = { Column(modifier = Modifier.padding(20.dp)) { content() } }
    )
}

// ============================================================
// AI COMPONENTS
// ============================================================

@Composable
fun ProposedActionCard(
    action: AiAction,
    plan: List<AiAction> = emptyList(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMultiAction = plan.size > 1
    val customColors = NexoraTheme.customColors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NexoraShapes.extraLarge,
        color = customColors.surfaceElevated,
        border = BorderStroke(0.5.dp, customColors.aiBorder)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(customColors.aiAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMultiAction) "NEXORA MULTI-ACTION PLAN (${plan.size} ACTIONS)" else "NEXORA INTELLIGENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = customColors.aiAccent,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isMultiAction) {
                Text(
                    text = "Proposed Execution Plan",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                plan.forEachIndexed { index, planItem ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}. ${planItem.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (planItem.description.isNotBlank() && planItem.description != planItem.title) {
                            Text(
                                text = planItem.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            val confirmLabel = if (isMultiAction) {
                "Confirm Plan (${plan.size} Actions)"
            } else {
                when (action.type) {
                    AiActionType.CREATE_TASK -> "Create Task"
                    AiActionType.COMPLETE_TASK -> "Complete Task"
                    AiActionType.DELETE_TASK -> "Delete Task"
                    AiActionType.UPDATE_TASK -> "Update Task"
                    AiActionType.CREATE_GOAL -> "Create Goal"
                    AiActionType.UPDATE_GOAL -> "Update Goal"
                    AiActionType.DELETE_GOAL -> "Delete Goal"
                    else -> "Confirm Action"
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(confirmLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 12.dp),
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
    val customColors = NexoraTheme.customColors
    NexoraCard(tier = NexoraCardTier.Resting) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBox(
                icon = when (signal.type) {
                    ProactiveSignalType.OVERLOAD -> Icons.Rounded.Speed
                    ProactiveSignalType.NEGLECTED_GOAL -> Icons.Rounded.Flag
                    else -> Icons.Rounded.Insights
                },
                containerColor = customColors.aiSurface,
                contentColor = customColors.aiAccent
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signal.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = signal.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                signal.suggestedAction?.let { action ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onAction(action) },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun WorkflowCard(workflow: AgentWorkflow) {
    NexoraCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "EXECUTING WORKFLOW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = workflow.objective,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(18.dp))

            workflow.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (step.status) {
                        StepStatus.COMPLETED -> NexoraTheme.customColors.success
                        StepStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val statusIcon = when (step.status) {
                        StepStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        StepStatus.FAILED -> Icons.Rounded.Error
                        else -> Icons.Rounded.Circle
                    }
                    
                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.status == StepStatus.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(
                    icon = icon,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 28
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            if (recommendation.evidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ReasoningList(factors = recommendation.evidence)
            }

            if (!recommendation.actionLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    onClick = { onAction(recommendation) },
                    shape = NexoraShapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recommendation.actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
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
        AiPriority.CRITICAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        AiPriority.HIGH -> NexoraTheme.customColors.warning.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (prediction.riskLevel) {
        AiPriority.CRITICAL -> MaterialTheme.colorScheme.error
        AiPriority.HIGH -> NexoraTheme.customColors.warning
        else -> MaterialTheme.colorScheme.primary
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
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(
                    icon = Icons.Rounded.Insights,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 28
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = prediction.targetTitle ?: "Predictive Intelligence",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Confidence: ${prediction.confidence.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = prediction.prediction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (prediction.evidence.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = prediction.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        factors.forEach { factor ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                val icon = when (factor.impact) {
                    ReasoningImpact.POSITIVE -> Icons.Rounded.CheckCircle
                    ReasoningImpact.CRITICAL -> Icons.Rounded.PriorityHigh
                    ReasoningImpact.NEGATIVE -> Icons.Rounded.RemoveCircle
                    ReasoningImpact.NEUTRAL -> Icons.Rounded.Info
                }
                val color = when (factor.impact) {
                    ReasoningImpact.POSITIVE -> NexoraTheme.customColors.success
                    ReasoningImpact.CRITICAL -> MaterialTheme.colorScheme.error
                    ReasoningImpact.NEGATIVE -> NexoraTheme.customColors.warning
                    ReasoningImpact.NEUTRAL -> MaterialTheme.colorScheme.outline
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = factor.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        isSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isPartial -> NexoraTheme.customColors.warning.copy(alpha = 0.12f)
        isCancelled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }

    val borderColor = when {
        isSuccess -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isPartial -> NexoraTheme.customColors.warning.copy(alpha = 0.3f)
        isCancelled -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
    }

    val primaryTint = when {
        isSuccess -> MaterialTheme.colorScheme.primary
        isPartial -> NexoraTheme.customColors.warning
        isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
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
                Spacer(Modifier.width(10.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (executionRecord != null && executionRecord.actionExecutions.isNotEmpty()) {
                val lines = executionRecord.actionExecutions.sortedBy { it.executionOrder }
                if (lines.size > 1 || !isSuccess) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.padding(start = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lines.forEach { child ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (childIcon, childTint) = when (child.status) {
                                    ActionExecutionStatus.SUCCESS -> Pair(Icons.Rounded.Check, NexoraTheme.customColors.success)
                                    ActionExecutionStatus.SKIPPED -> Pair(Icons.AutoMirrored.Rounded.Redo, NexoraTheme.customColors.warning)
                                    ActionExecutionStatus.FAILED -> Pair(Icons.Rounded.Close, MaterialTheme.colorScheme.error)
                                }
                                Icon(
                                    imageVector = childIcon,
                                    contentDescription = null,
                                    tint = childTint,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                val reasonSuffix = when {
                                    child.status == ActionExecutionStatus.SKIPPED -> " — ${child.error ?: "prerequisite failed"}"
                                    child.status == ActionExecutionStatus.FAILED -> " — ${child.error ?: child.failureReason ?: child.message}"
                                    else -> ""
                                }
                                Text(
                                    text = "${child.actionTitle}$reasonSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

