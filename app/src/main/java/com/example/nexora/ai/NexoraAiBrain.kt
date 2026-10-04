package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import java.time.LocalDate

/**
 * The unified AI Brain for Nexora.
 * Orchestrates language understanding, reasoning, analysis, and actions across all AI capabilities.
 */
class NexoraAiBrain(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService,
    private val providerManager: AiProviderManager,
    toolRegistry: AiToolRegistry,
    private val repository: NexoraRepository,
    val automationSystem: NexoraAutomationSystem
) {
    private val pipeline = AdvancedLocalLanguagePipeline()
    private val planner = AiPlanner()
    private val learningLoop = AiLearningLoop(repository)
    private val memoryRetriever = AiMemoryRetriever(repository)
    private val decisionGate = AiDecisionGate(repository)
    private val proactiveEngine = NexoraProactiveEngine()
    private val predictiveEngine = NexoraPredictiveEngine()
    
    // The Agent system is a multi-step capability of the Brain
    private val agent = NexoraAiAgent(toolRegistry, decisionGate, contextBuilder)

    private var lastContext: AiContext? = null
    private var lastContextBuiltAt: Long = 0
    private val contextCacheDuration = 1000 * 2 // 2 seconds for local responsiveness

    /**
     * Process a unified AI request.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        // Run outcome evaluation periodically or before analysis
        learningLoop.evaluateOutcomes()
        
        val context = getContext(request)
        val relevantMemory = memoryRetriever.retrieveRelevantMemory(request)
        val convContext = request.conversationContext ?: AiConversationContext()

        val response = if (request.type == AiRequestType.CHAT) {
            val message = request.userMessage ?: return AiResponse(AiResponseType.NO_ACTION, "Empty Message", "I didn't receive a message to process.")
            
            // 1. Unified Language Understanding
            val langResult = pipeline.process(message, context, convContext)

            // 2. Decide if multi-step Agent is genuinely required
            if (langResult.requiresMultiStepReasoning) {
                val agentResponse = agent.execute(request, context, relevantMemory)
                logResponseRecommendations(agentResponse)
                agentResponse
            } else {
                val brainResponse = handleStructuredChat(request, langResult, context, relevantMemory, convContext)
                logResponseRecommendations(brainResponse)
                brainResponse
            }
        } else {
            val brainResponse = when (request.type) {
                AiRequestType.CHAT -> handleChat(request, context, relevantMemory)
                AiRequestType.NEXT_TASK -> handleNextTask(context)
                AiRequestType.DAILY_PLAN -> handleDailyPlan(context, relevantMemory)
                AiRequestType.GOAL_ANALYSIS -> handleGoalAnalysis(context)
                AiRequestType.PRODUCTIVITY_ANALYSIS -> handleProductivityAnalysis(context)
                AiRequestType.PROACTIVE_ANALYSIS -> handleProactiveAnalysis(context)
                AiRequestType.GOAL_DECOMPOSITION -> handleGoalDecomposition(request, context)
                
                AiRequestType.CREATE_TASK -> executeDirectAction(AiActionType.CREATE_TASK, request.parameters)
                AiRequestType.UPDATE_TASK -> executeDirectAction(AiActionType.UPDATE_TASK, request.parameters, request.taskId)
                AiRequestType.COMPLETE_TASK -> executeDirectAction(AiActionType.COMPLETE_TASK, emptyMap(), request.taskId)
                AiRequestType.UPDATE_GOAL -> executeDirectAction(AiActionType.UPDATE_GOAL, request.parameters, goalId = request.goalId)
                AiRequestType.DELETE_TASK -> executeDirectAction(AiActionType.DELETE_TASK, emptyMap(), request.taskId)
                AiRequestType.DELETE_GOAL -> executeDirectAction(AiActionType.DELETE_GOAL, emptyMap(), goalId = request.goalId)
                
                AiRequestType.GENERAL_ANALYSIS -> handleGeneralAnalysis(context)
            }
            logResponseRecommendations(brainResponse)
            brainResponse
        }

        // Automation Rule Evaluation
        val automatedSignals = evaluateAutomations(request, context)
        
        return if (automatedSignals.isNotEmpty()) {
            response.copy(
                proactiveSignals = (response.proactiveSignals + automatedSignals).distinctBy { it.fingerprint }
            )
        } else {
            response
        }
    }

    fun invalidateContext() {
        lastContext = null
        lastContextBuiltAt = 0
    }

    fun getLastContext(): AiContext? = lastContext

    private suspend fun getContext(request: AiRequest): AiContext {
        val now = System.currentTimeMillis()
        
        // Cache bypass for writes or explicit refreshes
        val isWrite = request.type in listOf(
            AiRequestType.CREATE_TASK, AiRequestType.UPDATE_TASK, AiRequestType.COMPLETE_TASK,
            AiRequestType.UPDATE_GOAL, AiRequestType.DELETE_TASK, AiRequestType.DELETE_GOAL
        )
        
        if (!isWrite && lastContext != null && now - lastContextBuiltAt < contextCacheDuration) {
            return lastContext!!
        }
        
        val context = contextBuilder.build(request)
        lastContext = context
        lastContextBuiltAt = now
        return context
    }

    private suspend fun evaluateAutomations(request: AiRequest, context: AiContext): List<AiProactiveSignal> {
        val trigger = when (request.type) {
            AiRequestType.CREATE_TASK -> AutomationTriggerType.TASK_CREATED
            AiRequestType.COMPLETE_TASK -> AutomationTriggerType.TASK_COMPLETED
            AiRequestType.UPDATE_TASK -> AutomationTriggerType.TASK_UPDATED
            AiRequestType.UPDATE_GOAL -> AutomationTriggerType.GOAL_UPDATED
            AiRequestType.PROACTIVE_ANALYSIS, AiRequestType.GENERAL_ANALYSIS -> AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED
            else -> null
        } ?: return emptyList()

        return automationSystem.evaluateTriggers(trigger, context)
    }

    fun observeAutomationRules(): kotlinx.coroutines.flow.Flow<List<AiAutomationRule>> = automationSystem.observeRules()

    fun getAutomationRules(): List<AiAutomationRule> = automationSystem.getRules()

    suspend fun updateAutomationRule(rule: AiAutomationRule): Boolean = automationSystem.updateRule(rule)

    suspend fun addAutomationRule(rule: AiAutomationRule): Boolean = automationSystem.addRule(rule)

    suspend fun deleteAutomationRule(idOrName: String): Boolean = automationSystem.deleteRule(idOrName)

    suspend fun toggleAutomationRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? = automationSystem.toggleRule(idOrName, enabled)

    fun explainAutomationRun(query: String? = null): String = automationSystem.explainLastRun(query)

    /**
     * Handles natural language requests using the canonical structured interpretation.
     */
    private suspend fun handleStructuredChat(
        request: AiRequest,
        langResult: AiLanguageResult,
        context: AiContext,
        relevantMemory: List<AiMemoryItem>,
        convContext: AiConversationContext
    ): AiResponse {
        val rawMessage = request.userMessage ?: ""
        val lower = rawMessage.lowercase().trim()

        // 1. Clarification & Ambiguity Gate
        if (langResult.requiresClarification || langResult.intent == AiDecisionType.CLARIFY || langResult.intent == AiDecisionType.AMBIGUOUS) {
            val text = langResult.textResponse ?: langResult.clarificationNeeded?.question ?: "Could you please specify which task or goal you mean?"
            return AiResponse(
                responseType = AiResponseType.CLARIFICATION_NEEDED,
                title = "Clarification Needed",
                message = text,
                confidence = langResult.confidence,
                decision = AiDecision(
                    type = langResult.intent,
                    title = "Clarification Needed",
                    reason = text
                ),
                conversationContext = convContext.copy(
                    lastIntent = langResult.intent,
                    activeClarification = langResult.clarificationNeeded,
                    candidateIds = langResult.clarificationNeeded?.candidates ?: emptyList()
                )
            )
        }

        // 2. Cancellation
        if (langResult.isCancellation || langResult.intent == AiDecisionType.CANCEL) {
            val text = "Okay, I've cancelled that. What else can I help with?"
            return AiResponse(
                responseType = AiResponseType.NO_ACTION,
                title = "Action Cancelled",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.CANCEL, title = "Action Cancelled", reason = text),
                conversationContext = AiConversationContext()
            )
        }

        // 3. Confirmation Flow
        if (langResult.isConfirmation && convContext.pendingAction != null) {
            val action = convContext.pendingAction
            val authorizedParams = action.parameters.toMutableMap()
            authorizedParams["userConfirmed"] = true
            val authorizedAction = action.copy(requiresConfirmation = false, parameters = authorizedParams)
            
            invalidateContext()

            val confirmedDecisionType = mapActionTypeToDecision(action.type)

            return AiResponse(
                responseType = AiResponseType.ACTION_PROPOSAL,
                title = "Executing Confirmed Action",
                message = "Proceeding with ${action.title.lowercase()} as confirmed.",
                confidence = AiConfidence.HIGH,
                proposedActions = listOf(authorizedAction),
                relatedTaskId = action.taskId,
                relatedGoalId = action.goalId,
                decision = AiDecision(
                    type = confirmedDecisionType,
                    title = "Executing Confirmed Action",
                    reason = "User explicitly confirmed pending action.",
                    taskId = action.taskId,
                    goalId = action.goalId
                ),
                conversationContext = convContext.copy(
                    lastIntent = confirmedDecisionType,
                    lastTaskId = action.taskId ?: convContext.lastTaskId,
                    lastGoalId = action.goalId ?: convContext.lastGoalId,
                    pendingAction = null
                )
            )
        }

        // 4. Conceptual Explanations (NEVER Mutate!)
        if (langResult.intent == AiDecisionType.EXPLANATION) {
            val text = when {
                lower.contains("goal decomposition") || lower.contains("decompose") ->
                    "Goal decomposition is the process of breaking down high-level, complex objectives into smaller, manageable, and actionable milestones and sub-tasks. This eliminates ambiguity, reduces procrastination, and makes progress measurable."
                lower.contains("task prioritization") || lower.contains("prioritize") ->
                    "Task prioritization is the method of evaluating tasks by their urgency, impact, and alignment with your active goals to decide what to work on first, ensuring focus on highest-impact work."
                lower.contains("time blocking") ->
                    "Time blocking is a time management practice where you schedule specific, dedicated blocks of time for focused deep work on particular tasks, protecting focus from interruptions."
                lower.contains("task deletion") || lower.contains("delete a task") || lower.contains("how do i delete") ->
                    "Task deletion removes obsolete or unwanted tasks from your workspace. In Nexora, you can ask me to delete a specific task, and I will propose the deletion for your confirmation before removing it."
                lower.contains("goal deletion") || lower.contains("delete a goal") ->
                    "Goal deletion allows you to remove objectives that are no longer relevant, safely unlinking associated tasks while preserving execution history."
                lower.contains("carry forward") ->
                    "Carry forward represents unfinished tasks from previous days moving forward to today. Keeping carried tasks minimal preserves focus and momentum."
                else ->
                    "In Nexora, productivity intelligence combines task management, goal decomposition, and predictive analysis to help you execute your most important work consistently."
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Nexora Conceptual Explanation",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.EXPLANATION, title = "Conceptual Explanation", reason = text),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.EXPLANATION)
            )
        }

        // 5. Conversational Social Intents
        if (langResult.intent in listOf(AiDecisionType.GREETING, AiDecisionType.THANKS, AiDecisionType.GOODBYE, AiDecisionType.GENERAL_CONVERSATION)) {
            val text = when (langResult.intent) {
                AiDecisionType.GREETING -> "Hello! I'm Nexora, your Personal Intelligence Assistant. How can I help with your goals and tasks today?"
                AiDecisionType.THANKS -> "You're very welcome! Let me know whenever you'd like to plan, organize, or review your progress."
                AiDecisionType.GOODBYE -> "Goodbye! Stay focused, and I'll be here whenever you're ready to get back to work."
                else -> "I am Nexora, an AI assistant designed to manage your workload, break down goals into actionable steps, predict delays, and protect your focus."
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Nexora Assistant",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = langResult.intent, title = "Assistant", reason = text),
                conversationContext = convContext.copy(lastIntent = langResult.intent)
            )
        }

        // 6. Temporal Queries (Ground with Real Historical Records!)
        if (langResult.temporalRange != null && langResult.intent == AiDecisionType.SHOW_INSIGHT) {
            val range = langResult.temporalRange
            val responseText = when (range.scope) {
                TemporalScope.YESTERDAY -> {
                    if (lower.contains("carrying") || lower.contains("carried")) {
                        "You have ${context.carriedTasks} task(s) carried forward from yesterday. Resolving the highest-priority carried task first will help clear focus."
                    } else {
                        val record = context.history.find { it.date == range.startDate.toString() }
                        if (record == null) {
                            "Yesterday (${range.startDate}): No historical activity records found for this date."
                        } else {
                            val completed = record.tasksCompleted
                            val focus = record.focusMinutes
                            "Yesterday (${range.startDate}): According to your records, you had $completed completed tasks with $focus minutes of focused work."
                        }
                    }
                }
                TemporalScope.NEXT_WEEK -> {
                    "Next Week (${range.startDate} to ${range.endDate}): You have ${context.incompleteTasks.size} active tasks remaining across your goals."
                }
                TemporalScope.THIS_WEEK, TemporalScope.LAST_WEEK -> {
                    val weeklyRecords = context.history.filter {
                        val d = try { LocalDate.parse(it.date) } catch (_: Exception) { null }
                        d != null && !d.isBefore(range.startDate) && !d.isAfter(range.endDate)
                    }
                    val totalCompleted = weeklyRecords.sumOf { it.tasksCompleted } + (if (range.scope == TemporalScope.THIS_WEEK) context.tasksCompletedToday else 0)
                    val trend = context.personalContext.productivityTrend.name.lowercase()
                    "${range.label}: You completed $totalCompleted tasks, with ${context.incompleteTasks.size} active tasks remaining and a $trend productivity trend."
                }
                else -> {
                    "${range.label}: Records show ${context.tasksCompletedToday} tasks completed today with ${context.incompleteTasks.size} remaining."
                }
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Temporal Overview",
                message = responseText,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Temporal Overview", reason = responseText),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT)
            )
        }

        // 7. Predictions (Grounded, Truthful, Never Fabricated)
        if (langResult.intent in listOf(AiDecisionType.PREDICT_GOAL, AiDecisionType.PREDICT_TASK_RISK, AiDecisionType.PREDICT_WORKLOAD, AiDecisionType.PREDICT_PRODUCTIVITY)) {
            val predResponse = when (langResult.intent) {
                AiDecisionType.PREDICT_GOAL -> {
                    val queryTitle = langResult.entities["title"]?.toString() ?: ""
                    val goal = langResult.targetGoalId?.let { id -> context.goals.find { it.id == id } }
                        ?: if (queryTitle.isNotBlank()) context.goals.find { it.title.contains(queryTitle, ignoreCase = true) }
                           else context.activeGoals.firstOrNull()

                    if (goal == null) {
                        AiResponse(AiResponseType.INFORMATION, "Goal Prediction", if (queryTitle.isNotBlank()) "Goal not found matching \"$queryTitle\"." else "No active goals found to predict.")
                    } else {
                        val predictions = predictiveEngine.predictGoalRisksAndTimings(context)
                        val goalPred = predictions.find { it.targetId == goal.id }
                        val msg = if (goalPred != null && goalPred.prediction != "INSUFFICIENT_DATA") {
                            "Goal \"${goal.title}\" (${(goal.progress * 100).toInt()}% progress): Prediction: ${goalPred.prediction}. ${goalPred.evidence}"
                        } else {
                            "Goal \"${goal.title}\": Insufficient sub-task completion data to reliably predict completion date. Breaking this goal into smaller tasks will allow accurate forecasting."
                        }
                        AiResponse(
                            responseType = AiResponseType.INFORMATION,
                            title = "Goal Prediction",
                            message = msg,
                            confidence = goalPred?.confidence ?: AiConfidence.LOW,
                            relatedGoalId = goal.id,
                            decision = AiDecision(type = AiDecisionType.PREDICT_GOAL, title = "Goal Prediction", reason = msg, goalId = goal.id),
                            conversationContext = convContext.copy(lastIntent = AiDecisionType.PREDICT_GOAL, lastGoalId = goal.id)
                        )
                    }
                }
                AiDecisionType.PREDICT_TASK_RISK -> {
                    val predictions = predictiveEngine.predictTaskDelayRisks(context)
                    val highRisk = predictions.filter { it.probability >= 0.5f }
                    val msg = if (highRisk.isNotEmpty()) {
                        "Identified ${highRisk.size} task(s) with elevated delay risk:\n" + highRisk.joinToString("\n") { "• ${it.targetTitle}: ${it.prediction}" }
                    } else {
                        "All incomplete tasks are currently estimated within normal completion pace."
                    }
                    AiResponse(
                        responseType = AiResponseType.INFORMATION,
                        title = "Task Risk Prediction",
                        message = msg,
                        confidence = AiConfidence.HIGH,
                        decision = AiDecision(type = AiDecisionType.PREDICT_TASK_RISK, title = "Task Risk", reason = msg)
                    )
                }
                AiDecisionType.PREDICT_WORKLOAD -> {
                    val overload = predictiveEngine.predictWorkloadOverload(context)
                    val msg = if (overload?.prediction == "INSUFFICIENT_DATA") {
                        "I don't have enough historical data to make a reliable prediction."
                    } else overload?.let { "${it.prediction}. Evidence: ${it.evidence}" } ?: "Workload data is currently balanced."
                    AiResponse(
                        responseType = AiResponseType.INFORMATION,
                        title = "Workload Prediction",
                        message = msg,
                        confidence = overload?.confidence ?: AiConfidence.MEDIUM,
                        decision = AiDecision(type = AiDecisionType.PREDICT_WORKLOAD, title = "Workload Risk", reason = msg)
                    )
                }
                else -> {
                    val trend = predictiveEngine.predictProductivityTrend(context)
                    val msg = if (lower.contains("accurate") || lower.contains("accuracy") || lower.contains("calibration")) {
                        "Predictions are calibrated against your actual completion data, historical task durations, and adaptive personal capacity baseline."
                    } else if (trend?.prediction == "INSUFFICIENT_DATA") {
                        "I don't have enough historical data to make a reliable prediction."
                    } else {
                        trend?.let { "${it.prediction}. ${it.evidence}" } ?: "Productivity execution rate is stable."
                    }
                    AiResponse(
                        responseType = AiResponseType.INFORMATION,
                        title = "Productivity Trend",
                        message = msg,
                        confidence = trend?.confidence ?: AiConfidence.HIGH,
                        decision = AiDecision(type = AiDecisionType.PREDICT_PRODUCTIVITY, title = "Productivity Trend", reason = msg)
                    )
                }
            }
            return predResponse
        }

        // 8. Advisory Insight Questions ("Should I delete...", "Should I decompose...")
        val queryTitle = langResult.entities["title"]?.toString() ?: ""
        if (lower.contains("should i delete") || lower.contains("do you think i should delete")) {
            val targetTask = langResult.targetTaskId?.let { id -> context.tasks.find { it.id == id } }
                ?: context.tasks.find { it.title.contains(queryTitle, ignoreCase = true) }
            val text = if (targetTask != null) {
                "Reviewing \"${targetTask.title}\": It is currently ${if (targetTask.completed) "completed" else "incomplete"} with ${targetTask.priority} priority. If this task is no longer aligned with your goals, deleting it will declutter your workspace; otherwise, keeping or rescheduling it is recommended."
            } else {
                "If a task is obsolete, duplicate, or no longer serves your goals, deleting it helps keep your daily workload focused and realistic."
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Task Advice",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Task Advice", reason = text),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, lastTaskId = targetTask?.id)
            )
        }

        if (lower.contains("should i decompose") || lower.contains("do you think i should decompose")) {
            val goal = langResult.targetGoalId?.let { id -> context.goals.find { it.id == id } }
                ?: context.goals.find { it.title.contains(queryTitle, ignoreCase = true) }
                ?: context.activeGoals.firstOrNull()
            val text = if (goal != null) {
                val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
                if (linkedTasks.isEmpty()) {
                    "Yes, decomposing \"${goal.title}\" is highly recommended. It currently has no concrete sub-tasks, making steady progress difficult."
                } else {
                    "\"${goal.title}\" already has ${linkedTasks.size} sub-tasks (${linkedTasks.count { it.completed }} completed). Decomposing further is useful if you are stuck on large remaining milestones."
                }
            } else {
                "Decomposing goals into actionable 30-90 minute tasks is best when a goal feels overwhelming or has stalled."
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Goal Advice",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Goal Advice", reason = text),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, lastGoalId = goal?.id)
            )
        }

        // 9. Single Action Proposals
        if (langResult.requiresMutation) {
            when (langResult.intent) {
                AiDecisionType.CREATE_TASK -> {
                    val title = langResult.entities["title"]?.toString()?.ifBlank { "New Task" } ?: "New Task"
                    val duration = langResult.entities["duration"]?.toString() ?: "30 minutes"
                    val priority = langResult.entities["priority"]?.toString() ?: "MEDIUM"

                    val duplicate = context.tasks.find { it.title.equals(title, ignoreCase = true) && !it.completed }
                    if (duplicate != null) {
                        val msg = "A similar active task already exists: \"${duplicate.title}\""
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Duplicate Task Detected",
                            message = msg,
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.CREATE_TASK, title = "Duplicate Task Detected", reason = msg)
                        )
                    }

                    val params = mapOf("title" to title, "duration" to duration, "priority" to priority)
                    val action = AiAction(
                        type = AiActionType.CREATE_TASK,
                        title = "Create Task: $title",
                        description = "Create task \"$title\" ($duration, priority: $priority)",
                        parameters = params,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Task Proposal",
                        message = "I can create the task \"$title\" for you. Should I proceed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.CREATE_TASK, title = "Create Task", reason = "Create task $title", taskTitle = title),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.CREATE_TASK, lastEntityTitle = title, pendingAction = action)
                    )
                }
                AiDecisionType.COMPLETE_TASK -> {
                    val taskId = langResult.targetTaskId
                    val task = taskId?.let { id -> context.tasks.find { it.id == id } }

                    if (task == null) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Task Not Found",
                            message = "I couldn't find that task in your list.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.COMPLETE_TASK, title = "Task Not Found", reason = "Task not found")
                        )
                    }

                    if (task.completed) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Task Already Completed",
                            message = "\"${task.title}\" is already completed.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.COMPLETE_TASK, title = "Task Already Completed", reason = "Task already completed", taskId = task.id)
                        )
                    }

                    val action = AiAction(
                        type = AiActionType.COMPLETE_TASK,
                        title = "Complete Task: ${task.title}",
                        description = "Mark \"${task.title}\" as completed",
                        taskId = task.id,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Complete Task",
                        message = "Mark \"${task.title}\" as completed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        relatedTaskId = task.id,
                        decision = AiDecision(type = AiDecisionType.COMPLETE_TASK, title = "Complete Task", reason = "Mark ${task.title} completed", taskId = task.id),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.COMPLETE_TASK, lastTaskId = task.id, pendingAction = action)
                    )
                }
                AiDecisionType.DELETE_TASK -> {
                    val taskId = langResult.targetTaskId
                    val task = taskId?.let { id -> context.tasks.find { it.id == id } }

                    if (task == null) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Task Not Found",
                            message = "I couldn't find that task in your list.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.DELETE_TASK, title = "Task Not Found", reason = "Task not found")
                        )
                    }

                    val action = AiAction(
                        type = AiActionType.DELETE_TASK,
                        title = "Delete Task: ${task.title}",
                        description = "Delete task \"${task.title}\"",
                        taskId = task.id,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Delete Task",
                        message = "Are you sure you want to delete \"${task.title}\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        relatedTaskId = task.id,
                        decision = AiDecision(type = AiDecisionType.DELETE_TASK, title = "Delete Task", reason = "Delete ${task.title}", taskId = task.id),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.DELETE_TASK, lastTaskId = task.id, pendingAction = action)
                    )
                }
                AiDecisionType.UPDATE_TASK -> {
                    val taskId = langResult.targetTaskId
                    val task = taskId?.let { id -> context.tasks.find { it.id == id } }

                    if (task == null) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Task Not Found",
                            message = "I couldn't find that task in your list.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.UPDATE_TASK, title = "Task Not Found", reason = "Task not found")
                        )
                    }

                    val newTitle = langResult.entities["newTitle"]?.toString()
                    val newPriority = langResult.entities["priority"]?.toString()
                    val newDuration = langResult.entities["duration"]?.toString()
                    val newCategory = langResult.entities["category"]?.toString()

                    val params = mutableMapOf<String, Any>()
                    if (newTitle != null && newTitle.isNotBlank()) params["title"] = newTitle
                    if (newPriority != null) params["priority"] = newPriority
                    if (newDuration != null) params["duration"] = newDuration
                    if (newCategory != null && newCategory.isNotBlank()) params["category"] = newCategory

                    if (params.isEmpty()) {
                        val clar = AiClarification(
                            question = "What would you like to update about \"${task.title}\"?",
                            intent = AiDecisionType.UPDATE_TASK,
                            missingField = "update_fields",
                            originalQuery = rawMessage
                        )
                        return AiResponse(
                            responseType = AiResponseType.CLARIFICATION_NEEDED,
                            title = "Clarification Needed",
                            message = "What would you like to update about \"${task.title}\"? You can specify a new title, priority, duration, or category.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.CLARIFY, title = "Clarification Needed", reason = "No update parameters specified", taskId = task.id),
                            conversationContext = convContext.copy(lastIntent = AiDecisionType.UPDATE_TASK, lastTaskId = task.id, activeClarification = clar)
                        )
                    }

                    val action = AiAction(
                        type = AiActionType.UPDATE_TASK,
                        title = "Update Task: ${task.title}",
                        description = "Update task \"${task.title}\"",
                        taskId = task.id,
                        parameters = params,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Update Task",
                        message = "Update task \"${task.title}\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        relatedTaskId = task.id,
                        decision = AiDecision(type = AiDecisionType.UPDATE_TASK, title = "Update Task", reason = "Update ${task.title}", taskId = task.id),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.UPDATE_TASK, lastTaskId = task.id, pendingAction = action)
                    )
                }
                AiDecisionType.DELETE_ALL_TASKS -> {
                    if (context.incompleteTasks.isEmpty()) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "No Tasks Found",
                            message = "There are no tasks to delete.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.DELETE_ALL_TASKS, title = "No Tasks", reason = "No tasks to delete")
                        )
                    }
                    val action = AiAction(
                        type = AiActionType.DELETE_ALL_TASKS,
                        title = "Delete All Tasks",
                        description = "Delete all ${context.incompleteTasks.size} incomplete tasks",
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Delete All Tasks",
                        message = "This will delete all ${context.incompleteTasks.size} incomplete tasks. Are you completely sure you want to proceed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.DELETE_ALL_TASKS, title = "Delete All Tasks", reason = "Bulk deletion"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.DELETE_ALL_TASKS, pendingAction = action)
                    )
                }
                AiDecisionType.COMPLETE_ALL_TASKS -> {
                    if (context.incompleteTasks.isEmpty()) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "No Tasks Found",
                            message = "There are no tasks to complete.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.COMPLETE_ALL_TASKS, title = "No Tasks", reason = "No tasks to complete")
                        )
                    }
                    val action = AiAction(
                        type = AiActionType.COMPLETE_ALL_TASKS,
                        title = "Complete All Tasks",
                        description = "Mark all ${context.incompleteTasks.size} incomplete tasks as finished",
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Complete All Tasks",
                        message = "Mark all ${context.incompleteTasks.size} incomplete tasks as completed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.COMPLETE_ALL_TASKS, title = "Complete All Tasks", reason = "Bulk complete"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.COMPLETE_ALL_TASKS, pendingAction = action)
                    )
                }
                AiDecisionType.CREATE_GOAL -> {
                    val title = langResult.entities["title"]?.toString()?.ifBlank { "New Goal" } ?: "New Goal"
                    val category = langResult.entities["category"]?.toString() ?: "Personal"
                    val targetDate = langResult.entities["targetDate"]?.toString() ?: ""

                    val duplicate = context.goals.find { it.title.equals(title, ignoreCase = true) }
                    if (duplicate != null) {
                        val msg = "A similar active goal already exists: \"${duplicate.title}\""
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Duplicate Goal Detected",
                            message = msg,
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.CREATE_GOAL, title = "Duplicate Goal Detected", reason = msg)
                        )
                    }

                    val params = mapOf("title" to title, "category" to category, "targetDate" to targetDate)
                    val action = AiAction(
                        type = AiActionType.CREATE_GOAL,
                        title = "Create Goal: $title",
                        description = "Create goal \"$title\" ($category)",
                        parameters = params,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Goal Proposal",
                        message = "I can create the goal \"$title\" for you. Should I proceed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.CREATE_GOAL, title = "Create Goal", reason = "Create goal $title"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.CREATE_GOAL, lastEntityTitle = title, pendingAction = action)
                    )
                }
                AiDecisionType.UPDATE_GOAL -> {
                    val goalId = langResult.targetGoalId
                    val goal = goalId?.let { id -> context.goals.find { it.id == id } }

                    if (goal == null) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Goal Not Found",
                            message = "I couldn't find that goal in your list.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.UPDATE_GOAL, title = "Goal Not Found", reason = "Goal not found")
                        )
                    }

                    val newTitle = langResult.entities["newTitle"]?.toString()
                    val newCategory = langResult.entities["category"]?.toString()
                    val newTargetDate = langResult.entities["targetDate"]?.toString()

                    val params = mutableMapOf<String, Any>()
                    if (newTitle != null && newTitle.isNotBlank()) params["title"] = newTitle
                    if (newCategory != null && newCategory.isNotBlank()) params["category"] = newCategory
                    if (newTargetDate != null && newTargetDate.isNotBlank()) params["targetDate"] = newTargetDate

                    if (params.isEmpty()) {
                        val clar = AiClarification(
                            question = "What would you like to update about \"${goal.title}\"?",
                            intent = AiDecisionType.UPDATE_GOAL,
                            missingField = "update_fields",
                            originalQuery = rawMessage
                        )
                        return AiResponse(
                            responseType = AiResponseType.CLARIFICATION_NEEDED,
                            title = "Clarification Needed",
                            message = "What would you like to update about \"${goal.title}\"? You can specify a new title or category.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.CLARIFY, title = "Clarification Needed", reason = "No update parameters specified", goalId = goal.id),
                            conversationContext = convContext.copy(lastIntent = AiDecisionType.UPDATE_GOAL, lastGoalId = goal.id, activeClarification = clar)
                        )
                    }

                    val action = AiAction(
                        type = AiActionType.UPDATE_GOAL,
                        title = "Update Goal: ${goal.title}",
                        description = "Update goal \"${goal.title}\"",
                        goalId = goal.id,
                        parameters = params,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Update Goal",
                        message = "Update goal \"${goal.title}\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        relatedGoalId = goal.id,
                        decision = AiDecision(type = AiDecisionType.UPDATE_GOAL, title = "Update Goal", reason = "Update ${goal.title}", goalId = goal.id),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.UPDATE_GOAL, lastGoalId = goal.id, pendingAction = action)
                    )
                }
                AiDecisionType.DELETE_GOAL -> {
                    val goalId = langResult.targetGoalId
                    val goal = goalId?.let { id -> context.goals.find { it.id == id } }
                    if (goal == null) {
                        return AiResponse(
                            responseType = AiResponseType.NO_ACTION,
                            title = "Goal Not Found",
                            message = "I couldn't find that goal in your list.",
                            confidence = AiConfidence.HIGH,
                            decision = AiDecision(type = AiDecisionType.DELETE_GOAL, title = "Goal Not Found", reason = "Goal not found")
                        )
                    }
                    val action = AiAction(
                        type = AiActionType.DELETE_GOAL,
                        title = "Delete Goal: ${goal.title}",
                        description = "Delete goal \"${goal.title}\"",
                        goalId = goal.id,
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Delete Goal",
                        message = "Are you sure you want to delete \"${goal.title}\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        relatedGoalId = goal.id,
                        decision = AiDecision(type = AiDecisionType.DELETE_GOAL, title = "Delete Goal", reason = "Delete ${goal.title}", goalId = goal.id),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.DELETE_GOAL, lastGoalId = goal.id, pendingAction = action)
                    )
                }
                AiDecisionType.DECOMPOSE_GOAL -> {
                    val goalId = langResult.targetGoalId
                    val goal = goalId?.let { id -> context.goals.find { it.id == id } }
                    
                    if (goal != null) {
                        return handleGoalDecomposition(AiRequest(AiRequestType.GOAL_DECOMPOSITION, goalId = goal.id, parameters = mapOf("title" to goal.title)), context)
                    } else {
                        return AiResponse(AiResponseType.CLARIFICATION_NEEDED, "Identify Goal", "Which goal would you like to decompose?")
                    }
                }
                AiDecisionType.CREATE_AUTOMATION -> {
                    val name = if (lower.contains("morning") || lower.contains("daily plan")) {
                        "Morning Plan Assistant"
                    } else {
                        langResult.entities["title"]?.toString()?.ifBlank { "Custom Assistant" } ?: "Custom Assistant"
                    }
                    val desc = if (name == "Morning Plan Assistant") "Prepares recommended focus tasks at the start of each day" else "Custom automated rule"
                    val action = AiAction(
                        type = AiActionType.CREATE_AUTOMATION,
                        title = "Create Automation: $name",
                        description = desc,
                        parameters = mapOf(
                            "name" to name,
                            "description" to desc,
                            "triggerType" to "DAY_STARTED"
                        ),
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Create Automation Rule",
                        message = "I can set up the automation rule: \"$name\" ($desc). Should I proceed?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.CREATE_AUTOMATION, title = "Create Automation", reason = "Create rule $name"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.CREATE_AUTOMATION, pendingAction = action)
                    )
                }
                AiDecisionType.TOGGLE_AUTOMATION -> {
                    val rule = automationSystem.findRule(rawMessage)
                    val ruleName = rule?.name ?: if (lower.contains("morning")) "Morning Plan Assistant" else "rule"
                    val willEnable = !lower.contains("disable") && !lower.contains("turn off")
                    val action = AiAction(
                        type = AiActionType.TOGGLE_AUTOMATION,
                        title = if (willEnable) "Enable Automation" else "Disable Automation",
                        description = "${if (willEnable) "Enable" else "Disable"} rule $ruleName",
                        parameters = mapOf(
                            "ruleName" to ruleName,
                            "name" to ruleName,
                            "enabled" to willEnable
                        ),
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Toggle Automation Rule",
                        message = "Would you like to ${if (willEnable) "enable" else "disable"} the \"$ruleName\" automation rule?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.TOGGLE_AUTOMATION, title = "Toggle Automation", reason = "Toggle rule"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.TOGGLE_AUTOMATION, pendingAction = action)
                    )
                }
                AiDecisionType.DELETE_AUTOMATION -> {
                    val rule = automationSystem.findRule(rawMessage)
                    val action = AiAction(
                        type = AiActionType.DELETE_AUTOMATION,
                        title = "Delete Automation",
                        description = "Delete rule ${rule?.name ?: "rule"}",
                        parameters = mapOf("ruleName" to (rule?.name ?: rawMessage)),
                        requiresConfirmation = true
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Delete Automation",
                        message = "Are you sure you want to delete the automation \"${rule?.name ?: "rule"}\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.DELETE_AUTOMATION, title = "Delete Automation", reason = "Delete rule"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.DELETE_AUTOMATION, pendingAction = action)
                    )
                }
                AiDecisionType.UPDATE_AUTOMATION -> {
                    val rule = automationSystem.findRule(rawMessage)
                    val ruleName = rule?.name ?: (langResult.entities["title"]?.toString() ?: "rule")
                    val newTitle = langResult.entities["newTitle"]?.toString()
                    val newDesc = langResult.entities["description"]?.toString()
                    val action = AiAction(
                        type = AiActionType.UPDATE_AUTOMATION,
                        title = "Update Automation",
                        description = "Update rule $ruleName",
                        parameters = buildMap {
                            put("ruleName", ruleName)
                            rule?.id?.let { put("ruleId", it) }
                            if (newTitle != null) put("newTitle", newTitle)
                            if (newDesc != null) put("newDescription", newDesc)
                        },
                        requiresConfirmation = false
                    )
                    return AiResponse(
                        responseType = AiResponseType.ACTION_PROPOSAL,
                        title = "Update Automation",
                        message = "Update automation rule \"$ruleName\"?",
                        confidence = AiConfidence.HIGH,
                        proposedActions = listOf(action),
                        decision = AiDecision(type = AiDecisionType.UPDATE_AUTOMATION, title = "Update Automation", reason = "Update rule $ruleName"),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.UPDATE_AUTOMATION, pendingAction = action)
                    )
                }
                else -> { /* proceed to insights / general chat */ }
            }
        }

        // Planning & Recommendations
        if (langResult.intent == AiDecisionType.DAILY_PLAN) {
            return handleDailyPlan(context, relevantMemory)
        }
        if (langResult.intent == AiDecisionType.START_TASK) {
            val queryGoalTitle = langResult.targetGoalTitle ?: langResult.entities["title"]?.toString() ?: ""
            val matchedGoal = if (queryGoalTitle.isNotBlank()) {
                context.goals.find { it.title.contains(queryGoalTitle, ignoreCase = true) }
            } else null

            if (matchedGoal != null) {
                val incompleteLinked = context.incompleteTasks.filter { it.goalTitle == matchedGoal.title }
                val targetTask = incompleteLinked.maxByOrNull { it.priority.ordinal } ?: incompleteLinked.firstOrNull()
                if (targetTask != null) {
                    val action = AiAction(
                        type = AiActionType.COMPLETE_TASK,
                        title = "Complete Task",
                        description = "Work on \"${targetTask.title}\" for ${matchedGoal.title}",
                        taskId = targetTask.id
                    )
                    val text = "For your \"${matchedGoal.title}\" goal, you should work on \"${targetTask.title}\" next."
                    return AiResponse(
                        responseType = AiResponseType.RECOMMENDATION,
                        title = "Next Task for ${matchedGoal.title}",
                        message = text,
                        confidence = AiConfidence.HIGH,
                        relatedTaskId = targetTask.id,
                        relatedGoalId = matchedGoal.id,
                        proposedActions = listOf(action),
                        decision = AiDecision(
                            type = AiDecisionType.START_TASK,
                            title = "Next Task",
                            reason = text,
                            taskId = targetTask.id,
                            goalId = matchedGoal.id
                        ),
                        conversationContext = convContext.copy(lastIntent = AiDecisionType.START_TASK, lastTaskId = targetTask.id, lastGoalId = matchedGoal.id)
                    )
                }
            }

            val nextResp = handleNextTask(context)
            return nextResp.copy(
                decision = AiDecision(
                    type = AiDecisionType.START_TASK,
                    title = nextResp.title,
                    reason = nextResp.message,
                    taskId = nextResp.relatedTaskId
                ),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.START_TASK, lastTaskId = nextResp.relatedTaskId)
            )
        }

        // 10. Automations Info
        if (langResult.intent == AiDecisionType.LIST_AUTOMATIONS) {
            val rules = automationSystem.getRules()
            val text = "Active automations (${rules.count { it.enabled }} of ${rules.size} enabled):\n" +
                rules.joinToString("\n") { "• ${it.name} [${if (it.enabled) "ON" else "OFF"}]: ${it.description}" }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Automations",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.LIST_AUTOMATIONS, title = "Automations", reason = text)
            )
        }
        if (langResult.intent == AiDecisionType.EXPLAIN_AUTOMATION) {
            val text = automationSystem.explainLastRun(rawMessage)
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Automation Explanation",
                message = text,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.EXPLAIN_AUTOMATION, title = "Automation Run", reason = text)
            )
        }

        // 11. Conversational Reasoning Turn (e.g. "Which goal is behind?", "Why?", "What should I do?")
        if (lower.contains("which goal") && (lower.contains("behind") || lower.contains("stagnat") || lower.contains("risk") || lower.contains("least") || lower.contains("slow"))) {
            val activeGoals = context.activeGoals
            if (activeGoals.isEmpty()) {
                return AiResponse(AiResponseType.INFORMATION, "No Goals", "You don't have any active goals configured.")
            }
            val behindGoal = activeGoals.minByOrNull { it.progress } ?: activeGoals.first()
            val text = "Your \"${behindGoal.title}\" goal is falling behind with ${(behindGoal.progress * 100).toInt()}% progress."
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Goal Needing Attention",
                message = text,
                confidence = AiConfidence.HIGH,
                relatedGoalId = behindGoal.id,
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Goal Needing Attention",
                    reason = text,
                    goalId = behindGoal.id,
                    taskTitle = behindGoal.title
                ),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, lastGoalId = behindGoal.id)
            )
        }

        if (lower == "why" || lower == "why?" || lower.startsWith("why is it") || lower.startsWith("why is that")) {
            val lastGoalId = convContext.lastGoalId
            val goal = lastGoalId?.let { id -> context.goals.find { it.id == id } }
                ?: context.personalContext.goalHealth.find { it.state == GoalHealthState.AT_RISK }?.let { h -> context.goals.find { it.id == h.goalId } }
                ?: context.activeGoals.minByOrNull { it.progress }

            val reasonText = if (goal != null) {
                val linkedIncomplete = context.incompleteTasks.filter { it.goalTitle == goal.title }
                "\"${goal.title}\" is currently falling behind because it has ${(goal.progress * 100).toInt()}% progress with ${linkedIncomplete.size} pending sub-tasks and recent completion momentum has stalled."
            } else {
                "Your workload contains ${context.carriedTasks} carried tasks and several high-priority items competing for focus."
            }

            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Insight Reason",
                message = reasonText,
                confidence = AiConfidence.HIGH,
                relatedGoalId = goal?.id,
                decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Insight Reason", reason = reasonText, goalId = goal?.id),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, lastGoalId = goal?.id)
            )
        }

        if (lower.contains("what should i do") || lower == "what should i do?" || lower == "what next") {
            val lastGoalId = convContext.lastGoalId
            val goal = lastGoalId?.let { id -> context.goals.find { it.id == id } }
            if (goal != null) {
                val nextAction = AiAction(
                    type = AiActionType.DECOMPOSE_GOAL,
                    title = "Decompose \"${goal.title}\"",
                    description = "Break down \"${goal.title}\" into actionable sub-tasks",
                    goalId = goal.id,
                    requiresConfirmation = true
                )
                val text = "For \"${goal.title}\", I recommend breaking it down into smaller actionable tasks so you can build momentum today."
                return AiResponse(
                    responseType = AiResponseType.RECOMMENDATION,
                    title = "Recommended Next Action",
                    message = text,
                    confidence = AiConfidence.HIGH,
                    proposedActions = listOf(nextAction),
                    relatedGoalId = goal.id,
                    decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Recommended Action", reason = text, goalId = goal.id),
                    conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, lastGoalId = goal.id, pendingAction = nextAction)
                )
            }
        }

        // Historical task inquiry ("Did I complete my Java task yesterday?", "Did I finish...")
        if (lower.contains("did i complete") || lower.contains("did i finish") || lower.contains("have i completed")) {
            val query = langResult.entities["title"]?.toString() ?: ""
            val task = context.tasks.find { it.title.contains(query, ignoreCase = true) }
            val msg = if (task != null) {
                if (task.completed) {
                    "Yes, \"${task.title}\" was completed."
                } else {
                    "No, \"${task.title}\" is still incomplete."
                }
            } else {
                "I could not find a record for that task in your history."
            }
            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Task History",
                message = msg,
                confidence = AiConfidence.HIGH,
                decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Task History", reason = msg),
                conversationContext = convContext.copy(lastIntent = AiDecisionType.SHOW_INSIGHT, timestamp = System.currentTimeMillis())
            )
        }

        // Entity status inquiries ("Show my Kotlin goal", "How is it doing?", "Show my Java task")
        if (langResult.intent == AiDecisionType.SHOW_INSIGHT && langResult.temporalRange == null) {
            val query = langResult.entities["title"]?.toString() ?: ""
            val targetGoal = langResult.targetGoalId?.let { id -> context.goals.find { it.id == id } }
                ?: if (query.isNotBlank() && !query.equals("it", ignoreCase = true)) context.goals.find { it.title.contains(query, ignoreCase = true) } else null
                ?: if (lower.contains("goal") || lower.contains("it")) convContext.lastGoalId?.let { id -> context.goals.find { it.id == id } } else null

            if (targetGoal != null && (lower.contains("goal") || lower.contains("it") || lower.contains("how is") || query.isNotBlank())) {
                val linked = context.tasks.filter { it.goalTitle == targetGoal.title }
                val completedCount = linked.count { it.completed }
                val msg = "Goal \"${targetGoal.title}\": ${(targetGoal.progress * 100).toInt()}% progress, ${linked.size} sub-tasks ($completedCount completed)."
                return AiResponse(
                    responseType = AiResponseType.INFORMATION,
                    title = "Goal Overview",
                    message = msg,
                    confidence = AiConfidence.HIGH,
                    relatedGoalId = targetGoal.id,
                    decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Goal Overview", reason = msg, goalId = targetGoal.id),
                    conversationContext = convContext.copy(
                        lastIntent = AiDecisionType.SHOW_INSIGHT,
                        lastGoalId = targetGoal.id,
                        lastEntityTitle = targetGoal.title,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            val targetTask = langResult.targetTaskId?.let { id -> context.tasks.find { it.id == id } }
                ?: if (query.isNotBlank() && !query.equals("it", ignoreCase = true)) context.tasks.find { it.title.contains(query, ignoreCase = true) } else null
                ?: if (lower.contains("task") || lower.contains("it")) convContext.lastTaskId?.let { id -> context.tasks.find { it.id == id } } else null

            if (targetTask != null && (lower.contains("task") || lower.contains("it") || query.isNotBlank())) {
                val status = if (targetTask.completed) "completed" else "incomplete"
                val msg = "Task \"${targetTask.title}\" is currently $status (priority: ${targetTask.priority}, duration: ${targetTask.duration})."
                return AiResponse(
                    responseType = AiResponseType.INFORMATION,
                    title = "Task Overview",
                    message = msg,
                    confidence = AiConfidence.HIGH,
                    relatedTaskId = targetTask.id,
                    decision = AiDecision(type = AiDecisionType.SHOW_INSIGHT, title = "Task Overview", reason = msg, taskId = targetTask.id),
                    conversationContext = convContext.copy(
                        lastIntent = AiDecisionType.SHOW_INSIGHT,
                        lastTaskId = targetTask.id,
                        lastEntityTitle = targetTask.title,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        // 12. Fallback to General Analysis & Provider with Grounding
        return handleChat(request, context, relevantMemory)
    }

    private suspend fun handleChat(request: AiRequest, context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val message = request.userMessage ?: return AiResponse(AiResponseType.NO_ACTION, "Empty Message", "I didn't receive a message to process.")
        
        // 1. Check if user is asking specifically about memory/productivity patterns
        val isMemoryQuery = message.lowercase().contains(Regex("\\b(remember|productivity|pattern|history|behavior|how am i doing|falling behind|memory|observations)\\b"))
        if (isMemoryQuery) {
            val personal = context.personalContext
            val hasHistory = context.memory.analyzedDays > 0 || relevantMemory.isNotEmpty()
            val activeSignals = proactiveEngine.detectSignals(context)
            
            val statusSummary = if (!hasHistory && activeSignals.isEmpty()) {
                "I'm still learning your productivity style. Once you've completed more tasks and goals, I'll be able to show your consistency patterns and workload trends."
            } else {
                buildString {
                    append("Based on my latest analysis of your behavior and workload:\n\n")
                    
                    if (activeSignals.isNotEmpty()) {
                        activeSignals.forEach { signal ->
                            append("• ${signal.title}: ${signal.message} (Reason: ${signal.evidence})\n")
                        }
                        append("\n")
                    }

                    append("- Workload: ${personal.workload.state} (${personal.workload.taskCount} active tasks)\n")
                    append("- Productivity Trend: ${personal.productivityTrend}\n")
                    
                    val atRisk = personal.goalHealth.filter { it.state == GoalHealthState.AT_RISK }
                    if (atRisk.isNotEmpty()) {
                        append("- Stagnating Goals: ${atRisk.joinToString { it.goalTitle }}\n")
                    }
                    
                    if (relevantMemory.isNotEmpty()) {
                        val memorySummary = relevantMemory.joinToString("\n") { "• ${it.title}: ${it.content}" }
                        append("\nHistorical Observations:\n$memorySummary")
                    }
                    
                    if (personal.productivityTrend == ProductivityTrend.DECLINING || personal.workload.state == WorkloadState.VERY_HIGH) {
                        append("\n\nTip: You seem to be under heavy pressure lately. Focus on finishing one high-priority task before adding anything new.")
                    }
                }
            }

            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Nexora Context Analysis",
                message = statusSummary,
                confidence = personal.confidence,
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Nexora Context Analysis",
                    reason = statusSummary
                )
            )
        }

        // 2. Resolve using Provider Manager (Cloud with Local Fallback)
        val prompt = AiPromptBuilder.buildContextPrompt(context) + "\n\nUser Message: $message"
        val structuredResult = providerManager.generateStructuredResponse(prompt, context, request.conversationContext)
        
        // 3. Grounding & Factual Verification
        val groundedResponse = groundResponse(structuredResult, context)

        // 4. Map Structured Response to Unified Response
        val response = AiResponse(
            responseType = mapDecisionToResponseType(groundedResponse.decision.type),
            title = groundedResponse.decision.title,
            message = groundedResponse.textResponse ?: groundedResponse.decision.reason,
            confidence = mapPriorityToConfidence(groundedResponse.decision.priority),
            recommendations = emptyList(), 
            proposedActions = groundedResponse.actions,
            relatedTaskId = groundedResponse.decision.taskId,
            relatedGoalId = groundedResponse.decision.goalId,
            decision = groundedResponse.decision,
            conversationContext = groundedResponse.conversationContext
        )
        
        return response
    }
    
    private suspend fun logResponseRecommendations(response: AiResponse) {
        response.recommendations.forEach { rec ->
            repository.logRecommendation(
                AiRecommendationHistory(
                    id = java.util.UUID.randomUUID().toString(),
                    type = rec.type,
                    title = rec.title,
                    message = rec.message,
                    relatedTaskId = rec.relatedTaskId,
                    relatedGoalId = rec.relatedGoalId,
                    confidence = rec.confidence
                )
            )
        }
        
        if (response.responseType == AiResponseType.RECOMMENDATION || response.responseType == AiResponseType.ACTION_PROPOSAL) {
            val type = if (response.responseType == AiResponseType.RECOMMENDATION) 
                AiRecommendationType.NEXT_TASK else AiRecommendationType.GENERAL
            
            repository.logRecommendation(
                AiRecommendationHistory(
                    id = java.util.UUID.randomUUID().toString(),
                    type = type,
                    title = response.title,
                    message = response.message,
                    relatedTaskId = response.relatedTaskId,
                    relatedGoalId = response.relatedGoalId,
                    confidence = response.confidence
                )
            )
        }
    }

    private fun handleNextTask(context: AiContext): AiResponse {
        val recommendations = planner.analyze(context)
        val nextTaskRec = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        return if (nextTaskRec != null) {
            val suggested = nextTaskRec.suggestedAction ?: AiAction(
                type = AiActionType.COMPLETE_TASK,
                title = "Complete Task",
                description = "Mark \"${nextTaskRec.title}\" as finished?",
                taskId = nextTaskRec.relatedTaskId,
                reason = nextTaskRec.message
            )
            AiResponse(
                responseType = AiResponseType.RECOMMENDATION,
                title = nextTaskRec.title,
                message = nextTaskRec.message,
                confidence = nextTaskRec.confidence,
                evidence = nextTaskRec.evidence,
                relatedTaskId = nextTaskRec.relatedTaskId,
                recommendations = recommendations,
                proposedActions = listOf(suggested)
            )
        } else {
            AiResponse(
                responseType = AiResponseType.NO_ACTION,
                title = "No Urgent Tasks",
                message = "Nexora didn't find any urgent tasks requiring immediate attention. You're on top of things!"
            )
        }
    }

    private suspend fun handleDailyPlan(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val plan = planner.createDailyPlan(context)
        val proposedActions = plan.tasks.map { 
            AiAction(
                type = AiActionType.OPEN_TASK, 
                title = it.task.title, 
                description = "Recommended as part of your daily plan.", 
                taskId = it.task.id
            )
        }

        val prompt = "Explain why this daily plan is effective: ${plan.summary}. " +
                     "Tasks: ${plan.tasks.joinToString { it.task.title }}"
        
        val llmResponse = try {
            providerManager.generateResponse(prompt, context)
        } catch (_: Exception) {
            null
        }

        val memoryInsight = relevantMemory.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN }?.content
        val baseMessage = if (llmResponse != null && !llmResponse.text.startsWith("Task prioritization") && !llmResponse.text.startsWith("Goal decomposition")) {
            llmResponse.text
        } else {
            plan.summary
        }
        val finalMessage = if (memoryInsight != null) "$baseMessage\n\nNote: $memoryInsight" else baseMessage

        return AiResponse(
            responseType = AiResponseType.PLAN,
            title = "Daily Plan",
            message = finalMessage,
            confidence = AiConfidence.HIGH,
            proposedActions = proposedActions,
            decision = AiDecision(type = AiDecisionType.DAILY_PLAN, title = "Daily Plan", reason = finalMessage)
        )
    }

    private fun handleGoalAnalysis(context: AiContext): AiResponse {
        val recs = planner.analyzeGoals(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.NO_ACTION, "Goal Status", "Your goals are currently on track.")
        
        return AiResponse(
            responseType = AiResponseType.RECOMMENDATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence,
            evidence = best.evidence,
            relatedGoalId = best.relatedGoalId
        )
    }

    private fun handleProductivityAnalysis(context: AiContext): AiResponse {
        val recs = planner.analyzeProductivity(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.INFORMATION, "Productivity", "Keep working on your tasks to build your productivity history.")
        
        return AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence,
            evidence = best.evidence
        )
    }

    private suspend fun handleProactiveAnalysis(context: AiContext): AiResponse {
        val signals = proactiveEngine.detectSignals(context)
        val filteredSignals = filterProactiveSignals(signals)
        
        val recommendations = filteredSignals.map { signal ->
            AiRecommendation(
                id = signal.fingerprint,
                type = mapSignalTypeToRecType(signal.type),
                title = signal.title,
                message = signal.message,
                priority = signal.severity,
                confidence = signal.confidence,
                evidence = emptyList(), 
                relatedTaskId = signal.relatedTaskId,
                relatedGoalId = signal.relatedGoalId,
                actionLabel = signal.suggestedAction?.title
            )
        }

        val criticalSignal = filteredSignals.find { it.severity == AiPriority.CRITICAL } ?: filteredSignals.firstOrNull()
        
        return if (criticalSignal != null) {
            AiResponse(
                responseType = if (criticalSignal.severity >= AiPriority.MEDIUM) AiResponseType.WARNING else AiResponseType.INFORMATION,
                title = criticalSignal.title,
                message = criticalSignal.message,
                confidence = criticalSignal.confidence,
                recommendations = recommendations,
                proactiveSignals = filteredSignals,
                relatedTaskId = criticalSignal.relatedTaskId,
                relatedGoalId = criticalSignal.relatedGoalId,
                proposedActions = listOfNotNull(criticalSignal.suggestedAction)
            )
        } else {
            AiResponse(AiResponseType.NO_ACTION, "System Healthy", "Nexora hasn't detected any new immediate issues with your workflow.")
        }
    }

    private fun mapSignalTypeToRecType(type: ProactiveSignalType): AiRecommendationType {
        return when (type) {
            ProactiveSignalType.WORKLOAD_RISK, 
            ProactiveSignalType.OVERLOAD, 
            ProactiveSignalType.PLAN_MISMATCH -> AiRecommendationType.WARNING
            
            ProactiveSignalType.NEGLECTED_GOAL, 
            ProactiveSignalType.GOAL_NEGLECT, 
            ProactiveSignalType.GOAL_STAGNATION,
            ProactiveSignalType.MISSING_NEXT_ACTION,
            ProactiveSignalType.GOAL_PROGRESS_OPPORTUNITY -> AiRecommendationType.GOAL_ACTION
            
            ProactiveSignalType.REPEATED_CARRY_FORWARD,
            ProactiveSignalType.CARRY_FORWARD_PATTERN,
            ProactiveSignalType.PRODUCTIVITY_DROP,
            ProactiveSignalType.PRODUCTIVITY_IMPROVEMENT,
            ProactiveSignalType.WORKLOAD_BALANCED -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            
            ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> AiRecommendationType.WARNING
            ProactiveSignalType.TASK_TOO_LARGE -> AiRecommendationType.WARNING
            
            else -> AiRecommendationType.GENERAL
        }
    }

    private suspend fun filterProactiveSignals(signals: List<AiProactiveSignal>): List<AiProactiveSignal> {
        val recentRecs = repository.getRecentRecommendations(100)
        val now = System.currentTimeMillis()
        val cooldownMillis = 1000 * 60 * 60 * 6 // 6 hours cooldown
        val criticalCooldownMillis = 1000 * 60 * 60 * 1 // 1 hour for critical

        return signals.filter { signal ->
            val cooldown = if (signal.severity == AiPriority.CRITICAL) criticalCooldownMillis else cooldownMillis
            val recentlyShown = recentRecs.any { rec ->
                val sameType = rec.type == mapSignalTypeToRecType(signal.type)
                val sameTask = rec.relatedTaskId == signal.relatedTaskId
                val sameGoal = rec.relatedGoalId == signal.relatedGoalId
                
                val sameContext = if (signal.relatedTaskId == null && signal.relatedGoalId == null) {
                    rec.title == signal.title
                } else true

                rec.timestamp > (now - cooldown) && sameType && sameTask && sameGoal && sameContext
            }
            !recentlyShown
        }
    }

    private suspend fun handleGoalDecomposition(request: AiRequest, context: AiContext): AiResponse {
        val goalId = request.goalId
        val goal = context.goals.find { it.id == goalId } 
            ?: request.parameters["title"]?.toString()?.let { title -> context.goals.find { it.title.contains(title, ignoreCase = true) } }
            
        if (goal == null) {
            return AiResponse(AiResponseType.CLARIFICATION_NEEDED, "Identify Goal", "I couldn't find which goal to decompose. Please specify a goal title.")
        }

        val prompt = "Decompose the goal \"${goal.title}\" into actionable sub-tasks. " +
                     "Return a structured list of tasks with titles and estimated durations."
        
        val structuredResult = providerManager.generateStructuredResponse(prompt, context)
        
        val actions = if (structuredResult.actions.isNotEmpty()) {
            structuredResult.actions.map { action ->
                if (action.type == AiActionType.CREATE_TASK) {
                    val params = action.parameters.toMutableMap()
                    params["goalTitle"] = goal.title
                    params["category"] = goal.category
                    action.copy(parameters = params)
                } else action
            }
        } else {
            val result = aiService.decomposeGoal(goal.title, "", goal.category)
            result.steps.map { step ->
                AiAction(
                    type = AiActionType.CREATE_TASK,
                    title = "Create Task: ${step.title}",
                    description = step.description,
                    parameters = mapOf(
                        "title" to step.title,
                        "duration" to step.estimatedDuration,
                        "priority" to step.priority.name,
                        "goalTitle" to goal.title,
                        "category" to goal.category
                    )
                )
            }
        }

        return AiResponse(
            responseType = AiResponseType.ACTION_PROPOSAL,
            title = "Goal Decomposed",
            message = structuredResult.textResponse ?: "I've broken down \"${goal.title}\" into several actionable steps.",
            proposedActions = actions,
            relatedGoalId = goal.id,
            decision = AiDecision(
                type = AiDecisionType.DECOMPOSE_GOAL,
                title = "Decompose Goal",
                reason = "Break down ${goal.title} into actionable steps.",
                goalId = goal.id
            )
        )
    }

    private suspend fun handleGeneralAnalysis(context: AiContext): AiResponse {
        val signals = proactiveEngine.detectSignals(context)
        val filteredSignals = filterProactiveSignals(signals)
        
        val recommendations = filteredSignals.map { signal ->
            AiRecommendation(
                id = signal.fingerprint,
                type = mapSignalTypeToRecType(signal.type),
                title = signal.title,
                message = signal.message,
                priority = signal.severity,
                confidence = signal.confidence,
                evidence = emptyList()
            )
        }

        val next = planner.analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
        
        val summary = mutableListOf<String>()
        if (next != null) summary.add("Top priority: ${next.title}")
        
        val personal = context.personalContext
        summary.add("Workload: ${personal.workload.state}. Goal Health: ${personal.goalHealth.count { it.state == GoalHealthState.HEALTHY }} healthy.")

        if (filteredSignals.isNotEmpty()) summary.add("Detected ${filteredSignals.size} new proactive items.")
        
        return AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = "Nexora Analysis",
            message = if (summary.isEmpty()) "Your workspace is clear." else summary.joinToString("\n"),
            recommendations = recommendations,
            proactiveSignals = filteredSignals
        )
    }

    private fun executeDirectAction(type: AiActionType, params: Map<String, Any>, taskId: Long? = null, goalId: Long? = null): AiResponse {
        val action = AiAction(
            type = type, 
            title = "Direct Action", 
            description = "Execution of $type requested.", 
            parameters = params, 
            taskId = taskId, 
            goalId = goalId,
            requiresConfirmation = true
        )
        
        return AiResponse(
            responseType = AiResponseType.ACTION_PROPOSAL,
            title = "Action Proposal",
            message = "I can perform this action for you. Should I proceed?",
            proposedActions = listOf(action)
        )
    }

    private fun mapActionTypeToDecision(actionType: AiActionType): AiDecisionType {
        return when (actionType) {
            AiActionType.CREATE_TASK -> AiDecisionType.CREATE_TASK
            AiActionType.COMPLETE_TASK -> AiDecisionType.COMPLETE_TASK
            AiActionType.DELETE_TASK -> AiDecisionType.DELETE_TASK
            AiActionType.UPDATE_TASK -> AiDecisionType.UPDATE_TASK
            AiActionType.RESCHEDULE_TASK -> AiDecisionType.RESCHEDULE_TASK
            AiActionType.CREATE_GOAL -> AiDecisionType.CREATE_GOAL
            AiActionType.DELETE_GOAL -> AiDecisionType.DELETE_GOAL
            AiActionType.UPDATE_GOAL -> AiDecisionType.UPDATE_GOAL
            AiActionType.DECOMPOSE_GOAL -> AiDecisionType.DECOMPOSE_GOAL
            AiActionType.DELETE_ALL_TASKS -> AiDecisionType.DELETE_ALL_TASKS
            AiActionType.COMPLETE_ALL_TASKS -> AiDecisionType.COMPLETE_ALL_TASKS
            AiActionType.CREATE_AUTOMATION -> AiDecisionType.CREATE_AUTOMATION
            AiActionType.UPDATE_AUTOMATION -> AiDecisionType.UPDATE_AUTOMATION
            AiActionType.TOGGLE_AUTOMATION -> AiDecisionType.TOGGLE_AUTOMATION
            AiActionType.DELETE_AUTOMATION -> AiDecisionType.DELETE_AUTOMATION
            AiActionType.OPEN_TASK -> AiDecisionType.SHOW_INSIGHT
            AiActionType.OPEN_GOAL -> AiDecisionType.SHOW_INSIGHT
            AiActionType.SHOW_INSIGHT -> AiDecisionType.SHOW_INSIGHT
        }
    }

    private fun mapDecisionToResponseType(type: AiDecisionType): AiResponseType {
        return when (type) {
            AiDecisionType.START_TASK -> AiResponseType.RECOMMENDATION
            AiDecisionType.COMPLETE_TASK -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.RESCHEDULE_TASK -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.CREATE_TASK -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.UPDATE_TASK -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DELETE_TASK -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.UPDATE_GOAL -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DELETE_GOAL -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.CREATE_GOAL -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DECOMPOSE_GOAL -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DAILY_PLAN -> AiResponseType.PLAN
            AiDecisionType.SHOW_INSIGHT -> AiResponseType.INFORMATION
            AiDecisionType.EXPLANATION -> AiResponseType.INFORMATION
            AiDecisionType.WARNING -> AiResponseType.WARNING
            AiDecisionType.AMBIGUOUS -> AiResponseType.CLARIFICATION_NEEDED
            AiDecisionType.CLARIFY -> AiResponseType.CLARIFICATION_NEEDED
            AiDecisionType.CANCEL -> AiResponseType.NO_ACTION
            AiDecisionType.DELETE_ALL_TASKS -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.COMPLETE_ALL_TASKS -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.CREATE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.TOGGLE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DELETE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.UPDATE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.LIST_AUTOMATIONS -> AiResponseType.INFORMATION
            AiDecisionType.EXPLAIN_AUTOMATION -> AiResponseType.INFORMATION
            AiDecisionType.GREETING -> AiResponseType.INFORMATION
            AiDecisionType.GENERAL_CONVERSATION -> AiResponseType.INFORMATION
            AiDecisionType.THANKS -> AiResponseType.INFORMATION
            AiDecisionType.GOODBYE -> AiResponseType.INFORMATION
            AiDecisionType.PREDICT_GOAL -> AiResponseType.INFORMATION
            AiDecisionType.PREDICT_TASK_RISK -> AiResponseType.INFORMATION
            AiDecisionType.PREDICT_WORKLOAD -> AiResponseType.INFORMATION
            AiDecisionType.PREDICT_PRODUCTIVITY -> AiResponseType.INFORMATION
            AiDecisionType.NO_ACTION -> AiResponseType.NO_ACTION
        }
    }

    private fun groundResponse(
        response: AiModelStructuredResponse,
        context: AiContext
    ): AiModelStructuredResponse {
        val decision = response.decision
        
        // 1. Verify IDs exist in context - never allow hallucinated entities into execution
        val validTaskId = decision.taskId?.takeIf { id -> context.tasks.any { it.id == id } }
        val validGoalId = decision.goalId?.takeIf { id -> context.goals.any { it.id == id } }
        
        val validActions = response.actions.filter { action ->
            val taskOk = action.taskId == null || context.tasks.any { it.id == action.taskId }
            val goalOk = action.goalId == null || context.goals.any { it.id == action.goalId }
            taskOk && goalOk
        }

        // 2. Cross-reference claims against database facts
        val msg = response.textResponse?.lowercase() ?: ""
        val refinedText = if (msg.contains("you have") || msg.contains("there are")) {
            val taskCount = context.incompleteTasks.size
            if (msg.contains("$taskCount tasks") || (msg.contains("no tasks") && taskCount == 0)) {
                response.textResponse
            } else {
                response.textResponse + " (Actual status: you have $taskCount incomplete tasks remaining.)"
            }
        } else {
            response.textResponse
        }

        return response.copy(
            decision = decision.copy(
                taskId = validTaskId,
                goalId = validGoalId
            ),
            actions = validActions,
            textResponse = refinedText
        )
    }

    private fun mapPriorityToConfidence(priority: AiPriority): AiConfidence {
        return when (priority) {
            AiPriority.LOW -> AiConfidence.LOW
            AiPriority.MEDIUM -> AiConfidence.MEDIUM
            AiPriority.HIGH -> AiConfidence.HIGH
            AiPriority.CRITICAL -> AiConfidence.HIGH
        }
    }
}
