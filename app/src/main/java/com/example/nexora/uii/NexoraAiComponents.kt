package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

// ============================================================
// BASE COMPONENTS
// ============================================================

@Composable
fun NexoraCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    border: BorderStroke? = BorderStroke(1.dp, NexoraBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = NexoraShapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
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
            color = NexoraPrimaryText
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = NexoraPrimaryGreen,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
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
    NexoraCard(
        containerColor = NexoraPrimaryText,
        border = BorderStroke(1.dp, NexoraPrimaryGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(NexoraShapes.small)
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NexoraPrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Nexora suggests",
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraSoftGreen
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraMutedText,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraPrimaryGreen,
                        contentColor = Color.White
                    ),
                    shape = NexoraShapes.medium
                ) {
                    Text("Approve", style = MaterialTheme.typography.labelLarge)
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    shape = NexoraShapes.medium
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun HomeProactiveCard(
    signal: AiProactiveSignal,
    onAction: (AiAction) -> Unit
) {
    val icon = when (signal.type) {
        ProactiveSignalType.OVERLOAD -> Icons.Default.Warning
        ProactiveSignalType.NEGLECTED_GOAL -> Icons.Default.Flag
        ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> Icons.Default.PriorityHigh
        else -> Icons.Default.AutoAwesome
    }

    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NexoraPrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = signal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraPrimaryText
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = signal.message,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraMutedText,
                lineHeight = 18.sp
            )

            signal.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraPrimaryGreen,
                    modifier = Modifier.clickable { onAction(action) }
                )
            }
        }
    }
}

@Composable
fun WorkflowCard(
    workflow: AgentWorkflow
) {
    NexoraCard(
        containerColor = NexoraPrimaryText,
        border = BorderStroke(1.dp, NexoraPrimaryGreen.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Nexora is working",
                style = MaterialTheme.typography.labelSmall,
                color = NexoraPrimaryGreen,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))

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
                    val (icon, color) = when (step.status) {
                        StepStatus.COMPLETED -> Icons.Default.Check to NexoraPrimaryGreen
                        StepStatus.FAILED -> Icons.Default.Close to NexoraError
                        else -> Icons.Default.HorizontalRule to NexoraMutedText
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.status == StepStatus.PENDING) NexoraMutedText else Color.White
                    )
                }
            }

            if (workflow.status == WorkflowStatus.EXECUTING) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = NexoraPrimaryGreen,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun ProactiveSignalCard(
    signal: AiProactiveSignal,
    onAction: (AiAction) -> Unit
) {
    val icon = when (signal.type) {
        ProactiveSignalType.OVERLOAD -> Icons.Default.Warning
        ProactiveSignalType.NEGLECTED_GOAL -> Icons.Default.Flag
        ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> Icons.Default.PriorityHigh
        else -> Icons.Default.AutoAwesome
    }

    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NexoraPrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = signal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraPrimaryText
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = signal.message,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraMutedText,
                lineHeight = 18.sp
            )

            signal.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraPrimaryGreen),
                    shape = NexoraShapes.medium
                ) {
                    Text(action.title, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

