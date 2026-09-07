package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority

class LocalAiIntentResolver {

    private val pipeline = AdvancedLocalLanguagePipeline()
    private var conversationContext = AiConversationContext()

    /**
     * Resolves natural language queries into structured AI responses using local heuristics.
     * Operates entirely offline without external APIs.
     */
    fun resolve(query: String, context: AiContext): AiModelStructuredResponse {
        // Refresh context if expired
        if (conversationContext.isExpired()) {
            conversationContext = AiConversationContext()
        }

        val langResult = pipeline.process(query, context, conversationContext)
        
        val response = when (langResult.intent) {
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
            else -> noAction(query, context, AiPlanner())
        }

        updateConversationContext(langResult, response)
        return response
    }

    private fun updateConversationContext(langResult: AiLanguageResult, response: AiModelStructuredResponse) {
        conversationContext = AiConversationContext(
            lastIntent = langResult.intent,
            lastTaskId = response.decision.taskId ?: conversationContext.lastTaskId,
            lastGoalId = response.decision.goalId ?: conversationContext.lastGoalId,
            lastEntityTitle = langResult.entities["title"]?.toString() ?: conversationContext.lastEntityTitle,
            activeClarification = langResult.clarificationNeeded,
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

        val duration = langResult.entities["duration"]?.toString() ?: "30 minutes"
        val priorityStr = langResult.entities["priority"]?.toString() ?: "MEDIUM"
        val priority = try { AiPriority.valueOf(priorityStr) } catch (e: Exception) { AiPriority.MEDIUM }

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
                    description = "Add \"$title\" ($duration, $priority priority)?",
                    parameters = mapOf(
                        "title" to title,
                        "duration" to duration,
                        "priority" to mapAiPriorityToTaskPriority(priority).name
                    )
                )
            ),
            textResponse = "I'll create a task for \"$title\" with $priority priority.",
            modelName = "local-heuristic"
        )
    }

    private fun handleCompleteTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val taskId = langResult.entities["taskId"] as? Long
        if (taskId != null) {
            val task = context.tasks.find { it.id == taskId } ?: return notFoundResult("Task not found.")
            return buildCompleteAction(task)
        }

        val title = langResult.entities["title"]?.toString() ?: ""
        val resolution = AiEntityResolver.resolveTask(title, context.tasks)
        
        return when (resolution) {
            is ResolutionResult.Success -> buildCompleteAction(resolution.entity)
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
                    taskId = task.id
                )
            ),
            textResponse = "Marking \"${task.title}\" as complete. Well done!",
            modelName = "local-heuristic"
        )
    }

    private fun handleUpdateTask(langResult: AiLanguageResult, context: AiContext): AiModelStructuredResponse {
        val taskId = langResult.entities["taskId"] as? Long
        val title = langResult.entities["title"]?.toString() ?: ""
        
        val task = if (taskId != null) {
            context.tasks.find { it.id == taskId }
        } else {
            (AiEntityResolver.resolveTask(title, context.tasks) as? ResolutionResult.Success)?.entity
        } ?: return notFoundResult("I couldn't find the task to update.")

        val newPriorityStr = langResult.entities["priority"]?.toString()
        val newDuration = langResult.entities["duration"]?.toString()

        val params = mutableMapOf<String, Any>()
        if (newPriorityStr != null) params["priority"] = newPriorityStr
        if (newDuration != null) params["duration"] = newDuration

        return AiModelStructuredResponse(
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
                    parameters = params
                )
            ),
            textResponse = "Updating \"${task.title}\" as requested.",
            modelName = "local-heuristic"
        )
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
                            priority = AiPriority.HIGH
                        )
                    ),
                    textResponse = "I'll delete the task \"${task.title}\".",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Multiple tasks match. Which one should I delete?", resolution.candidates.map { it.id })
            else -> notFoundResult("Task not found.")
        }
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
            "You have ${context.incompleteTasks.size} tasks to complete. Your top priority is \"${context.incompleteTasks.first().title}\"."
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
            "You are currently working toward ${context.activeGoals.size} active goals."
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
                    parameters = mapOf("title" to title)
                )
            ),
            textResponse = "I'll create the goal \"$title\" for you.",
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
        val textResponse = "I'm Nexora, your local intelligence. I can help you plan your day, prioritize tasks, analyze your goals, or review productivity patterns—all entirely offline. Try asking 'What should I do next?' or 'Plan my day'."

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "No Action",
                reason = ""
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
