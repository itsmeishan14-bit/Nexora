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
        val adaptiveProfile = calculateAdaptiveProfile(history, tasks)

        return AiContext(
            tasks = tasks,
            goals = goals,
            tasksCompletedToday = todayProgress?.tasksCompleted ?: 0,
            tasksPlannedToday = todayProgress?.tasksPlanned ?: 0,
            focusMinutesToday = todayProgress?.focusMinutes ?: 0,
            goalsWorkedOnToday = todayProgress?.goalsWorkedOn ?: 0,
            carriedTasks = todayProgress?.carriedTasks ?: 0,
            memory = memory,
            adaptiveProfile = adaptiveProfile
        )
    }

    private fun calculateAdaptiveProfile(
        history: List<DailyProgressEntity>,
        tasks: List<PremiumTask>
    ): AdaptiveProfile {
        if (history.isEmpty()) return AdaptiveProfile()

        val sampleCount = history.size
        
        // Recency weighting: history is ordered by date DESC
        // We give more weight to recent days.
        var totalWeight = 0f
        var weightedCompleted = 0f
        var weightedFocus = 0f
        var weightedCarried = 0f
        
        history.forEachIndexed { index, entry ->
            val weight = if (index < 7) 1.0f else 0.5f // Higher weight for last 7 entries
            weightedCompleted += entry.tasksCompleted * weight
            weightedFocus += entry.focusMinutes * weight
            weightedCarried += entry.carriedTasks * weight
            totalWeight += weight
        }

        val avgCompleted = weightedCompleted / totalWeight
        val avgFocus = weightedFocus / totalWeight
        val avgCarried = weightedCarried / totalWeight
        
        val totalPlanned = history.sumOf { it.tasksPlanned }
        val totalCompleted = history.sumOf { it.tasksCompleted }
        val completionRate = if (totalPlanned > 0) totalCompleted.toFloat() / totalPlanned else 0f

        val confidence = when {
            sampleCount >= 14 -> AdaptiveConfidence.HIGH
            sampleCount >= 7 -> AdaptiveConfidence.MODERATE
            sampleCount >= 3 -> AdaptiveConfidence.LOW
            else -> AdaptiveConfidence.UNKNOWN
        }

        // Calculate consistency (variance in completion rate could be used, but keep it simple)
        val rates = history.filter { it.tasksPlanned > 0 }
            .map { it.tasksCompleted.toFloat() / it.tasksPlanned }
        
        val consistency = if (rates.size > 1) {
            val mean = rates.average()
            val variance = rates.map { Math.pow(it - mean, 2.0) }.average()
            (1.0 - Math.min(variance * 5, 1.0)).toFloat() // Heuristic: high variance = low consistency
        } else 0.5f

        // Preferred task size based on completed tasks
        val completedTasks = tasks.filter { it.completed }
        val preferredSize = if (completedTasks.isNotEmpty()) {
            val avgDur = completedTasks.map { extractDurationMinutes(it.duration) }.average()
            when {
                avgDur < 30 -> "Small"
                avgDur > 90 -> "Large"
                else -> "Medium"
            }
        } else "Medium"

        return AdaptiveProfile(
            averageTasksCompleted = avgCompleted,
            averageFocusMinutes = avgFocus,
            averageCarriedTasks = avgCarried,
            completionRate = completionRate,
            consistencyScore = consistency,
            preferredTaskSize = preferredSize,
            preferredDailyWorkload = Math.max(3, Math.round(avgCompleted)),
            sampleCount = sampleCount,
            confidence = confidence
        )
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: return 30
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
    }
}
