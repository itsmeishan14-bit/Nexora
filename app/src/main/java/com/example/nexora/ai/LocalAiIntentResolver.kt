package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class LocalAiIntentResolver(
    private val automationSystem: NexoraAutomationSystem = NexoraAutomationSystem()
) {

    private val pipeline = AdvancedLocalLanguagePipeline()
    private var conversationContext = AiConversationContext()

    fun getAutomationSystem(): NexoraAutomationSystem = automationSystem

    /**
     * Resolves natural language queries into structured AI responses using local heuristics.
     * Operates entirely offline without external APIs.
     */
    fun resolve(query: String, context: AiContext, externalConvContext: AiConversationContext? = null): AiModelStructuredResponse {
        val effectiveConvContext = when {
            externalConvContext != null -> externalConvContext
            !conversationContext.isExpired() -> conversationContext
            else -> AiConversationContext()
        }
        
        conversationContext = effectiveConvContext

        val langResult = pipeline.process(query, context, conversationContext)
        
        val response = when {
            langResult.isConfirmation && conversationContext.pendingAction != null -> {
                handleConfirmation(conversationContext.pendingAction!!, context)
            }
            langResult.isCancellation -> handleCancel()
            else -> when (langResult.intent) {
                AiDecisionType.EXPLANATION -> handleExplanation(query, langResult)
                AiDecisionType.SHOW_INSIGHT -> handleShowInsight(langResult, query, context)
                AiDecisionType.CREATE_TASK -> handleCreateTask(langResult, context)
                AiDecisionType.COMPLETE_TASK -> handleCompleteTask(langResult, context)
                AiDecisionType.DELETE_TASK -> handleDeleteTask(langResult, context)
                AiDecisionType.DELETE_GOAL -> handleDeleteGoal(langResult, context)
                AiDecisionType.UPDATE_TASK -> handleUpdateTask(langResult, context)
                AiDecisionType.CREATE_GOAL -> handleCreateGoal(langResult, context)
                AiDecisionType.DECOMPOSE_GOAL -> handleDecomposeGoal(langResult, context)
                AiDecisionType.DAILY_PLAN -> planDay(context, AiPlanner())
                AiDecisionType.START_TASK -> nextTask(context, AiPlanner())
                AiDecisionType.UPDATE_GOAL -> decomposeGoal(query, context)
                AiDecisionType.CLARIFY -> handleClarify(langResult)
                AiDecisionType.CANCEL -> handleCancel()
                AiDecisionType.DELETE_ALL_TASKS -> handleDeleteAllTasks(context)
                AiDecisionType.COMPLETE_ALL_TASKS -> handleCompleteAllTasks(context)
                AiDecisionType.LIST_AUTOMATIONS -> handleListAutomations()
                AiDecisionType.EXPLAIN_AUTOMATION -> handleExplainAutomation(query)
                AiDecisionType.TOGGLE_AUTOMATION -> handleToggleAutomation(query)
                AiDecisionType.DELETE_AUTOMATION -> handleDeleteAutomation(query)
                AiDecisionType.CREATE_AUTOMATION -> handleCreateAutomation(query)
                AiDecisionType.GREETING -> handleGreeting(query)
                AiDecisionType.GENERAL_CONVERSATION -> handleGeneralConversation(query)
                AiDecisionType.THANKS -> handleThanks()
                AiDecisionType.GOODBYE -> handleGoodbye()
                AiDecisionType.PREDICT_GOAL -> handlePredictGoal(query, context)
                AiDecisionType.PREDICT_TASK_RISK -> handlePredictTaskRisk(query, context)
                AiDecisionType.PREDICT_WORKLOAD -> handlePredictWorkload(query, context)
                AiDecisionType.PREDICT_PRODUCTIVITY -> handlePredictProductivity(query, context)
                else -> noAction(query, context, AiPlanner())
            }
        }

        updateConversationContext(langResult, response)
        return response.copy(conversationContext = conversationContext)
    }

    private fun handleConfirmation(action: AiAction, context: AiContext): AiModelStructuredResponse {
        // We mark it as authorized by setting userConfirmed = true in parameters
        val authorizedParams = action.parameters.toMutableMap()
        authorizedParams["userConfirmed"] = true
        
        val authorizedAction = action.copy(
            requiresConfirmation = false,
            parameters = authorizedParams
        )

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = mapActionToDecision(action.type),
                title = "Executing Action",
                reason = "Executing previously proposed action after confirmation."
            ),
            actions = listOf(authorizedAction),
            textResponse = "Proceeding with ${action.title.lowercase()} as requested.",
            modelName = "local-heuristic"
        )
    }

    private fun mapActionToDecision(type: AiActionType): AiDecisionType {
        return when (type) {
            AiActionType.CREATE_TASK -> AiDecisionType.CREATE_TASK
            AiActionType.COMPLETE_TASK -> AiDecisionType.COMPLETE_TASK
            AiActionType.UPDATE_TASK -> AiDecisionType.UPDATE_TASK
            AiActionType.DELETE_TASK -> AiDecisionType.DELETE_TASK
            AiActionType.RESCHEDULE_TASK -> AiDecisionType.RESCHEDULE_TASK
            AiActionType.CREATE_GOAL -> AiDecisionType.CREATE_GOAL
            AiActionType.UPDATE_GOAL -> AiDecisionType.UPDATE_GOAL
            AiActionType.DELETE_GOAL -> AiDecisionType.DELETE_GOAL
            AiActionType.DECOMPOSE_GOAL -> AiDecisionType.DECOMPOSE_GOAL
            AiActionType.SHOW_INSIGHT -> AiDecisionType.SHOW_INSIGHT
            AiActionType.OPEN_TASK -> AiDecisionType.START_TASK
            AiActionType.OPEN_GOAL -> AiDecisionType.UPDATE_GOAL
            AiActionType.DELETE_ALL_TASKS -> AiDecisionType.DELETE_ALL_TASKS
            AiActionType.COMPLETE_ALL_TASKS -> AiDecisionType.COMPLETE_ALL_TASKS
            AiActionType.CREATE_AUTOMATION -> AiDecisionType.CREATE_AUTOMATION
            AiActionType.TOGGLE_AUTOMATION -> AiDecisionType.TOGGLE_AUTOMATION
            AiActionType.DELETE_AUTOMATION -> AiDecisionType.DELETE_AUTOMATION
        }
    }


    private fun handleCancel(): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CANCEL,
                title = "Action Cancelled",
                reason = "Cancelled the current operation."
            ),
            textResponse = "Okay, I've cancelled that. What else can I help with?",
            modelName = "local-heuristic"
        )
    }

    private fun updateConversationContext(langResult: AiLanguageResult, response: AiModelStructuredResponse) {
        conversationContext = AiConversationContext(
            lastIntent = langResult.intent,
            lastTaskId = response.decision.taskId ?: conversationContext.lastTaskId,
            lastGoalId = response.decision.goalId ?: conversationContext.lastGoalId,
            lastEntityTitle = response.decision.taskTitle ?: langResult.entities["title"]?.toString() ?: conversationContext.lastEntityTitle,
            activeClarification = langResult.clarificationNeeded,
            pendingAction = response.actions.firstOrNull()?.takeIf { it.requiresConfirmation },
            candidateIds = response.candidateTaskIds.takeIf { it.isNotEmpty() } ?: conversationContext.candidateIds
        )
    }

    private fun handleShowInsight(langResult: AiLanguageResult, query: String, context: AiContext): AiModelStructuredResponse {
        val q = query.lowercase().trim()
        val entityQuery = langResult.entities["query"]?.toString()?.lowercase() ?: ""

        // 1. Follow-up conversational reasoning: "why?" / "why is it behind?"
        if (q == "why" || q == "why?" || q.startsWith("why is it") || q.startsWith("why is that")) {
            val lastGoalId = conversationContext.lastGoalId
            if (lastGoalId != null) {
                val goal = context.goals.find { it.id == lastGoalId }
                if (goal != null) {
                    val linkedIncomplete = context.tasks.filter { it.goalTitle == goal.title && !it.completed }
                    val linkedCompleted = context.tasks.filter { it.goalTitle == goal.title && it.completed }
                    val daysRemaining = parseTargetDateDaysRemaining(goal.targetDate)
                    val deadlineMsg = if (daysRemaining != null) " Target date is in $daysRemaining days." else ""
                    val explanation = "Your \"${goal.title}\" goal is behind because it has had no completed linked tasks recently (${linkedCompleted.size} completed, ${linkedIncomplete.size} pending).$deadlineMsg Progress is currently at ${(goal.progress * 100).toInt()}%."
                    return AiModelStructuredResponse(
                        decision = AiDecision(
                            type = AiDecisionType.SHOW_INSIGHT,
                            title = "Goal Status: ${goal.title}",
                            reason = explanation,
                            goalId = goal.id,
                            taskTitle = goal.title
                        ),
                        textResponse = explanation,
                        modelName = "local-heuristic"
                    )
                }
            }
            
            val lastTaskId = conversationContext.lastTaskId
            if (lastTaskId != null) {
                val task = context.tasks.find { it.id == lastTaskId }
                if (task != null) {
                    val explanation = "\"${task.title}\" is prioritized based on ${task.priority.name.lowercase()} priority and duration (${task.duration})."
                    return AiModelStructuredResponse(
                        decision = AiDecision(
                            type = AiDecisionType.SHOW_INSIGHT,
                            title = "Task Reasoning: ${task.title}",
                            reason = explanation,
                            taskId = task.id,
                            taskTitle = task.title
                        ),
                        textResponse = explanation,
                        modelName = "local-heuristic"
                    )
                }
            }

            // General "why am I falling behind"
            val carried = context.carriedTasks
            val workload = context.personalContext.workload.state
            val reason = "You are falling behind because your workload ($workload, ${context.incompleteTasks.size} tasks) exceeds typical capacity, and you have $carried carried-forward tasks."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Workload Assessment",
                    reason = reason
                ),
                textResponse = reason,
                modelName = "local-heuristic"
            )
        }

        // 2. Goal Triage / Falling behind
        if (q.contains("which goal") && (q.contains("behind") || q.contains("stagnat") || q.contains("risk") || q.contains("least"))) {
            val activeGoals = context.activeGoals
            if (activeGoals.isEmpty()) {
                return notFoundResult("You don't have any active goals configured.")
            }
            val behindGoal = activeGoals.minByOrNull { it.progress } ?: activeGoals.first()
            val text = "Your \"${behindGoal.title}\" goal has received the least recent activity (${(behindGoal.progress * 100).toInt()}% progress)."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Goal Needing Attention",
                    reason = text,
                    goalId = behindGoal.id,
                    taskTitle = behindGoal.title
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        // 3. Specific Goal Inspection ("how is my Java goal doing", "is my Java goal on track", "show my Java goal")
        if (q.contains("goal") && (q.contains("how is") || q.contains("on track") || q.contains("status") || q.contains("show") || q.contains("view") || q.contains("need") || q.contains("should i"))) {
            val goalTitle = langResult.entities["title"]?.toString() ?: entityQuery
            val resolution = AiEntityResolver.resolveGoal(goalTitle, context.goals, conversationContext)
            if (resolution is ResolutionResult.Success) {
                val goal = resolution.entity
                val linked = context.tasks.filter { it.goalTitle == goal.title }
                val done = linked.count { it.completed }
                val pending = linked.count { !it.completed }
                val text = "Goal \"${goal.title}\" is at ${(goal.progress * 100).toInt()}% progress ($done completed tasks, $pending pending). Target date: ${goal.targetDate}."
                return AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.SHOW_INSIGHT,
                        title = "Goal: ${goal.title}",
                        reason = text,
                        goalId = goal.id,
                        taskTitle = goal.title
                    ),
                    textResponse = text,
                    modelName = "local-heuristic"
                )
            }
        }

        // 4. Temporal Insights (Yesterday, Carried, Weekly)
        if (q.contains("yesterday") || q.contains("accomplish")) {
            val yesterdayStr = java.time.LocalDate.now().minusDays(1).toString()
            val completedCount = context.completedTasks.size
            val text = "Yesterday ($yesterdayStr): According to your records, you had $completedCount completed tasks."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Yesterday's Accomplishments",
                    reason = text
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        if (q.contains("carrying") || q.contains("carried")) {
            val text = "You have ${context.carriedTasks} task(s) carried forward from yesterday. Resolving the highest-priority carried task first will help clear focus."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Carried Tasks",
                    reason = text
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        if (q.contains("this week") || q.contains("last week")) {
            val trend = context.personalContext.productivityTrend.name.lowercase()
            val text = "This week: You have completed ${context.completedTasks.size} tasks, with ${context.incompleteTasks.size} active tasks remaining and a $trend productivity trend."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Weekly Overview",
                    reason = text
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        if (q.contains("why am i") || q.contains("falling behind") || q.contains("behind")) {
            val carried = context.carriedTasks
            val workload = context.personalContext.workload.state
            val text = "Based on your recent activity: You have $carried tasks carried forward, and your workload is currently $workload. Focusing on one high-impact task at a time will help you get back on track."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Productivity Insights",
                    reason = text
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        if (q.contains("remember") || q.contains("memory")) {
            val text = "I've stored that observation in your active profile to refine future recommendations."
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "Learned Memory",
                    reason = text
                ),
                textResponse = text,
                modelName = "local-heuristic"
            )
        }

        return when {
            q.contains("goal") -> listGoals(context)
            q.contains("productivity") || q.contains("pattern") -> showProductivity(context, AiPlanner())
            q.contains("progress") -> showProgress(context)
            else -> listTasks(context)
        }
    }

    private fun handleCreateTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val title = langResult.entities["title"]?.toString() ?: ""
        
        if (title.isBlank()) {
            return AiModelStructuredResponse(
                decision = AiDecision(AiDecisionType.CLARIFY, "Missing Title", "What should the task be called?"),
                textResponse = "What should the task be called?",
                modelName = "local-heuristic"
            )
        }

        // Duplicate Check
        val normalizedTitle = title.lowercase().trim()
        val duplicate = context.tasks.find { it.title.lowercase().trim() == normalizedTitle && !it.completed }
        if (duplicate != null) {
            return AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.NO_ACTION,
                    title = "Duplicate Task",
                    reason = "A similar active task already exists: \"${duplicate.title}\""
                ),
                textResponse = "A similar active task already exists: \"${duplicate.title}\"",
                modelName = "local-heuristic"
            )
        }

        val duration = langResult.entities["duration"]?.toString() ?: "30 minutes"
        val priorityStr = langResult.entities["priority"]?.toString() ?: "MEDIUM"
        val priority = try { AiPriority.valueOf(priorityStr) } catch (e: Exception) { AiPriority.MEDIUM }

        val goalTitle = langResult.entities["goalTitle"]?.toString() ?: langResult.entities["goal"]?.toString()
        val resolvedGoal = if (goalTitle != null) {
            (AiEntityResolver.resolveGoal(goalTitle, context.goals) as? ResolutionResult.Success)?.entity?.title
        } else null

        val descriptionText = buildString {
            append("Add \"$title\" ($duration, ${priority.name.lowercase()} priority)")
            if (resolvedGoal != null) append(" for goal \"$resolvedGoal\"")
            append("?")
        }

        val params = mutableMapOf<String, Any>(
            "title" to title,
            "duration" to duration,
            "priority" to mapAiPriorityToTaskPriority(priority).name
        )
        if (resolvedGoal != null) params["goalTitle"] = resolvedGoal

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CREATE_TASK,
                title = "Create Task",
                reason = "Adding new task: $title",
                taskTitle = title,
                priority = priority
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.CREATE_TASK,
                    title = "Create Task",
                    description = descriptionText,
                    parameters = params,
                    requiresConfirmation = true
                )
            ),
            textResponse = "I can create a task for \"$title\" ($duration, ${priority.name.lowercase()} priority). Should I proceed?",
            modelName = "local-heuristic"
        )
    }

    private fun handleCompleteTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val taskId = langResult.entities["taskId"] as? Long
        if (taskId != null) {
            val task = context.tasks.find { it.id == taskId } ?: return notFoundResult("I couldn't find that task.")
            if (task.completed) {
                return AiModelStructuredResponse(
                    decision = AiDecision(AiDecisionType.NO_ACTION, "Already Completed", "\"${task.title}\" is already completed."),
                    textResponse = "\"${task.title}\" is already completed.",
                    modelName = "local-heuristic"
                )
            }
            return buildCompleteAction(task)
        }

        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = AiEntityResolver.resolveTask(title, context.tasks, conversationContext)
        
        return when (resolution) {
            is ResolutionResult.Success -> {
                val task = resolution.entity
                if (task.completed) {
                    AiModelStructuredResponse(
                        decision = AiDecision(AiDecisionType.NO_ACTION, "Already Completed", "\"${task.title}\" is already completed."),
                        textResponse = "\"${task.title}\" is already completed.",
                        modelName = "local-heuristic"
                    )
                } else {
                    buildCompleteAction(task)
                }
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("I found multiple tasks matching \"$title\". Which one should I mark as complete?", resolution.candidates.map { it.id })
            else -> notFoundResult("I couldn't find a task matching \"$title\".")
        }
    }

    private fun buildCompleteAction(task: com.example.nexora.uii.PremiumTask): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.COMPLETE_TASK,
                title = "Complete Task",
                reason = "Mark ${task.title} as complete",
                taskId = task.id
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.COMPLETE_TASK,
                    title = "Complete Task",
                    description = "Mark \"${task.title}\" as complete?",
                    taskId = task.id,
                    requiresConfirmation = true
                )
            ),
            textResponse = "I found the task \"${task.title}\". Should I mark it as complete?",
            modelName = "local-heuristic"
        )
    }

    private fun handleUpdateTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val taskId = langResult.entities["taskId"] as? Long
        val title = langResult.entities["title"]?.toString() ?: ""
        
        val resolution = if (taskId != null) {
            context.tasks.find { it.id == taskId }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            AiEntityResolver.resolveTask(title, context.tasks, conversationContext)
        }

        return when (resolution) {
            is ResolutionResult.Success -> {
                val task = resolution.entity
                val newPriorityStr = langResult.entities["priority"]?.toString()
                val newDuration = langResult.entities["duration"]?.toString()

                val params = mutableMapOf<String, Any>()
                if (newPriorityStr != null) params["priority"] = newPriorityStr
                if (newDuration != null) params["duration"] = newDuration

                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.UPDATE_TASK,
                        title = "Update Task",
                        reason = "Updating ${task.title}",
                        taskId = task.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.UPDATE_TASK,
                            title = "Update Task",
                            description = "Update \"${task.title}\"? ${params.entries.joinToString { "${it.key}: ${it.value}" }}",
                            taskId = task.id,
                            parameters = params,
                            requiresConfirmation = true
                        )
                    ),
                    textResponse = "I can update \"${task.title}\". Should I proceed?",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Multiple tasks match \"$title\". Which task should I update?", resolution.candidates.map { it.id })
            else -> notFoundResult("I couldn't find a task matching \"$title\".")
        }
    }

    private fun handleDeleteTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = AiEntityResolver.resolveTask(title, context.tasks, conversationContext)
        
        return when (resolution) {
            is ResolutionResult.Success -> {
                val task = resolution.entity
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.DELETE_TASK,
                        title = "Delete Task",
                        reason = "Permanently delete ${task.title}",
                        taskId = task.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.DELETE_TASK,
                            title = "Delete Task",
                            description = "Are you sure you want to delete \"${task.title}\"?",
                            taskId = task.id,
                            priority = AiPriority.HIGH,
                            requiresConfirmation = true
                        )
                    ),
                    textResponse = "I found the task \"${task.title}\". Do you want me to delete it?",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Multiple tasks match \"$title\". Which one should I delete?", resolution.candidates.map { it.id })
            else -> notFoundResult("I couldn't find a task matching \"$title\".")
        }
    }

    private fun handleDeleteAllTasks(context: AiContext): AiModelStructuredResponse {
        val count = context.tasks.size
        if (count == 0) return notFoundResult("There are no tasks to delete.")

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.DELETE_ALL_TASKS,
                title = "Delete All Tasks",
                reason = "Permanently delete all $count tasks."
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.DELETE_ALL_TASKS,
                    title = "Delete All Tasks",
                    description = "I found $count tasks. This will permanently delete all of them. Are you sure you want to continue?",
                    priority = AiPriority.CRITICAL,
                    requiresConfirmation = true
                )
            ),
            textResponse = "I can delete all $count tasks for you. This cannot be undone. Should I proceed?",
            modelName = "local-heuristic"
        )
    }

    private fun handleCompleteAllTasks(context: AiContext): AiModelStructuredResponse {
        val incompleteCount = context.incompleteTasks.size
        if (incompleteCount == 0) return notFoundResult("All tasks are already completed.")

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.COMPLETE_ALL_TASKS,
                title = "Complete All Tasks",
                reason = "Mark all $incompleteCount incomplete tasks as finished."
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.COMPLETE_ALL_TASKS,
                    title = "Complete All Tasks",
                    description = "Mark all $incompleteCount incomplete tasks as complete?",
                    requiresConfirmation = true
                )
            ),
            textResponse = "I found $incompleteCount incomplete tasks. Should I mark them all as complete?",
            modelName = "local-heuristic"
        )
    }

    private fun handleClarify(langResult: AiLanguageResult): AiModelStructuredResponse {
        val clar = langResult.clarificationNeeded ?: return notFoundResult("I'm not sure how to proceed.")
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CLARIFY,
                title = "Clarification Needed",
                reason = clar.question
            ),
            textResponse = clar.question,
            candidateTaskIds = clar.candidates,
            modelName = "local-heuristic"
        )
    }

    private fun listTasks(context: AiContext): AiModelStructuredResponse {
        val textResponse = if (context.incompleteTasks.isEmpty()) {
            "You have no incomplete tasks. It's a great time to plan something new!"
        } else {
            val taskList = context.incompleteTasks.take(5).joinToString("\n") { "- ${it.title} (${it.priority.name.lowercase().replaceFirstChar { it.uppercase() }})" }
            val count = context.incompleteTasks.size
            val header = if (count > 5) "You have $count incomplete tasks. Here are the top 5:" else "You have $count incomplete tasks:"
            "$header\n\n$taskList"
        }
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "Task Overview",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun listGoals(context: AiContext): AiModelStructuredResponse {
        val textResponse = if (context.activeGoals.isEmpty()) {
            "You don't have any active goals yet. Setting a goal helps Nexora provide better recommendations."
        } else {
            val goalList = context.activeGoals.joinToString("\n") { "- ${it.title} (${(it.progress * 100).toInt()}% progress)" }
            "You are currently working toward ${context.activeGoals.size} active goals:\n\n$goalList"
        }
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "Goal Overview",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun showProgress(context: AiContext): AiModelStructuredResponse {
        val progress = if (context.tasksPlannedToday > 0) {
            (context.tasksCompletedToday.toFloat() / context.tasksPlannedToday * 100).toInt()
        } else 0
        
        val textResponse = when {
            context.tasksPlannedToday == 0 -> "You haven't planned any tasks for today yet."
            progress >= 100 -> "Incredible! You've completed all of your planned tasks for today."
            progress >= 50 -> "You're making solid progress, with $progress% of your plan completed."
            else -> "You've completed $progress% of your planned tasks today. Focus on one small step next."
        }
        
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "Today's Progress",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun showProductivity(context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val hasData = context.memory.items.isNotEmpty() || (context.memory.legacyPatterns.isNotEmpty() && context.memory.legacyPatterns.none { it.type == AiPatternType.INSUFFICIENT_DATA })
        
        val textResponse = if (!hasData) {
            "I'm still learning your productivity style. Keep using Nexora and I'll soon be able to show your consistency patterns."
        } else {
            val itemList = context.memory.items.joinToString("\n") { "- ${it.title}: ${it.content}" }
            val patternList = context.memory.legacyPatterns.joinToString("\n") { "- ${it.title}: ${it.description}" }
            "Here is what I've learned about your productivity recently:\n\n$itemList\n$patternList".trim()
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "Productivity Analysis",
                reason = "Reviewing your patterns."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handleCreateGoal(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val title = langResult.entities["title"]?.toString() ?: ""
        
        if (title.isBlank()) {
            return AiModelStructuredResponse(
                decision = AiDecision(AiDecisionType.CLARIFY, "Missing Title", "What should the goal be called?"),
                textResponse = "What should the goal be called?",
                modelName = "local-heuristic"
            )
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CREATE_GOAL,
                title = "Create Goal",
                reason = "Creating new goal: $title",
                actionLabel = "Create"
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.CREATE_GOAL,
                    title = "Create Goal",
                    description = "Create goal \"$title\"?",
                    parameters = mapOf("title" to title),
                    requiresConfirmation = true
                )
            ),
            textResponse = "I can create the goal \"$title\" for you. Should I proceed?",
            modelName = "local-heuristic"
        )
    }

    private fun handleDecomposeGoal(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val goalId = langResult.entities["goalId"] as? Long
        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = if (goalId != null) {
            context.goals.find { it.id == goalId }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            AiEntityResolver.resolveGoal(title, context.goals, conversationContext)
        }
        
        return when (resolution) {
            is ResolutionResult.Success -> {
                val goal = resolution.entity
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.DECOMPOSE_GOAL,
                        title = "Decompose Goal",
                        reason = "Breaking down ${goal.title} into steps.",
                        goalId = goal.id,
                        taskTitle = goal.title
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.DECOMPOSE_GOAL,
                            title = "Decompose Goal",
                            description = "Break down goal \"${goal.title}\" into tasks?",
                            goalId = goal.id,
                            requiresConfirmation = false
                        )
                    ),
                    textResponse = "I'll break down the goal \"${goal.title}\" into actionable steps.",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Which goal should I break down?", resolution.candidates.map { it.id })
            else -> notFoundResult("I couldn't find the goal you want to break down.")
        }
    }

    private fun handleDeleteGoal(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val goalId = langResult.entities["goalId"] as? Long
        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = if (goalId != null) {
            context.goals.find { it.id == goalId }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            AiEntityResolver.resolveGoal(title, context.goals, conversationContext)
        }
        
        return when (resolution) {
            is ResolutionResult.Success -> {
                val goal = resolution.entity
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.DELETE_GOAL,
                        title = "Delete Goal",
                        reason = "Permanently delete goal \"${goal.title}\"",
                        goalId = goal.id,
                        taskTitle = goal.title
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.DELETE_GOAL,
                            title = "Delete Goal",
                            description = "Are you sure you want to delete goal \"${goal.title}\"?",
                            goalId = goal.id,
                            priority = AiPriority.HIGH,
                            requiresConfirmation = true
                        )
                    ),
                    textResponse = "I found the goal \"${goal.title}\". Do you want me to delete it?",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Multiple goals match \"$title\". Which one should I delete?", resolution.candidates.map { it.id })
            else -> notFoundResult("I couldn't find a goal matching \"$title\".")
        }
    }

    private fun planDay(context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val plan = planner.createDailyPlan(context)
        val textResponse = if (plan.tasks.isEmpty()) {
            plan.summary
        } else {
            val taskList = plan.tasks.joinToString("\n") { 
                "${it.recommendedOrder}. ${it.task.title} (${it.task.duration})" 
            }
            "${plan.summary}\n\n$taskList"
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.DAILY_PLAN,
                title = "Daily Plan",
                reason = "Generating your daily plan based on priorities and goals."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun nextTask(context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val recommendations = planner.analyze(context)
        val next = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        val textResponse = if (next != null) {
            val task = context.tasks.find { it.id == next.relatedTaskId }
            val factors = next.evidence.joinToString("; ") { it.evidence }
            val why = if (factors.isNotBlank()) factors else next.message
            val goalConn = if (task?.goalTitle != null) " Linked to goal '${task.goalTitle}'." else ""
            val effort = if (task != null) " Estimated effort: ${task.duration}." else ""
            
            "Next recommended task: \"${next.title}\".\nWhy: $why$goalConn$effort"
        } else {
            "You have no urgent tasks. Consider reviewing your goals or planning for tomorrow."
        }
        
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.START_TASK,
                title = next?.title ?: "Next Task",
                reason = textResponse,
                taskId = next?.relatedTaskId,
                confidence = next?.confidence ?: AiConfidence.MEDIUM
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun decomposeGoal(query: String, context: AiContext): AiModelStructuredResponse {
        val goalIdMatch = Regex("Goal ID (\\d+)").find(query)
        val result = if (goalIdMatch != null) {
            val id = goalIdMatch.groupValues[1].toLong()
            context.goals.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val goalQuery = query.replace(Regex("(?i)\\b(break down|decompose|goal|steps|objective)\\b"), "").trim()
            AiEntityResolver.resolveGoal(goalQuery, context.goals)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.UPDATE_GOAL,
                        title = "Decompose Goal",
                        reason = "Breaking down ${result.entity.title} into actionable steps.",
                        goalId = result.entity.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.DECOMPOSE_GOAL,
                            title = "Decompose Goal",
                            description = "Break down goal \"${result.entity.title}\" into tasks?",
                            goalId = result.entity.id,
                            requiresConfirmation = false
                        )
                    ),
                    textResponse = "I'll break down the goal \"${result.entity.title}\" into a practical sequence of steps for you.",
                    modelName = "local-heuristic"
                )
            }
            else -> notFoundResult("I couldn't find the goal you wanted to break down.")
        }
    }

    private fun handleGreeting(query: String): AiModelStructuredResponse {
        val q = query.lowercase()
        val textResponse = when {
            q.contains("morning") -> "Good morning! Ready to plan your day or tackle your tasks?"
            q.contains("afternoon") -> "Good afternoon! What can I help you work on today?"
            q.contains("evening") -> "Good evening! What would you like to review or accomplish?"
            else -> "Hey! I'm Nexora, your AI Personal Operating System. What can I help you with today?"
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.GREETING,
                title = "Greeting",
                reason = "Responded to user greeting."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handleGeneralConversation(query: String): AiModelStructuredResponse {
        val q = query.lowercase()
        val textResponse = if (q.contains("how are you")) {
            "I'm running smoothly and ready to assist! How can I help you with your tasks or goals today?"
        } else {
            "I'm Nexora, an AI-first Personal Operating System. I can help you manage tasks and goals, plan your day, analyze your productivity patterns, automate workflows, and execute actions on your behalf when you approve them."
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.GENERAL_CONVERSATION,
                title = "Nexora Capabilities",
                reason = "Explained assistant capabilities."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handleThanks(): AiModelStructuredResponse {
        val textResponse = "You're welcome! Let me know whenever you need further assistance."
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.THANKS,
                title = "Acknowledgement",
                reason = "Responded to thanks."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handleGoodbye(): AiModelStructuredResponse {
        val textResponse = "Goodbye! Have a focused and productive day."
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.GOODBYE,
                title = "Goodbye",
                reason = "Responded to goodbye."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handlePredictGoal(query: String, context: AiContext): AiModelStructuredResponse {
        val predictiveEngine = NexoraPredictiveEngine()
        val predictions = predictiveEngine.predictGoalRisksAndTimings(context)
        
        val textResponse = if (predictions.isEmpty()) {
            "You don't have any active goals configured. Setting a goal allows Nexora to estimate completion timelines and track risk."
        } else {
            val list = predictions.joinToString("\n\n") { p ->
                "• Goal: ${p.targetTitle}\n  Status: ${p.prediction}\n  Evidence: ${p.evidence}"
            }
            "Here is my predictive analysis for your active goals:\n\n$list"
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.PREDICT_GOAL,
                title = "Predictive Goal Analysis",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handlePredictTaskRisk(query: String, context: AiContext): AiModelStructuredResponse {
        val predictiveEngine = NexoraPredictiveEngine()
        val predictions = predictiveEngine.predictTaskDelayRisks(context)
        
        val textResponse = if (predictions.isEmpty()) {
            "All your current tasks appear to be within normal duration and focus parameters with low delay risk."
        } else {
            val list = predictions.take(3).joinToString("\n\n") { p ->
                "• Task: ${p.targetTitle}\n  Risk: ${p.prediction}\n  Why: ${p.evidence}"
            }
            "Based on your historical completion patterns and current workload:\n\n$list"
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.PREDICT_TASK_RISK,
                title = "Task Delay Risk",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handlePredictWorkload(query: String, context: AiContext): AiModelStructuredResponse {
        val predictiveEngine = NexoraPredictiveEngine()
        val prediction = predictiveEngine.predictWorkloadOverload(context)
        
        val textResponse = if (prediction != null) {
            "Workload Assessment:\n${prediction.prediction}\n\n${prediction.evidence}"
        } else {
            "Your task list is clear right now. No workload pressure detected."
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.PREDICT_WORKLOAD,
                title = "Workload Feasibility",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handlePredictProductivity(query: String, context: AiContext): AiModelStructuredResponse {
        val predictiveEngine = NexoraPredictiveEngine()
        val prediction = predictiveEngine.predictProductivityTrend(context)
        
        val isAccuracyQuery = query.lowercase().contains(Regex("\\b(accurate|accuracy|calibration|prediction quality)\\b"))
        
        val textResponse = if (isAccuracyQuery) {
            val evalCount = context.recentEvaluations.size
            if (evalCount == 0) {
                "I am actively building your personal productivity baseline. As you complete daily plans and tasks, I will calibrate my prediction accuracy against real outcomes."
            } else {
                val realisticPlans = context.recentEvaluations.count { it.outcome == AiOutcomeType.PLAN_REALISTIC }
                val passRate = (realisticPlans.toFloat() / evalCount * 100).toInt()
                "Based on $evalCount evaluated daily plan outcomes, $realisticPlans were realistic ($passRate% plan accuracy). Predictions are dynamically calibrated using your actual completion data."
            }
        } else {
            prediction?.let {
                "Productivity Pace Prediction:\n${it.prediction}\n\nEvidence: ${it.evidence}"
            } ?: "Keep completing tasks to build historical trend predictions."
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.PREDICT_PRODUCTIVITY,
                title = "Productivity Trend & Calibration",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun handleExplanation(query: String, langResult: AiLanguageResult): AiModelStructuredResponse {
        val q = query.lowercase().trim()
        val concept = langResult.entities["concept"]?.toString()?.lowercase() ?: q

        val textResponse = when {
            concept.contains("goal decomposition") || q.contains("goal decomposition") -> {
                "Goal decomposition is the process of breaking down a high-level objective into smaller, concrete milestones and actionable tasks. In Nexora, decomposing a goal creates linked sub-tasks so you can make consistent, measurable daily progress without feeling overwhelmed."
            }
            concept.contains("task prioritization") || q.contains("prioritization") || q.contains("priority") -> {
                "Task prioritization is the method of ranking tasks by urgency, impact, and goal alignment. Nexora uses priority tiers (Urgent, High, Medium, Low) to ensure your most critical work is completed first."
            }
            concept.contains("time blocking") || q.contains("time block") -> {
                "Time blocking is a productivity strategy where you schedule dedicated blocks of time for specific tasks or categories, minimizing distractions and eliminating context switching."
            }
            concept.contains("carry forward") || q.contains("carrying") -> {
                "Carrying forward refers to postponing unfinished tasks to the following day. Nexora monitors carry-forward frequency to identify tasks that may be too large or ambiguous and suggest breaking them down."
            }
            concept.contains("workload") || concept.contains("capacity") -> {
                "Workload capacity represents the sustainable number of tasks and focus minutes you can complete in a single day, calibrated from your recent completion history."
            }
            concept.contains("eisenhower") -> {
                "The Eisenhower Matrix categorizes tasks into four quadrants based on urgency and importance: Do first (urgent & important), Schedule (important, not urgent), Delegate (urgent, not important), and Eliminate (neither)."
            }
            concept.contains("pomodoro") -> {
                "The Pomodoro Technique breaks work into intervals, typically 25 minutes of deep focus followed by a 5-minute break, maintaining high focus and preventing cognitive fatigue."
            }
            else -> {
                "Goal decomposition and structured task planning allow you to convert ambitious objectives into manageable daily steps. You can ask Nexora to break down any of your active goals whenever you're ready."
            }
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.EXPLANATION,
                title = "Concept Explanation",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun noAction(query: String, context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val textResponse = "I'm here to assist. Try asking me 'What should I do next?', 'Plan my day', or ask me to create or manage a task or goal."

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "Nexora Assistant",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun ambiguousResult(message: String, candidates: List<Long>): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.AMBIGUOUS,
                title = "Clarification Needed",
                reason = message
            ),
            textResponse = message,
            candidateTaskIds = candidates,
            modelName = "local-heuristic"
        )
    }

    private fun notFoundResult(message: String): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "Not Found",
                reason = message
            ),
            textResponse = message,
            modelName = "local-heuristic"
        )
    }

    private fun mapAiPriorityToTaskPriority(priority: AiPriority): TaskPriority {
        return when (priority) {
            AiPriority.LOW -> TaskPriority.LOW
            AiPriority.MEDIUM -> TaskPriority.MEDIUM
            AiPriority.HIGH -> TaskPriority.HIGH
            AiPriority.CRITICAL -> TaskPriority.URGENT
        }
    }

    private fun handleListAutomations(): AiModelStructuredResponse {
        val rules = automationSystem.getRules()
        val text = if (rules.isEmpty()) {
            "You have no automation rules configured."
        } else {
            val list = rules.joinToString("\n") { 
                "• ${it.name} (${if (it.enabled) "Active" else "Disabled"}): ${it.description}" 
            }
            "Here are your active Nexora automations:\n\n$list"
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.LIST_AUTOMATIONS,
                title = "Automations Overview",
                reason = "Listed ${rules.size} automations."
            ),
            textResponse = text,
            modelName = "local-heuristic"
        )
    }

    private fun handleExplainAutomation(query: String): AiModelStructuredResponse {
        val explanation = automationSystem.explainLastRun(query)
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.EXPLAIN_AUTOMATION,
                title = "Automation Explanation",
                reason = explanation
            ),
            textResponse = explanation,
            modelName = "local-heuristic"
        )
    }

    private fun handleToggleAutomation(query: String): AiModelStructuredResponse {
        val rule = automationSystem.findRule(query)
            ?: return notFoundResult("I couldn't find a matching automation rule to toggle.")

        val q = query.lowercase()
        val targetState = if (q.contains("disable") || q.contains("turn off") || q.contains("deactivate")) false else true
        val statusText = if (targetState) "enabled" else "disabled"
        val responseText = "Automation \"${rule.name}\" will be $statusText."

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.TOGGLE_AUTOMATION,
                title = "Toggle Automation",
                reason = responseText
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.TOGGLE_AUTOMATION,
                    title = "Toggle Automation",
                    description = responseText,
                    parameters = mapOf(
                        "ruleId" to rule.id,
                        "ruleName" to rule.name,
                        "name" to rule.name,
                        "enabled" to targetState
                    ),
                    requiresConfirmation = false
                )
            ),
            textResponse = responseText,
            modelName = "local-heuristic"
        )
    }

    private fun handleDeleteAutomation(query: String): AiModelStructuredResponse {
        val rule = automationSystem.findRule(query)
            ?: return notFoundResult("I couldn't find the automation rule you want to delete.")

        val responseText = "Are you sure you want to delete the \"${rule.name}\" automation rule?"

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.DELETE_AUTOMATION,
                title = "Delete Automation",
                reason = responseText
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.DELETE_AUTOMATION,
                    title = "Delete Automation",
                    description = responseText,
                    parameters = mapOf(
                        "ruleId" to rule.id,
                        "ruleName" to rule.name,
                        "name" to rule.name
                    ),
                    requiresConfirmation = true
                )
            ),
            textResponse = responseText,
            modelName = "local-heuristic"
        )
    }

    private fun handleCreateAutomation(query: String): AiModelStructuredResponse {
        val q = query.lowercase()
        val (name, trigger, desc, isStateChanging) = when {
            q.contains("morning") || q.contains("plan my day") -> {
                Quadruple("Morning Plan Assistant", AutomationTriggerType.DAY_STARTED, "Prepares your daily plan automatically every morning.", false)
            }
            q.contains("finish") || q.contains("complete") -> {
                Quadruple("Post-Task Next Action", AutomationTriggerType.TASK_COMPLETED, "Suggests next task immediately after task completion.", false)
            }
            q.contains("carry") || q.contains("postpone") -> {
                Quadruple("Carry-forward Breakdown Assistant", AutomationTriggerType.TASK_CARRIED_FORWARD, "Suggests breaking down tasks carried forward repeatedly.", false)
            }
            q.contains("workload") || q.contains("high") -> {
                Quadruple("High Workload Monitor", AutomationTriggerType.WORKLOAD_CHANGED, "Warns when workload exceeds learned capacity.", false)
            }
            else -> {
                Quadruple("Custom Assistant Rule", AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED, "Monitors productivity patterns and alerts on key changes.", false)
            }
        }

        val responseText = "I can create and enable the automation rule: \"$name\" ($desc)."

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CREATE_AUTOMATION,
                title = "Automation Created",
                reason = responseText
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.CREATE_AUTOMATION,
                    title = "Create Automation",
                    description = responseText,
                    parameters = mapOf(
                        "name" to name,
                        "ruleName" to name,
                        "description" to desc,
                        "triggerType" to trigger.name
                    ),
                    requiresConfirmation = false
                )
            ),
            textResponse = responseText,
            modelName = "local-heuristic"
        )
    }

    private fun parseTargetDateDaysRemaining(targetDate: String): Long? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
            val date = LocalDate.parse(targetDate, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, date)
        } catch (e: Exception) {
            null
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

