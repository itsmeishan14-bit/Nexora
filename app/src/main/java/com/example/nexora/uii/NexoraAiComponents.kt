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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
            width = if (tier == NexoraCardTier.Elevated) 1.5.dp else 1.dp,
            color = if (tier == NexoraCardTier.Elevated) Clay60.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
        ),
        shadowElevation = if (tier == NexoraCardTier.Elevated) 8.dp else 0.dp,
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
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NexoraPrimaryTextLight
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
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
        border = BorderStroke(1.5.dp, Clay60.copy(alpha = 0.5f)),
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
    val infiniteTransition = rememberInfiniteTransition(label = "aiPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Clay60.copy(alpha = pulseAlpha), Color.Transparent),
                        center = center,
                        radius = size.width
                    )
                )
            },
        shape = NexoraShapes.extraLarge,
        color = Green10,
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Clay60, Green40)))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(
                    icon = Icons.Rounded.AutoAwesome,
                    containerColor = Clay60,
                    contentColor = Green10,
                    size = 36
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Nexora Intelligence",
                    style = MaterialTheme.typography.labelLarge,
                    color = Clay60,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = Clay60, contentColor = Green10),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("Approve Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                    shape = NexoraShapes.medium,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("Not now", style = MaterialTheme.typography.labelLarge)
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
