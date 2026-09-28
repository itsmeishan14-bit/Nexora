package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Deterministic local engine for calculating predictive personal intelligence signals.
 * Analyzes tasks, goals, workload, adaptive profile, and historical progress.
 */
class NexoraPredictiveEngine {

    /**
     * Generates all relevant predictive intelligence signals from the current AI context.
     */
    fun generatePredictions(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()

        predictions.addAll(predictTaskDelayRisks(context))
        predictions.addAll(predictGoalRisksAndTimings(context))
        predictWorkloadOverload(context)?.let { predictions.add(it) }
        predictProductivityTrend(context)?.let { predictions.add(it) }

        return predictions
    }

    /**
     * Calculates delay risks for incomplete tasks based on historical behavior and current workload.
     */
    fun predictTaskDelayRisks(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()
        val profile = context.adaptiveProfile
        val incompleteTasks = context.incompleteTasks

        if (incompleteTasks.isEmpty()) return emptyList()

        val isDataSufficient = profile.sampleCount >= 2 || context.recentEvaluations.isNotEmpty() || context.memory.items.isNotEmpty()
        val defaultConfidence = if (isDataSufficient) {
            if (profile.sampleCount >= 5) AiConfidence.HIGH else AiConfidence.MEDIUM
        } else {
            AiConfidence.LOW
        }

        incompleteTasks.forEach { task ->
            var riskScore = 0.0f
            val factors = mutableListOf<ReasoningFactor>()

            // 1. Duration factor
            val durationMin = extractDurationMinutes(task.duration)
            if (durationMin > 120) {
                riskScore += 0.35f
                factors.add(ReasoningFactor("Task Size", ReasoningImpact.CRITICAL, "Task duration ($durationMin min) exceeds typical focus blocks."))
            } else if (durationMin > 60) {
                riskScore += 0.20f
                factors.add(ReasoningFactor("Task Size", ReasoningImpact.POSITIVE, "Task requires over an hour of sustained focus."))
            }

            // 2. Priority factor
            if (task.priority == TaskPriority.URGENT) {
                riskScore += 0.15f
                factors.add(ReasoningFactor("Priority", ReasoningImpact.CRITICAL, "Marked as URGENT priority."))
            }

            // 3. Workload pressure factor
            if (context.personalContext.workload.state == WorkloadState.VERY_HIGH) {
                riskScore += 0.25f
                factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.CRITICAL, "Current workload is VERY HIGH relative to typical capacity."))
            } else if (context.personalContext.workload.state == WorkloadState.HIGH) {
                riskScore += 0.15f
                factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.POSITIVE, "Current workload is high."))
            }

            // 4. Goal health factor
            val linkedGoal = context.goals.find { it.title == task.goalTitle }
            if (linkedGoal != null) {
                val health = context.personalContext.goalHealth.find { it.goalId == linkedGoal.id }
                if (health?.state == GoalHealthState.AT_RISK) {
                    riskScore += 0.20f
                    factors.add(ReasoningFactor("Goal Health", ReasoningImpact.CRITICAL, "Linked goal \"${linkedGoal.title}\" is currently at risk."))
                }
            }

            // 5. Memory / Carry-forward pattern factor
            val taskMemory = context.memory.items.find { it.category == AiMemoryCategory.TASK_PATTERN && it.relatedTaskId == task.id }
            if (taskMemory != null && taskMemory.content.contains("carried", ignoreCase = true)) {
                riskScore += 0.30f
                factors.add(ReasoningFactor("Carry-forward Pattern", ReasoningImpact.CRITICAL, "Task has been carried forward in previous sessions."))
            }

            val probability = riskScore.coerceIn(0.05f, 0.95f)
            val riskLevel = when {
                probability >= 0.7f -> AiPriority.CRITICAL
                probability >= 0.45f -> AiPriority.HIGH
                probability >= 0.25f -> AiPriority.MEDIUM
                else -> AiPriority.LOW
            }

            val statusText = if (defaultConfidence == AiConfidence.gitINSUFFICIENT_DATA) {
                "INSUFFICIENT_DATA (Building initial history)"
            } else if (probability >= 0.5f) {
                "High Delay Risk (${(probability * 100).toInt()}% probability)"
            } else {
                "Low Delay Risk (${(probability * 100).toInt()}% probability)"
            }

            // Only add prediction if elevated risk or if data is insufficient
            if (probability >= 0.35f || defaultConfidence == AiConfidence.INSUFFICIENT_DATA) {
                predictions.add(
                    AiPrediction(
                        type = PredictionType.TASK_DELAY_RISK,
                        targetId = task.id,
                        targetTitle = task.title,
                        prediction = statusText,
                        probability = probability,
                        confidence = defaultConfidence,
                        riskLevel = riskLevel,
                        evidence = "Calculated from duration ($durationMin min), priority (${task.priority}), and workload pressure.",
                        contributingFactors = factors
                    )
                )
            }
        }

        return predictions
    }

    /**
     * Calculates risk status and estimated completion timing for active goals.
     */
    fun predictGoalRisksAndTimings(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()

        context.activeGoals.filter { it.progress < 1.0f }.forEach { goal ->
            val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
            val incompleteLinked = linkedTasks.filter { !it.completed }
            val completedLinked = linkedTasks.count { it.completed }

            // Estimate daily completion velocity from adaptive profile or history
            val dailyVelocity = max(1.0f, context.adaptiveProfile.averageTasksCompleted.takeIf { it > 0f } ?: 2.0f)
            val remainingTasks = max(1, incompleteLinked.size.takeIf { it > 0 } ?: ((1.0f - goal.progress) * 5).toInt())
            val estimatedDays = (remainingTasks.toFloat() / dailyVelocity * 1.5f).toInt().coerceAtLeast(1)

            val daysToDeadline = parseTargetDateDaysRemaining(goal.targetDate)
            
            val isAtRisk = (daysToDeadline != null && estimatedDays > daysToDeadline) ||
                           (goal.progress < 0.3f && completedLinked == 0 && linkedTasks.isNotEmpty()) ||
                           (context.personalContext.goalHealth.find { it.goalId == goal.id }?.state == GoalHealthState.AT_RISK)

            val riskLevel = when {
                daysToDeadline != null && estimatedDays > daysToDeadline -> AiPriority.CRITICAL
                isAtRisk -> AiPriority.HIGH
                else -> AiPriority.LOW
            }

            val statusText = when {
                daysToDeadline != null && estimatedDays > daysToDeadline -> "DELAYED (Estimated $estimatedDays days needed vs $daysToDeadline days remaining)"
                isAtRisk -> "AT_RISK (Progress is behind expected trajectory)"
                else -> "ON_TRACK (Estimated ~$estimatedDays days to completion)"
            }

            val factors = mutableListOf<ReasoningFactor>()
            factors.add(ReasoningFactor("Progress", ReasoningImpact.NEUTRAL, "Current progress: ${(goal.progress * 100).toInt()}%."))
            factors.add(ReasoningFactor("Tasks Remaining", ReasoningImpact.POSITIVE, "$remainingTasks sub-tasks remaining."))
            if (daysToDeadline != null) {
                factors.add(ReasoningFactor("Deadline", if (estimatedDays > daysToDeadline) ReasoningImpact.CRITICAL else ReasoningImpact.POSITIVE, "$daysToDeadline days remaining to target date."))
            }

            val confidence = if (context.memory.analyzedDays >= 3 || linkedTasks.isNotEmpty()) AiConfidence.HIGH else AiConfidence.MEDIUM

            predictions.add(
                AiPrediction(
                    type = PredictionType.GOAL_RISK,
                    targetId = goal.id,
                    targetTitle = goal.title,
                    prediction = statusText,
                    probability = if (isAtRisk) 0.75f else 0.2f,
                    confidence = confidence,
                    riskLevel = riskLevel,
                    evidence = "Estimated $estimatedDays days based on $remainingTasks remaining tasks and daily velocity of $dailyVelocity tasks/day.",
                    contributingFactors = factors,
                    estimatedDaysToCompletion = estimatedDays
                )
            )
        }

        return predictions
    }

    /**
     * Predicts whether today's workload presents an overload risk.
     */
    fun predictWorkloadOverload(context: AiContext): AiPrediction? {
        val profile = context.adaptiveProfile
        val incomplete = context.incompleteTasks
        val plannedToday = context.tasksPlannedToday
        val baselineCapacity = max(1, profile.preferredDailyWorkload)

        if (incomplete.isEmpty() && plannedToday == 0) return null

        val totalMinutes = incomplete.sumOf { extractDurationMinutes(it.duration) }
        val probability = (plannedToday.toFloat() / baselineCapacity.toFloat() * 0.5f +
                           incomplete.size.toFloat() / (baselineCapacity * 2).toFloat() * 0.5f).coerceIn(0.1f, 0.95f)

        val state = when {
            probability >= 0.8f -> "OVERLOADED"
            probability >= 0.6f -> "HEAVY"
            probability >= 0.35f -> "BALANCED"
            else -> "LIGHT"
        }

        val riskLevel = when (state) {
            "OVERLOADED" -> AiPriority.CRITICAL
            "HEAVY" -> AiPriority.HIGH
            "BALANCED" -> AiPriority.LOW
            else -> AiPriority.LOW
        }

        val factors = listOf(
            ReasoningFactor("Incomplete Count", ReasoningImpact.NEUTRAL, "${incomplete.size} total incomplete tasks."),
            ReasoningFactor("Planned Today", ReasoningImpact.NEUTRAL, "$plannedToday tasks planned today vs baseline capacity of $baselineCapacity."),
            ReasoningFactor("Total Duration", ReasoningImpact.NEUTRAL, "${totalMinutes} minutes of total estimated work.")
        )

        return AiPrediction(
            type = PredictionType.WORKLOAD_OVERLOAD_RISK,
            prediction = "Workload state: $state (${(probability * 100).toInt()}% load risk)",
            probability = probability,
            confidence = if (profile.sampleCount >= 2) AiConfidence.HIGH else AiConfidence.MEDIUM,
            riskLevel = riskLevel,
            evidence = "Planned $plannedToday tasks vs historical capacity of $baselineCapacity tasks/day.",
            contributingFactors = factors
        )
    }

    /**
     * Calculates time-weighted productivity trend prediction.
     */
    fun predictProductivityTrend(context: AiContext): AiPrediction? {
        val history = context.recentEvaluations
        val trend = context.personalContext.productivityTrend

        if (trend == ProductivityTrend.INSUFFICIENT_DATA) {
            return AiPrediction(
                type = PredictionType.PRODUCTIVITY_TREND_PREDICTION,
                prediction = "INSUFFICIENT_DATA",
                probability = 0.0f,
                confidence = AiConfidence.LOW,
                riskLevel = AiPriority.LOW,
                evidence = "Need at least 3-5 days of completion history to calculate productivity trend."
            )
        }

        val statusText = when (trend) {
            ProductivityTrend.IMPROVING -> "IMPROVING (Execution rate is trending upward)"
            ProductivityTrend.DECLINING -> "DECLINING (Recent completion pace is below baseline)"
            else -> "STABLE (Consistent execution pace)"
        }

        return AiPrediction(
            type = PredictionType.PRODUCTIVITY_TREND_PREDICTION,
            prediction = statusText,
            probability = if (trend == ProductivityTrend.DECLINING) 0.7f else 0.3f,
            confidence = AiConfidence.HIGH,
            riskLevel = if (trend == ProductivityTrend.DECLINING) AiPriority.HIGH else AiPriority.LOW,
            evidence = "Calculated from time-weighted completion rate history across recent days."
        )
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: 30
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
    }

    private fun parseTargetDateDaysRemaining(targetDate: String): Long? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = LocalDate.parse(targetDate, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, date)
        } catch (e: Exception) {
            null
        }
    }
}
