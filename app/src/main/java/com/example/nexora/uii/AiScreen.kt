package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexora.ai.AiMemoryItem
import com.example.nexora.ai.AiAction
import com.example.nexora.ai.AiActionExecutor
import com.example.nexora.ai.AiActionResult
import com.example.nexora.ai.AiActionType
import com.example.nexora.ai.AiPriority
import com.example.nexora.ai.AiRecommendation
import com.example.nexora.ai.AiRecommendationType
import com.example.nexora.ai.NexoraAiEngine
import com.example.nexora.ai.NexoraAiViewModel
import com.example.nexora.ai.NexoraChatMessage
import com.example.nexora.ai.NexoraDailyPlan
import com.example.nexora.ai.PlannedTask
import com.example.nexora.ai.AiProductivityPattern
import com.example.nexora.ai.AiPatternType
import com.example.nexora.ai.AiProactiveSignal
import com.example.nexora.ai.ProactiveSignalType
import com.example.nexora.ai.AgentWorkflow
import com.example.nexora.ai.AgentWorkflowStep
import com.example.nexora.ai.WorkflowStatus
import com.example.nexora.ai.StepStatus
import com.example.nexora.uii.TaskPriority

private val NexoraBackground = Color(0xFFF7F8F4)
private val NexoraInk = Color(0xFF17231C)
private val NexoraGreen = Color(0xFF78A982)
private val NexoraSoftGreen = Color(0xFFE4EFE5)
private val NexoraMuted = Color(0xFF747B75)
private val NexoraBorder = Color(0xFFE1E5E1)

