package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository

class AiMemoryRetriever(
    private val repository: NexoraRepository
) {
    /**
     * Retrieve memories relevant to the current AI request context.
     */
    suspend fun retrieveRelevantMemory(request: AiRequest): List<AiMemoryItem> {
        // Targeted retrieval
        val candidates = mutableSetOf<AiMemoryItem>()
        
        // 1. By relationship
        if (request.taskId != null) {
            candidates.addAll(repository.getMemoryByTask(request.taskId))
        }
        if (request.goalId != null) {
            candidates.addAll(repository.getMemoryByGoal(request.goalId))
        }
        
        // 2. By Category (broadened based on request type)
        val categories = when (request.type) {
            AiRequestType.NEXT_TASK -> listOf(AiMemoryCategory.TASK_PATTERN, AiMemoryCategory.PRODUCTIVITY_PATTERN)
            AiRequestType.DAILY_PLAN -> listOf(AiMemoryCategory.PLANNING_PATTERN, AiMemoryCategory.WORKLOAD_PATTERN)
            AiRequestType.GOAL_ANALYSIS -> listOf(AiMemoryCategory.GOAL_PATTERN)
            AiRequestType.PRODUCTIVITY_ANALYSIS -> listOf(AiMemoryCategory.PRODUCTIVITY_PATTERN)
            else -> emptyList()
        }
        
        categories.forEach {
            candidates.addAll(repository.getMemoryByCategory(it))
        }

        // If we still have few candidates, fall back to all memory or a sample
        if (candidates.size < 5) {
            candidates.addAll(repository.getAllMemory().take(20))
        }

        return candidates.map { memory ->
            val relevance = calculateRelevance(memory, request)
            memory to relevance
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(5) // Limit to top 5 relevant memories
    }

    private fun calculateRelevance(memory: AiMemoryItem, request: AiRequest): Int {
        var score = 0

        // 1. Direct relationship (Task/Goal ID)
        if (request.taskId != null && memory.relatedTaskId == request.taskId) score += 50
        if (request.goalId != null && memory.relatedGoalId == request.goalId) score += 50

        // 2. Category matching
        score += when (request.type) {
            AiRequestType.NEXT_TASK -> if (memory.category == AiMemoryCategory.TASK_PATTERN || memory.category == AiMemoryCategory.PRODUCTIVITY_PATTERN) 20 else 0
            AiRequestType.DAILY_PLAN -> if (memory.category == AiMemoryCategory.PLANNING_PATTERN || memory.category == AiMemoryCategory.WORKLOAD_PATTERN) 30 else 0
            AiRequestType.GOAL_ANALYSIS -> if (memory.category == AiMemoryCategory.GOAL_PATTERN) 30 else 0
            AiRequestType.PRODUCTIVITY_ANALYSIS -> if (memory.category == AiMemoryCategory.PRODUCTIVITY_PATTERN) 40 else 0
            else -> 0
        }

        // 3. Keyword matching in user message if available
        request.userMessage?.lowercase()?.let { msg ->
            if (memory.title.lowercase().contains(msg) || memory.content.lowercase().contains(msg)) score += 15
        }

        // 4. Recency (Decay)
        val ageDays = (System.currentTimeMillis() - memory.lastUsedAt) / (1000 * 60 * 60 * 24)
        if (ageDays < 1) score += 10
        else if (ageDays < 7) score += 5

        // 5. Importance & Confidence
        score += when (memory.importance) {
            AiMemoryImportance.HIGH -> 15
            AiMemoryImportance.MEDIUM -> 10
            AiMemoryImportance.LOW -> 5
        }

        score += when (memory.confidence) {
            AiMemoryConfidence.VERY_HIGH -> 15
            AiMemoryConfidence.HIGH -> 10
            AiMemoryConfidence.MEDIUM -> 5
            else -> 0
        }

        return score
    }
}
