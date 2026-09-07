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
    private val actionExecutor: AiActionExecutor,
    toolRegistry: AiToolRegistry,
    private val repository: NexoraRepository,
    private val intentResolver: LocalAiIntentResolver = LocalAiIntentResolver()
) {
    private val planner = AiPlanner()
    private val learningLoop = AiLearningLoop(repository)
    private val memoryRetriever = AiMemoryRetriever(repository)
    private val decisionGate = AiDecisionGate(repository)
    private val proactiveEngine = NexoraProactiveEngine()
    private val automationSystem = NexoraAutomationSystem()
    
    // The Agent system is a capability of the Brain
    private val agent = NexoraAiAgent(toolRegistry, decisionGate, contextBuilder)

    /**
     * Process a unified AI request.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        // Run outcome evaluation periodically or before analysis
        learningLoop.evaluateOutcomes()
        
        val context = contextBuilder.build()
        val relevantMemory = memoryRetriever.retrieveRelevantMemory(request)
        
        // Decide if we should use the multi-step Agent
        val response = if (shouldUseAgent(request)) {
            val agentResponse = agent.execute(request, context, relevantMemory)
            logResponseRecommendations(agentResponse)
            agentResponse
        } else {
            val brainResponse = when (request.type) {
                AiRequestType.CHAT -> handleChat(request, context, relevantMemory)
                AiRequestType.NEXT_TASK -> handleNextTask(context, relevantMemory)
                AiRequestType.DAILY_PLAN -> handleDailyPlan(context, relevantMemory)
                AiRequestType.GOAL_ANALYSIS -> handleGoalAnalysis(context, relevantMemory)
                AiRequestType.PRODUCTIVITY_ANALYSIS -> handleProductivityAnalysis(context, relevantMemory)
                AiRequestType.PROACTIVE_ANALYSIS -> handleProactiveAnalysis(context, relevantMemory)
                AiRequestType.GOAL_DECOMPOSITION -> handleGoalDecomposition(request, context)
                
                AiRequestType.CREATE_TASK -> executeDirectAction(AiActionType.CREATE_TASK, request.parameters)
                AiRequestType.UPDATE_TASK -> executeDirectAction(AiActionType.UPDATE_TASK, request.parameters, request.taskId)
                AiRequestType.COMPLETE_TASK -> executeDirectAction(AiActionType.COMPLETE_TASK, emptyMap(), request.taskId)
                AiRequestType.UPDATE_GOAL -> executeDirectAction(AiActionType.UPDATE_GOAL, request.parameters, goalId = request.goalId)
                
                AiRequestType.GENERAL_ANALYSIS -> handleGeneralAnalysis(context, relevantMemory)
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

    private fun shouldUseAgent(request: AiRequest): Boolean {
        if (request.type != AiRequestType.CHAT) return false
        val msg = request.userMessage?.lowercase() ?: ""
        
        // If user says "complete", "finish", "add", "create", etc., they want an action performed.
        // The agent is better at multi-step action resolution.
        return msg.contains("complete") || msg.contains("finish") || 
               msg.contains("add") || msg.contains("create") || msg.contains("goal") || 
               msg.contains("organize") || msg.contains("clean") || msg.contains("yes") || 
               msg.contains("approve") || msg.contains("confirm")
    }

    private suspend fun handleChat(request: AiRequest, context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val message = request.userMessage ?: return AiResponse(AiResponseType.NO_ACTION, "Empty Message", "I didn't receive a message to process.")
        
        // 1. Check if user is asking about memory/productivity specifically
        val isMemoryQuery = message.lowercase().contains(Regex("know|remember|productivity|pattern|history|behavior|status|how am i doing|current situation"))
        if (isMemoryQuery) {
            val personal = context.personalContext
            val statusSummary = buildString {
                append("Here is your current Nexora status:\n\n")
                append("- Workload: ${personal.workload.state} (${personal.workload.taskCount} tasks)\n")
                append("- Day State: ${personal.dayState}\n")
                append("- Productivity Trend: ${personal.productivityTrend}\n")
                
                val atRisk = personal.goalHealth.filter { it.state == GoalHealthState.AT_RISK }
                if (atRisk.isNotEmpty()) {
                    append("- Goal Risks: ${atRisk.joinToString { it.goalTitle }}\n")
                }
                
                if (relevantMemory.isNotEmpty()) {
                    val memorySummary = relevantMemory.joinToString("\n") { "- ${it.title}: ${it.content}" }
                    append("\nHistorical Patterns:\n$memorySummary")
                }
            }

            return AiResponse(
                responseType = AiResponseType.INFORMATION,
                title = "Nexora Context Analysis",
                message = statusSummary,
                confidence = personal.confidence
            )
        }

        // 2. Understand Intent
        val structuredResult = intentResolver.resolve(message, context)
        
        // 2. Map Structured Response to Unified Response
        val response = AiResponse(
            responseType = mapDecisionToResponseType(structuredResult.decision.type),
            title = structuredResult.decision.title,
            message = structuredResult.textResponse ?: structuredResult.decision.reason,
            confidence = mapPriorityToConfidence(structuredResult.decision.priority),
            recommendations = emptyList(), 
            proposedActions = structuredResult.actions,
            relatedTaskId = structuredResult.decision.taskId,
            relatedGoalId = structuredResult.decision.goalId
        )
        
        logResponseRecommendations(response)
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
        
        // Log the main recommendation if it's a recommendation type
        if (response.responseType == AiResponseType.RECOMMENDATION) {
            repository.logRecommendation(
                AiRecommendationHistory(
                    id = java.util.UUID.randomUUID().toString(),
                    type = AiRecommendationType.NEXT_TASK, // Default for next task
                    title = response.title,
                    message = response.message,
                    relatedTaskId = response.relatedTaskId,
                    relatedGoalId = response.relatedGoalId,
                    confidence = response.confidence
                )
            )
        }
    }

    private suspend fun handleNextTask(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val recommendations = planner.analyze(context)
        val nextTaskRec = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        val response = if (nextTaskRec != null) {
            val task = context.tasks.find { it.id == nextTaskRec.relatedTaskId }
            val reasoning = buildTaskReasoning(task, context, relevantMemory)
            
            AiResponse(
                responseType = AiResponseType.RECOMMENDATION,
                title = nextTaskRec.title,
                message = nextTaskRec.message,
                confidence = nextTaskRec.confidence,
                evidence = reasoning,
                relatedTaskId = nextTaskRec.relatedTaskId
            )
        } else {
            AiResponse(
                responseType = AiResponseType.NO_ACTION,
                title = "No Urgent Tasks",
                message = "Nexora didn't find any urgent tasks requiring immediate attention. You're on top of things!"
            )
        }
        
        logResponseRecommendations(response)
        return response
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

        // Add memory insights to the message if relevant
        val memoryInsight = relevantMemory.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN }?.content
        val finalMessage = if (memoryInsight != null) "${plan.summary}\n\nNote: $memoryInsight" else plan.summary

        val response = AiResponse(
            responseType = AiResponseType.PLAN,
            title = "Daily Plan",
            message = finalMessage,
            confidence = AiConfidence.HIGH,
            proposedActions = proposedActions
        )
        
        logResponseRecommendations(response)
        return response
    }

    private suspend fun handleGoalAnalysis(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val recs = planner.analyzeGoals(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.NO_ACTION, "Goal Status", "Your goals are currently on track.")
        
        val response = AiResponse(
            responseType = AiResponseType.RECOMMENDATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence,
            relatedGoalId = best.relatedGoalId
        )
        
        logResponseRecommendations(response)
        return response
    }

    private suspend fun handleProductivityAnalysis(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val recs = planner.analyzeProductivity(context)
        val best = recs.firstOrNull() ?: return AiResponse(AiResponseType.INFORMATION, "Productivity", "Keep working on your tasks to build your productivity history.")
        
        val response = AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = best.title,
            message = best.message,
            confidence = best.confidence
        )
        
        logResponseRecommendations(response)
        return response
    }

    private suspend fun handleProactiveAnalysis(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val signals = proactiveEngine.detectSignals(context)
        
        // Map signals to recommendations for backward compatibility
        val recommendations = signals.map { signal ->
            AiRecommendation(
                type = mapSignalTypeToRecType(signal.type),
                title = signal.title,
                message = signal.message,
                priority = signal.severity,
                confidence = signal.confidence,
                evidence = signal.evidence,
                relatedTaskId = signal.relatedTaskId,
                relatedGoalId = signal.relatedGoalId,
                actionLabel = signal.suggestedAction?.title
            )
        }

        val criticalSignal = signals.find { it.severity == AiPriority.CRITICAL } ?: signals.firstOrNull()
        
        val response = if (criticalSignal != null) {
            AiResponse(
                responseType = if (criticalSignal.severity >= AiPriority.MEDIUM) AiResponseType.WARNING else AiResponseType.INFORMATION,
                title = criticalSignal.title,
                message = criticalSignal.message,
                confidence = criticalSignal.confidence,
                recommendations = recommendations,
                proactiveSignals = signals,
                relatedTaskId = criticalSignal.relatedTaskId,
                relatedGoalId = criticalSignal.relatedGoalId,
                proposedActions = listOfNotNull(criticalSignal.suggestedAction)
            )
        } else {
            AiResponse(AiResponseType.NO_ACTION, "System Healthy", "Nexora hasn't detected any immediate issues with your workflow.")
        }
        
        logResponseRecommendations(response)
        return response
    }

    private fun mapSignalTypeToRecType(type: ProactiveSignalType): AiRecommendationType {
        return when (type) {
            ProactiveSignalType.OVERLOAD -> AiRecommendationType.WARNING
            ProactiveSignalType.NEGLECTED_GOAL -> AiRecommendationType.GOAL_ACTION
            ProactiveSignalType.REPEATED_CARRY_FORWARD -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            ProactiveSignalType.MISSING_NEXT_ACTION -> AiRecommendationType.GOAL_ACTION
            ProactiveSignalType.HIGH_PRIORITY_CONFLICT -> AiRecommendationType.WARNING
            ProactiveSignalType.PRODUCTIVITY_DROP -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            ProactiveSignalType.PRODUCTIVITY_IMPROVEMENT -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            ProactiveSignalType.WORKLOAD_BALANCED -> AiRecommendationType.PRODUCTIVITY_INSIGHT
            else -> AiRecommendationType.GENERAL
        }
    }

    private suspend fun handleGoalDecomposition(request: AiRequest, context: AiContext): AiResponse {
        val goalId = request.goalId
        val goal = context.goals.find { it.id == goalId } 
            ?: request.parameters["title"]?.toString()?.let { title -> context.goals.find { it.title == title } }
            
        if (goal == null) {
            return AiResponse(AiResponseType.CLARIFICATION_NEEDED, "Identify Goal", "I couldn't find which goal to decompose. Please specify a goal title.")
        }

        val result = aiService.decomposeGoal(goal.title, "", goal.category)
        val actions = result.steps.map { step ->
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

        return AiResponse(
            responseType = AiResponseType.ACTION_PROPOSAL,
            title = "Goal Decomposed",
            message = result.summary,
            proposedActions = actions,
            relatedGoalId = goal.id
        )
    }

    private suspend fun handleGeneralAnalysis(context: AiContext, relevantMemory: List<AiMemoryItem>): AiResponse {
        val signals = proactiveEngine.detectSignals(context)
        val recommendations = signals.map { signal ->
            AiRecommendation(
                type = mapSignalTypeToRecType(signal.type),
                title = signal.title,
                message = signal.message,
                priority = signal.severity,
                confidence = signal.confidence,
                evidence = signal.evidence,
                relatedTaskId = signal.relatedTaskId,
                relatedGoalId = signal.relatedGoalId
            )
        }

        val next = planner.analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
        
        val summary = mutableListOf<String>()
        if (next != null) summary.add("Top priority: ${next.title}")
        
        val personal = context.personalContext
        summary.add("Workload: ${personal.workload.state}. Goal Health: ${personal.goalHealth.count { it.state == GoalHealthState.HEALTHY }} healthy.")

        if (signals.isNotEmpty()) summary.add("Detected ${signals.size} proactive items.")
        
        val response = AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = "Nexora Analysis",
            message = if (summary.isEmpty()) "Your workspace is clear." else summary.joinToString("\n"),
            recommendations = recommendations,
            proactiveSignals = signals
        )
        
        logResponseRecommendations(response)
        return response
    }

    private suspend fun executeDirectAction(type: AiActionType, params: Map<String, Any>, taskId: Long? = null, goalId: Long? = null): AiResponse {
        val action = AiAction(
            type = type, 
            title = "Direct Action", 
            description = "", 
            parameters = params, 
            taskId = taskId, 
            goalId = goalId
        )
        val result = actionExecutor.execute(action)
        
        return AiResponse(
            responseType = if (result.success) AiResponseType.INFORMATION else AiResponseType.WARNING,
            title = if (result.success) "Action Succeeded" else "Action Failed",
            message = result.message
        )
    }

    // Reasoning Engine Helpers

    private fun buildTaskReasoning(task: PremiumTask?, context: AiContext, relevantMemory: List<AiMemoryItem>): List<ReasoningFactor> {
        if (task == null) return emptyList()
        val factors = mutableListOf<ReasoningFactor>()

        // 1. Priority
        factors.add(ReasoningFactor("Priority", 
            if (task.priority == com.example.nexora.uii.TaskPriority.URGENT) ReasoningImpact.CRITICAL else ReasoningImpact.POSITIVE,
            "Task is marked as ${task.priority}."
        ))

        // 2. Goal Alignment
        val goal = context.activeGoals.find { it.title == task.goalTitle }
        if (goal != null) {
            factors.add(ReasoningFactor("Goal Alignment", ReasoningImpact.POSITIVE, 
                "Contributes to '${goal.title}' (${(goal.progress * 100).toInt()}% progress)."))
            
            if (goal.progress < 0.3f) {
                factors.add(ReasoningFactor("Goal Momentum", ReasoningImpact.POSITIVE, "This goal needs early momentum."))
            }
        }

        // 3. Workload Fit
        val profile = context.adaptiveProfile
        val duration = extractDurationMinutes(task.duration)
        if (profile.confidence != AdaptiveConfidence.UNKNOWN) {
            if (profile.preferredTaskSize == "Small" && duration <= 30) {
                factors.add(ReasoningFactor("Workload Fit", ReasoningImpact.POSITIVE, "Fits your pattern of completing smaller tasks."))
            } else if (profile.preferredTaskSize == "Large" && duration > 60) {
                factors.add(ReasoningFactor("Workload Fit", ReasoningImpact.POSITIVE, "Matches your deep work preference."))
            }
        }
        
        // 4. Memory-based reasoning
        relevantMemory.forEach { memory ->
            if (memory.category == AiMemoryCategory.TASK_PATTERN && memory.relatedTaskId == task.id) {
                factors.add(ReasoningFactor("Historical Behavior", ReasoningImpact.NEUTRAL, memory.content))
            }
        }

        return factors
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: return 30
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
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
            AiDecisionType.NO_ACTION -> AiResponseType.NO_ACTION
        }
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
