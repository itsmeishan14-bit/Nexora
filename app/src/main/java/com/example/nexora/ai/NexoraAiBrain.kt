package com.example.nexora.ai

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
    private val intentResolver: LocalAiIntentResolver = LocalAiIntentResolver()
) {
    private val planner = AiPlanner()

    /**
     * Process a unified AI request.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        val context = contextBuilder.build()

        return when (request.type) {
            AiRequestType.CHAT -> handleChat(request, context)
            AiRequestType.NEXT_TASK -> handleNextTask(context)
            AiRequestType.DAILY_PLAN -> handleDailyPlan(context)
            AiRequestType.GOAL_ANALYSIS -> handleGoalAnalysis(context)
            AiRequestType.PRODUCTIVITY_ANALYSIS -> handleProductivityAnalysis(context)
            AiRequestType.PROACTIVE_ANALYSIS -> handleProactiveAnalysis(context)
            AiRequestType.GOAL_DECOMPOSITION -> handleGoalDecomposition(request, context)
            
            AiRequestType.CREATE_TASK -> executeDirectAction(AiActionType.CREATE_TASK, request.parameters)
            AiRequestType.UPDATE_TASK -> executeDirectAction(AiActionType.UPDATE_TASK, request.parameters, request.taskId)
            AiRequestType.COMPLETE_TASK -> executeDirectAction(AiActionType.COMPLETE_TASK, emptyMap(), request.taskId)
            AiRequestType.UPDATE_GOAL -> executeDirectAction(AiActionType.UPDATE_GOAL, request.parameters, goalId = request.goalId)
            
            AiRequestType.GENERAL_ANALYSIS -> handleGeneralAnalysis(context)
        }
    }

    private fun handleChat(request: AiRequest, context: AiContext): AiResponse {
        val message = request.userMessage ?: return AiResponse(AiResponseType.NO_ACTION, "Empty Message", "I didn't receive a message to process.")
        
        // 1. Understand Intent
        val structuredResult = intentResolver.resolve(message, context)
        
        // 2. Map Structured Response to Unified Response
        return AiResponse(
            responseType = mapDecisionToResponseType(structuredResult.decision.type),
            title = structuredResult.decision.title,
            message = structuredResult.textResponse ?: structuredResult.decision.reason,
            confidence = mapPriorityToConfidence(structuredResult.decision.priority),
            recommendations = emptyList(), 
            proposedActions = structuredResult.actions,
            relatedTaskId = structuredResult.decision.taskId,
            relatedGoalId = structuredResult.decision.goalId
        )
    }

    private fun handleNextTask(context: AiContext): AiResponse {
        val recommendations = planner.analyze(context)
        val nextTaskRec = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        return if (nextTaskRec != null) {
            val task = context.tasks.find { it.id == nextTaskRec.relatedTaskId }
            val reasoning = buildTaskReasoning(task, context)
            
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
    }

    private fun handleDailyPlan(context: AiContext): AiResponse {
        val plan = planner.createDailyPlan(context)
        val proposedActions = plan.tasks.map { 
            AiAction(
                type = AiActionType.OPEN_TASK, 
                title = it.task.title, 
                description = "Recommended as part of your daily plan.", 
                taskId = it.task.id
            )
        }

        return AiResponse(
            responseType = AiResponseType.PLAN,
            title = "Daily Plan",
            message = plan.summary,
            confidence = AiConfidence.HIGH,
            proposedActions = proposedActions
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
            confidence = best.confidence
        )
    }

    private fun handleProactiveAnalysis(context: AiContext): AiResponse {
        val insights = planner.getProactiveInsights(context)
        val critical = insights.find { it.priority == AiPriority.CRITICAL } ?: insights.firstOrNull()
        
        return if (critical != null) {
            AiResponse(
                responseType = AiResponseType.WARNING,
                title = critical.title,
                message = critical.message,
                confidence = critical.confidence,
                recommendations = insights,
                relatedTaskId = critical.relatedTaskId,
                relatedGoalId = critical.relatedGoalId
            )
        } else {
            AiResponse(AiResponseType.NO_ACTION, "System Healthy", "Nexora hasn't detected any immediate issues with your workflow.")
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

    private fun handleGeneralAnalysis(context: AiContext): AiResponse {
        val insights = planner.getProactiveInsights(context)
        val next = planner.analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
        
        val summary = mutableListOf<String>()
        if (next != null) summary.add("Top priority: ${next.title}")
        if (insights.isNotEmpty()) summary.add("Detected ${insights.size} items requiring attention.")
        
        return AiResponse(
            responseType = AiResponseType.INFORMATION,
            title = "Nexora Analysis",
            message = if (summary.isEmpty()) "Your workspace is clear." else summary.joinToString("\n"),
            recommendations = insights
        )
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

    private fun buildTaskReasoning(task: PremiumTask?, context: AiContext): List<ReasoningFactor> {
        if (task == null) return emptyList()
        val factors = mutableListOf<ReasoningFactor>()

        // 1. Priority
        factors.add(ReasoningFactor("Priority", 
            if (task.priority == TaskPriority.URGENT) ReasoningImpact.CRITICAL else ReasoningImpact.POSITIVE,
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
            AiDecisionType.DAILY_PLAN -> AiResponseType.PLAN
            AiDecisionType.SHOW_INSIGHT -> AiResponseType.INFORMATION
            AiDecisionType.WARNING -> AiResponseType.WARNING
            AiDecisionType.AMBIGUOUS -> AiResponseType.CLARIFICATION_NEEDED
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
