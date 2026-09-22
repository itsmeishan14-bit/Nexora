package com.example.nexora.ai.evaluation

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal

/**
 * A repository that keeps data in memory for deterministic evaluation.
 */
class MockNexoraRepository : NexoraRepository(null) {
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

    override suspend fun addTask(task: PremiumTask): PremiumTask {
        val newTask = task.copy(id = (tasks.size + 1).toLong())
        tasks.add(newTask)
        return newTask
    }

    override suspend fun updateTask(task: PremiumTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task
    }

    override suspend fun deleteTask(task: PremiumTask) {
        tasks.removeAll { it.id == task.id }
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
        val newGoal = goal.copy(id = (goals.size + 1).toLong())
        goals.add(newGoal)
        return newGoal
    }

    override suspend fun updateGoal(goal: NexoraGoal) {
        val index = goals.indexOfFirst { it.id == goal.id }
        if (index >= 0) goals[index] = goal
    }

    override suspend fun deleteGoal(goal: NexoraGoal) {
        goals.removeAll { it.id == goal.id }
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

    // Initialize with data
    fun seed(tasks: List<PremiumTask> = emptyList(), goals: List<NexoraGoal> = emptyList()) {
        this.tasks.clear()
        this.tasks.addAll(tasks)
        this.goals.clear()
        this.goals.addAll(goals)
    }
}
