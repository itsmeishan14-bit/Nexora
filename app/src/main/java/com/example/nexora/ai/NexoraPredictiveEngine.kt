package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Predictive Personal Intelligence Engine 2.1.
 * Grounded in real data, honest uncertainty modeling, token-based similarity,
 * and conservative outcome calibration.
 */
class NexoraPredictiveEngine {

    private val stopWords = setOf(
        "my", "the", "a", "an", "task", "for", "in", "to", "and", "of", 
        "with", "complete", "finish", "study", "build", "create", "update"
    )

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
     * Calculates delay risks for incomplete tasks based on task-specific historical evidence,
     * exact token similarity matching, and personal capacity.
     */
    fun predictTaskDelayRisks(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()
        val incompleteTasks = context.incompleteTasks

        if (incompleteTasks.isEmpty()) return emptyList()

        val profile = context.adaptiveProfile
        val taskHistoryCount = profile.sampleCount + context.recentEvaluations.size + context.memory.items.count { it.category == AiMemoryCategory.TASK_PATTERN }
        val evidenceQuality = calculateTaskEvidenceQuality(taskHistoryCount)
        val isDataSufficient = evidenceQuality != EvidenceQuality.INSUFFICIENT

        val defaultConfidence = when (evidenceQuality) {
            EvidenceQuality.STRONG -> AiConfidence.HIGH
            EvidenceQuality.MODERATE -> AiConfidence.MEDIUM
            EvidenceQuality.WEAK, EvidenceQuality.INSUFFICIENT -> AiConfidence.LOW
        }

        val calibration = getCalibration(context)

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

            // 3. Workload Pressure vs Personal Daily Capacity
            val capacityEstimate = calculatePersonalCapacity(context)
            val personalCapacity = capacityEstimate.value
            if (personalCapacity != null) {
                if (context.incompleteTasks.size > personalCapacity * 1.5) {
                    riskScore += 0.25f
                    factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.CRITICAL, "Incomplete tasks (${context.incompleteTasks.size}) exceed ${capacityEstimate.source} ($personalCapacity tasks/day)."))
                } else if (context.incompleteTasks.size > personalCapacity) {
                    riskScore += 0.15f
                    factors.add(ReasoningFactor("Workload Pressure", ReasoningImpact.POSITIVE, "Workload is above average daily capacity."))
                }
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

            // 6. Token-based Similar Task Behavior
            val similarTaskEvidence = findSimilarTaskEvidence(task, context)
            if (similarTaskEvidence != null) {
                riskScore += similarTaskEvidence.first
                factors.add(similarTaskEvidence.second)
            }

