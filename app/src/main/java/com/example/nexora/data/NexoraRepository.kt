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
import com.example.nexora.ai.AiActionType
import com.example.nexora.ai.AiAutomationRule
import com.example.nexora.ai.AutomationExecutionRecord
import com.example.nexora.ai.AutomationExecutionStage
import com.example.nexora.ai.AutomationTriggerType
import com.example.nexora.uii.TaskPriority
import com.example.nexora.util.NexoraLogger
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
    private val aiAutomationDao = database?.aiAutomationDao()

    // ─────────────────────────────────────
    // TASKS
    // ─────────────────────────────────────

    open fun observeTasks(): Flow<List<PremiumTask>> {
        val dao = taskDao ?: return flowOf(emptyList())
        return dao.observeAll().map { entities ->
            entities.map { entity ->
                mapTaskEntityToDomain(entity)
            }
        }
    }

    open suspend fun observeTasksOnce(): List<PremiumTask> {
        val dao = taskDao ?: return emptyList()
        return dao.observeAllOnce().map { entity ->
            mapTaskEntityToDomain(entity)
        }
    }

    open suspend fun getTaskById(id: Long): PremiumTask? {
        val dao = taskDao ?: return null
        return dao.getById(id)?.let { mapTaskEntityToDomain(it) }
    }

    open suspend fun getIncompleteTasksOnce(): List<PremiumTask> {
        val dao = taskDao ?: return emptyList()
        return dao.getIncomplete().map { mapTaskEntityToDomain(it) }
    }

    open suspend fun getTasksByGoal(goalTitle: String): List<PremiumTask> {
        val dao = taskDao ?: return emptyList()
        return dao.getByGoalTitle(goalTitle).map { mapTaskEntityToDomain(it) }
    }

    private fun mapTaskEntityToDomain(entity: TaskEntity): PremiumTask {
        return PremiumTask(
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

    open suspend fun updateTask(task: PremiumTask): Boolean {
        val dao = taskDao ?: return false
        val entity = TaskEntity(
            id = task.id,
            title = task.title,
            category = task.category,
            duration = task.duration,
            goalTitle = task.goalTitle,
            priority = task.priority.name,
            completed = task.completed
        )
        return try {
            dao.update(entity) > 0
        } catch (e: Exception) {
            NexoraLogger.e("REPO", "Failed to update task ${task.id}", e)
            false
        }
    }

    open suspend fun deleteTask(task: PremiumTask): Boolean {
        val dao = taskDao ?: return false
        val entity = TaskEntity(
            id = task.id,
            title = task.title,
            category = task.category,
            duration = task.duration,
            goalTitle = task.goalTitle,
            priority = task.priority.name,
            completed = task.completed
        )
        return try {
            dao.delete(entity) > 0
        } catch (e: Exception) {
            NexoraLogger.e("REPO", "Failed to delete task ${task.id}", e)
            false
        }
    }

    open suspend fun deleteAllTasks() {
        taskDao?.deleteAll()
    }

    open suspend fun completeAllTasks() {
        taskDao?.markAllCompleted()
    }

    // ─────────────────────────────────────
    // GOALS
    // ─────────────────────────────────────

    open fun observeGoals(): Flow<List<NexoraGoal>> {
        val dao = goalDao ?: return flowOf(emptyList())
        return dao.observeAll().map { entities ->
            entities.map { entity ->
                mapGoalEntityToDomain(entity)
            }
        }
    }

    open suspend fun observeGoalsOnce(): List<NexoraGoal> {
        val dao = goalDao ?: return emptyList()
        return dao.observeAllOnce().map { entity ->
            mapGoalEntityToDomain(entity)
        }
    }

    open suspend fun getGoalById(id: Long): NexoraGoal? {
        val dao = goalDao ?: return null
        return dao.getById(id)?.let { mapGoalEntityToDomain(it) }
    }

    private fun mapGoalEntityToDomain(entity: GoalEntity): NexoraGoal {
        return NexoraGoal(
            id = entity.id,
            title = entity.title,
            category = entity.category,
            targetDate = entity.targetDate,
            progress = entity.progress
        )
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

    open suspend fun updateGoal(goal: NexoraGoal): Boolean {
        val dao = goalDao ?: return false
        val entity = GoalEntity(
            id = goal.id,
            title = goal.title,
            category = goal.category,
            targetDate = goal.targetDate,
            progress = goal.progress
        )
        return try {
            dao.update(entity) > 0
        } catch (e: Exception) {
            NexoraLogger.e("REPO", "Failed to update goal ${goal.id}", e)
            false
        }
    }

    open suspend fun deleteGoal(goal: NexoraGoal): Boolean {
        val dao = goalDao ?: return false
        val entity = GoalEntity(
            id = goal.id,
            title = goal.title,
            category = goal.category,
            targetDate = goal.targetDate,
            progress = goal.progress
        )
        return try {
            dao.delete(entity) > 0
        } catch (e: Exception) {
            NexoraLogger.e("REPO", "Failed to delete goal ${goal.id}", e)
            false
        }
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
            mapMemoryEntityToDomain(entity)
        }
    }

    open suspend fun getMemoryByTask(taskId: Long): List<AiMemoryItem> {
        val dao = aiMemoryDao ?: return emptyList()
        return dao.getMemoryByTask(taskId).map { mapMemoryEntityToDomain(it) }
    }

    open suspend fun getMemoryByGoal(goalId: Long): List<AiMemoryItem> {
        val dao = aiMemoryDao ?: return emptyList()
        return dao.getMemoryByGoal(goalId).map { mapMemoryEntityToDomain(it) }
    }

    open suspend fun getMemoryByCategory(category: AiMemoryCategory): List<AiMemoryItem> {
        val dao = aiMemoryDao ?: return emptyList()
        return dao.getMemoryByCategory(category.name).map { mapMemoryEntityToDomain(it) }
    }

    private fun mapMemoryEntityToDomain(entity: AiMemoryEntity): AiMemoryItem {
        return AiMemoryItem(
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

    open suspend fun deleteMemory(id: String) {
        aiMemoryDao?.deleteMemoryById(id)
    }

    open suspend fun clearAllMemory() {
        aiMemoryDao?.deleteAllMemory()
    }

    // ─────────────────────────────────────
    // AUTOMATIONS
    // ─────────────────────────────────────

    open fun observeAutomationRules(): Flow<List<AiAutomationRule>> {
        val dao = aiAutomationDao ?: return flowOf(emptyList())
        return dao.observeAllRules().map { entities ->
            entities.map { mapAutomationRuleEntityToDomain(it) }
        }
    }

    open suspend fun getAutomationRules(): List<AiAutomationRule> {
        val dao = aiAutomationDao ?: return emptyList()
        return dao.getAllRulesOnce().map { mapAutomationRuleEntityToDomain(it) }
    }

    open suspend fun getAutomationRule(idOrName: String): AiAutomationRule? {
        val dao = aiAutomationDao ?: return null
        val byId = dao.getRuleById(idOrName)
        if (byId != null) return mapAutomationRuleEntityToDomain(byId)
        return dao.getRuleByName(idOrName)?.let { mapAutomationRuleEntityToDomain(it) }
    }

    open suspend fun insertAutomationRule(rule: AiAutomationRule): Boolean {
        val dao = aiAutomationDao ?: return false
        val existing = dao.getRuleByName(rule.name) ?: dao.getRuleById(rule.id)
        if (existing != null) {
            return false
        }
        val entity = mapAutomationRuleDomainToEntity(rule)
        dao.insertRule(entity)
        return true
    }

    open suspend fun updateAutomationRule(rule: AiAutomationRule): Boolean {
        val dao = aiAutomationDao ?: return false
        val existing = dao.getRuleById(rule.id) ?: return false
        val entity = mapAutomationRuleDomainToEntity(rule)
        return dao.updateRule(entity) > 0
    }

    open suspend fun deleteAutomationRule(idOrName: String): Boolean {
        val dao = aiAutomationDao ?: return false
        val existing = getAutomationRule(idOrName) ?: return false
        dao.deleteRuleById(existing.id)
        return true
    }

    open suspend fun deleteAllAutomationRules() {
        aiAutomationDao?.deleteAllRules()
    }

    open suspend fun insertAutomationExecution(record: AutomationExecutionRecord) {
        val dao = aiAutomationDao ?: return
        dao.insertExecution(mapAutomationExecutionDomainToEntity(record))
    }

    open fun observeAutomationExecutions(limit: Int = 50): Flow<List<AutomationExecutionRecord>> {
        val dao = aiAutomationDao ?: return flowOf(emptyList())
        return dao.observeRecentExecutions(limit).map { entities ->
            entities.map { mapAutomationExecutionEntityToDomain(it) }
        }
    }

    open suspend fun getRecentAutomationExecutions(limit: Int = 50): List<AutomationExecutionRecord> {
        val dao = aiAutomationDao ?: return emptyList()
        return dao.getRecentExecutionsOnce(limit).map { mapAutomationExecutionEntityToDomain(it) }
    }

    open suspend fun trimAutomationExecutions(keepCount: Int = 50) {
        aiAutomationDao?.trimExecutions(keepCount)
    }

    private fun mapAutomationRuleEntityToDomain(entity: AiAutomationRuleEntity): AiAutomationRule {
        return AiAutomationRule(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            triggerType = try { AutomationTriggerType.valueOf(entity.triggerType) } catch (e: Exception) { AutomationTriggerType.DAY_STARTED },
            enabled = entity.enabled,
            cooldownMillis = entity.cooldownMillis,
            lastTriggeredAt = entity.lastTriggeredAt,
            lastTriggeredFingerprint = entity.lastTriggeredFingerprint,
            isStateChanging = entity.isStateChanging,
            targetActionType = entity.targetActionType?.let { try { AiActionType.valueOf(it) } catch (e: Exception) { null } },
            conditionExpression = entity.conditionExpression,
            lastRunReason = entity.lastRunReason,
            runCount = entity.runCount,
            createdAt = entity.createdAt
        )
    }

    private fun mapAutomationRuleDomainToEntity(rule: AiAutomationRule): AiAutomationRuleEntity {
        return AiAutomationRuleEntity(
            id = rule.id,
            name = rule.name,
            description = rule.description,
            triggerType = rule.triggerType.name,
            enabled = rule.enabled,
            cooldownMillis = rule.cooldownMillis,
            lastTriggeredAt = rule.lastTriggeredAt,
            lastTriggeredFingerprint = rule.lastTriggeredFingerprint,
            isStateChanging = rule.isStateChanging,
            targetActionType = rule.targetActionType?.name,
            conditionExpression = rule.conditionExpression,
            lastRunReason = rule.lastRunReason,
            runCount = rule.runCount,
            createdAt = rule.createdAt
        )
    }

    private fun mapAutomationExecutionEntityToDomain(entity: AutomationExecutionEntity): AutomationExecutionRecord {
        val stageParsed = try {
            if (entity.actionTaken.startsWith("[STAGE:")) {
                val stageName = entity.actionTaken.substringAfter("[STAGE:").substringBefore("]")
                AutomationExecutionStage.valueOf(stageName)
            } else null
        } catch (_: Exception) { null }

        val cleanAction = if (entity.actionTaken.startsWith("[STAGE:")) {
            entity.actionTaken.substringAfter("] ")
        } else {
            entity.actionTaken
        }

        return AutomationExecutionRecord(
            id = entity.id,
            ruleId = entity.ruleId,
            ruleName = entity.ruleName,
            timestamp = entity.timestamp,
            triggerType = try { AutomationTriggerType.valueOf(entity.triggerType) } catch (e: Exception) { AutomationTriggerType.DAY_STARTED },
            conditionMatched = entity.conditionMatched,
            evidence = entity.evidence,
            actionTaken = cleanAction,
            success = entity.success,
            stage = stageParsed ?: if (entity.success) AutomationExecutionStage.ACTION_PROPOSED else AutomationExecutionStage.ACTION_FAILED
        )
    }

    private fun mapAutomationExecutionDomainToEntity(record: AutomationExecutionRecord): AutomationExecutionEntity {
        return AutomationExecutionEntity(
            id = record.id,
            ruleId = record.ruleId,
            ruleName = record.ruleName,
            timestamp = record.timestamp,
            triggerType = record.triggerType.name,
            conditionMatched = record.conditionMatched,
            evidence = record.evidence,
            actionTaken = "[STAGE:${record.stage.name}] ${record.actionTaken}",
            success = record.success
        )
    }
}
