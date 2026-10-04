package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.util.NexoraLogger
import com.example.nexora.util.NexoraSecurity

open class AiActionExecutor(
    private val repository: NexoraRepository?,
    private val automationSystem: NexoraAutomationSystem = NexoraAutomationSystem(repository)
) {
    fun getAutomationSystem(): NexoraAutomationSystem = automationSystem

    var onActionExecuted: (() -> Unit)? = null

    private val validator = repository?.let { NexoraAiValidator(it) }

    open suspend fun execute(action: AiAction): AiActionResult {
        val repo = repository ?: return AiActionResult(false, "Repository not available")
        
        NexoraLogger.d(message = "Executing AI action: ${action.type}")

        // 1. Authorization & Validation Layer
        // Skip authorization check for pre-confirmed Agent tools (safety handled by Agent reasoning + confirm card)
        val isAgentConfirmed = action.parameters["userConfirmed"] == true || action.parameters["userConfirmed"]?.toString() == "true"
        if (!isAgentConfirmed && !NexoraSecurity.isAuthorized(action)) {
             return AiActionResult(
                success = false,
                message = "Action requires explicit user confirmation.",
                error = "Authorization error"
            )
        }

        val validationResult = validator?.validate(action)
        if (validationResult is ValidationResult.Invalid) {
            NexoraLogger.w(message = "Validation failed for ${action.type}: ${validationResult.message}")
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
                AiActionType.DECOMPOSE_GOAL -> AiActionResult(false, "Goal decomposition is proposal-only and creates sub-task proposals for review rather than direct database mutations.", error = "Proposal-only action")
                AiActionType.SHOW_INSIGHT -> AiActionResult(true, "Insight displayed.")
                AiActionType.OPEN_TASK -> AiActionResult(true, "Task opened.")
                AiActionType.OPEN_GOAL -> AiActionResult(true, "Goal opened.")
                AiActionType.DELETE_ALL_TASKS -> deleteAllTasks(repo, action)
                AiActionType.COMPLETE_ALL_TASKS -> completeAllTasks(repo, action)
                AiActionType.CREATE_AUTOMATION -> createAutomation(action)
                AiActionType.UPDATE_AUTOMATION -> updateAutomation(action)
                AiActionType.TOGGLE_AUTOMATION -> toggleAutomation(action)
                AiActionType.DELETE_AUTOMATION -> deleteAutomation(action)
            }

            // 3. Verification Layer: Check actual repository/system state
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

        if (result.success) {
            onActionExecuted?.invoke()
        }
        
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
                val task = repository.getTaskById(taskId)
                if (task != null) executionResult else AiActionResult(false, "Verification failed: Task not found in DB after creation.", error = "Verification failed")
            }
            AiActionType.COMPLETE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.getTaskById(taskId)
                if (task?.completed == true) executionResult else AiActionResult(false, "Verification failed: Task still marked incomplete in database.", error = "Verification failed")
            }
            AiActionType.UPDATE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Verification failed: Task lost after update.", error = "Verification failed")
                
                val expectedPriority = action.parameters["priority"] as? String
                if (expectedPriority != null && task.priority.name != expectedPriority) {
                     return AiActionResult(false, "Verification failed: Priority mismatch. Expected $expectedPriority but found ${task.priority.name}", error = "Verification failed")
                }
                val expectedTitle = action.parameters["title"] as? String
                if (expectedTitle != null && task.title != expectedTitle) {
                    return AiActionResult(false, "Verification failed: Title mismatch. Expected \"$expectedTitle\" but found \"${task.title}\"", error = "Verification failed")
                }
                val expectedDuration = action.parameters["duration"] as? String
                if (expectedDuration != null && task.duration != expectedDuration) {
                    return AiActionResult(false, "Verification failed: Duration mismatch. Expected \"$expectedDuration\" but found \"${task.duration}\"", error = "Verification failed")
                }
                executionResult
            }
            AiActionType.DELETE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.getTaskById(taskId)
                if (task == null) executionResult else AiActionResult(false, "Verification failed: Task still exists in database after deletion.", error = "Verification failed")
            }
            AiActionType.RESCHEDULE_TASK -> {
                val taskId = action.taskId ?: return executionResult
                val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Verification failed: Task lost after rescheduling.", error = "Verification failed")
                val expectedPriority = action.parameters["priority"] as? String
                if (expectedPriority != null && task.priority.name != expectedPriority) {
                    return AiActionResult(false, "Verification failed: Rescheduled priority mismatch. Expected $expectedPriority but found ${task.priority.name}", error = "Verification failed")
                }
                executionResult
            }
            AiActionType.CREATE_GOAL -> {
                val goalId = executionResult.affectedGoalId ?: return executionResult
                val goal = repository.getGoalById(goalId)
                if (goal != null) executionResult else AiActionResult(false, "Verification failed: Goal not found in database after creation.", error = "Verification failed")
            }
            AiActionType.UPDATE_GOAL -> {
                val goalId = action.goalId ?: return executionResult
                val goal = repository.getGoalById(goalId) ?: return AiActionResult(false, "Verification failed: Goal not found after update.", error = "Verification failed")
                val expectedTitle = action.parameters["title"] as? String
                if (expectedTitle != null && goal.title != expectedTitle) {
                    return AiActionResult(false, "Verification failed: Goal title mismatch. Expected \"$expectedTitle\" but found \"${goal.title}\"", error = "Verification failed")
                }
                executionResult
            }
            AiActionType.DELETE_GOAL -> {
                val goalId = action.goalId ?: return executionResult
                val goal = repository.getGoalById(goalId)
                if (goal == null) executionResult else AiActionResult(false, "Verification failed: Goal still exists after deletion.", error = "Verification failed")
            }
            AiActionType.DELETE_ALL_TASKS -> {
                val remaining = repository.observeTasksOnce().size
                if (remaining == 0) executionResult else AiActionResult(false, "Verification failed: $remaining tasks still remain after delete-all.", error = "Verification failed")
            }
            AiActionType.COMPLETE_ALL_TASKS -> {
                val remainingIncomplete = repository.getIncompleteTasksOnce().size
                if (remainingIncomplete == 0) executionResult else AiActionResult(false, "Verification failed: $remainingIncomplete tasks still incomplete after complete-all.", error = "Verification failed")
            }
            AiActionType.CREATE_AUTOMATION -> {
                val name = action.parameters["ruleName"] as? String ?: action.parameters["name"] as? String ?: action.parameters["title"] as? String ?: ""
                val clean = name.trim().removeSuffix(".")
                val found = automationSystem.getRules().find { 
                    it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true) 
                }
                if (found != null) executionResult else AiActionResult(false, "Verification failed: Automation rule \"$name\" not found after creation.", error = "Verification failed")
            }
            AiActionType.UPDATE_AUTOMATION -> {
                val newName = action.parameters["newName"] as? String ?: action.parameters["name"] as? String
                val target = action.parameters["ruleId"] as? String ?: action.parameters["ruleName"] as? String ?: action.parameters["name"] as? String ?: ""
                val cleanTarget = target.trim().removeSuffix(".")
                val cleanNewName = newName?.trim()?.removeSuffix(".")
                val found = automationSystem.getRules().find { 
                    (cleanNewName != null && it.name.equals(cleanNewName, ignoreCase = true)) ||
                    it.name.equals(cleanTarget, ignoreCase = true) || 
                    it.id.equals(cleanTarget, ignoreCase = true) 
                }
                if (found != null) executionResult else AiActionResult(false, "Verification failed: Automation rule \"$target\" not found after update.", error = "Verification failed")
            }
            AiActionType.TOGGLE_AUTOMATION -> {
                val target = action.parameters["ruleId"] as? String ?: action.parameters["ruleName"] as? String ?: action.parameters["name"] as? String ?: ""
                val clean = target.trim().removeSuffix(".")
                val expectedEnabled = action.parameters["enabled"] as? Boolean
                val found = automationSystem.getRules().find { 
                    it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true) 
                }
                if (found != null && (expectedEnabled == null || found.enabled == expectedEnabled)) {
                    executionResult
                } else {
                    AiActionResult(false, "Verification failed: Automation rule \"$target\" toggle state did not update.", error = "Verification failed")
                }
            }
            AiActionType.DELETE_AUTOMATION -> {
                val target = action.parameters["ruleId"] as? String ?: action.parameters["ruleName"] as? String ?: action.parameters["name"] as? String ?: ""
                val clean = target.trim().removeSuffix(".")
                val stillExists = automationSystem.getRules().any { 
                    it.id.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
                }
                if (!stillExists) executionResult else AiActionResult(false, "Verification failed: Automation rule \"$target\" still exists after deletion.", error = "Verification failed")
            }
            else -> executionResult
        }
    }

    private suspend fun recordActionOutcome(action: AiAction, result: AiActionResult) {
        val repo = repository ?: return
        try {
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
        } catch (e: Exception) {
            NexoraLogger.e("EXECUTOR", "Failed to record telemetry outcome for action: ${action.title}", e)
        }
    }

    private suspend fun createTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val title = action.parameters["title"] as? String ?: action.title
        
        // 1. Sanity check for duplicates
        val tasks = repository.observeTasksOnce()
        if (tasks.any { it.title.lowercase().trim() == title.lowercase().trim() && !it.completed }) {
            return AiActionResult(false, "A similar active task already exists: \"$title\"")
        }

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
        val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Task not found.")

        if (task.completed) {
            return AiActionResult(
                success = true,
                message = "Task \"${task.title}\" is already completed.",
                affectedTaskId = taskId
            )
        }

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
        val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Task not found.")

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
            message = "Successfully updated task \"${updated.title}\".",
            affectedTaskId = taskId
        )
    }

    private suspend fun deleteTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val taskId = action.taskId ?: return AiActionResult(false, "Task ID missing.")
        val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Task not found.")

        repository.deleteTask(task)
        
        return AiActionResult(
            success = true,
            message = "Task \"${task.title}\" has been permanently deleted.",
            affectedTaskId = taskId
        )
    }

    private suspend fun rescheduleTask(repository: NexoraRepository, action: AiAction): AiActionResult {
        val taskId = action.taskId ?: return AiActionResult(false, "Task ID missing for rescheduling.")
        val task = repository.getTaskById(taskId) ?: return AiActionResult(false, "Task not found.")

        val newPriorityStr = action.parameters["priority"] as? String
        val newDuration = action.parameters["duration"] as? String

        if (newPriorityStr != null || newDuration != null) {
            val newPriority = newPriorityStr?.let {
                try { TaskPriority.valueOf(it) } catch (e: Exception) { null }
            } ?: task.priority
            
            val updated = task.copy(
                priority = newPriority,
                duration = newDuration ?: task.duration
            )
            repository.updateTask(updated)
            return AiActionResult(
                success = true,
                message = "Rescheduled \"${task.title}\" by updating priority to $newPriority.",
                affectedTaskId = taskId
            )
        }

        // If no priority or duration parameter was provided to reschedule the workload, return a truthful non-success result
        return AiActionResult(
            success = false,
            message = "Rescheduling is proposal-only: tasks in Nexora do not store calendar dates. Specify a new priority or duration to reschedule.",
            affectedTaskId = taskId,
            error = "Persistent calendar date property not present on TaskEntity"
        )
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
        val goal = repository.getGoalById(goalId) ?: return AiActionResult(false, "Goal not found.")

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
        val goal = repository.getGoalById(goalId) ?: return AiActionResult(false, "Goal not found.")

        // Unlink tasks before deleting goal
        val tasks = repository.observeTasksOnce().filter { it.goalTitle == goal.title }
        tasks.forEach { repository.updateTask(it.copy(goalTitle = null)) }

        repository.deleteGoal(goal)
        
        return AiActionResult(
            success = true,
            message = "Goal deleted: ${goal.title}",
            affectedGoalId = goalId
        )
    }

    private suspend fun deleteAllTasks(repository: NexoraRepository, action: AiAction): AiActionResult {
        repository.deleteAllTasks()
        val remaining = repository.observeTasksOnce().size
        return AiActionResult(
            success = remaining == 0, 
            message = if (remaining == 0) "Done. I deleted all tasks." else "Failed to delete all tasks. $remaining tasks remain.",
            error = if (remaining == 0) null else "Verification failed"
        )
    }

    private suspend fun completeAllTasks(repository: NexoraRepository, action: AiAction): AiActionResult {
        repository.completeAllTasks()
        val remainingIncomplete = repository.getIncompleteTasksOnce().size
        return AiActionResult(
            success = remainingIncomplete == 0, 
            message = if (remainingIncomplete == 0) "Done. I marked all tasks as complete." else "Failed to complete all tasks. $remainingIncomplete tasks remain incomplete.",
            error = if (remainingIncomplete == 0) null else "Verification failed"
        )
    }

    private suspend fun createAutomation(action: AiAction): AiActionResult {
        val name = action.parameters["ruleName"] as? String 
            ?: action.parameters["name"] as? String 
            ?: action.parameters["title"] as? String
        if (name.isNullOrBlank()) {
            return AiActionResult(false, "Automation rule name missing.", error = "Rule name missing")
        }
        val description = action.parameters["description"] as? String ?: "Custom automation rule"
        val triggerTypeStr = action.parameters["triggerType"] as? String
        val triggerType = try {
            AutomationTriggerType.valueOf(triggerTypeStr ?: "DAY_STARTED")
        } catch (e: Exception) {
            AutomationTriggerType.DAY_STARTED
        }
        val rule = AiAutomationRule(name = name, description = description, triggerType = triggerType)
        val added = automationSystem.addRule(rule)
        return if (added) {
            AiActionResult(true, "Automation rule created: $name")
        } else {
            AiActionResult(false, "Failed to create automation rule: duplicate rule name \"$name\".", error = "Duplicate rule name")
        }
    }

    private suspend fun updateAutomation(action: AiAction): AiActionResult {
        val target = action.parameters["ruleId"] as? String 
            ?: action.parameters["ruleName"] as? String 
            ?: action.parameters["name"] as? String
            ?: action.parameters["title"] as? String
        if (target.isNullOrBlank()) {
            return AiActionResult(false, "Automation rule ID or name missing.", error = "Rule ID missing")
        }
        val existing = automationSystem.findRule(target)
            ?: return AiActionResult(false, "Automation rule not found matching: \"$target\".", error = "Rule not found")
        val newName = action.parameters["newName"] as? String 
            ?: action.parameters["newTitle"] as? String 
            ?: action.parameters["name"] as? String 
            ?: action.parameters["title"] as? String 
            ?: existing.name
        val newDescription = action.parameters["newDescription"] as? String 
            ?: action.parameters["description"] as? String 
            ?: existing.description
        val updated = existing.copy(name = newName, description = newDescription)
        val success = automationSystem.updateRule(updated)
        return if (success) {
            AiActionResult(true, "Automation rule updated: ${updated.name}")
        } else {
            AiActionResult(false, "Failed to persist automation rule update: \"${updated.name}\".", error = "Persistence failed")
        }
    }

    private suspend fun toggleAutomation(action: AiAction): AiActionResult {
        val target = action.parameters["ruleId"] as? String 
            ?: action.parameters["ruleName"] as? String 
            ?: action.parameters["name"] as? String
            ?: action.parameters["title"] as? String
        if (target.isNullOrBlank()) {
            return AiActionResult(false, "Automation rule ID or name missing.", error = "Rule ID missing")
        }
        val enabled = action.parameters["enabled"] as? Boolean
        val toggled = automationSystem.toggleRule(target, enabled)
        return if (toggled != null) {
            AiActionResult(true, "Automation rule \"${toggled.name}\" state toggled to ${toggled.enabled}.")
        } else {
            AiActionResult(false, "Automation rule not found matching: \"$target\".", error = "Rule not found")
        }
    }

    private suspend fun deleteAutomation(action: AiAction): AiActionResult {
        val target = action.parameters["ruleId"] as? String 
            ?: action.parameters["ruleName"] as? String 
            ?: action.parameters["name"] as? String
            ?: action.parameters["title"] as? String
        if (target.isNullOrBlank()) {
            return AiActionResult(false, "Automation rule ID or name missing.", error = "Rule ID missing")
        }
        val existing = automationSystem.findRule(target)
            ?: return AiActionResult(false, "Automation rule not found matching: \"$target\".", error = "Rule not found")
        val deleted = automationSystem.deleteRule(existing.id)
        return if (deleted) {
            AiActionResult(true, "Automation rule deleted: ${existing.name}")
        } else {
            AiActionResult(false, "Failed to delete automation rule: \"${existing.name}\".", error = "Rule not found or persistence failed")
        }
    }
}
