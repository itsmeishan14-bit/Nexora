package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.*

private val Ink = Color(0xFF17231C)
private val Green = Color(0xFF78A982)
private val SoftGreen = Color(0xFFE4EFE5)
private val Muted = Color(0xFF747B75)
private val Border = Color(0xFFE1E5E1)

@Composable
fun ProposedActionCard(
    action: AiAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Ink),
        border = BorderStroke(2.dp, Green)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI Proposed Action",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = action.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Green
            )
            
            Text(
                text = action.description,
                fontSize = 14.sp,
                color = Color(0xFFB8C1BA),
                lineHeight = 20.sp
            )
            
            action.reason?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reason: $it",
                    fontSize = 12.sp,
                    color = Color(0xFF747B75),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Ink),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Approve", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF354439), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
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
        ProactiveSignalType.NEGLECTED_GOAL -> Icons.Default.Lightbulb
        ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> Icons.Default.Warning
        else -> Icons.Default.AutoAwesome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = signal.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = signal.message,
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 18.sp
            )

            signal.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = action.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green,
                    modifier = Modifier.clickable { onAction(action) }
                )
            }
        }
    }
}

@Composable
fun HomeLegacyInsightCard(insight: AiRecommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGreen),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = insight.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = insight.message,
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun WorkflowCard(
    workflow: AgentWorkflow
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Ink),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "AI Workflow Progress",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Objective: ${workflow.objective}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green
            )

            Spacer(modifier = Modifier.height(16.dp))

            workflow.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stepIcon = when (step.status) {
                        StepStatus.COMPLETED -> Icons.Default.CheckCircle
                        StepStatus.FAILED -> Icons.Default.Warning
                        else -> Icons.Default.RadioButtonUnchecked
                    }
                    val stepColor = when (step.status) {
                        StepStatus.COMPLETED -> Green
                        StepStatus.FAILED -> Color(0xFFE57373)
                        else -> Muted
                    }

                    Icon(
                        imageVector = stepIcon,
                        contentDescription = null,
                        tint = stepColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = step.description,
                        fontSize = 13.sp,
                        color = if (step.status == StepStatus.PENDING) Color.Gray else Color.White
                    )
                }
            }

            if (workflow.status == WorkflowStatus.EXECUTING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Green,
                    trackColor = Color(0xFF354439)
                )
            }

            workflow.failureReason?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Failure: $it",
                    fontSize = 12.sp,
                    color = Color(0xFFE57373),
                    fontWeight = FontWeight.Medium
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
        ProactiveSignalType.NEGLECTED_GOAL -> Icons.Default.Lightbulb
        ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> Icons.Default.Warning
        ProactiveSignalType.PRODUCTIVITY_DROP -> Icons.Default.Warning
        else -> Icons.Default.AutoAwesome
    }

    val backgroundColor = when (signal.severity) {
        AiPriority.CRITICAL -> Color(0xFFFFF4F2)
        AiPriority.HIGH -> Color(0xFFFFF9E6)
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column {
                    Text(
                        text = signal.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    
                    Text(
                        text = signal.severity.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Muted,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = signal.message,
                fontSize = 14.sp,
                color = Ink,
                lineHeight = 21.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Evidence: ${signal.evidence}",
                fontSize = 12.sp,
                color = Muted,
                fontWeight = FontWeight.Medium
            )
            
            signal.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(action.title, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
