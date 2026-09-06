package com.example.nexora.ai

enum class AdaptiveConfidence {
    UNKNOWN,
    LOW,
    MODERATE,
    HIGH
}

data class AdaptiveProfile(
    val averageTasksCompleted: Float = 0f,
    val averageFocusMinutes: Float = 0f,
    val averageCarriedTasks: Float = 0f,
    val averageTaskDurationMinutes: Float = 0f,
    val completionRate: Float = 0f,
    val consistencyScore: Float = 0f, // 0 to 1
    val preferredTaskSize: String = "Medium", // "Small", "Medium", "Large"
    val preferredDailyWorkload: Int = 5,
    val sampleCount: Int = 0,
    val confidence: AdaptiveConfidence = AdaptiveConfidence.UNKNOWN
)