@Composable
fun AiScreen(
    engine: NexoraAiEngine,
    onOpenGoalDecomposer: () -> Unit,
    onRecommendationAction: (AiRecommendation) -> Unit,
    onTaskAction: (Long) -> Unit = {}
) {

    val viewModel: NexoraAiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {

                return NexoraAiViewModel(
                    engine = engine
                ) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size + 10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexoraBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // ====================================================
                // HEADER
                // ====================================================

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(NexoraSoftGreen),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Nexora AI",
                            tint = NexoraGreen
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(14.dp)
                    )

                    Column {

                        Text(
                            text = "Nexora Intelligence",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexoraInk
                        )

                        Text(
                            text = "Understand. Prioritize. Improve.",
                            fontSize = 14.sp,
                            color = NexoraMuted
                        )
                    }
                }
            }

            uiState.lastActionResult?.let { result ->
                item {
                    ActionResultCard(
                        result = result,
                        onDismiss = { viewModel.dismissResult() }
                    )
                }
            }

            uiState.proposedAction?.let { action ->
                item {
                    ProposedActionCard(
                        action = action,
                        onConfirm = { viewModel.confirmAction() },
                        onDismiss = { viewModel.dismissAction() }
                    )
                }
            }

            uiState.currentWorkflow?.let { workflow ->
                item {
                    WorkflowCard(workflow = workflow)
                }
            }

            item {
                // ====================================================
                // ACTION CARDS
                // ====================================================

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NexoraInk
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = "Unified Intelligence",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "Nexora analyzes your tasks and goals to provide personalized recommendations.",
                            fontSize = 14.sp,
                            color = Color(0xFFB8C1BA),
                            lineHeight = 21.sp
                        )

                        Spacer(
                            modifier = Modifier.height(18.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            Button(
                                onClick = {
                                    viewModel.analyze()
                                },
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NexoraGreen,
                                    contentColor = NexoraInk
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Analyze", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    viewModel.createDailyPlan()
                                },
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = NexoraInk
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Daily plan", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                // ====================================================
                // GOAL DECOMPOSER
                // ====================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, NexoraBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = NexoraGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Goal Decomposer",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexoraInk
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onOpenGoalDecomposer() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexoraSoftGreen,
                                contentColor = NexoraInk
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Open Decomposer", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ====================================================
            // DAILY PLAN
            // ====================================================

            uiState.dailyPlan?.let { plan ->
                item {
                    Text(
                        text = "Today's Plan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, NexoraBorder)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = plan.summary,
                                fontSize = 14.sp,
                                color = NexoraMuted,
                                lineHeight = 21.sp
                            )
                            
                            if (plan.totalDurationMinutes > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = NexoraSoftGreen,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Estimated: ${plan.totalDurationMinutes} min",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NexoraGreen
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = plan.tasks,
                    key = { "plan_${it.task.id}_${it.recommendedOrder}" }
                ) { plannedTask ->
                    PlannedTaskCard(
                        plannedTask = plannedTask,
                        onClick = { onTaskAction(plannedTask.task.id) }
                    )
                }
            }

            // ====================================================
            // PROACTIVE SIGNALS (2.0)
            // ====================================================

            if (uiState.proactiveSignals.isNotEmpty()) {
                item {
                    Text(
                        text = "What Nexora Noticed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = uiState.proactiveSignals,
                    key = { "signal_${it.fingerprint}" }
                ) { signal ->
                    ProactiveSignalCard(
                        signal = signal,
                        onAction = { action: AiAction ->
                            viewModel.proposeAction(action)
                        }
                    )
                }
            }

            // ====================================================
            // PROACTIVE INSIGHTS (Legacy Recommendations)
            // ====================================================

            if (uiState.proactiveInsights.isNotEmpty() && uiState.proactiveSignals.isEmpty()) {
                item {
                    Text(
                        text = "Nexora Intelligence",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = uiState.proactiveInsights,
                    key = { "proactive_${it.title}_${it.type}" }
                ) { insight ->
                    ProactiveInsightCard(
                        insight = insight,
                        onAction = { rec ->
                            val action = recommendationToAction(rec)
                            if (action != null) {
                                viewModel.proposeAction(action)
                            } else {
                                onRecommendationAction(rec)
                            }
                        }
                    )
                }
            }

            // ====================================================
            // PRODUCTIVITY INTELLIGENCE & MEMORY
            // ====================================================

            if (uiState.memory.items.isNotEmpty() || uiState.memory.legacyPatterns.isNotEmpty()) {
                item {
                    Text(
                        text = "Productivity Intelligence",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = uiState.memory.items,
                    key = { "mem_${it.id}" }
                ) { memoryItem ->
                    AiMemoryCard(memoryItem)
                }

                items(
                    items = uiState.memory.legacyPatterns,
                    key = { "pattern_${it.type}_${it.title}" }
                ) { pattern ->
                    AiPatternCard(pattern)
                }
            }

            // ====================================================
            // RECOMMENDATIONS
            // ====================================================

            if (uiState.recommendations.isNotEmpty()) {
                item {
                    Text(
                        text = "Current Recommendations",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = uiState.recommendations,
                    key = { "rec_${it.title}_${it.type}" }
                ) { recommendation ->
                    AiRecommendationCard(
                        recommendation = recommendation,
                        onAction = { rec ->
                            val action = recommendationToAction(rec)
                            if (action != null) {
                                viewModel.proposeAction(action)
                            } else {
                                onRecommendationAction(rec)
                            }
                        }
                    )
                }
            }

            // ====================================================
            // CHAT MESSAGES
            // ====================================================

            if (uiState.chatMessages.isNotEmpty()) {
                item {
                    Text(
                        text = "Conversation",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = uiState.chatMessages,
                    key = { it.id }
                ) { message ->
                    ChatMessageBubble(message)
                }
            }

            if (uiState.isChatLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NexoraGreen,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // ====================================================
            // ERROR
            // ====================================================

            uiState.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF4F2)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE8D3CF))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFF9A5B50)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = error, fontSize = 14.sp, color = NexoraInk)
                        }
                    }
                }
            }

            // ====================================================
            // EMPTY CHAT SUGGESTIONS
            // ====================================================

            if (uiState.chatMessages.isEmpty() && !uiState.isChatLoading) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = "Ask Nexora",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexoraMuted
                        )
                        
                        val suggestions = listOf(
                            "Plan my day",
                            "What should I work on next?",
                            "Review my goals",
                            "I have too many tasks",
                            "Break down a goal"
                        )
                        
                        suggestions.forEach { suggestion ->
                            SuggestionChip(suggestion) {
                                viewModel.sendMessage(suggestion)
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ====================================================
        // INPUT AREA
        // ====================================================

        ChatInput(
            onSend = { viewModel.sendMessage(it) },
            enabled = !uiState.isChatLoading
        )
    }
}

