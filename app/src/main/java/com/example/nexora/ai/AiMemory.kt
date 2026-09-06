package com.example.nexora.ai

enum class AiMemoryCategory {
    TASK_PATTERN,
    GOAL_PATTERN,
    PRODUCTIVITY_PATTERN,
    PLANNING_PATTERN,
    WORKLOAD_PATTERN,
    PREFERENCE_PATTERN,
    OUTCOME_PATTERN,
    SYSTEM_PATTERN
}

enum class AiMemoryConfidence {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class AiMemoryImportance {
    LOW,
    MEDIUM,
    HIGH
}

data class AiMemoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: AiMemoryCategory,
    val title: String,
    val content: String,
    val confidence: AiMemoryConfidence = AiMemoryConfidence.LOW,
    val importance: AiMemoryImportance = AiMemoryImportance.LOW,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val expiration: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class AiMemory(
    val items: List<AiMemoryItem> = emptyList(),
    val analyzedDays: Int = 0,
    val legacyPatterns: List<AiProductivityPattern> = emptyList() // For backward compatibility
)

// Legacy model for backward compatibility
enum class AiPatternType {
    WORKLOAD_CONSISTENCY,
    GOAL_ATTENTION,
    FOCUS_TREND,
    COMPLETION_ACCURACY,
    INSUFFICIENT_DATA
}

data class AiProductivityPattern(
    val type: AiPatternType,
    val title: String,
    val description: String,
    val confidence: Float, // 0.0 to 1.0
    val severity: AiPriority = AiPriority.LOW,
    val recommendation: String? = null
)
