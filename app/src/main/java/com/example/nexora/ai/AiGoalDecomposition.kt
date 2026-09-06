package com.example.nexora.ai

data class AiGoalStep(
    val title: String,
    val description: String = "",
    val priority: AiPriority = AiPriority.MEDIUM,
    val estimatedDuration: String = "",
    val order: Int = 0
)

data class AiGoalDecomposition(
    val goalTitle: String,
    val summary: String,
    val steps: List<AiGoalStep>,
    val confidence: AiConfidence = AiConfidence.MEDIUM
)