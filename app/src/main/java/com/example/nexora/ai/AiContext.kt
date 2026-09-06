package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask

data class AiContext(
    val tasks: List<PremiumTask> = emptyList(),
    val goals: List<NexoraGoal> = emptyList(),
    val tasksCompletedToday: Int = 0,
    val tasksPlannedToday: Int = 0,
    val focusMinutesToday: Int = 0,
    val goalsWorkedOnToday: Int = 0,
    val carriedTasks: Int = 0,
    val memory: AiMemory = AiMemory()
) {
    val incompleteTasks: List<PremiumTask>
        get() = tasks.filter { !it.completed }

    val completedTasks: List<PremiumTask>
        get() = tasks.filter { it.completed }

    val activeGoals: List<NexoraGoal>
        get() = goals.filter { it.progress < 1f }

    val completedGoals: List<NexoraGoal>
        get() = goals.filter { it.progress >= 1f }
}