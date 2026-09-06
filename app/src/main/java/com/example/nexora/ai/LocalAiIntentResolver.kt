package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority

class LocalAiIntentResolver {

    fun resolve(query: String, context: AiContext): AiModelStructuredResponse {
        val input = query.lowercase().trim()
        val planner = AiPlanner()

        return when {
            // READ intents
            input.contains("show") && input.contains("tasks") -> listTasks(context)
            input.contains("show") && input.contains("goals") -> listGoals(context)
            input.contains("progress") -> showProgress(context)
            
            // WRITE intents - Task Creation
            input.contains("create") && input.contains("task") || input.contains("add") && input.contains("task") -> createTask(query, context)
            
            // WRITE intents - Task Updates
            input.contains("priority") -> updateTaskPriority(query, context)
            input.contains("complete") || input.contains("mark") && input.contains("done") -> completeTask(query, context)
            input.contains("delete") && input.contains("task") -> deleteTask(query, context)
            input.contains("move") && input.contains("tomorrow") -> rescheduleTask(query, context)
            
            // PLANNING intents
            input.contains("plan") && input.contains("day") -> planDay(context, planner)
            input.contains("next") && (input.contains("work") || input.contains("task")) -> nextTask(context, planner)
            input.contains("break down") || input.contains("decompose") -> decomposeGoal(query, context)
            
            // PROACTIVE queries
            input.contains("notice") || input.contains("what's up") || input.contains("status") || 
            input.contains("behind") || input.contains("attention") || input.contains("issue") ||
            input.contains("overload") || input.contains("too many") -> analyzeProactive(context, planner)

            // PRODUCTIVITY
            input.contains("productivity") || input.contains("pattern") || input.contains("consistent") ||
            input.contains("momentum") || input.contains("habit") -> showProductivity(context, planner)

            else -> noAction(query, context, planner)
        }
    }

