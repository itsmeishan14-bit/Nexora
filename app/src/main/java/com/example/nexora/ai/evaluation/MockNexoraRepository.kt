package com.example.nexora.ai.evaluation

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A repository that keeps data in memory for deterministic evaluation.
 */
class MockNexoraRepository : NexoraRepository(null) {
    private val tasks = mutableListOf<PremiumTask>()
    private val goals = mutableListOf<NexoraGoal>()
    private val memories = mutableListOf<com.example.nexora.ai.AiMemoryItem>()

    override suspend fun observeTasksOnce(): List<PremiumTask> = tasks.toList()
    
    override suspend fun addTask(task: PremiumTask): PremiumTask {
        val newTask = task.copy(id = (tasks.size + 1).toLong())
        tasks.add(newTask)
        return newTask
    }

    override suspend fun updateTask(task: PremiumTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task
    }

    override suspend fun observeGoalsOnce(): List<NexoraGoal> = goals.toList()

    override suspend fun addGoal(goal: NexoraGoal): NexoraGoal {
        val newGoal = goal.copy(id = (goals.size + 1).toLong())
        goals.add(newGoal)
        return newGoal
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
