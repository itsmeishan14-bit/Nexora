package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask

/**
 * Represents a continuously updated understanding of the user's current productivity situation.
 * Synthesizes raw data into meaningful interpretations.
 */
data class AiPersonalContext(
    val workload: WorkloadAssessment = WorkloadAssessment(),
    val goalHealth: List<GoalHealthAssessment> = emptyList(),
    val dayState: CurrentDayState = CurrentDayState.INSUFFICIENT_DATA,
    val productivityTrend: ProductivityTrend = ProductivityTrend.STABLE,
    val risks: List<AiRisk> = emptyList(),
    val opportunities: List<AiOpportunity> = emptyList(),
    val confidence: AiConfidence = AiConfidence.LOW,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkloadAssessment(
    val state: WorkloadState = WorkloadState.BALANCED,
    val deviationFromBaseline: WorkloadDeviation = WorkloadDeviation.NEGLIGIBLE,
    val totalEstimatedMinutes: Int = 0,
    val taskCount: Int = 0,
    val baselineCapacity: Int = 0,
    val evidence: String = ""
)

enum class WorkloadState {
    VERY_LOW, LOW, BALANCED, HIGH, VERY_HIGH
}

enum class WorkloadDeviation {
    NEGLIGIBLE, SIGNIFICANT, EXTREME
}

data class GoalHealthAssessment(
    val goalId: Long,
    val goalTitle: String,
    val state: GoalHealthState,
    val progress: Float,
    val recentActivityLevel: ActivityLevel,
    val neglectedDurationDays: Int,
    val evidence: String
)

enum class GoalHealthState {
    HEALTHY, NEEDS_ATTENTION, AT_RISK, COMPLETED, INSUFFICIENT_DATA
}

enum class ActivityLevel {
    HIGH, MODERATE, LOW, NONE
}

enum class CurrentDayState {
    NOT_STARTED, ON_TRACK, AHEAD, BEHIND, OVERLOADED, INSUFFICIENT_DATA
}

enum class ProductivityTrend {
    IMPROVING, STABLE, DECLINING, VOLATILE, INSUFFICIENT_DATA
}

data class AiRisk(
    val type: RiskType,
    val severity: AiPriority,
    val message: String,
    val evidence: String,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null
)

enum class RiskType {
    OVERLOAD, NEGLECTED_GOAL, CARRY_OVER_PATTERN, DEADLINE_APPROACHING, UNREALISTIC_PLANNING, PERFORMANCE_DROP
}

data class AiOpportunity(
    val type: OpportunityType,
    val title: String,
    val message: String,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null
)

enum class OpportunityType {
    QUICK_WIN, GOAL_MOMENTUM, CAPACITY_AVAILABLE, HIGH_VALUE_FOCUS
}

/**
 * Task-level signals derived from current context.
 */
enum class TaskSignal {
    HIGH_PRIORITY, URGENT, QUICK_WIN, LONG_TASK, REPEATED_CARRYOVER, GOAL_CRITICAL, COMPLETED, OVERDUE
}
