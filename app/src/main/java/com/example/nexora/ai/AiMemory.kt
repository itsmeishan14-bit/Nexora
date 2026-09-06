package com.example.nexora.ai

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

data class AiMemory(
    val patterns: List<AiProductivityPattern> = emptyList(),
    val analyzedDays: Int = 0
)
