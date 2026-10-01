package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

/**
 * Robust local entity resolution for Nexora.
 * Matches user natural language to actual tasks and goals using deterministic scoring.
 */
object AiEntityResolver {

    /**
     * Resolves a user query to a specific task.
     * Prefers exact matches, contextual references, then prefix matches, then containing matches.
     */
    fun resolveTask(
        query: String, 
        tasks: List<PremiumTask>,
        convContext: AiConversationContext? = null
    ): ResolutionResult<PremiumTask> {
        val cleanQuery = normalize(query)
        val rawCleanQuery = normalizeRaw(query)

        // 1. Contextual Pronouns & References ("it", "that", "that task", "the previous task", "previous task", "that one", "the task we discussed")
        val isContextualRef = query.matches(Regex("(?i)^\\s*(it|that|that task|the task|this task|the previous task|previous task|that one|this one|the task we discussed|last task)\\s*$"))
        if (isContextualRef) {
            val lastId = convContext?.lastTaskId
            if (lastId != null) {
                val matched = tasks.find { it.id == lastId }
                if (matched != null) return ResolutionResult.Success(matched, EntityResolutionStatus.EXACT_MATCH)
            }
            val lastTitle = convContext?.lastEntityTitle
            if (!lastTitle.isNullOrBlank()) {
                val matched = tasks.find { it.title.equals(lastTitle, ignoreCase = true) }
                if (matched != null) return ResolutionResult.Success(matched, EntityResolutionStatus.EXACT_MATCH)
            }
            return ResolutionResult.NotFound()
        }

        // 2. Goal-linked task references ("the Java task", "the task linked to my Java goal", "the task linked to Java")
        val goalLinkMatch = Regex("(?i)(?:the\\s+)?([a-zA-Z0-9_-]+)\\s+task|(?:(?:task\\s+)?(?:linked\\s+to|for)\\s+(?:my\\s+)?([a-zA-Z0-9_-]+)(?:\\s+goal)?)").find(query)
        val goalKeyword = goalLinkMatch?.let { 
            it.groupValues[1].takeIf { g -> g.isNotBlank() && g.lowercase() !in listOf("first", "urgent", "previous", "next", "current") }
                ?: it.groupValues[2].takeIf { g -> g.isNotBlank() }
        }
        if (goalKeyword != null) {
            val linkedTasks = tasks.filter { 
                it.goalTitle?.contains(goalKeyword, ignoreCase = true) == true ||
                it.title.contains(goalKeyword, ignoreCase = true)
            }
            if (linkedTasks.size == 1) {
                return ResolutionResult.Success(linkedTasks.first(), EntityResolutionStatus.EXACT_MATCH)
            } else if (linkedTasks.size > 1) {
                return ResolutionResult.Ambiguous(linkedTasks)
            }
        }

        // 3. Natural / Relational References
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.contains("urgent") || lowerQuery.contains("highest priority")) {
            val urgentIncomplete = tasks.filter { it.priority == TaskPriority.URGENT && !it.completed }
            if (urgentIncomplete.size == 1) return ResolutionResult.Success(urgentIncomplete.first(), EntityResolutionStatus.EXACT_MATCH)
            if (urgentIncomplete.size > 1) return ResolutionResult.Ambiguous(urgentIncomplete)
            
            val highIncomplete = tasks.filter { it.priority == TaskPriority.HIGH && !it.completed }
            if (highIncomplete.size == 1) return ResolutionResult.Success(highIncomplete.first(), EntityResolutionStatus.EXACT_MATCH)
            if (highIncomplete.size > 1) return ResolutionResult.Ambiguous(highIncomplete)
        }

        if (lowerQuery.contains("first task") || lowerQuery == "the first") {
            val incomplete = tasks.filter { !it.completed }
            if (incomplete.size == 1) return ResolutionResult.Success(incomplete.first(), EntityResolutionStatus.EXACT_MATCH)
            if (incomplete.size > 1) return ResolutionResult.Success(incomplete.first(), EntityResolutionStatus.EXACT_MATCH)
        }

        if (lowerQuery.contains("carried forward") || lowerQuery.contains("carrying from yesterday")) {
            val incomplete = tasks.filter { !it.completed }
            if (incomplete.size == 1) return ResolutionResult.Success(incomplete.first(), EntityResolutionStatus.EXACT_MATCH)
            if (incomplete.size > 1) return ResolutionResult.Ambiguous(incomplete)
        }

        if (lowerQuery.contains("completed yesterday") || lowerQuery.contains("completed task")) {
            val completed = tasks.filter { it.completed }
            if (completed.size == 1) return ResolutionResult.Success(completed.first(), EntityResolutionStatus.EXACT_MATCH)
            if (completed.size > 1) return ResolutionResult.Ambiguous(completed)
        }

