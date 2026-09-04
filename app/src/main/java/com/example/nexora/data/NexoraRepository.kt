package com.example.nexora.data

import com.example.nexora.uii.DailyProgress
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NexoraRepository(
    private val database: NexoraDatabase
) {

    private val taskDao = database.taskDao()
    private val goalDao = database.goalDao()
    private val dailyProgressDao = database.dailyProgressDao()

    // ---------------------------------------------------------
    // TASKS
    // ---------------------------------------------------------

    fun observeTasks(): Flow<List<PremiumTask>> {
        return taskDao.observeAll().map { entities ->
            entities.map { entity ->
                PremiumTask(
                    id = entity.id,
                    title = entity.title,
                    category = entity.category,
                    duration = entity.duration,
                    goalTitle = entity.goalTitle,
                    priority = entity.priority,
                    completed = entity.completed
                )
            }
        }
    }

    suspend fun addTask(task: PremiumTask) {
        taskDao.insert(
            TaskEntity(
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority,
                completed = task.completed
            )
        )
    }

    suspend fun updateTask(task: PremiumTask) {
        taskDao.update(
            TaskEntity(
                id = task.id,
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority,
                completed = task.completed
            )
        )
    }

    suspend fun deleteTask(task: PremiumTask) {
        taskDao.delete(
            TaskEntity(
                id = task.id,
                title = task.title,
                category = task.category,
                duration = task.duration,
                goalTitle = task.goalTitle,
                priority = task.priority,
                completed = task.completed
            )
        )
    }

    // ---------------------------------------------------------
    // GOALS
    // ---------------------------------------------------------

    fun observeGoals(): Flow<List<NexoraGoal>> {
        return goalDao.observeAll().map { entities ->
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

    suspend fun addGoal(goal: NexoraGoal) {
        goalDao.insert(
            GoalEntity(
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )
    }

    suspend fun updateGoal(goal: NexoraGoal) {
        goalDao.update(
            GoalEntity(
                id = goal.id,
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )
    }

    suspend fun deleteGoal(goal: NexoraGoal) {
        goalDao.delete(
            GoalEntity(
                id = goal.id,
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )
    }

    // ---------------------------------------------------------
    // DAILY PROGRESS
    // ---------------------------------------------------------

    fun observeDailyProgress(): Flow<List<DailyProgressEntity>> {
        return dailyProgressDao.observeAll()
    }

    suspend fun saveDailyProgress(progress: DailyProgressEntity) {
        dailyProgressDao.insert(progress)
    }
}