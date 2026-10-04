package com.example.nexora.ai.evaluation

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal

/**
 * A repository that keeps data in memory for deterministic evaluation.
 */
open class MockNexoraRepository : NexoraRepository(null) {
    private val tasks = mutableListOf<PremiumTask>()
    private val goals = mutableListOf<NexoraGoal>()
    private val memories = mutableListOf<com.example.nexora.ai.AiMemoryItem>()
    private val dailyProgress = mutableListOf<com.example.nexora.data.DailyProgressEntity>()

    override suspend fun saveDailyProgress(progress: com.example.nexora.data.DailyProgressEntity) {
        dailyProgress.add(progress)
    }

    override suspend fun getDailyProgress(date: String): com.example.nexora.data.DailyProgressEntity? {
        return dailyProgress.find { it.date == date }
    }

    override suspend fun getHistoricalProgress(limit: Int): List<com.example.nexora.data.DailyProgressEntity> {
        return dailyProgress.takeLast(limit)
    }

    override suspend fun observeTasksOnce(): List<PremiumTask> = tasks.toList()
    
    override suspend fun getTaskById(id: Long): PremiumTask? = tasks.find { it.id == id }

    override suspend fun getIncompleteTasksOnce(): List<PremiumTask> = tasks.filter { !it.completed }

    var failAddTask: Boolean = false
    var failUpdateTask: Boolean = false
    var failDeleteTask: Boolean = false
    var failAddGoal: Boolean = false
    var failUpdateGoal: Boolean = false
    var failDeleteGoal: Boolean = false

    override suspend fun addTask(task: PremiumTask): PremiumTask {
        if (failAddTask) return task.copy(id = 0L)
        val newTask = task.copy(id = (tasks.size + 1).toLong())
        tasks.add(newTask)
        return newTask
    }

    override suspend fun updateTask(task: PremiumTask): Boolean {
        if (failUpdateTask) return false
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            return true
        }
        return false
    }

    override suspend fun deleteTask(task: PremiumTask): Boolean {
        if (failDeleteTask) return false
        return tasks.removeAll { it.id == task.id }
    }

    override suspend fun deleteAllTasks() {
        tasks.clear()
    }

    override suspend fun completeAllTasks() {
        val updated = tasks.map { it.copy(completed = true) }
        tasks.clear()
        tasks.addAll(updated)
    }

    override suspend fun observeGoalsOnce(): List<NexoraGoal> = goals.toList()

    override suspend fun getGoalById(id: Long): NexoraGoal? = goals.find { it.id == id }

    override suspend fun addGoal(goal: NexoraGoal): NexoraGoal {
        if (failAddGoal) return goal.copy(id = 0L)
        val newGoal = goal.copy(id = (goals.size + 1).toLong())
        goals.add(newGoal)
        return newGoal
    }

    override suspend fun updateGoal(goal: NexoraGoal): Boolean {
        if (failUpdateGoal) return false
        val index = goals.indexOfFirst { it.id == goal.id }
        if (index >= 0) {
            goals[index] = goal
            return true
        }
        return false
    }

    override suspend fun deleteGoal(goal: NexoraGoal): Boolean {
        if (failDeleteGoal) return false
        return goals.removeAll { it.id == goal.id }
    }

    var failSaveOutcome: Boolean = false
    private val outcomes = mutableListOf<com.example.nexora.ai.AiOutcome>()

    override suspend fun saveOutcome(outcome: com.example.nexora.ai.AiOutcome) {
        if (failSaveOutcome) throw java.io.IOException("Outcome write failure")
        outcomes.add(outcome)
    }

    override suspend fun getRecentOutcomes(limit: Int): List<com.example.nexora.ai.AiOutcome> {
        return outcomes.takeLast(limit)
    }

    override suspend fun saveMemory(item: com.example.nexora.ai.AiMemoryItem) {
        memories.add(item)
    }

    override suspend fun getAllMemory(): List<com.example.nexora.ai.AiMemoryItem> {
        return memories.toList()
    }

    override suspend fun deleteMemory(id: String) {
        memories.removeAll { it.id == id }
    }

    override suspend fun clearAllMemory() {
        memories.clear()
    }

    // ─────────────────────────────────────
    // AUTOMATIONS (MOCK)
    // ─────────────────────────────────────

    private val automationRules = mutableListOf<com.example.nexora.ai.AiAutomationRule>()
    private val automationExecutions = mutableListOf<com.example.nexora.ai.AutomationExecutionRecord>()

    override fun observeAutomationRules(): kotlinx.coroutines.flow.Flow<List<com.example.nexora.ai.AiAutomationRule>> {
        return kotlinx.coroutines.flow.flowOf(automationRules.toList())
    }

    var failGetAutomationRules: Boolean = false
    var failInsertAutomation: Boolean = false
    var failUpdateAutomation: Boolean = false
    var failDeleteAutomation: Boolean = false
    var failInsertExecution: Boolean = false

    override suspend fun getAutomationRules(): List<com.example.nexora.ai.AiAutomationRule> {
        if (failGetAutomationRules) throw java.io.IOException("Automation rules read failure")
        return automationRules.toList()
    }

    override suspend fun getAutomationRule(idOrName: String): com.example.nexora.ai.AiAutomationRule? {
        val clean = idOrName.trim().removeSuffix(".")
        return automationRules.find { 
            it.id.equals(clean, ignoreCase = true) || 
            it.name.equals(clean, ignoreCase = true) 
        }
    }

    override suspend fun insertAutomationRule(rule: com.example.nexora.ai.AiAutomationRule): Boolean {
        if (failInsertAutomation) return false
        if (automationRules.any { it.name.equals(rule.name, ignoreCase = true) }) {
            return false // duplicate name
        }
        automationRules.add(rule)
        return true
    }

    override suspend fun updateAutomationRule(rule: com.example.nexora.ai.AiAutomationRule): Boolean {
        if (failUpdateAutomation) return false
        val index = automationRules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            automationRules[index] = rule
            return true
        }
        return false
    }

    override suspend fun deleteAutomationRule(idOrName: String): Boolean {
        if (failDeleteAutomation) return false
        val clean = idOrName.trim().removeSuffix(".")
        return automationRules.removeAll { 
            it.id.equals(clean, ignoreCase = true) || 
            it.name.equals(clean, ignoreCase = true) 
        }
    }

    override suspend fun deleteAllAutomationRules() {
        automationRules.clear()
    }

    override suspend fun insertAutomationExecution(record: com.example.nexora.ai.AutomationExecutionRecord) {
        if (failInsertExecution) throw java.io.IOException("Automation execution insert failure")
        automationExecutions.add(record)
    }

    override fun observeAutomationExecutions(limit: Int): kotlinx.coroutines.flow.Flow<List<com.example.nexora.ai.AutomationExecutionRecord>> {
        return kotlinx.coroutines.flow.flowOf(automationExecutions.takeLast(limit).reversed())
    }

    override suspend fun getRecentAutomationExecutions(limit: Int): List<com.example.nexora.ai.AutomationExecutionRecord> {
        return automationExecutions.takeLast(limit).reversed()
    }

    override suspend fun trimAutomationExecutions(keepCount: Int) {
        while (automationExecutions.size > keepCount) {
            automationExecutions.removeAt(0)
        }
    }

    // Initialize with data
    fun seed(tasks: List<PremiumTask> = emptyList(), goals: List<NexoraGoal> = emptyList()) {
        this.tasks.clear()
        this.tasks.addAll(tasks)
        this.goals.clear()
        this.goals.addAll(goals)
    }
}