    private fun analyzeProactive(context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val insights = planner.getProactiveInsights(context)
        val best = insights.maxByOrNull { it.priority }
        
        val textResponse = if (insights.isEmpty()) {
            "Nexora hasn't noticed anything unusual. You're on track with your current plan."
        } else {
            val summary = insights.joinToString("\n") { "- ${it.title}: ${it.message}" }
            "Nexora has noticed several things that might need your attention:\n\n$summary"
        }

        return if (best != null) {
            AiModelStructuredResponse(
                decision = AiDecision(
                    type = mapRecTypeToDecisionType(best.type),
                    title = best.title,
                    reason = best.message,
                    evidence = best.evidence,
                    confidence = best.confidence,
                    taskId = best.relatedTaskId,
                    goalId = best.relatedGoalId,
                    priority = best.priority
                ),
                textResponse = textResponse,
                modelName = "local-heuristic"
            )
        } else {
            AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.SHOW_INSIGHT,
                    title = "All Clear",
                    reason = "Nexora hasn't noticed anything unusual. You're doing great."
                ),
                textResponse = textResponse,
                modelName = "local-heuristic"
            )
        }
    }

    private fun mapRecTypeToDecisionType(type: AiRecommendationType): AiDecisionType {
        return when (type) {
            AiRecommendationType.NEXT_TASK -> AiDecisionType.START_TASK
            AiRecommendationType.DAILY_PLAN -> AiDecisionType.DAILY_PLAN
            AiRecommendationType.GOAL_ACTION -> AiDecisionType.UPDATE_GOAL
            AiRecommendationType.PRODUCTIVITY_INSIGHT -> AiDecisionType.SHOW_INSIGHT
            AiRecommendationType.WARNING -> AiDecisionType.WARNING
            else -> AiDecisionType.NO_ACTION
        }
    }

    private fun listTasks(context: AiContext): AiModelStructuredResponse {
        val textResponse = "You have ${context.incompleteTasks.size} incomplete tasks."
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "List Tasks",
                reason = textResponse
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun listGoals(context: AiContext): AiModelStructuredResponse {
        val textResponse = "You have ${context.activeGoals.size} active goals."
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "List Goals",
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
        
        val textResponse = "You've completed $progress% of your planned tasks today."
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
        val textResponse = if (context.memory.patterns.isEmpty()) {
            "I don't have enough history to detect specific patterns yet. Keep using Nexora and I'll analyze your consistency over time."
        } else {
            val patternList = context.memory.patterns.joinToString("\n") { "- ${it.title}: ${it.description}" }
            "Here is what I've learned about your productivity recently:\n\n$patternList"
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

    private fun createTask(query: String, context: AiContext): AiModelStructuredResponse {
        val titleMatch = Regex("\"([^\"]*)\"").find(query)
        val title = titleMatch?.groupValues?.get(1) ?: query.replace(Regex("(?i)\\b(create|add|task|a)\\b"), "").trim()
        
        val duration = if (query.contains("minute")) {
            Regex("\\d+").find(query)?.value?.let { "$it minutes" } ?: "30 minutes"
        } else "30 minutes"
        
        val priority = when {
            query.contains("urgent") -> AiPriority.CRITICAL
            query.contains("high") -> AiPriority.HIGH
            query.contains("low") -> AiPriority.LOW
            else -> AiPriority.MEDIUM
        }

        val textResponse = "I'll create a task for \"$title\"."

        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.CREATE_TASK,
                title = "Create Task",
                reason = "Create new task: $title",
                taskTitle = title,
                priority = priority,
                actionLabel = "Create"
            ),
            actions = listOf(
                AiAction(
                    type = AiActionType.CREATE_TASK,
                    title = "Create Task",
                    description = "Create task \"$title\" ($duration, $priority priority)?",
                    parameters = mapOf(
                        "title" to title,
                        "duration" to duration,
                        "priority" to mapAiPriorityToTaskPriority(priority).name
                    )
                )
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun updateTaskPriority(query: String, context: AiContext): AiModelStructuredResponse {
        val taskIdMatch = Regex("Task ID (\\d+)").find(query)
        val result = if (taskIdMatch != null) {
            val id = taskIdMatch.groupValues[1].toLong()
            context.tasks.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val taskQuery = query.replace(Regex("(?i)\\b(make|change|priority|to|high|low|medium|urgent|task)\\b"), "").trim()
            AiEntityResolver.resolveTask(taskQuery, context.tasks)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                val priority = when {
                    query.contains("urgent") -> AiPriority.CRITICAL
                    query.contains("high") -> AiPriority.HIGH
                    query.contains("low") -> AiPriority.LOW
                    else -> AiPriority.MEDIUM
                }
                
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.UPDATE_TASK,
                        title = "Update Priority",
                        reason = "Change ${result.entity.title} priority to $priority",
                        taskId = result.entity.id,
                        priority = priority
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.UPDATE_TASK,
                            title = "Update Priority",
                            description = "Change priority of \"${result.entity.title}\" to $priority?",
                            taskId = result.entity.id,
                            parameters = mapOf("priority" to mapAiPriorityToTaskPriority(priority).name)
                        )
                    ),
                    textResponse = "Updating priority for \"${result.entity.title}\" to $priority.",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Which task did you mean?", result.candidates.map { it.id })
            else -> notFoundResult("I couldn't find the task.")
        }
    }

    private fun completeTask(query: String, context: AiContext): AiModelStructuredResponse {
        val taskIdMatch = Regex("Task ID (\\d+)").find(query)
        val result = if (taskIdMatch != null) {
            val id = taskIdMatch.groupValues[1].toLong()
            context.tasks.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val taskQuery = query.replace(Regex("(?i)\\b(complete|mark|done|as|task)\\b"), "").trim()
            AiEntityResolver.resolveTask(taskQuery, context.tasks)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.COMPLETE_TASK,
                        title = "Complete Task",
                        reason = "Mark ${result.entity.title} as complete",
                        taskId = result.entity.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.COMPLETE_TASK,
                            title = "Complete Task",
                            description = "Mark \"${result.entity.title}\" as complete?",
                            taskId = result.entity.id
                        )
                    ),
                    textResponse = "Marking \"${result.entity.title}\" as complete.",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("I found multiple tasks. Which one should I complete?", result.candidates.map { it.id })
            else -> notFoundResult("I couldn't find that task.")
        }
    }

    private fun deleteTask(query: String, context: AiContext): AiModelStructuredResponse {
        val taskIdMatch = Regex("Task ID (\\d+)").find(query)
        val result = if (taskIdMatch != null) {
            val id = taskIdMatch.groupValues[1].toLong()
            context.tasks.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val taskQuery = query.replace(Regex("(?i)\\b(delete|task)\\b"), "").trim()
            AiEntityResolver.resolveTask(taskQuery, context.tasks)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.DELETE_TASK,
                        title = "Delete Task",
                        reason = "Permanently delete ${result.entity.title}",
                        taskId = result.entity.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.DELETE_TASK,
                            title = "Delete Task",
                            description = "Are you sure you want to delete \"${result.entity.title}\"?",
                            taskId = result.entity.id,
                            priority = AiPriority.HIGH
                        )
                    ),
                    textResponse = "I'll delete the task \"${result.entity.title}\".",
                    modelName = "local-heuristic"
                )
            }
            is ResolutionResult.Ambiguous -> ambiguousResult("Multiple tasks match. Which one should I delete?", result.candidates.map { it.id })
            else -> notFoundResult("Task not found.")
        }
    }

    private fun rescheduleTask(query: String, context: AiContext): AiModelStructuredResponse {
        val taskIdMatch = Regex("Task ID (\\d+)").find(query)
        val result = if (taskIdMatch != null) {
            val id = taskIdMatch.groupValues[1].toLong()
            context.tasks.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val taskQuery = query.replace(Regex("(?i)\\b(move|to|tomorrow|task)\\b"), "").trim()
            AiEntityResolver.resolveTask(taskQuery, context.tasks)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.RESCHEDULE_TASK,
                        title = "Reschedule Task",
                        reason = "Move ${result.entity.title} to tomorrow",
                        taskId = result.entity.id
                    ),
                    actions = listOf(
                        AiAction(
                            type = AiActionType.RESCHEDULE_TASK,
                            title = "Reschedule Task",
                            description = "Move \"${result.entity.title}\" to tomorrow?",
                            taskId = result.entity.id
                        )
                    ),
                    textResponse = "Moving \"${result.entity.title}\" to tomorrow.",
                    modelName = "local-heuristic"
                )
            }
            else -> notFoundResult("Task not found.")
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
                reason = "Generating your daily plan..."
            ),
            textResponse = textResponse,
            modelName = "local-heuristic"
        )
    }

    private fun nextTask(context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val next = planner.analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
        val textResponse = next?.message ?: "I don't see any urgent tasks right now."
        
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
            val goalQuery = query.replace(Regex("(?i)\\b(break down|decompose|goal)\\b"), "").trim()
            AiEntityResolver.resolveGoal(goalQuery, context.goals)
        }
        
        return when (result) {
            is ResolutionResult.Success -> {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.UPDATE_GOAL,
                        title = "Decompose Goal",
                        reason = "Breaking down ${result.entity.title}",
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
                    textResponse = "I'll break down the goal \"${result.entity.title}\" for you.",
                    modelName = "local-heuristic"
                )
            }
            else -> notFoundResult("Goal not found.")
        }
    }

    private fun noAction(query: String, context: AiContext, planner: AiPlanner): AiModelStructuredResponse {
        val textResponse = "I'm Nexora, your productivity assistant. I can help you plan your day, prioritize tasks, or review your goals. Try asking 'What should I work on next?'"

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
