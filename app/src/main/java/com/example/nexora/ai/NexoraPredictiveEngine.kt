package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Predictive Personal Intelligence Engine 2.0.
 * Evidence-based, self-calibrating, personalized predictions with real uncertainty modeling.
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
     * Calculates delay risks for incomplete tasks using personal history, similar task behavior,
     * task-size completion rates, and workload pressure.
     */
    fun predictTaskDelayRisks(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()
        val profile = context.adaptiveProfile
        val incompleteTasks = context.incompleteTasks

        if (incompleteTasks.isEmpty()) return emptyList()

        val totalObservations = profile.sampleCount + context.recentEvaluations.size + context.memory.items.size
        val evidenceQuality = calculateEvidenceQuality(totalObservations)
        val isDataSufficient = evidenceQuality != EvidenceQuality.INSUFFICIENT

        val defaultConfidence = when (evidenceQuality) {
            EvidenceQuality.STRONG -> AiConfidence.HIGH
            EvidenceQuality.MODERATE -> AiConfidence.MEDIUM
            EvidenceQuality.WEAK, EvidenceQuality.INSUFFICIENT -> AiConfidence.LOW
        }

        val calibrationFactor = getCalibrationFactor(context)

        incompleteTasks.forEach { task ->
            var riskScore = 0.0f
            val factors = mutableListOf<ReasoningFactor>()

            // 1. Task Size & Duration Bucket
            val durationMin = extractDurationMinutes(task.duration)
            val sizeBucket = classifyTaskSize(durationMin)
            
            if (sizeBucket == "VERY_LARGE" || sizeBucket == "LARGE") {
                riskScore += if (sizeBucket == "VERY_LARGE") 0.35f else 0.20f
                factors.add(ReasoningFactor("Task Size", ReasoningImpact.CRITICAL, "Task duration ($durationMin min) is classified as $sizeBucket."))
            }

            // 2. Priority
            if (task.priority == TaskPriority.URGENT) {
                riskScore += 0.15f
                factors.add(ReasoningFactor("Priority", ReasoningImpact.CRITICAL, "Marked as URGENT priority."))
            }

            // 3. Workload Pressure vs Personal 7-Day Capacity
            val personalCapacity = calculatePersonalCapacity(context)
            if (context.incompleteTasks.size > personalCapacity * 1.5) {
                riskScore += 0.25f
                factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.CRITICAL, "Incomplete tasks (${context.incompleteTasks.size}) exceed 7-day typical capacity ($personalCapacity tasks/day)."))
            } else if (context.incompleteTasks.size > personalCapacity) {
                riskScore += 0.15f
                factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.POSITIVE, "Workload is above average daily capacity."))
            }

            // 4. Linked Goal Health
            val linkedGoal = context.goals.find { it.title == task.goalTitle }
            if (linkedGoal != null) {
                val health = context.personalContext.goalHealth.find { it.goalId == linkedGoal.id }
                if (health?.state == GoalHealthState.AT_RISK) {
                    riskScore += 0.20f
                    factors.add(ReasoningFactor("Goal Health", ReasoningImpact.CRITICAL, "Linked goal \"${linkedGoal.title}\" is currently at risk."))
                }
            }

            // 5. Carry-forward History for this Specific Task
            val taskMemory = context.memory.items.find { it.category == AiMemoryCategory.TASK_PATTERN && it.relatedTaskId == task.id }
            if (taskMemory != null && taskMemory.content.contains("carried", ignoreCase = true)) {
                riskScore += 0.30f
                factors.add(ReasoningFactor("Carry-forward Pattern", ReasoningImpact.CRITICAL, "Task has been carried forward in previous sessions."))
            }

            // 6. Similar Task Behavior (Personalized Category / Keyword Reasoning)
            val similarTaskEvidence = findSimilarTaskEvidence(task, context)
            if (similarTaskEvidence != null) {
                riskScore += similarTaskEvidence.first
                factors.add(similarTaskEvidence.second)
            }

            // Apply Calibration Factor
            val calibratedProbability = (riskScore * calibrationFactor).coerceIn(0.05f, 0.95f)

            val riskLevel = when {
                calibratedProbability >= 0.7f -> AiPriority.CRITICAL
                calibratedProbability >= 0.45f -> AiPriority.HIGH
                calibratedProbability >= 0.25f -> AiPriority.MEDIUM
                else -> AiPriority.LOW
            }

            val statusText = if (!isDataSufficient) {
                "INSUFFICIENT_DATA (Building initial history)"
            } else if (calibratedProbability >= 0.5f) {
                "High Delay Risk (${(calibratedProbability * 100).toInt()}% probability)"
            } else {
                "Low Delay Risk (${(calibratedProbability * 100).toInt()}% probability)"
            }

            // Add prediction if risk factors exist or if data is insufficient
            if (calibratedProbability >= 0.15f || !isDataSufficient) {
                predictions.add(
                    AiPrediction(
                        type = PredictionType.TASK_DELAY_RISK,
                        targetId = task.id,
                        targetTitle = task.title,
                        prediction = statusText,
                        probability = calibratedProbability,
                        confidence = defaultConfidence,
                        evidenceQuality = evidenceQuality,
                        riskLevel = riskLevel,
                        evidence = "Calculated from duration ($durationMin min), priority (${task.priority}), similar task history, and personal capacity.",
                        contributingFactors = factors
                    )
                )
            }
        }

        return predictions
    }

    /**
     * Calculates risk status and estimated completion timing for active goals without fabricating hidden tasks.
     */
    fun predictGoalRisksAndTimings(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()

        context.activeGoals.filter { it.progress < 1.0f }.forEach { goal ->
            val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
            val incompleteLinked = linkedTasks.filter { !it.completed }
            val completedLinked = linkedTasks.count { it.completed }

            val totalObservations = context.memory.analyzedDays + linkedTasks.size
            val evidenceQuality = calculateEvidenceQuality(totalObservations)

            // If no tasks exist and progress is zero, return INSUFFICIENT_DATA
            if (linkedTasks.isEmpty() && goal.progress == 0.0f) {
                predictions.add(
                    AiPrediction(
                        type = PredictionType.GOAL_RISK,
                        targetId = goal.id,
                        targetTitle = goal.title,
                        prediction = "INSUFFICIENT_DATA (No sub-tasks or activity history available to estimate completion timeline)",
                        probability = 0.0f,
                        confidence = AiConfidence.LOW,
                        evidenceQuality = EvidenceQuality.INSUFFICIENT,
                        riskLevel = AiPriority.LOW,
                        evidence = "Goal active with 0 linked tasks and 0% progress."
                    )
                )
                return@forEach
            }

            val dailyVelocity = max(0.5f, calculatePersonalCapacity(context).toFloat())
            val remainingTasks = if (incompleteLinked.isNotEmpty()) incompleteLinked.size else max(1, ((1.0f - goal.progress) * 3).toInt())
            val estimatedDays = (remainingTasks.toFloat() / dailyVelocity * 1.3f).toInt().coerceAtLeast(1)

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

            val confidence = when (evidenceQuality) {
                EvidenceQuality.STRONG -> AiConfidence.HIGH
                EvidenceQuality.MODERATE -> AiConfidence.MEDIUM
                else -> AiConfidence.LOW
            }

            predictions.add(
                AiPrediction(
                    type = PredictionType.GOAL_RISK,
                    targetId = goal.id,
                    targetTitle = goal.title,
                    prediction = statusText,
                    probability = if (isAtRisk) 0.75f else 0.2f,
                    confidence = confidence,
                    evidenceQuality = evidenceQuality,
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
     * Predicts whether today's workload presents an overload risk using real personal capacity.
     */
    fun predictWorkloadOverload(context: AiContext): AiPrediction? {
        val incomplete = context.incompleteTasks
        val plannedToday = context.tasksPlannedToday
        val baselineCapacity = calculatePersonalCapacity(context)

        if (incomplete.isEmpty() && plannedToday == 0) return null

        val totalMinutes = incomplete.sumOf { extractDurationMinutes(it.duration) }
        val totalObs = context.adaptiveProfile.sampleCount + context.recentEvaluations.size
        val evidenceQuality = calculateEvidenceQuality(totalObs)

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
            ReasoningFactor("Planned Today", ReasoningImpact.NEUTRAL, "$plannedToday tasks planned today vs 7-day capacity of $baselineCapacity tasks/day."),
            ReasoningFactor("Total Duration", ReasoningImpact.NEUTRAL, "${totalMinutes} minutes of total estimated work.")
        )

        return AiPrediction(
            type = PredictionType.WORKLOAD_OVERLOAD_RISK,
            prediction = "Workload state: $state (${(probability * 100).toInt()}% load risk)",
            probability = probability,
            confidence = if (evidenceQuality >= EvidenceQuality.MODERATE) AiConfidence.HIGH else AiConfidence.MEDIUM,
            evidenceQuality = evidenceQuality,
            riskLevel = riskLevel,
            evidence = "Planned $plannedToday tasks vs historical capacity of $baselineCapacity tasks/day.",
            contributingFactors = factors
        )
    }

    /**
     * Calculates time-weighted productivity trend prediction.
     */
    fun predictProductivityTrend(context: AiContext): AiPrediction? {
        val trend = context.personalContext.productivityTrend

        if (trend == ProductivityTrend.INSUFFICIENT_DATA) {
            return AiPrediction(
                type = PredictionType.PRODUCTIVITY_TREND_PREDICTION,
                prediction = "INSUFFICIENT_DATA",
                probability = 0.0f,
                confidence = AiConfidence.LOW,
                evidenceQuality = EvidenceQuality.INSUFFICIENT,
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
            evidenceQuality = EvidenceQuality.STRONG,
            riskLevel = if (trend == ProductivityTrend.DECLINING) AiPriority.HIGH else AiPriority.LOW,
            evidence = "Calculated from time-weighted completion rate history across recent days."
        )
    }

    // --- Helper Personalization Functions ---

    private fun calculatePersonalCapacity(context: AiContext): Int {
        val profileCapacity = context.adaptiveProfile.preferredDailyWorkload
        val memory = context.memory.items.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN }
        
        // Extract capacity from memory if present (e.g., "target of 3-4 key tasks")
        val memoryCapacity = memory?.content?.let { content ->
            Regex("(\\d+)-(\\d+)|(\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull()
        }

        return memoryCapacity ?: if (profileCapacity > 0) profileCapacity else 4
    }

    private fun classifyTaskSize(minutes: Int): String {
        return when {
            minutes <= 30 -> "SMALL"
            minutes <= 60 -> "MEDIUM"
            minutes <= 120 -> "LARGE"
            else -> "VERY_LARGE"
        }
    }

    private fun findSimilarTaskEvidence(task: PremiumTask, context: AiContext): Pair<Float, ReasoningFactor>? {
        val cleanTitle = task.title.lowercase().trim()
        val keywords = cleanTitle.split(" ").filter { it.length >= 3 }
        
        if (keywords.isEmpty()) return null

        val similarCarried = context.memory.items.filter { item ->
            item.category == AiMemoryCategory.TASK_PATTERN &&
            keywords.any { kw -> item.content.lowercase().contains(kw) || item.title.lowercase().contains(kw) }
        }

        if (similarCarried.isNotEmpty()) {
            val kw = keywords.firstOrNull { kw -> similarCarried.any { it.content.contains(kw, ignoreCase = true) } } ?: "similar"
            return Pair(
                0.20f,
                ReasoningFactor(
                    "Similar Task Behavior",
                    ReasoningImpact.CRITICAL,
                    "Historical tasks matching '$kw' were carried forward in previous sessions."
                )
            )
        }

        return null
    }

    private fun getCalibrationFactor(context: AiContext): Float {
        val evaluations = context.recentEvaluations
        val planTooLargeCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_TOO_LARGE }
        val planRealisticCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_REALISTIC }

        return when {
            planTooLargeCount >= 3 -> 1.15f // Underestimating overload, boost delay risk
            planRealisticCount >= 5 -> 0.85f // Very consistent, decrease delay risk slightly
            else -> 1.0f
        }
    }

    private fun calculateEvidenceQuality(sampleCount: Int): EvidenceQuality {
        return when {
            sampleCount >= 10 -> EvidenceQuality.STRONG
            sampleCount >= 5 -> EvidenceQuality.MODERATE
            sampleCount >= 1 -> EvidenceQuality.WEAK
            else -> EvidenceQuality.INSUFFICIENT
        }
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: 30
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
    }

    private fun parseTargetDateDaysRemaining(targetDate: String): Long? {
        if (targetDate.isBlank()) return null
        return try {
            val date = try {
                LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("MMM d, yyyy"))
            }
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, date)
        } catch (e: Exception) {
            null
        }
    }
}
