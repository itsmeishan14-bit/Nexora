package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository

class NexoraAiValidator(
    private val repository: NexoraRepository
) {
    suspend fun validate(action: AiAction): ValidationResult {
        return when (action.type) {
            AiActionType.COMPLETE_TASK,
            AiActionType.UPDATE_TASK,
            AiActionType.DELETE_TASK,
            AiActionType.RESCHEDULE_TASK,
            AiActionType.OPEN_TASK -> validateTaskAction(action)

            AiActionType.UPDATE_GOAL,
            AiActionType.DELETE_GOAL,
            AiActionType.DECOMPOSE_GOAL,
            AiActionType.OPEN_GOAL -> validateGoalAction(action)

            AiActionType.CREATE_TASK -> validateCreateTask(action)
            AiActionType.CREATE_GOAL -> validateCreateGoal(action)
            
            AiActionType.SHOW_INSIGHT -> ValidationResult.Valid
        }
    }

    private suspend fun validateTaskAction(action: AiAction): ValidationResult {
        val taskId = action.taskId ?: return ValidationResult.Invalid("Task ID is missing.")
        val tasks = repository.observeTasksOnce()
        val task = tasks.find { it.id == taskId } ?: return ValidationResult.Invalid("Task with ID $taskId not found.")

        if (action.type == AiActionType.COMPLETE_TASK && task.completed) {
            return ValidationResult.Invalid("Task \"${task.title}\" is already completed.")
        }

        return ValidationResult.Valid
    }

    private suspend fun validateGoalAction(action: AiAction): ValidationResult {
        val goalId = action.goalId ?: return ValidationResult.Invalid("Goal ID is missing.")
        val goals = repository.observeGoalsOnce()
        val goal = goals.find { it.id == goalId } ?: return ValidationResult.Invalid("Goal with ID $goalId not found.")
        
        return ValidationResult.Valid
    }

    private fun validateCreateTask(action: AiAction): ValidationResult {
        val title = action.parameters["title"] as? String ?: action.title
        if (title.isBlank()) {
            return ValidationResult.Invalid("Task title cannot be empty.")
        }
        return ValidationResult.Valid
    }

    private fun validateCreateGoal(action: AiAction): ValidationResult {
        val title = action.parameters["title"] as? String ?: action.title
        if (title.isBlank()) {
            return ValidationResult.Invalid("Goal title cannot be empty.")
        }
        return ValidationResult.Valid
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}
