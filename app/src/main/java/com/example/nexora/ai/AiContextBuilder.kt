package com.example.nexora.ai

import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import java.time.LocalDate

open class AiContextBuilder(
    private val repository: NexoraRepository?
) {

    open suspend fun build(request: AiRequest? = null): AiContext {
        if (repository == null) return AiContext()

        // Apply Data Minimization: only load what's needed for the request
        val tasks = when {
            request?.type == AiRequestType.NEXT_TASK -> repository.getIncompleteTasksOnce()
            shouldLoadTasks(request) -> repository.observeTasksOnce()
            else -> emptyList()
        }
        val goals = if (shouldLoadGoals(request)) repository.observeGoalsOnce() else emptyList()

        val today = LocalDate.now().toString()

        val todayProgress =
            repository.getDailyProgress(today)

        val history = if (shouldLoadHistory(request)) repository.getHistoricalProgress(30) else emptyList()
        val recentOutcomes = if (shouldLoadHistory(request)) repository.getRecentOutcomes(20) else emptyList()
        val recentEvaluations = if (shouldLoadHistory(request)) repository.getRecentEvaluations(10) else emptyList()
        
        val planner = AiPlanner()
        val memory = if (shouldLoadMemory(request)) planner.detectPatterns(history) else AiMemory()
        val adaptiveProfile = if (shouldLoadAdaptive(request)) calculateAdaptiveProfile(history, tasks) else AdaptiveProfile()

        val personalContextBuilder = AiPersonalContextBuilder()
        val personalContext = if (shouldLoadPersonal(request)) personalContextBuilder.build(
            tasks = tasks,
            goals = goals,
            todayProgress = todayProgress,
            history = history,
            adaptiveProfile = adaptiveProfile,
            memory = memory
        ) else AiPersonalContext()

        return AiContext(
            tasks = tasks,
            goals = goals,
            tasksCompletedToday = todayProgress?.tasksCompleted ?: 0,
            tasksPlannedToday = todayProgress?.tasksPlanned ?: 0,
            focusMinutesToday = todayProgress?.focusMinutes ?: 0,
            goalsWorkedOnToday = todayProgress?.goalsWorkedOn ?: 0,
            carriedTasks = todayProgress?.carriedTasks ?: 0,
            memory = memory,
            adaptiveProfile = adaptiveProfile,
            recentOutcomes = recentOutcomes,
            recentEvaluations = recentEvaluations,
            personalContext = personalContext
        )
    }

    private fun shouldLoadTasks(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.NEXT_TASK, AiRequestType.DAILY_PLAN, AiRequestType.CREATE_TASK, AiRequestType.UPDATE_TASK, AiRequestType.COMPLETE_TASK, AiRequestType.GENERAL_ANALYSIS, AiRequestType.CHAT)
    }

    private fun shouldLoadGoals(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.GOAL_ANALYSIS, AiRequestType.GOAL_DECOMPOSITION, AiRequestType.UPDATE_GOAL, AiRequestType.GENERAL_ANALYSIS, AiRequestType.CHAT)
    }

    private fun shouldLoadHistory(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.PRODUCTIVITY_ANALYSIS, AiRequestType.PROACTIVE_ANALYSIS, AiRequestType.GENERAL_ANALYSIS, AiRequestType.CHAT)
    }

    private fun shouldLoadMemory(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.GENERAL_ANALYSIS, AiRequestType.CHAT, AiRequestType.PROACTIVE_ANALYSIS)
    }

    private fun shouldLoadAdaptive(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.DAILY_PLAN, AiRequestType.NEXT_TASK, AiRequestType.GENERAL_ANALYSIS)
    }

    private fun shouldLoadPersonal(request: AiRequest?): Boolean {
        if (request == null) return true
        return request.type in listOf(AiRequestType.GENERAL_ANALYSIS, AiRequestType.CHAT, AiRequestType.PROACTIVE_ANALYSIS)
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

        // Calculate consistency
        val rates = history.filter { it.tasksPlanned > 0 }
            .map { it.tasksCompleted.toFloat() / it.tasksPlanned }
        
        val consistency = if (rates.size > 1) {
            val mean = rates.average()
            val variance = rates.map { Math.pow(it - mean, 2.0) }.average()
            (1.0 - Math.min(variance * 5, 1.0)).toFloat()
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
