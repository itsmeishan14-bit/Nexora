package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority

class LocalAiIntentResolver {

    private val pipeline = AdvancedLocalLanguagePipeline()
    private var conversationContext = AiConversationContext()

    /**
     * Resolves natural language queries into structured AI responses using local heuristics.
     * Operates entirely offline without external APIs.
     */
    fun resolve(query: String, context: AiContext, externalConvContext: AiConversationContext? = null): AiModelStructuredResponse {
        // Use external context if provided, otherwise fallback to local persistence
        val effectiveConvContext = externalConvContext ?: conversationContext
        
        // Refresh context if expired
        if (effectiveConvContext.isExpired()) {
            conversationContext = AiConversationContext()
        } else {
            conversationContext = effectiveConvContext
        }

        val langResult = pipeline.process(query, context, conversationContext)
        
        val response = when {
            langResult.isConfirmation && conversationContext.pendingAction != null -> {
                handleConfirmation(conversationContext.pendingAction!!, context)
            }
            langResult.isCancellation -> handleCancel()
            else -> when (langResult.intent) {
                AiDecisionType.SHOW_INSIGHT -> handleShowInsight(langResult, context)
                AiDecisionType.CREATE_TASK -> handleCreateTask(langResult, context)
                AiDecisionType.COMPLETE_TASK -> handleCompleteTask(langResult, context)
                AiDecisionType.DELETE_TASK -> handleDeleteTask(langResult, context)
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
            lastEntityTitle = langResult.entities["title"]?.toString() ?: conversationContext.lastEntityTitle,
            activeClarification = langResult.clarificationNeeded,
            pendingAction = response.actions.firstOrNull()?.takeIf { it.requiresConfirmation },
            candidateIds = response.candidateTaskIds.takeIf { it.isNotEmpty() } ?: conversationContext.candidateIds
        )
    }

    private fun handleShowInsight(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val query = langResult.entities["query"]?.toString()?.lowercase() ?: ""
        return when {
            query.contains("goal") -> listGoals(context)
            query.contains("productivity") || query.contains("pattern") -> showProductivity(context, AiPlanner())
            query.contains("progress") -> showProgress(context)
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
        val resolution = AiEntityResolver.resolveTask(title, context.tasks)
        
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
            AiEntityResolver.resolveTask(title, context.tasks)
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
        val resolution = AiEntityResolver.resolveTask(title, context.tasks)
        
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
        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = AiEntityResolver.resolveGoal(title, context.goals)
        
        return when (resolution) {
            is ResolutionResult.Success -> {
                val goal = resolution.entity
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.DECOMPOSE_GOAL,
                        title = "Decompose Goal",
                        reason = "Breaking down ${goal.title} into steps.",
                        goalId = goal.id
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
            next.message
        } else {
            "You have no urgent tasks. Consider reviewing your goals or planning for tomorrow."
        }
        
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.START_TASK,
                title = "Next Task",
                reason = "Finding your next best step...",
                taskId = next?.relatedTaskId
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

    private fun noAction(query: String, context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val textResponse = if (context.incompleteTasks.isEmpty()) {
            "You're all caught up! It's a great time to set a new goal or plan some future work."
        } else {
            "I'm here to help. You have ${context.incompleteTasks.size} tasks pending. Try asking 'What should I do next?' or 'Plan my day' for a focused itinerary."
        }

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "Nexora Intelligence",
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
}
