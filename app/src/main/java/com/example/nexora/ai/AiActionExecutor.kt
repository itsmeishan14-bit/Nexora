package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal

open class AiActionExecutor(
    private val repository: NexoraRepository?
) {
    private val validator = repository?.let { NexoraAiValidator(it) }

    open suspend fun execute(action: AiAction): AiActionResult {
        val repo = repository ?: return AiActionResult(false, "Repository not available")
        
        // 1. Validation Layer
        val validationResult = validator?.validate(action)
        if (validationResult is ValidationResult.Invalid) {
            return AiActionResult(
                success = false,
                message = "Validation failed: ${validationResult.message}",
                error = "Validation error"
            )
        }

        // 2. Execution Layer
        val result = try {
            val executionResult = when (action.type) {
                AiActionType.CREATE_TASK -> createTask(repo, action)
                AiActionType.COMPLETE_TASK -> completeTask(repo, action)
                AiActionType.UPDATE_TASK -> updateTask(repo, action)
                AiActionType.DELETE_TASK -> deleteTask(repo, action)
                AiActionType.RESCHEDULE_TASK -> rescheduleTask(repo, action)
                AiActionType.CREATE_GOAL -> createGoal(repo, action)
                AiActionType.UPDATE_GOAL -> updateGoal(repo, action)
                AiActionType.DELETE_GOAL -> deleteGoal(repo, action)
                AiActionType.DECOMPOSE_GOAL -> AiActionResult(true, "Goal decomposition requested.")
                AiActionType.SHOW_INSIGHT -> AiActionResult(true, "Insight displayed.")
                AiActionType.OPEN_TASK -> AiActionResult(true, "Task opened.")
                AiActionType.OPEN_GOAL -> AiActionResult(true, "Goal opened.")
            }

            // 3. Verification Layer
            if (executionResult.success) {
                verifyAction(repo, action, executionResult)
            } else {
                executionResult
            }
        } catch (e: Exception) {
            AiActionResult(
                success = false,
                message = "Action failed: ${action.title}",
                error = e.message
            )
        }
        
        // 4. Learning Loop: Record action outcome
        recordActionOutcome(action, result)
        
        return result
    }

    private suspend fun verifyAction(
        repository: NexoraRepository,
        action: AiAction,
        executionResult: AiActionResult
    ): AiActionResult {
        return when (action.type) {
            AiActionType.CREATE_TASK -> {
                val taskId = executionResult.affectedTaskId ?: return executionResult
                val task = repository.observeTasksOnce().find { it.id == taskId }
                if (task != null) executionResult else AiActionResult(false, "Verification failed: Task not found in DB after creation.")
            }
            AiActionType.COMPLETE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.observeTasksOnce().find { it.id == taskId }
                if (task?.completed == true) executionResult else AiActionResult(false, "Verification failed: Task still marked incomplete.")
            }
            AiActionType.DELETE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.observeTasksOnce().find { it.id == taskId }
                if (task == null) executionResult else AiActionResult(false, "Verification failed: Task still exists after deletion.")
            }
            AiActionType.UPDATE_GOAL -> {
                val goalId = action.goalId ?: return executionResult
                val goal = repository.observeGoalsOnce().find { it.id == goalId }
                // Basic existence check, could check specific fields if needed
                if (goal != null) executionResult else AiActionResult(false, "Verification failed: Goal not found after update.")
            }
            else -> executionResult
        }
    }

    private suspend fun recordActionOutcome(action: AiAction, result: AiActionResult) {
        val repo = repository ?: return
        val outcome = AiOutcome(
            id = java.util.UUID.randomUUID().toString(),
            recommendationId = null,
            actionId = action.id,
            type = if (result.success) AiOutcomeType.SUCCESS else AiOutcomeType.FAILED,
            timestamp = System.currentTimeMillis(),
            relatedTaskId = result.affectedTaskId ?: action.taskId,
            relatedGoalId = result.affectedGoalId ?: action.goalId,
            expectedResult = action.title,
            actualResult = result.message,
            evidence = if (result.success) "Action execution returned success." else "Error: ${result.error}"
        )
        repo.saveOutcome(outcome)
    }

    private suspend fun createTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val title = action.parameters["title"] as? String ?: action.title
        val category = action.parameters["category"] as? String ?: "Personal"
        val duration = action.parameters["duration"] as? String ?: "30 minutes"
        val priorityStr = action.parameters["priority"] as? String ?: "MEDIUM"
        val goalTitle = action.parameters["goalTitle"] as? String

        val priority = try {
            TaskPriority.valueOf(priorityStr)
        } catch (e: Exception) {
            TaskPriority.MEDIUM
        }

        val task = PremiumTask(
            title = title,
            category = category,
            duration = duration,
            priority = priority,
            goalTitle = goalTitle,
            completed = false
        )

        val created = repository.addTask(task)
        return AiActionResult(
            success = true,
            message = "Task created: ${created.title}",
            affectedTaskId = created.id
        )
    }

    private suspend fun completeTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val taskId = action.taskId ?: return AiActionResult(false, "Task ID missing.")
        val tasks = repository.observeTasksOnce()
        val task = tasks.find { it.id == taskId } ?: return AiActionResult(false, "Task not found.")

        val updated = task.copy(completed = true)
        repository.updateTask(updated)
        
        return AiActionResult(
            success = true,
            message = "Task completed: ${task.title}",
            affectedTaskId = taskId
        )
    }

    private suspend fun updateTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val taskId = action.taskId ?: return AiActionResult(false, "Task ID missing.")
        val tasks = repository.observeTasksOnce()
        val task = tasks.find { it.id == taskId } ?: return AiActionResult(false, "Task not found.")

        val newPriorityStr = action.parameters["priority"] as? String
        val newDuration = action.parameters["duration"] as? String
        val newTitle = action.parameters["title"] as? String

        var updated = task
        if (newPriorityStr != null) {
            val priority = try {
                TaskPriority.valueOf(newPriorityStr)
            } catch (e: Exception) {
                task.priority
            }
            updated = updated.copy(priority = priority)
        }
        if (newDuration != null) updated = updated.copy(duration = newDuration)
        if (newTitle != null) updated = updated.copy(title = newTitle)

        repository.updateTask(updated)
        
        return AiActionResult(
            success = true,
            message = "Task updated: ${task.title}",
            affectedTaskId = taskId
        )
    }

    private suspend fun deleteTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val taskId = action.taskId ?: return AiActionResult(false, "Task ID missing.")
        val tasks = repository.observeTasksOnce()
        val task = tasks.find { it.id == taskId } ?: return AiActionResult(false, "Task not found.")

        repository.deleteTask(task)
        
        return AiActionResult(
            success = true,
            message = "Task deleted: ${task.title}",
            affectedTaskId = taskId
        )
    }

    private suspend fun rescheduleTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        // In this simple app, reschedule might just mean changing a parameter or just a confirmation
        return AiActionResult(true, "Task rescheduled: ${action.title}")
    }

    private suspend fun createGoal(repository: NexoraRepository, action: AiAction): AiActionResult {
        val title = action.parameters["title"] as? String ?: action.title
        val category = action.parameters["category"] as? String ?: "Personal"
        val targetDate = action.parameters["targetDate"] as? String ?: ""

        val goal = NexoraGoal(
            title = title,
            category = category,
            targetDate = targetDate,
            progress = 0f
        )

        val created = repository.addGoal(goal)
        return AiActionResult(
            success = true,
            message = "Goal created: ${created.title}",
            affectedGoalId = created.id
        )
    }

    private suspend fun updateGoal(repository: NexoraRepository, action: AiAction): AiActionResult {
        val goalId = action.goalId ?: return AiActionResult(false, "Goal ID missing.")
        val goals = repository.observeGoalsOnce()
        val goal = goals.find { it.id == goalId } ?: return AiActionResult(false, "Goal not found.")

        val newTitle = action.parameters["title"] as? String
        val newCategory = action.parameters["category"] as? String

        var updated = goal
        if (newTitle != null) updated = updated.copy(title = newTitle)
        if (newCategory != null) updated = updated.copy(category = newCategory)

        repository.updateGoal(updated)
        
        return AiActionResult(
            success = true,
            message = "Goal updated: ${goal.title}",
            affectedGoalId = goalId
        )
    }

    private suspend fun deleteGoal(repository: NexoraRepository, action: AiAction): AiActionResult {
        val goalId = action.goalId ?: return AiActionResult(false, "Goal ID missing.")
        val goals = repository.observeGoalsOnce()
        val goal = goals.find { it.id == goalId } ?: return AiActionResult(false, "Goal not found.")

        repository.deleteGoal(goal)
        
        return AiActionResult(
            success = true,
            message = "Goal deleted: ${goal.title}",
            affectedGoalId = goalId
        )
    }
}