        if (cleanQuery.isBlank() && rawCleanQuery.isBlank()) return ResolutionResult.NotFound()

        val candidates = tasks.map { task ->
            val normalizedTitle = normalize(task.title)
            val rawNormalizedTitle = normalizeRaw(task.title)
            val score = maxOf(
                scoreMatch(cleanQuery, normalizedTitle),
                scoreMatch(rawCleanQuery, rawNormalizedTitle)
            )
            task to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) return ResolutionResult.NotFound()

        val maxScore = candidates.first().second
        val bestMatches = candidates.filter { it.second == maxScore }

        return when {
            bestMatches.size == 1 -> {
                val status = if (maxScore >= 90) EntityResolutionStatus.EXACT_MATCH else EntityResolutionStatus.PARTIAL_MATCH
                ResolutionResult.Success(bestMatches.first().first, status)
            }
            bestMatches.size > 1 -> ResolutionResult.Ambiguous(bestMatches.map { it.first })
            else -> ResolutionResult.NotFound()
        }
    }

    /**
     * Resolves a user query to a specific goal.
     */
    fun resolveGoal(
        query: String, 
        goals: List<NexoraGoal>,
        convContext: AiConversationContext? = null
    ): ResolutionResult<NexoraGoal> {
        val cleanQuery = normalize(query)
        val rawCleanQuery = normalizeRaw(query)

        // Contextual Pronouns ("that goal", "it", "that one", "the goal we discussed")
        val isContextualRef = query.matches(Regex("(?i)^\\s*(it|that|that goal|that one|the goal|the goal we discussed|this goal|this one)\\s*$"))
        if (isContextualRef) {
            val lastId = convContext?.lastGoalId
            if (lastId != null) {
                val matched = goals.find { it.id == lastId }
                if (matched != null) return ResolutionResult.Success(matched, EntityResolutionStatus.EXACT_MATCH)
            }
            val lastTitle = convContext?.lastEntityTitle
            if (!lastTitle.isNullOrBlank()) {
                val matched = goals.find { it.title.equals(lastTitle, ignoreCase = true) }
                if (matched != null) return ResolutionResult.Success(matched, EntityResolutionStatus.EXACT_MATCH)
            }
            return ResolutionResult.NotFound()
        }

        if (cleanQuery.isBlank() && rawCleanQuery.isBlank()) return ResolutionResult.NotFound()

        val candidates = goals.map { goal ->
            val normalizedTitle = normalize(goal.title)
            val rawNormalizedTitle = normalizeRaw(goal.title)
            val score = maxOf(
                scoreMatch(cleanQuery, normalizedTitle),
                scoreMatch(rawCleanQuery, rawNormalizedTitle)
            )
            goal to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) return ResolutionResult.NotFound()

        val maxScore = candidates.first().second
        val bestMatches = candidates.filter { it.second == maxScore }

        return when {
            bestMatches.size == 1 -> {
                val status = if (maxScore >= 90) EntityResolutionStatus.EXACT_MATCH else EntityResolutionStatus.PARTIAL_MATCH
                ResolutionResult.Success(bestMatches.first().first, status)
            }
            bestMatches.size > 1 -> ResolutionResult.Ambiguous(bestMatches.map { it.first })
            else -> ResolutionResult.NotFound()
        }
    }

    /**
     * Normalizes text by removing filler words for natural language comparison.
     */
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("(?i)\\b(the|my|a|an|task|goal|called|as|complete|mark|delete|update|change|to|for|with)\\b"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Raw normalization without filler removal (preserves exact title structure).
     */
    private fun normalizeRaw(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Scores how well a query matches a target string.
     */
    private fun scoreMatch(query: String, target: String): Int {
        if (query.isBlank() || target.isBlank()) return 0
        if (query == target) return 100
        if (target.startsWith(query) || query.startsWith(target)) return 80
        if (target.contains(query) || query.contains(target)) return 60
        
        val queryWords = query.split(" ").filter { it.length >= 2 }
        val targetWords = target.split(" ").toSet()
        if (queryWords.isEmpty()) return 0
        
        val matchingWords = queryWords.count { it in targetWords }
        
        if (matchingWords > 0) {
            return (matchingWords.toFloat() / queryWords.size * 50).toInt()
        }
        
        return 0
    }
}

/**
 * Result of an entity resolution attempt.
 */
sealed class ResolutionResult<T> {
    data class Success<T>(val entity: T, val status: EntityResolutionStatus = EntityResolutionStatus.EXACT_MATCH) : ResolutionResult<T>()
    data class Ambiguous<T>(val candidates: List<T>) : ResolutionResult<T>()
    class NotFound<T> : ResolutionResult<T>()
}
