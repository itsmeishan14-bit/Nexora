package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask

/**
 * Robust local entity resolution for Nexora.
 * Matches user natural language to actual tasks and goals using deterministic scoring.
 */
object AiEntityResolver {

    /**
     * Resolves a user query to a specific task.
     * Prefers exact matches, then prefix matches, then containing matches.
     */
    fun resolveTask(query: String, tasks: List<PremiumTask>): ResolutionResult<PremiumTask> {
        val cleanQuery = normalize(query)
        val rawCleanQuery = normalizeRaw(query)
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
    fun resolveGoal(query: String, goals: List<NexoraGoal>): ResolutionResult<NexoraGoal> {
        val cleanQuery = normalize(query)
        val rawCleanQuery = normalizeRaw(query)
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
