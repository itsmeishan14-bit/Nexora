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
            AiRequestType.NEXT_TASK -> listOf(AiMemoryCategory.TASK_PATTERN, AiMemoryCategory.PRODUCTIVITY_PATTERN, AiMemoryCategory.TASK_SIZE_PATTERN)
            AiRequestType.DAILY_PLAN -> listOf(AiMemoryCategory.PLANNING_PATTERN, AiMemoryCategory.WORKLOAD_PATTERN, AiMemoryCategory.TASK_SIZE_PATTERN)
            AiRequestType.GOAL_ANALYSIS -> listOf(AiMemoryCategory.GOAL_PATTERN, AiMemoryCategory.PRODUCTIVITY_PATTERN)
            AiRequestType.PRODUCTIVITY_ANALYSIS -> listOf(AiMemoryCategory.PRODUCTIVITY_PATTERN, AiMemoryCategory.WORKLOAD_PATTERN, AiMemoryCategory.TASK_SIZE_PATTERN)
            AiRequestType.PROACTIVE_ANALYSIS -> listOf(AiMemoryCategory.PRODUCTIVITY_PATTERN, AiMemoryCategory.WORKLOAD_PATTERN, AiMemoryCategory.GOAL_PATTERN)
            AiRequestType.CHAT -> listOf(AiMemoryCategory.PRODUCTIVITY_PATTERN, AiMemoryCategory.WORKLOAD_PATTERN, AiMemoryCategory.TASK_SIZE_PATTERN, AiMemoryCategory.GOAL_PATTERN)
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
        var baseRelevance = 0

        // 1. Direct relationship (Task/Goal ID)
        if (request.taskId != null && memory.relatedTaskId == request.taskId) baseRelevance += 60
        if (request.goalId != null && memory.relatedGoalId == request.goalId) baseRelevance += 60

        // 2. Category matching based on request intent
        val categoryMatch = when (request.type) {
            AiRequestType.NEXT_TASK -> memory.category == AiMemoryCategory.TASK_PATTERN || memory.category == AiMemoryCategory.TASK_SIZE_PATTERN
            AiRequestType.DAILY_PLAN -> memory.category == AiMemoryCategory.PLANNING_PATTERN || memory.category == AiMemoryCategory.WORKLOAD_PATTERN
            AiRequestType.GOAL_ANALYSIS -> memory.category == AiMemoryCategory.GOAL_PATTERN
            AiRequestType.PRODUCTIVITY_ANALYSIS -> memory.category == AiMemoryCategory.PRODUCTIVITY_PATTERN || memory.category == AiMemoryCategory.WORKLOAD_PATTERN
            AiRequestType.PROACTIVE_ANALYSIS -> memory.category == AiMemoryCategory.WORKLOAD_PATTERN || memory.category == AiMemoryCategory.GOAL_PATTERN
            else -> false
        }
        if (categoryMatch) baseRelevance += 30

        // 3. Semantic / keyword matching in user message if available
        val msg = request.userMessage?.lowercase()?.trim()
        if (msg != null) {
            val stopWords = setOf(
                "what", "this", "that", "with", "have", "from", "your", "about", 
                "could", "would", "should", "doing", "please", "tell", "which", "there",
                "goal", "task", "show", "help", "need"
            )
            val queryWords = msg.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 && it !in stopWords }
            val memContent = "${memory.title} ${memory.content}".lowercase()
            
            val matches = queryWords.count { memContent.contains(it) }
            if (matches > 0) {
                baseRelevance += matches * 25
            } else if (request.type == AiRequestType.CHAT && request.taskId == null && request.goalId == null) {
                val isExplicitMemoryQuery = msg.contains(Regex("\\b(remember|history|pattern|behavior|memory|observations|learned)\\b"))
                if (!isExplicitMemoryQuery) {
                    // Chat query unrelated to this memory item; do not inject unrelated memory
                    return 0
                }
            }
        }

        // If there is zero entity relationship and zero semantic/category match, return 0 (never fabricate/leak unrelated memory)
        if (baseRelevance == 0) {
            return 0
        }

        var score = baseRelevance

        // 4. Recency (Decay)
        val ageDays = (System.currentTimeMillis() - memory.lastUsedAt) / (1000 * 60 * 60 * 24)
        if (ageDays < 1) score += 10
        else if (ageDays < 7) score += 5
        else if (ageDays > 30) score -= 10

        // 5. Importance
        score += when (memory.importance) {
            AiMemoryImportance.HIGH -> 15
            AiMemoryImportance.MEDIUM -> 5
            AiMemoryImportance.LOW -> 0
        }

        // 6. Memory Trust & Confidence (Low confidence memory must not be trusted like high confidence)
        score += when (memory.confidence) {
            AiMemoryConfidence.VERY_HIGH -> 20
            AiMemoryConfidence.HIGH -> 10
            AiMemoryConfidence.MEDIUM -> 0
            AiMemoryConfidence.LOW -> -15
            AiMemoryConfidence.VERY_LOW -> -30
        }

        return if (score > 15) score else 0
    }
}
