package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask

object AiEntityResolver {

    fun resolveTask(query: String, tasks: List<PremiumTask>): ResolutionResult<PremiumTask> {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank()) return ResolutionResult.NotFound()

        // 1. Exact match
        val exactMatch = tasks.find { it.title.lowercase() == cleanQuery }
        if (exactMatch != null) return ResolutionResult.Success(exactMatch)

        // 2. Contains match
        val containingMatches = tasks.filter { it.title.lowercase().contains(cleanQuery) }
        
        return when {
            containingMatches.size == 1 -> ResolutionResult.Success(containingMatches.first())
            containingMatches.size > 1 -> ResolutionResult.Ambiguous(containingMatches)
            else -> ResolutionResult.NotFound()
        }
    }

    fun resolveGoal(query: String, goals: List<NexoraGoal>): ResolutionResult<NexoraGoal> {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank()) return ResolutionResult.NotFound()

        val exactMatch = goals.find { it.title.lowercase() == cleanQuery }
        if (exactMatch != null) return ResolutionResult.Success(exactMatch)

        val containingMatches = goals.filter { it.title.lowercase().contains(cleanQuery) }
        
        return when {
            containingMatches.size == 1 -> ResolutionResult.Success(containingMatches.first())
            containingMatches.size > 1 -> ResolutionResult.Ambiguous(containingMatches)
            else -> ResolutionResult.NotFound()
        }
    }
}

sealed class ResolutionResult<T> {
    data class Success<T>(val entity: T) : ResolutionResult<T>()
    data class Ambiguous<T>(val candidates: List<T>) : ResolutionResult<T>()
    class NotFound<T> : ResolutionResult<T>()
}
