package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority

class LocalAiIntentResolver {

    fun resolve(query: String, context: AiContext): AiModelStructuredResponse {
        val input = query.lowercase().trim()

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
            input.contains("plan") && input.contains("day") -> planDay(context)
            input.contains("next") && (input.contains("work") || input.contains("task")) -> nextTask(context)
            input.contains("break down") || input.contains("decompose") -> decomposeGoal(query, context)

            else -> noAction()
        }
    }

    private fun listTasks(context: AiContext): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "List Tasks",
                reason = "You have ${context.incompleteTasks.size} incomplete tasks."
            ),
            modelName = "local-heuristic"
        )
    }

    private fun listGoals(context: AiContext): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "List Goals",
                reason = "You have ${context.activeGoals.size} active goals."
            ),
            modelName = "local-heuristic"
        )
    }

    private fun showProgress(context: AiContext): AiModelStructuredResponse {
        val progress = if (context.tasksPlannedToday > 0) {
            (context.tasksCompletedToday.toFloat() / context.tasksPlannedToday * 100).toInt()
        } else 0
        
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.SHOW_INSIGHT,
                title = "Today's Progress",
                reason = "You've completed $progress% of your planned tasks today."
            ),
            modelName = "local-heuristic"
        )
    }

    private fun createTask(query: String, context: AiContext): AiModelStructuredResponse {
        val titleMatch = Regex("\"([^\"]*)\"").find(query)
        val title = titleMatch?.groupValues?.get(1) ?: query.replace(Regex("(?i)(create|add|task|a)"), "").trim()
        
        val duration = if (query.contains("minute")) {
            Regex("\\d+").find(query)?.value?.let { "$it minutes" } ?: "30 minutes"
        } else "30 minutes"
        
        val priority = when {
            query.contains("urgent") -> AiPriority.CRITICAL
            query.contains("high") -> AiPriority.HIGH
            query.contains("low") -> AiPriority.LOW
            else -> AiPriority.MEDIUM
        }

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
            modelName = "local-heuristic"
        )
    }

    private fun updateTaskPriority(query: String, context: AiContext): AiModelStructuredResponse {
        val taskIdMatch = Regex("Task ID (\\d+)").find(query)
        val result = if (taskIdMatch != null) {
            val id = taskIdMatch.groupValues[1].toLong()
            context.tasks.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val taskQuery = query.replace(Regex("(?i)(make|change|priority|to|high|low|medium|urgent|task)"), "").trim()
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
            val taskQuery = query.replace(Regex("(?i)(complete|mark|done|as|task)"), "").trim()
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
            val taskQuery = query.replace(Regex("(?i)(delete|task)"), "").trim()
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
            val taskQuery = query.replace(Regex("(?i)(move|to|tomorrow|task)"), "").trim()
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
                    modelName = "local-heuristic"
                )
            }
            else -> notFoundResult("Task not found.")
        }
    }

    private fun planDay(context: AiContext): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.DAILY_PLAN,
                title = "Daily Plan",
                reason = "Generating your daily plan..."
            ),
            modelName = "local-heuristic"
        )
    }

    private fun nextTask(context: AiContext): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.START_TASK,
                title = "Next Task",
                reason = "Finding your next best step..."
            ),
            modelName = "local-heuristic"
        )
    }

    private fun decomposeGoal(query: String, context: AiContext): AiModelStructuredResponse {
        val goalIdMatch = Regex("Goal ID (\\d+)").find(query)
        val result = if (goalIdMatch != null) {
            val id = goalIdMatch.groupValues[1].toLong()
            context.goals.find { it.id == id }?.let { ResolutionResult.Success(it) } ?: ResolutionResult.NotFound()
        } else {
            val goalQuery = query.replace(Regex("(?i)(break down|decompose|goal)"), "").trim()
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
                    modelName = "local-heuristic"
                )
            }
            else -> notFoundResult("Goal not found.")
        }
    }

    private fun noAction(): AiModelStructuredResponse {
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "No Action",
                reason = ""
            ),
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
