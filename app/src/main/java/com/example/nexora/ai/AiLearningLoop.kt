package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import java.time.LocalDate

class AiLearningLoop(
    private val repository: NexoraRepository
) {
    /**
     * Evaluate recent recommendations and daily plans to detect outcomes.
     */
    suspend fun evaluateOutcomes() {
        val recentRecommendations = repository.getRecentRecommendations(50)
        val tasks = repository.observeTasksOnce()
        
        // 1. Evaluate Task-Specific Recommendations
        recentRecommendations.filter { it.relatedTaskId != null }.forEach { rec ->
            val existingOutcome = (repository as? com.example.nexora.data.NexoraRepository)?.getRecentOutcomes(100)?.find { it.recommendationId == rec.id }
            if (existingOutcome == null) {
                detectTaskOutcome(rec, tasks)
            }
        }

        // 2. Evaluate Daily Plans
        evaluateRecentDailyPlans()

        // 3. Generate Structured Memories
        generateMemories()
    }

    private suspend fun generateMemories() {
        val evaluations = repository.getRecentEvaluations(10)
        val allMemory = repository.getAllMemory()

        // Workload Memory
        val tooLargeCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_TOO_LARGE }
        if (tooLargeCount >= 3) {
            val existing = allMemory.find { it.category == AiMemoryCategory.WORKLOAD_PATTERN && it.title == "Workload Capacity" }
            val content = "Recent history shows daily plans often exceed your completed workload. A limit of 3-4 priority tasks seems most effective."
            
            if (existing == null) {
                repository.saveMemory(AiMemoryItem(
                    category = AiMemoryCategory.WORKLOAD_PATTERN,
                    title = "Workload Capacity",
                    content = content,
                    confidence = AiMemoryConfidence.HIGH,
                    importance = AiMemoryImportance.HIGH
                ))
            } else if (existing.content != content) {
                repository.saveMemory(existing.copy(content = content, lastUsedAt = System.currentTimeMillis()))
            }
        }
        
        // Task Pattern Memory - repeated carry-over
        val outcomes = repository.getRecentOutcomes(20)
        val carriedForward = outcomes.filter { it.type == AiOutcomeType.NOT_COMPLETED }
        
        carriedForward.groupBy { it.relatedTaskId }.forEach { (taskId, carryEvents) ->
            if (carryEvents.size >= 2 && taskId != null) {
                val existing = allMemory.find { it.relatedTaskId == taskId && it.category == AiMemoryCategory.TASK_PATTERN }
                if (existing == null) {
                    repository.saveMemory(AiMemoryItem(
                        category = AiMemoryCategory.TASK_PATTERN,
                        title = "Recurring Carry-over",
                        content = "This task has been carried forward multiple times. Consider decomposing it into smaller steps.",
                        relatedTaskId = taskId,
                        confidence = AiMemoryConfidence.MEDIUM,
                        importance = AiMemoryImportance.MEDIUM
                    ))
                }
            }
        }
    }

    private suspend fun detectTaskOutcome(rec: AiRecommendationHistory, tasks: List<PremiumTask>) {
        val task = tasks.find { it.id == rec.relatedTaskId }
        
        if (task == null) {
            // Task might have been deleted
            saveOutcome(rec, AiOutcomeType.REJECTED, evidence = "Task no longer exists.")
            return
        }

        if (task.completed) {
            saveOutcome(
                rec, 
                AiOutcomeType.SUCCESS, 
                actualResult = "Task completed.", 
                evidence = "Task state in database is completed."
            )
        } else {
            // Check if it's been several days since recommendation
            val daysOld = (System.currentTimeMillis() - rec.timestamp) / (1000 * 60 * 60 * 24)
            if (daysOld >= 1) {
                saveOutcome(
                    rec, 
                    AiOutcomeType.NOT_COMPLETED, 
                    actualResult = "Task remains incomplete.", 
                    evidence = "Task not completed after 24 hours of recommendation."
                )
            }
        }
    }

    private suspend fun saveOutcome(
        rec: AiRecommendationHistory, 
        type: AiOutcomeType, 
        actualResult: String? = null,
        evidence: String? = null
    ) {
        val outcome = AiOutcome(
            id = java.util.UUID.randomUUID().toString(),
            recommendationId = rec.id,
            actionId = null,
            type = type,
            relatedTaskId = rec.relatedTaskId,
            relatedGoalId = rec.relatedGoalId,
            expectedResult = rec.message,
            actualResult = actualResult,
            evidence = evidence
        )
        repository.saveOutcome(outcome)
    }

    private suspend fun evaluateRecentDailyPlans() {
        val history = repository.getHistoricalProgress(7)
        if (history.isEmpty()) return

        history.forEach { progress ->
            val date = progress.date
            // Check if we already evaluated this day
            val evaluations = repository.getRecentEvaluations(10)
            if (evaluations.any { it.title.contains(date) }) return@forEach

            val planned = progress.tasksPlanned
            val completed = progress.tasksCompleted
            val rate = if (planned > 0) completed.toFloat() / planned else 1f

            val outcome = when {
                planned == 0 -> AiOutcomeType.UNKNOWN
                rate >= 0.8f -> AiOutcomeType.PLAN_REALISTIC
                planned > 7 && rate < 0.5f -> AiOutcomeType.PLAN_TOO_LARGE
                rate < 0.4f -> AiOutcomeType.PLAN_TOO_LARGE
                else -> AiOutcomeType.PARTIAL_SUCCESS
            }

            if (outcome != AiOutcomeType.UNKNOWN) {
                val evaluation = AiEvaluation(
                    title = "Daily Plan Evaluation: $date",
                    whatWasExpected = "Complete $planned tasks.",
                    whatActuallyHappened = "Completed $completed tasks ($planned planned).",
                    outcome = outcome,
                    improvementSignal = when (outcome) {
                        AiOutcomeType.PLAN_TOO_LARGE -> "Reduce future daily workload limit."
                        AiOutcomeType.PLAN_REALISTIC -> "Maintain current workload capacity."
                        else -> null
                    }
                )
                repository.saveEvaluation(evaluation)
            }
        }
    }
}