            // Apply Calibration Factor
            val calibratedProbability = (riskScore * calibration.factor).coerceIn(0.05f, 0.95f)

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
                        evidence = "Calculated from duration ($durationMin min), priority (${task.priority}), and task history.",
                        contributingFactors = factors
                    )
                )
            }
        }

        return predictions
    }

    /**
     * Calculates risk status and estimated completion timing for active goals.
     * Uses strict decision tree without hidden task fallbacks.
     */
    fun predictGoalRisksAndTimings(context: AiContext): List<AiPrediction> {
        val predictions = mutableListOf<AiPrediction>()

        context.activeGoals.filter { it.progress < 1.0f }.forEach { goal ->
            val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
            val incompleteLinked = linkedTasks.filter { !it.completed }
            val completedLinked = linkedTasks.count { it.completed }

            val goalEvidenceCount = linkedTasks.size + (if (goal.progress > 0f) 1 else 0)
            val evidenceQuality = calculateGoalEvidenceQuality(goalEvidenceCount)

            // Decision Tree:
            // IF incomplete linked tasks exist: use real count
            // ELSE IF progress > 0 and activity history exists: estimate from progress
            // ELSE: INSUFFICIENT_DATA
            val (remainingTaskCount, isDataSufficient) = when {
                incompleteLinked.isNotEmpty() -> Pair(incompleteLinked.size, true)
                goal.progress > 0.0f && completedLinked > 0 -> Pair(max(1, ((1.0f - goal.progress) * 5).toInt()), true)
                else -> Pair(null, false)
            }

            if (!isDataSufficient || remainingTaskCount == null) {
                predictions.add(
                    AiPrediction(
                        type = PredictionType.GOAL_RISK,
                        targetId = goal.id,
                        targetTitle = goal.title,
                        prediction = "INSUFFICIENT_DATA",
                        probability = 0.0f,
                        confidence = AiConfidence.LOW,
                        evidenceQuality = EvidenceQuality.INSUFFICIENT,
                        riskLevel = AiPriority.LOW,
                        evidence = "Not enough sub-tasks or progress history to estimate completion timeline."
                    )
                )
                return@forEach
            }

            val capacityEstimate = calculatePersonalCapacity(context)
            val dailyVelocity = max(0.5f, (capacityEstimate.value ?: 2).toFloat())
            val estimatedDays = (remainingTaskCount.toFloat() / dailyVelocity * 1.3f).toInt().coerceAtLeast(1)

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
            factors.add(ReasoningFactor("Tasks Remaining", ReasoningImpact.POSITIVE, "$remainingTaskCount sub-tasks remaining."))
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
                    evidence = "Estimated $estimatedDays days based on $remainingTaskCount remaining tasks and daily velocity of $dailyVelocity tasks/day.",
                    contributingFactors = factors,
                    estimatedDaysToCompletion = estimatedDays
                )
            )
        }

        return predictions
    }

    /**
     * Predicts whether today's workload presents an overload risk.
     * Uses real personal capacity estimate without silent defaults.
     */
    fun predictWorkloadOverload(context: AiContext): AiPrediction? {
        val incomplete = context.incompleteTasks
        val plannedToday = context.tasksPlannedToday
        val capacityEstimate = calculatePersonalCapacity(context)

        if (capacityEstimate.value == null) {
            return AiPrediction(
                type = PredictionType.WORKLOAD_OVERLOAD_RISK,
                prediction = "INSUFFICIENT_DATA",
                probability = 0.0f,
                confidence = AiConfidence.LOW,
                evidenceQuality = EvidenceQuality.INSUFFICIENT,
                riskLevel = AiPriority.LOW,
                evidence = "Not enough task history to estimate daily workload capacity."
            )
        }

        val baselineCapacity = capacityEstimate.value
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
            ReasoningFactor("Planned Today", ReasoningImpact.NEUTRAL, "$plannedToday tasks planned today vs ${capacityEstimate.source} ($baselineCapacity tasks/day)."),
            ReasoningFactor("Total Duration", ReasoningImpact.NEUTRAL, "${totalMinutes} minutes of total estimated work.")
        )

        return AiPrediction(
            type = PredictionType.WORKLOAD_OVERLOAD_RISK,
            prediction = "Workload state: $state (${(probability * 100).toInt()}% load risk)",
            probability = probability,
            confidence = capacityEstimate.confidence,
            evidenceQuality = capacityEstimate.evidenceQuality,
            riskLevel = riskLevel,
            evidence = "Planned $plannedToday tasks vs ${capacityEstimate.source} ($baselineCapacity tasks/day).",
            contributingFactors = factors
        )
    }

    /**
     * Calculates productivity trend with strict historical day thresholds.
     */
    fun predictProductivityTrend(context: AiContext): AiPrediction? {
        val daysCount = context.memory.analyzedDays + context.recentEvaluations.size + context.adaptiveProfile.sampleCount
        val trend = context.personalContext.productivityTrend

        if (daysCount < 3 || trend == ProductivityTrend.INSUFFICIENT_DATA) {
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

        val evidenceQuality = when {
            daysCount >= 10 -> EvidenceQuality.STRONG
            daysCount >= 5 -> EvidenceQuality.MODERATE
            daysCount >= 3 -> EvidenceQuality.WEAK
            else -> EvidenceQuality.INSUFFICIENT
        }

        val confidence = when (evidenceQuality) {
            EvidenceQuality.STRONG -> AiConfidence.HIGH
            EvidenceQuality.MODERATE -> AiConfidence.MEDIUM
            else -> AiConfidence.LOW
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
            confidence = confidence,
            evidenceQuality = evidenceQuality,
            riskLevel = if (trend == ProductivityTrend.DECLINING) AiPriority.HIGH else AiPriority.LOW,
            evidence = "Calculated from time-weighted completion rate history across $daysCount active days."
        )
    }

    // --- Personal Baseline & Capacity ---

    fun calculatePersonalCapacity(context: AiContext): PersonalCapacityEstimate {
        val profile = context.adaptiveProfile
        val memory = context.memory.items.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN }

        if (profile.averageTasksCompleted > 0f && profile.sampleCount >= 2) {
            return PersonalCapacityEstimate(
                value = profile.averageTasksCompleted.toInt().coerceAtLeast(1),
                confidence = if (profile.sampleCount >= 5) AiConfidence.HIGH else AiConfidence.MEDIUM,
                evidenceQuality = if (profile.sampleCount >= 5) EvidenceQuality.STRONG else EvidenceQuality.MODERATE,
                source = "Observed from ${profile.sampleCount} days of completion history"
            )
        }

        val memoryCapacity = memory?.content?.let { content ->
            Regex("(\\d+)-(\\d+)|(\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull()
        }
        if (memoryCapacity != null) {
            return PersonalCapacityEstimate(
                value = memoryCapacity,
                confidence = AiConfidence.MEDIUM,
                evidenceQuality = EvidenceQuality.MODERATE,
                source = "Observed from workload pattern memory"
            )
        }

        if (profile.preferredDailyWorkload > 0) {
            return PersonalCapacityEstimate(
                value = profile.preferredDailyWorkload,
                confidence = AiConfidence.LOW,
                evidenceQuality = EvidenceQuality.WEAK,
                source = "Preferred workload setting"
            )
        }

        return PersonalCapacityEstimate(
            value = null,
            confidence = AiConfidence.LOW,
            evidenceQuality = EvidenceQuality.INSUFFICIENT,
            source = "Insufficient Data"
        )
    }

    private fun classifyTaskSize(minutes: Int): String {
        return when {
            minutes <= 30 -> "SMALL"
            minutes <= 60 -> "MEDIUM"
            minutes <= 120 -> "LARGE"
            else -> "VERY_LARGE"
        }
    }

    // Token-based similar task behavior
    private fun findSimilarTaskEvidence(task: PremiumTask, context: AiContext): Pair<Float, ReasoningFactor>? {
        val taskTokens = tokenize(task.title)
        if (taskTokens.isEmpty()) return null

        val similarCarried = context.memory.items.filter { item ->
            if (item.category != AiMemoryCategory.TASK_PATTERN) return@filter false
            val memoryTokens = tokenize("${item.title} ${item.content}")
            // Check exact token overlap
            taskTokens.any { t -> memoryTokens.contains(t) }
        }

        if (similarCarried.isNotEmpty()) {
            val matchingToken = taskTokens.firstOrNull { t ->
                similarCarried.any { item -> tokenize("${item.title} ${item.content}").contains(t) }
            } ?: "similar"

            return Pair(
                0.20f,
                ReasoningFactor(
                    "Similar Task Behavior",
                    ReasoningImpact.CRITICAL,
                    "Historical tasks matching token '$matchingToken' were carried forward in previous sessions."
                )
            )
        }

        return null
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 3 && !stopWords.contains(it) }
            .toSet()
    }

    private fun getCalibration(context: AiContext): PersonalPredictionCalibration {
        val evaluations = context.recentEvaluations
        val evalCount = evaluations.size

        if (evalCount < 3) {
            return PersonalPredictionCalibration(
                status = CalibrationStatus.INSUFFICIENT_DATA,
                factor = 1.0f,
                evaluatedCount = evalCount
            )
        }

        val planTooLargeCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_TOO_LARGE }
        val planRealisticCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_REALISTIC }

        val status = if (evalCount >= 5) CalibrationStatus.CALIBRATED else CalibrationStatus.LEARNING
        val factor = (1.0f + (planTooLargeCount * 0.05f) - (planRealisticCount * 0.05f)).coerceIn(0.85f, 1.15f)

        return PersonalPredictionCalibration(
            status = status,
            factor = factor,
            evaluatedCount = evalCount,
            accuracyRate = if (evalCount > 0) planRealisticCount.toFloat() / evalCount else 0f
        )
    }

    private fun calculateTaskEvidenceQuality(taskObservationCount: Int): EvidenceQuality {
        return when {
            taskObservationCount >= 10 -> EvidenceQuality.STRONG
            taskObservationCount >= 5 -> EvidenceQuality.MODERATE
            taskObservationCount >= 1 -> EvidenceQuality.WEAK
            else -> EvidenceQuality.INSUFFICIENT
        }
    }

    private fun calculateGoalEvidenceQuality(goalObservationCount: Int): EvidenceQuality {
        return when {
            goalObservationCount >= 5 -> EvidenceQuality.STRONG
            goalObservationCount >= 3 -> EvidenceQuality.MODERATE
            goalObservationCount >= 1 -> EvidenceQuality.WEAK
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

data class PersonalPredictionCalibration(
    val status: CalibrationStatus = CalibrationStatus.INSUFFICIENT_DATA,
    val factor: Float = 1.0f,
    val evaluatedCount: Int = 0,
    val accuracyRate: Float = 0.0f
)
