package com.example.nexora.ai

import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import java.time.LocalDate

class AiContextBuilder(
    private val repository: NexoraRepository
) {

    suspend fun build(): AiContext {

        val tasks = repository.observeTasksOnce()
        val goals = repository.observeGoalsOnce()

        val today = LocalDate.now().toString()

        val todayProgress =
            repository.getDailyProgress(today)

        val history = repository.getHistoricalProgress(30)
        
        val planner = AiPlanner()
        val memory = planner.detectPatterns(history)

        return AiContext(
            tasks = tasks,
            goals = goals,
            tasksCompletedToday = todayProgress?.tasksCompleted ?: 0,
            tasksPlannedToday = todayProgress?.tasksPlanned ?: 0,
            focusMinutesToday = todayProgress?.focusMinutes ?: 0,
            goalsWorkedOnToday = todayProgress?.goalsWorkedOn ?: 0,
            carriedTasks = todayProgress?.carriedTasks ?: 0,
            memory = memory
        )
    }
}