private fun recommendationToAction(recommendation: AiRecommendation): AiAction? {
    return when (recommendation.type) {
        AiRecommendationType.NEXT_TASK -> {
            recommendation.relatedTaskId?.let { taskId ->
                AiAction(
                    type = AiActionType.COMPLETE_TASK,
                    title = "Complete Task",
                    description = "Mark this task as finished?",
                    reason = recommendation.message,
                    taskId = taskId
                )
            }
        }
        AiRecommendationType.WARNING -> {
            if (recommendation.title.contains("workload")) {
                 AiAction(
                    type = AiActionType.RESCHEDULE_TASK,
                    title = "Reschedule Low Priority Tasks",
                    description = "Move 3 low-priority tasks to tomorrow to reduce overload?",
                    reason = recommendation.message
                )
            } else null
        }
        else -> null
    }
}

@Composable
fun ProactiveInsightCard(
    insight: AiRecommendation,
    onAction: (AiRecommendation) -> Unit
) {
    val icon = when (insight.type) {
        AiRecommendationType.WARNING -> Icons.Default.Warning
        AiRecommendationType.GOAL_ACTION -> Icons.Default.Lightbulb
        AiRecommendationType.PRODUCTIVITY_INSIGHT -> Icons.Default.AutoAwesome
        else -> Icons.Default.Lightbulb
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column {
                    Text(
                        text = insight.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk
                    )
                    
                    val priorityColor = when (insight.priority) {
                        com.example.nexora.ai.AiPriority.CRITICAL -> Color(0xFFE57373)
                        com.example.nexora.ai.AiPriority.HIGH -> Color(0xFFF0AD4E)
                        else -> NexoraGreen
                    }
                    
                    Text(
                        text = insight.priority.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = priorityColor,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = insight.message,
                fontSize = 14.sp,
                color = NexoraInk,
                lineHeight = 21.sp
            )
            
            insight.evidence?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Evidence: $it",
                    fontSize = 12.sp,
                    color = NexoraMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (insight.actionLabel != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { onAction(insight) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraInk,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(insight.actionLabel, fontWeight = FontWeight.Bold)
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
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column {
                    Text(
                        text = signal.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk
                    )
                    
                    Text(
                        text = signal.severity.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraMuted,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = signal.message,
                fontSize = 14.sp,
                color = NexoraInk,
                lineHeight = 21.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Evidence: ${signal.evidence}",
                fontSize = 12.sp,
                color = NexoraMuted,
                fontWeight = FontWeight.Medium
            )
            
            signal.suggestedAction?.let { action ->
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraInk,
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

@Composable
fun ProposedActionCard(
    action: AiAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NexoraInk),
        border = BorderStroke(2.dp, NexoraGreen)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NexoraGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
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
                color = NexoraGreen
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
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraGreen, contentColor = NexoraInk),
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
fun ActionResultCard(
    result: AiActionResult,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) NexoraSoftGreen else Color(0xFFFFF4F2)
        ),
        border = BorderStroke(1.dp, if (result.success) NexoraGreen else Color(0xFFE8D3CF))
    ) {
        Row(
            modifier = Modifier.padding(18.dp).clickable { onDismiss() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (result.success) Icons.Default.TaskAlt else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result.success) NexoraGreen else Color(0xFF9A5B50)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = result.message,
                fontSize = 14.sp,
                color = NexoraInk,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SuggestionChip(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 14.sp,
            color = NexoraInk,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChatMessageBubble(message: NexoraChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isFromUser) 20.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) NexoraInk else Color.White
            ),
            border = if (message.isFromUser) null else BorderStroke(1.dp, NexoraBorder)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                fontSize = 15.sp,
                color = if (message.isFromUser) Color.White else NexoraInk,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = Color.White,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(WindowInsets.ime.asPaddingValues())
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Nexora something...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NexoraSoftGreen,
                    unfocusedContainerColor = NexoraSoftGreen,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank() && enabled) {
                        onSend(text)
                        text = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }),
                enabled = enabled
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (text.isNotBlank() && enabled) NexoraGreen else NexoraSoftGreen)
                    .clickable(enabled = text.isNotBlank() && enabled) {
                        onSend(text)
                        text = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank() && enabled) NexoraInk else NexoraMuted
                )
            }
        }
    }
}

