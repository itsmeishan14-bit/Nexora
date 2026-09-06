package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask

data class PlannedTask(
    val task: PremiumTask,
    val reason: String,
    val recommendedOrder: Int
)

data class NexoraDailyPlan(
    val date: String,
    val tasks: List<PlannedTask> = emptyList(),
    val totalDurationMinutes: Int = 0,
    val summary: String = ""
)
