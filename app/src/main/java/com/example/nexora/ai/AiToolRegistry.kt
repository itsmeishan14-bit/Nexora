package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository

/**
 * Registry of all available tools for the AI Agent.
 */
open class AiToolRegistry(
    private val repository: NexoraRepository?,
    private val actionExecutor: AiActionExecutor?,
    private val planner: AiPlanner = AiPlanner()
) {
    private val tools = mutableMapOf<String, AiTool>()

    init {
        repository?.let { repo ->
            actionExecutor?.let { exec ->
                registerTool(FindTaskTool(repo))
                registerTool(CreateTaskTool(exec))
                registerTool(CompleteTaskTool(exec))
                registerTool(DeleteTaskTool(exec))
                registerTool(ListTasksTool(repo))
                
                registerTool(FindGoalTool(repo))
                registerTool(ListGoalsTool(repo))
                registerTool(AnalyzeGoalTool(repo))
                
                registerTool(CreateDailyPlanTool())
                registerTool(DecomposeGoalTool(planner))
            }
        }
    }

    private fun registerTool(tool: AiTool) {
        tools[tool.name] = tool
    }

    open fun getTool(name: String): AiTool? = tools[name]

    fun getAllTools(): List<AiTool> = tools.values.toList()

    // --- TOOL IMPLEMENTATIONS ---

    private class FindTaskTool(private val repository: NexoraRepository) : AiTool {
        override val name = "findTask"
        override val description = "Finds a specific task by title or description."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val query = parameters["query"]?.toString() ?: return ToolResult(false, message = "Missing 'query' parameter")
            val tasks = repository.observeTasksOnce()
            val match = AiEntityResolver.resolveTask(query, tasks)
            
            return when (match) {
                is ResolutionResult.Success -> ToolResult(true, match.entity, "Found task: ${match.entity.title}")
                is ResolutionResult.Ambiguous -> ToolResult(false, match.candidates, "Found multiple matching tasks.")
                is ResolutionResult.NotFound -> ToolResult(false, message = "No task found matching: $query")
            }
        }
    }

    private class CreateTaskTool(private val executor: AiActionExecutor) : AiTool {
        override val name = "createTask"
        override val description = "Creates a new task."
        override val riskLevel = ToolRiskLevel.LOW_RISK

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val title = parameters["title"]?.toString() ?: return ToolResult(false, message = "Title is required")
            val action = AiAction(
                type = AiActionType.CREATE_TASK, 
                title = "Create Task", 
                description = "Agent requested creation of task: $title", 
                parameters = parameters
            )
            val result = executor.execute(action)
            return ToolResult(result.success, result.affectedTaskId, result.message, result.error)
        }
    }

    private class CompleteTaskTool(private val executor: AiActionExecutor) : AiTool {
        override val name = "completeTask"
        override val description = "Marks a task as complete."
        override val riskLevel = ToolRiskLevel.LOW_RISK

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val taskId = parameters["taskId"]?.toString()?.toLongOrNull() ?: return ToolResult(false, message = "Valid taskId is required")
            val action = AiAction(
                type = AiActionType.COMPLETE_TASK, 
                title = "Complete Task", 
                description = "Agent requested completion of task ID: $taskId", 
                taskId = taskId
            )
            val result = executor.execute(action)
            return ToolResult(result.success, taskId, result.message, result.error)
        }
    }

    private class DeleteTaskTool(private val executor: AiActionExecutor) : AiTool {
        override val name = "deleteTask"
        override val description = "Permanently deletes a task."
        override val riskLevel = ToolRiskLevel.DESTRUCTIVE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val taskId = parameters["taskId"]?.toString()?.toLongOrNull() ?: return ToolResult(false, message = "Valid taskId is required")
            val action = AiAction(
                type = AiActionType.DELETE_TASK, 
                title = "Delete Task", 
                description = "Agent requested deletion of task ID: $taskId",
                taskId = taskId
            )
            val result = executor.execute(action)
            return ToolResult(result.success, taskId, result.message, result.error)
        }
    }

    private class ListTasksTool(private val repository: NexoraRepository) : AiTool {
        override val name = "listTasks"
        override val description = "Lists all tasks, optionally filtered by state."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val filter = parameters["filter"]?.toString() ?: "all"
            val allTasks = repository.observeTasksOnce()
            val filtered = when (filter) {
                "incomplete" -> allTasks.filter { !it.completed }
                "completed" -> allTasks.filter { it.completed }
                else -> allTasks
            }
            return ToolResult(true, filtered, "Listed ${filtered.size} tasks.")
        }
    }

    private class FindGoalTool(private val repository: NexoraRepository) : AiTool {
        override val name = "findGoal"
        override val description = "Finds a specific goal by title."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val query = parameters["query"]?.toString() ?: return ToolResult(false, message = "Missing 'query' parameter")
            val goals = repository.observeGoalsOnce()
            val match = AiEntityResolver.resolveGoal(query, goals)
            
            return when (match) {
                is ResolutionResult.Success -> ToolResult(true, match.entity, "Found goal: ${match.entity.title}")
                else -> ToolResult(false, message = "Goal not found: $query")
            }
        }
    }

    private class ListGoalsTool(private val repository: NexoraRepository) : AiTool {
        override val name = "listGoals"
        override val description = "Lists all active goals."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val goals = repository.observeGoalsOnce().filter { it.progress < 1f }
            return ToolResult(true, goals, "Listed ${goals.size} active goals.")
        }
    }

    private class AnalyzeGoalTool(private val repository: NexoraRepository) : AiTool {
        override val name = "analyzeGoal"
        override val description = "Analyzes progress and tasks related to a goal."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val goalId = parameters["goalId"]?.toString()?.toLongOrNull() ?: return ToolResult(false, message = "goalId is required")
            val goals = repository.observeGoalsOnce()
            val goal = goals.find { it.id == goalId } ?: return ToolResult(false, message = "Goal not found")
            
            val tasks = repository.observeTasksOnce().filter { it.goalTitle == goal.title }
            val analysis = mapOf("goal" to goal, "tasks" to tasks)
            return ToolResult(true, analysis, "Analyzed goal: ${goal.title}")
        }
    }

    private class CreateDailyPlanTool : AiTool {
        override val name = "createDailyPlan"
        override val description = "Generates a personalized daily plan based on context."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            return ToolResult(false, message = "Daily plan requires full AiContext which is managed by the Agent loop.")
        }
    }

    private class DecomposeGoalTool(private val planner: AiPlanner) : AiTool {
        override val name = "decomposeGoal"
        override val description = "Breaks a goal into actionable steps."
        override val riskLevel = ToolRiskLevel.SAFE

        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
            val title = parameters["title"]?.toString() ?: return ToolResult(false, message = "Goal title is required")
            val category = parameters["category"]?.toString() ?: "Personal"
            val result = planner.decomposeGoal(title, "", category)
            return ToolResult(true, result, "Decomposed goal into ${result.steps.size} steps.")
        }
    }
}
