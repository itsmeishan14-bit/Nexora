package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

/**
 * The unified AI Brain for Nexora.
 * Orchestrates reasoning, analysis, and actions across all AI capabilities.
 */
class NexoraAiBrain(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService,
    private val providerManager: AiProviderManager,
    toolRegistry: AiToolRegistry,
    private val repository: NexoraRepository
) {
    private val planner = AiPlanner()
    private val learningLoop = AiLearningLoop(repository)
    private val memoryRetriever = AiMemoryRetriever(repository)
    private val decisionGate = AiDecisionGate(repository)
    private val proactiveEngine = NexoraProactiveEngine()
    private val automationSystem = NexoraAutomationSystem()
    
    // The Agent system is a capability of the Brain
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
        
        // Use conversational context if provided
        val convContext = request.conversationContext ?: AiConversationContext()

        // Decide if we should use the multi-step Agent
        val response = if (shouldUseAgent(request, context)) {
            val agentResponse = agent.execute(request, context, relevantMemory)
            logResponseRecommendations(agentResponse)
            agentResponse
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

        // 3. Automation Rule Evaluation
        val automatedSignals = evaluateAutomations(request, context)
        
        // If automations triggered new signals, attach them to the response
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

    private fun evaluateAutomations(request: AiRequest, context: AiContext): List<AiProactiveSignal> {
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

    fun getAutomationRules(): List<AiAutomationRule> = automationSystem.getRules()

    fun updateAutomationRule(rule: AiAutomationRule) {
        automationSystem.updateRule(rule)
    }

    fun addAutomationRule(rule: AiAutomationRule): Boolean = automationSystem.addRule(rule)

    fun deleteAutomationRule(idOrName: String): Boolean = automationSystem.deleteRule(idOrName)

    fun toggleAutomationRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? = automationSystem.toggleRule(idOrName, enabled)

    fun explainAutomationRun(query: String? = null): String = automationSystem.explainLastRun(query)

    private fun shouldUseAgent(request: AiRequest, context: AiContext): Boolean {
        if (request.type != AiRequestType.CHAT) return false
        val msg = request.userMessage?.lowercase() ?: ""
        
        // Use intent resolver for smarter selection
        val prompt = "Select component. User message: $msg"
        val langResult = AdvancedLocalLanguagePipeline().process(msg, context)
        
        // Multi-step complex workflow triggers
        return (msg.contains("goal") && (msg.contains("finish") || msg.contains("help") || msg.contains("work") || msg.contains("milestones") || msg.contains("progress"))) ||
               msg.contains("organize") || msg.contains("clean") || msg.contains("overload") ||
               (langResult.intent == AiDecisionType.COMPLETE_TASK && msg.contains("java")) // Example for testing multi-turn
    }

    private suspend fun handleChat(request: AiRequest, context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val message = request.userMessage ?: return AiResponse(AiResponseType.NO_ACTION, "Empty Message", "I didn't receive a message to process.")
        
        // 1. Check if user is asking specifically about memory/productivity patterns
        val isMemoryQuery = message.lowercase().contains(Regex("\\b(remember|productivity|pattern|history|behavior|how am i doing|falling behind|memory|observations)\\b"))
        if (isMemoryQuery) {
            val personal = context.personalContext
            val hasHistory = context.memory.analyzedDays > 0 || relevantMemory.isNotEmpty()
            
            // Check for active proactive signals that might explain a warning
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
                confidence = personal.confidence
            )
        }

        // 2. Resolve using Provider Manager (Cloud with Local Fallback)
        val prompt = AiPromptBuilder.buildContextPrompt(context) + "\n\nUser Message: $message"
        val structuredResult = providerManager.generateStructuredResponse(prompt, context, request.conversationContext)
        
        // 3. Grounding & Factual Verification
        // If the LLM claims a fact that contradicts the DB, we prefer the DB.
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
        // Log individual recommendations if present
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
        
        // Log the main recommendation or action if relevant
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
            AiResponse(
                responseType = AiResponseType.RECOMMENDATION,
                title = nextTaskRec.title,
                message = nextTaskRec.message,
                confidence = nextTaskRec.confidence,
                evidence = nextTaskRec.evidence,
                relatedTaskId = nextTaskRec.relatedTaskId
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

        // Use Provider for a natural explanation of the plan if available
        val prompt = "Explain why this daily plan is effective: ${plan.summary}. " +
                     "Tasks: ${plan.tasks.joinToString { it.task.title }}"
        
        val llmResponse = try {
            providerManager.generateResponse(prompt, context)
        } catch (e: Exception) {
            null
        }

        // Add memory insights to the message if relevant
        val memoryInsight = relevantMemory.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN }?.content
        val baseMessage = llmResponse?.text ?: plan.summary
        val finalMessage = if (memoryInsight != null) "$baseMessage\n\nNote: $memoryInsight" else baseMessage

        val response = AiResponse(
            responseType = AiResponseType.PLAN,
            title = "Daily Plan",
            message = finalMessage,
            confidence = AiConfidence.HIGH,
            proposedActions = proposedActions
        )
        
        return response
    }

    private fun handleGoalAnalysis(context: AiContext): AiResponse {
        val recs = planner.analyzeGoals(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.NO_ACTION, "Goal Status", "Your goals are currently on track.")
        
        val response = AiResponse(
            responseType = AiResponseType.RECOMMENDATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence,
            evidence = best.evidence,
            relatedGoalId = best.relatedGoalId
        )
        
        return response
    }

    private fun handleProductivityAnalysis(context: AiContext): AiResponse {
        val recs = planner.analyzeProductivity(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.INFORMATION, "Productivity", "Keep working on your tasks to build your productivity history.")
        
        val response = AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence,
            evidence = best.evidence
        )
        
        return response
    }

    private suspend fun handleProactiveAnalysis(context: AiContext): AiResponse {
        val signals = proactiveEngine.detectSignals(context)
        val filteredSignals = filterProactiveSignals(signals)
        
        // Map signals to recommendations for backward compatibility
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
        
        val response = if (criticalSignal != null) {
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
        
        return response
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
        val cooldownMillis = 1000 * 60 * 60 * 6 // 6 hours cooldown for most signals
        val criticalCooldownMillis = 1000 * 60 * 60 * 1 // 1 hour for critical signals

        return signals.filter { signal ->
            val cooldown = if (signal.severity == AiPriority.CRITICAL) criticalCooldownMillis else cooldownMillis
            val recentlyShown = recentRecs.any { rec ->
                val sameType = rec.type == mapSignalTypeToRecType(signal.type)
                val sameTask = rec.relatedTaskId == signal.relatedTaskId
                val sameGoal = rec.relatedGoalId == signal.relatedGoalId
                
                // If it has no related entities, match by title
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
            ?: request.parameters["title"]?.toString()?.let { title -> context.goals.find { it.title == title } }
            
        if (goal == null) {
            return AiResponse(AiResponseType.CLARIFICATION_NEEDED, "Identify Goal", "I couldn't find which goal to decompose. Please specify a goal title.")
        }

        // Use Provider for advanced decomposition if available
        val prompt = "Decompose the goal \"${goal.title}\" into actionable sub-tasks. " +
                     "Return a structured list of tasks with titles and estimated durations."
        
        val structuredResult = providerManager.generateStructuredResponse(prompt, context)
        
        val actions = if (structuredResult.actions.isNotEmpty()) {
            // LLM provided specific actions (task creation proposals)
            structuredResult.actions.map { action ->
                if (action.type == AiActionType.CREATE_TASK) {
                    val params = action.parameters.toMutableMap()
                    params["goalTitle"] = goal.title
                    params["category"] = goal.category
                    action.copy(parameters = params)
                } else action
            }
        } else {
            // Fallback to deterministic decomposition from planner
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
            relatedGoalId = goal.id
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
        
        val response = AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = "Nexora Analysis",
            message = if (summary.isEmpty()) "Your workspace is clear." else summary.joinToString("\n"),
            recommendations = recommendations,
            proactiveSignals = filteredSignals
        )
        
        return response
    }

    private fun executeDirectAction(type: AiActionType, params: Map<String, Any>, taskId: Long? = null, goalId: Long? = null): AiResponse {
        val action = AiAction(
            type = type, 
            title = "Direct Action", 
            description = "Execution of $type requested.", 
            parameters = params, 
            taskId = taskId, 
            goalId = goalId,
            requiresConfirmation = true // Direct brain requests default to requiring confirmation
        )
        
        // If it's a modifying action, we propose it first unless it's explicitly authorized
        return AiResponse(
            responseType = AiResponseType.ACTION_PROPOSAL,
            title = "Action Proposal",
            message = "I can perform this action for you. Should I proceed?",
            proposedActions = listOf(action)
        )
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
            AiDecisionType.WARNING -> AiResponseType.WARNING
            AiDecisionType.AMBIGUOUS -> AiResponseType.CLARIFICATION_NEEDED
            AiDecisionType.CLARIFY -> AiResponseType.CLARIFICATION_NEEDED
            AiDecisionType.CANCEL -> AiResponseType.NO_ACTION
            AiDecisionType.DELETE_ALL_TASKS -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.COMPLETE_ALL_TASKS -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.CREATE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.TOGGLE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.DELETE_AUTOMATION -> AiResponseType.ACTION_PROPOSAL
            AiDecisionType.LIST_AUTOMATIONS -> AiResponseType.INFORMATION
            AiDecisionType.EXPLAIN_AUTOMATION -> AiResponseType.INFORMATION
            AiDecisionType.GREETING -> AiResponseType.INFORMATION
            AiDecisionType.GENERAL_CONVERSATION -> AiResponseType.INFORMATION
            AiDecisionType.THANKS -> AiResponseType.INFORMATION
            AiDecisionType.GOODBYE -> AiResponseType.INFORMATION
            AiDecisionType.NO_ACTION -> AiResponseType.NO_ACTION
        }
    }

    private fun groundResponse(
        response: AiModelStructuredResponse,
        context: AiContext
    ): AiModelStructuredResponse {
        val decision = response.decision
        
        // 1. Verify IDs exist in context
        val validTaskId = decision.taskId?.takeIf { id -> context.tasks.any { it.id == id } }
        val validGoalId = decision.goalId?.takeIf { id -> context.goals.any { it.id == id } }
        
        // 2. Cross-reference claims (Simple heuristic: if it claims a state, check it)
        val msg = response.textResponse?.lowercase() ?: ""
        val refinedText = if (msg.contains("you have") || msg.contains("there are")) {
            val taskCount = context.incompleteTasks.size
            if (msg.contains("$taskCount tasks") || msg.contains("no tasks") && taskCount == 0) {
                response.textResponse
            } else {
                // If LLM hallucinates count, inject factual correction
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
