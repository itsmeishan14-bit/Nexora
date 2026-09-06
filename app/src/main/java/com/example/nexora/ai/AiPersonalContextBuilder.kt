package com.example.nexora.ai

import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import java.time.LocalDate

class AiPersonalContextBuilder {

    fun build(
        tasks: List<PremiumTask>,
        goals: List<NexoraGoal>,
        todayProgress: DailyProgressEntity?,
        history: List<DailyProgressEntity>,
        adaptiveProfile: AdaptiveProfile,
        memory: AiMemory
    ): AiPersonalContext {
        val workload = assessWorkload(tasks, adaptiveProfile, todayProgress)
        val goalHealth = assessGoalHealth(goals, tasks, history)
        val dayState = assessDayState(todayProgress, adaptiveProfile, workload)
        val trend = assessProductivityTrend(history)
        
        val risks = detectRisks(workload, goalHealth, dayState, trend, tasks, adaptiveProfile)
        val opportunities = detectOpportunities(tasks, goals, adaptiveProfile, workload)
        
        val confidence = mapAdaptiveConfidence(adaptiveProfile.confidence)

        return AiPersonalContext(
            workload = workload,
            goalHealth = goalHealth,
            dayState = dayState,
            productivityTrend = trend,
            risks = risks,
            opportunities = opportunities,
            confidence = confidence
        )
    }

    private fun assessWorkload(
        tasks: List<PremiumTask>,
        profile: AdaptiveProfile,
        todayProgress: DailyProgressEntity?
    ): WorkloadAssessment {
        val incomplete = tasks.filter { !it.completed }
        val totalMinutes = incomplete.sumOf { extractDurationMinutes(it.duration) }
        val taskCount = incomplete.size
        val baseline = profile.preferredDailyWorkload

        val plannedToday = todayProgress?.tasksPlanned ?: 0
        
        val state = when {
            plannedToday > baseline * 1.8 || (taskCount > baseline * 2 && totalMinutes > 480) -> WorkloadState.VERY_HIGH
            plannedToday > baseline * 1.3 || taskCount > baseline * 1.5 -> WorkloadState.HIGH
            plannedToday < baseline * 0.5 && taskCount < baseline * 0.5 -> WorkloadState.LOW
            plannedToday == 0 && taskCount == 0 -> WorkloadState.VERY_LOW
            else -> WorkloadState.BALANCED
        }

        val deviation = when {
            Math.abs(plannedToday - baseline) > baseline * 1.0 -> WorkloadDeviation.EXTREME
            Math.abs(plannedToday - baseline) > baseline * 0.5 -> WorkloadDeviation.SIGNIFICANT
            else -> WorkloadDeviation.NEGLIGIBLE
        }

        val evidence = "Planned: $plannedToday, Baseline: $baseline, Total Incomplete: $taskCount (${totalMinutes}m)."

        return WorkloadAssessment(
            state = state,
            deviationFromBaseline = deviation,
            totalEstimatedMinutes = totalMinutes,
            taskCount = taskCount,
            baselineCapacity = baseline,
            evidence = evidence
        )
    }

    private fun assessGoalHealth(
        goals: List<NexoraGoal>,
        tasks: List<PremiumTask>,
        history: List<DailyProgressEntity>
    ): List<GoalHealthAssessment> {
        return goals.filter { it.progress < 1.0f }.map { goal ->
            val linkedTasks = tasks.filter { it.goalTitle == goal.title }
            val completedRecently = linkedTasks.count { it.completed } // Simple heuristic
            
            val activity = when {
                completedRecently > 3 -> ActivityLevel.HIGH
                completedRecently > 0 -> ActivityLevel.MODERATE
                linkedTasks.any { !it.completed } -> ActivityLevel.LOW
                else -> ActivityLevel.NONE
            }

            // Heuristic for neglected duration: if no tasks completed in last X days of history
            // For now, let's keep it simple
            val neglectedDays = if (activity == ActivityLevel.NONE) 7 else 0 

            val state = when {
                goal.progress > 0.8f -> GoalHealthState.HEALTHY
                activity == ActivityLevel.NONE && goal.progress < 0.5f -> GoalHealthState.AT_RISK
                activity == ActivityLevel.LOW -> GoalHealthState.NEEDS_ATTENTION
                history.isEmpty() -> GoalHealthState.INSUFFICIENT_DATA
                else -> GoalHealthState.HEALTHY
            }

            GoalHealthAssessment(
                goalId = goal.id,
                goalTitle = goal.title,
                state = state,
                progress = goal.progress,
                recentActivityLevel = activity,
                neglectedDurationDays = neglectedDays,
                evidence = "Progress: ${(goal.progress * 100).toInt()}%, Activity: $activity"
            )
        }
    }