@Composable
fun PlannedTaskCard(
    plannedTask: PlannedTask,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NexoraSoftGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = plannedTask.recommendedOrder.toString(),
                    fontWeight = FontWeight.Bold,
                    color = NexoraGreen
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plannedTask.task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
                
                Text(
                    text = plannedTask.reason,
                    fontSize = 13.sp,
                    color = NexoraMuted
                )

                if (plannedTask.task.duration.isNotBlank() || plannedTask.task.goalTitle != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (plannedTask.task.duration.isNotBlank()) {
                            Text(
                                text = plannedTask.task.duration,
                                fontSize = 11.sp,
                                color = NexoraMuted,
                                modifier = Modifier
                                    .background(Color(0xFFF1F3EF), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (plannedTask.task.goalTitle != null) {
                            Text(
                                text = plannedTask.task.goalTitle,
                                fontSize = 11.sp,
                                color = NexoraGreen,
                                modifier = Modifier
                                    .background(NexoraSoftGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                tint = if (plannedTask.task.priority == com.example.nexora.uii.TaskPriority.URGENT) Color(0xFFE57373) else NexoraBorder,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AiMemoryCard(item: AiMemoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.content,
                fontSize = 14.sp,
                color = NexoraMuted,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = NexoraSoftGreen.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.category.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraGreen
                    )
                }
            }
        }
    }
}

@Composable
fun AiPatternCard(pattern: AiProductivityPattern) {
    val icon = when (pattern.type) {
        AiPatternType.WORKLOAD_CONSISTENCY -> Icons.Default.Warning
        AiPatternType.FOCUS_TREND -> Icons.Default.AutoAwesome
        AiPatternType.COMPLETION_ACCURACY -> Icons.Default.Lightbulb
        else -> Icons.Default.AutoAwesome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NexoraBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = pattern.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = pattern.description,
                fontSize = 14.sp,
                color = NexoraMuted,
                lineHeight = 20.sp
            )
            
            pattern.recommendation?.let { rec ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = rec,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexoraGreen
                )
            }
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
        colors = CardDefaults.cardColors(containerColor = NexoraInk),
        border = BorderStroke(1.dp, NexoraGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NexoraGreen,
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
                color = NexoraGreen
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Steps Progress
            workflow.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stepIcon = when (step.status) {
                        StepStatus.COMPLETED -> Icons.Default.TaskAlt
                        StepStatus.FAILED -> Icons.Default.Warning
                        else -> Icons.Default.AutoAwesome
                    }
                    val stepColor = when (step.status) {
                        StepStatus.COMPLETED -> NexoraGreen
                        StepStatus.FAILED -> Color(0xFFE57373)
                        else -> NexoraMuted
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
                    color = NexoraGreen,
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
private fun AiRecommendationCard(
    recommendation: AiRecommendation,
    onAction: (AiRecommendation) -> Unit
) {

    val icon = when (recommendation.type) {

        AiRecommendationType.NEXT_TASK ->
            Icons.Default.TaskAlt

        AiRecommendationType.GOAL_ACTION ->
            Icons.Default.Lightbulb

        AiRecommendationType.WARNING ->
            Icons.Default.Warning

        else ->
            Icons.Default.AutoAwesome
    }

    val isActionable = 
        recommendation.relatedTaskId != null || 
        recommendation.relatedGoalId != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            NexoraBorder
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Text(
                    text = recommendation.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = recommendation.message,
                fontSize = 14.sp,
                color = NexoraMuted,
                lineHeight = 21.sp
            )

            if (isActionable && recommendation.actionLabel != null) {

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    text = recommendation.actionLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexoraGreen,
                    modifier = Modifier.clickable {
                        onAction(recommendation)
                    }
                )
            }
        }
    }
}
