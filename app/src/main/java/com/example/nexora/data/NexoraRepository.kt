package com.example.nexora.data

import com.example.nexora.ai.AiConfidence
import com.example.nexora.ai.AiEvaluation
import com.example.nexora.ai.AiMemoryCategory
import com.example.nexora.ai.AiMemoryConfidence
import com.example.nexora.ai.AiMemoryImportance
import com.example.nexora.ai.AiMemoryItem
import com.example.nexora.ai.AiOutcome
import com.example.nexora.ai.AiOutcomeType
import com.example.nexora.ai.AiRecommendationHistory
import com.example.nexora.ai.AiRecommendationType
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

open class NexoraRepository(
    private val database: NexoraDatabase?
) {

    private val taskDao = database?.taskDao()
    private val goalDao = database?.goalDao()
    private val dailyProgressDao = database?.dailyProgressDao()
    private val aiLearningDao = database?.aiLearningDao()

    // ─────────────────────────────────────
    // TASKS
    // ─────────────────────────────────────

    open fun observeTasks(): Flow<List<PremiumTask>> {
        val dao = taskDao ?: return flowOf(emptyList())
        return dao.observeAll().map { entities ->
            entities.map { entity ->
                PremiumTask(
                    id = entity.id,
                    title = entity.title,
                    category = entity.category,
                    duration = entity.duration,
                    goalTitle = entity.goalTitle,
                    priority = try {
                        TaskPriority.valueOf(entity.priority)
                    } catch (e: IllegalArgumentException) {
                        TaskPriority.MEDIUM
                    },
                    completed = entity.completed
                )
            }
        }
    }

    open suspend fun observeTasksOnce(): List<PremiumTask> {
        val dao = taskDao ?: return emptyList()
        return dao.observeAllOnce().map { entity ->
            PremiumTask(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                duration = entity.duration,
                goalTitle = entity.goalTitle,
                priority = try {
                    TaskPriority.valueOf(entity.priority)
                } catch (e: IllegalArgumentException) {
                    TaskPriority.MEDIUM
                },
                completed = entity.completed
            )
        }
    }

    open suspend fun addTask(task: PremiumTask): PremiumTask {
        val dao = taskDao ?: return task
        val id = dao.insert(
            TaskEntity(
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority.name,
                completed = task.completed
            )
        )

        return task.copy(id = id)
    }

    open suspend fun updateTask(task: PremiumTask) {
        val dao = taskDao ?: return
        dao.update(
            TaskEntity(
                id = task.id,
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority.name,
                completed = task.completed
            )
        )
    }

    open suspend fun deleteTask(task: PremiumTask) {
        val dao = taskDao ?: return
        dao.delete(
            TaskEntity(
                id = task.id,
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority.name,
                completed = task.completed
            )
        )
    }

    // ─────────────────────────────────────
    // GOALS
    // ─────────────────────────────────────

    open fun observeGoals(): Flow<List<NexoraGoal>> {
        val dao = goalDao ?: return flowOf(emptyList())
        return dao.observeAll().map { entities ->
            entities.map { entity ->
                NexoraGoal(
                    id = entity.id,
                    title = entity.title,
                    category = entity.category,
                    targetDate = entity.targetDate,
                    progress = entity.progress
                )
            }
        }
    }

    open suspend fun observeGoalsOnce(): List<NexoraGoal> {
        val dao = goalDao ?: return emptyList()
        return dao.observeAllOnce().map { entity ->
            NexoraGoal(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                targetDate = entity.targetDate,
                progress = entity.progress
            )
        }
    }

    open suspend fun addGoal(goal: NexoraGoal): NexoraGoal {
        val dao = goalDao ?: return goal
        val id = dao.insert(
            GoalEntity(
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )

        return goal.copy(id = id)
    }

    open suspend fun updateGoal(goal: NexoraGoal) {
        val dao = goalDao ?: return
        dao.update(
            GoalEntity(
                id = goal.id,
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )
    }

    open suspend fun deleteGoal(goal: NexoraGoal) {
        val dao = goalDao ?: return
        dao.delete(
            GoalEntity(
                id = goal.id,
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )
    }

    // ─────────────────────────────────────
    // DAILY PROGRESS
    // ─────────────────────────────────────

    open fun observeDailyProgress(): Flow<List<DailyProgressEntity>> {
        val dao = dailyProgressDao ?: return flowOf(emptyList())
        return dao.observeAll()
    }

    open suspend fun getDailyProgress(
        date: String
    ): DailyProgressEntity? {
        val dao = dailyProgressDao ?: return null
        return dao.getByDate(date)
    }

    open suspend fun getHistoricalProgress(
        limit: Int
    ): List<DailyProgressEntity> {
        val dao = dailyProgressDao ?: return emptyList()
        return dao.getHistory(limit)
    }

    open suspend fun saveDailyProgress(
        progress: DailyProgressEntity
    ) {
        val dao = dailyProgressDao ?: return
        dao.insert(progress)
    }

    // ─────────────────────────────────────
    // AI LEARNING
    // ─────────────────────────────────────

    open suspend fun logRecommendation(recommendation: AiRecommendationHistory) {
        val dao = aiLearningDao ?: return
        dao.insertRecommendation(
            AiRecommendationHistoryEntity(
                id = recommendation.id,
                type = recommendation.type.name,
                title = recommendation.title,
                message = recommendation.message,
                timestamp = recommendation.timestamp,
                relatedTaskId = recommendation.relatedTaskId,
                relatedGoalId = recommendation.relatedGoalId,
                confidence = recommendation.confidence.name
            )
        )
    }

    open suspend fun getRecentRecommendations(limit: Int): List<AiRecommendationHistory> {
        val dao = aiLearningDao ?: return emptyList()
        return dao.getRecentRecommendations(limit).map { entity ->
            AiRecommendationHistory(
                id = entity.id,
                type = AiRecommendationType.valueOf(entity.type),
                title = entity.title,
                message = entity.message,
                timestamp = entity.timestamp,
                relatedTaskId = entity.relatedTaskId,
                relatedGoalId = entity.relatedGoalId,
                confidence = AiConfidence.valueOf(entity.confidence)
            )
        }
    }

    open suspend fun saveOutcome(outcome: AiOutcome) {
        val dao = aiLearningDao ?: return
        dao.insertOutcome(
            AiOutcomeEntity(
                id = outcome.id,
                recommendationId = outcome.recommendationId,
                actionId = outcome.actionId,
                type = outcome.type.name,
                timestamp = outcome.timestamp,
                relatedTaskId = outcome.relatedTaskId,
                relatedGoalId = outcome.relatedGoalId,
                expectedResult = outcome.expectedResult,
                actualResult = outcome.actualResult,
                confidence = outcome.confidence.name,
                evidence = outcome.evidence
            )
        )
    }

    open suspend fun getRecentOutcomes(limit: Int): List<AiOutcome> {
        val dao = aiLearningDao ?: return emptyList()
        return dao.getRecentOutcomes(limit).map { entity ->
            AiOutcome(
                id = entity.id,
                recommendationId = entity.recommendationId,
                actionId = entity.actionId,
                type = AiOutcomeType.valueOf(entity.type),
                timestamp = entity.timestamp,
                relatedTaskId = entity.relatedTaskId,
                relatedGoalId = entity.relatedGoalId,
                expectedResult = entity.expectedResult,
                actualResult = entity.actualResult,
                confidence = AiConfidence.valueOf(entity.confidence),
                evidence = entity.evidence
            )
        }
    }

    open suspend fun saveEvaluation(evaluation: AiEvaluation) {
        val dao = aiLearningDao ?: return
        dao.insertEvaluation(
            AiEvaluationEntity(
                id = evaluation.id,
                title = evaluation.title,
                whatWasExpected = evaluation.whatWasExpected,
                whatActuallyHappened = evaluation.whatActuallyHappened,
                outcome = evaluation.outcome.name,
                improvementSignal = evaluation.improvementSignal,
                timestamp = evaluation.timestamp,
                confidence = evaluation.confidence.name
            )
        )
    }

    open suspend fun getRecentEvaluations(limit: Int): List<AiEvaluation> {
        val dao = aiLearningDao ?: return emptyList()
        return dao.getRecentEvaluations(limit).map { entity ->
            AiEvaluation(
                id = entity.id,
                title = entity.title,
                whatWasExpected = entity.whatWasExpected,
                whatActuallyHappened = entity.whatActuallyHappened,
                outcome = AiOutcomeType.valueOf(entity.outcome),
                improvementSignal = entity.improvementSignal,
                timestamp = entity.timestamp,
                confidence = AiConfidence.valueOf(entity.confidence)
            )
        }
    }

    // ─────────────────────────────────────
    // AI MEMORY
    // ─────────────────────────────────────

    private val aiMemoryDao = database?.aiMemoryDao()

    open suspend fun saveMemory(item: AiMemoryItem) {
        val dao = aiMemoryDao ?: return
        dao.insertMemory(
            AiMemoryEntity(
                id = item.id,
                category = item.category.name,
                title = item.title,
                content = item.content,
                confidence = item.confidence.name,
                importance = item.importance.name,
                relatedTaskId = item.relatedTaskId,
                relatedGoalId = item.relatedGoalId,
                createdAt = item.createdAt,
                lastUsedAt = item.lastUsedAt,
                expiration = item.expiration,
                metadata = item.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
            )
        )
    }

    open suspend fun getAllMemory(): List<AiMemoryItem> {
        val dao = aiMemoryDao ?: return emptyList()
        return dao.getAllMemory().map { entity ->
            AiMemoryItem(
                id = entity.id,
                category = AiMemoryCategory.valueOf(entity.category),
                title = entity.title,
                content = entity.content,
                confidence = AiMemoryConfidence.valueOf(entity.confidence),
                importance = AiMemoryImportance.valueOf(entity.importance),
                relatedTaskId = entity.relatedTaskId,
                relatedGoalId = entity.relatedGoalId,
                createdAt = entity.createdAt,
                lastUsedAt = entity.lastUsedAt,
                expiration = entity.expiration,
                metadata = if (entity.metadata.isBlank()) emptyMap() else 
                    entity.metadata.split(";").associate { 
                        val parts = it.split("=")
                        parts[0] to (parts.getOrNull(1) ?: "")
                    }
            )
        }
    }

    open suspend fun deleteMemory(id: String) {
        aiMemoryDao?.deleteMemoryById(id)
    }

    open suspend fun clearAllMemory() {
        aiMemoryDao?.deleteAllMemory()
    }
}