    private fun assessDayState(
        todayProgress: DailyProgressEntity?,
        profile: AdaptiveProfile,
        workload: WorkloadAssessment
    ): CurrentDayState {
        if (todayProgress == null || todayProgress.tasksPlanned == 0) return CurrentDayState.NOT_STARTED
        
        val completed = todayProgress.tasksCompleted
        val planned = todayProgress.tasksPlanned
        val rate = completed.toFloat() / planned.toFloat()
        
        return when {
            workload.state == WorkloadState.VERY_HIGH -> CurrentDayState.OVERLOADED
            rate >= 1.0f -> CurrentDayState.AHEAD
            rate >= 0.6f -> CurrentDayState.ON_TRACK
            rate > 0f -> CurrentDayState.BEHIND
            else -> CurrentDayState.BEHIND
        }
    }

    private fun assessProductivityTrend(history: List<DailyProgressEntity>): ProductivityTrend {
        if (history.size < 5) return ProductivityTrend.INSUFFICIENT_DATA
        
        val recent = history.take(3).map { it.tasksCompleted.toFloat() / Math.max(1, it.tasksPlanned) }
        val older = history.drop(3).take(3).map { it.tasksCompleted.toFloat() / Math.max(1, it.tasksPlanned) }
        
        val recentAvg = recent.average()
        val olderAvg = older.average()
        
        return when {
            recentAvg > olderAvg + 0.2 -> ProductivityTrend.IMPROVING
            recentAvg < olderAvg - 0.2 -> ProductivityTrend.DECLINING
            else -> ProductivityTrend.STABLE
        }
    }

    private fun detectRisks(
        workload: WorkloadAssessment,
        goalHealth: List<GoalHealthAssessment>,
        dayState: CurrentDayState,
        trend: ProductivityTrend,
        tasks: List<PremiumTask>,
        profile: AdaptiveProfile
    ): List<AiRisk> {
        val risks = mutableListOf<AiRisk>()
        
        if (workload.state == WorkloadState.VERY_HIGH || workload.state == WorkloadState.HIGH) {
            risks.add(AiRisk(
                RiskType.OVERLOAD,
                if (workload.state == WorkloadState.VERY_HIGH) AiPriority.CRITICAL else AiPriority.HIGH,
                "Current workload exceeds your typical capacity.",
                workload.evidence
            ))
        }
        
        goalHealth.filter { it.state == GoalHealthState.AT_RISK }.forEach { goal ->
            risks.add(AiRisk(
                RiskType.NEGLECTED_GOAL,
                AiPriority.HIGH,
                "Goal \"${goal.goalTitle}\" is at risk due to inactivity.",
                goal.evidence,
                relatedGoalId = goal.goalId
            ))
        }
        
        if (trend == ProductivityTrend.DECLINING) {
            risks.add(AiRisk(
                RiskType.PERFORMANCE_DROP,
                AiPriority.MEDIUM,
                "Your recent completion rate is showing a downward trend.",
                "Compare recent vs older history."
            ))
        }

        val highCarryOver = tasks.count { !it.completed && it.priority == TaskPriority.URGENT } > 2
        if (highCarryOver) {
            risks.add(AiRisk(
                RiskType.CARRY_OVER_PATTERN,
                AiPriority.HIGH,
                "Multiple urgent tasks are being carried forward.",
                "Check urgent incomplete tasks."
            ))
        }

        return risks
    }

    private fun detectOpportunities(
        tasks: List<PremiumTask>,
        goals: List<NexoraGoal>,
        profile: AdaptiveProfile,
        workload: WorkloadAssessment
    ): List<AiOpportunity> {
        val opportunities = mutableListOf<AiOpportunity>()
        
        val quickWin = tasks.find { !it.completed && extractDurationMinutes(it.duration) <= 15 }
        if (quickWin != null) {
            opportunities.add(AiOpportunity(
                OpportunityType.QUICK_WIN,
                "Quick Win Available",
                "You can finish \"${quickWin.title}\" in under 15 minutes.",
                relatedTaskId = quickWin.id
            ))
        }
        
        val nearCompletion = goals.find { it.progress > 0.85f && it.progress < 1.0f }
        if (nearCompletion != null) {
            opportunities.add(AiOpportunity(
                OpportunityType.GOAL_MOMENTUM,
                "Goal Near Completion",
                "\"${nearCompletion.title}\" is 85% complete. A small push could finish it.",
                relatedGoalId = nearCompletion.id
            ))
        }
        
        if (workload.state == WorkloadState.LOW || workload.state == WorkloadState.VERY_LOW) {
            opportunities.add(AiOpportunity(
                OpportunityType.CAPACITY_AVAILABLE,
                "Capacity for Deep Work",
                "Your current workload is low, making it a good time for a high-value task."
            ))
        }

        return opportunities
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: 30
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
    }

    private fun mapAdaptiveConfidence(confidence: AdaptiveConfidence): AiConfidence {
        return when (confidence) {
            AdaptiveConfidence.HIGH -> AiConfidence.HIGH
            AdaptiveConfidence.MODERATE -> AiConfidence.MEDIUM
            AdaptiveConfidence.LOW -> AiConfidence.LOW
            AdaptiveConfidence.UNKNOWN -> AiConfidence.LOW
        }
    }
}
