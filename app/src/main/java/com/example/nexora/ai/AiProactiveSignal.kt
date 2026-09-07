package com.example.nexora.ai

import java.util.UUID

/**
 * Represents a signal detected by Nexora's proactive intelligence system.
 */
data class AiProactiveSignal(
    val id: String = UUID.randomUUID().toString(),
    val type: ProactiveSignalType,
    val title: String,
    val message: String,
    val severity: AiPriority,
    val confidence: AiConfidence,
    val evidence: String,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val suggestedAction: AiAction? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val fingerprint: String
)

enum class ProactiveSignalType {
    OVERLOAD,
    NEGLECTED_GOAL,
    REPEATED_CARRY_FORWARD,
    DEADLINE_RISK,
    HIGH_PRIORITY_CONFLICT,
    LOW_COMPLETION_RATE,
    UNFINISHED_WORK,
    TASK_TOO_LARGE,
    TASK_STAGNATION,
    GOAL_STAGNATION,
    PRODUCTIVITY_DROP,
    PRODUCTIVITY_IMPROVEMENT,
    PLAN_OVERLOAD,
    MISSING_NEXT_ACTION,
    DUPLICATE_TASK_RISK,
    WORKLOAD_BALANCED,
    NO_ACTION_NEEDED
}

/**
 * Lightweight local automation rule architecture.
 */
data class AiAutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val triggerType: AutomationTriggerType,
    val enabled: Boolean = true,
    val cooldownMillis: Long = 3600000, // Default 1 hour
    val lastTriggeredAt: Long = 0,
    val lastTriggeredFingerprint: String? = null
)

enum class AutomationTriggerType {
    TASK_CREATED,
    TASK_COMPLETED,
    TASK_UPDATED,
    TASK_CARRIED_FORWARD,
    GOAL_CREATED,
    GOAL_UPDATED,
    GOAL_PROGRESS_CHANGED,
    DAILY_PROGRESS_UPDATED,
    WORKLOAD_CHANGED,
    APP_OPENED,
    DAY_STARTED,
    DAY_ENDED,
    PRODUCTIVITY_PATTERN_DETECTED
}
