package com.example.nexora.ai

import java.util.UUID

enum class AiActionType {
    CREATE_TASK,
    COMPLETE_TASK,
    UPDATE_TASK,
    DELETE_TASK,
    RESCHEDULE_TASK,
    CREATE_GOAL,
    UPDATE_GOAL,
    DELETE_GOAL,
    DECOMPOSE_GOAL,
    SHOW_INSIGHT,
    OPEN_TASK,
    OPEN_GOAL
}

data class AiAction(
    val id: String = UUID.randomUUID().toString(),
    val type: AiActionType,
    val title: String,
    val description: String,
    val reason: String? = null,
    val taskId: Long? = null,
    val goalId: Long? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val priority: AiPriority = AiPriority.MEDIUM,
    val requiresConfirmation: Boolean = true
)

data class AiActionResult(
    val success: Boolean,
    val message: String,
    val affectedTaskId: Long? = null,
    val affectedGoalId: Long? = null,
    val error: String? = null
)
