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
        if (cleanQuery.isBlank()) return ResolutionResult.NotFound()

        // Match against all tasks, but we could prioritize incomplete ones if needed.
        val candidates = tasks.map { it to scoreMatch(cleanQuery, normalize(it.title)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) return ResolutionResult.NotFound()

        val maxScore = candidates.first().second
        val bestMatches = candidates.filter { it.second == maxScore }

        return when {
            bestMatches.size == 1 -> ResolutionResult.Success(bestMatches.first().first)
            bestMatches.size > 1 -> ResolutionResult.Ambiguous(bestMatches.map { it.first })
            else -> ResolutionResult.NotFound()
        }
    }

    /**
     * Resolves a user query to a specific goal.
     */
    fun resolveGoal(query: String, goals: List<NexoraGoal>): ResolutionResult<NexoraGoal> {
        val cleanQuery = normalize(query)
        if (cleanQuery.isBlank()) return ResolutionResult.NotFound()

        val candidates = goals.map { it to scoreMatch(cleanQuery, normalize(it.title)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) return ResolutionResult.NotFound()

        val maxScore = candidates.first().second
        val bestMatches = candidates.filter { it.second == maxScore }

        return when {
            bestMatches.size == 1 -> ResolutionResult.Success(bestMatches.first().first)
            bestMatches.size > 1 -> ResolutionResult.Ambiguous(bestMatches.map { it.first })
            else -> ResolutionResult.NotFound()
        }
    }

    /**
     * Normalizes text for consistent comparison.
     */
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Scores how well a query matches a target string.
     */
    private fun scoreMatch(query: String, target: String): Int {
        if (query == target) return 100
        if (target.startsWith(query)) return 80
        if (target.contains(query)) return 60
        
        val queryWords = query.split(" ").filter { it.length > 2 }
        val targetWords = target.split(" ").toSet()
        val matchingWords = queryWords.count { it in targetWords }
        
        if (matchingWords > 0) {
            return matchingWords * 20
        }
        
        return 0
    }
}

/**
 * Result of an entity resolution attempt.
 */
sealed class ResolutionResult<T> {
    data class Success<T>(val entity: T) : ResolutionResult<T>()
    data class Ambiguous<T>(val candidates: List<T>) : ResolutionResult<T>()
    class NotFound<T> : ResolutionResult<T>()
}
