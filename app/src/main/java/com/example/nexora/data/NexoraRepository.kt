package com.example.nexora.data

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
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
        val existingTasks = taskDao.observeAllOnce()

        val entity = existingTasks.firstOrNull {
            it.title == task.title &&
                    it.category == task.category &&
                    it.duration == task.duration
        }

        if (entity != null) {
            taskDao.update(
                entity.copy(
                    title = task.title,
                    category = task.category,
                    duration = task.duration,
                    goalTitle = task.goalTitle,
                    priority = task.priority,
                    completed = task.completed
                )
            )
        }
    }

    suspend fun deleteTask(task: PremiumTask) {
        val existingTasks = taskDao.observeAllOnce()

        val entity = existingTasks.firstOrNull {
            it.title == task.title &&
                    it.category == task.category &&
                    it.duration == task.duration
        }

        if (entity != null) {
            taskDao.delete(entity)
        }
    }

    // ---------------------------------------------------------
    // GOALS
    // ---------------------------------------------------------

    fun observeGoals(): Flow<List<NexoraGoal>> {
        return goalDao.observeAll().map { entities ->
            entities.map { entity ->
                NexoraGoal(
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
        val existingGoals = goalDao.observeAllOnce()

        val entity = existingGoals.firstOrNull {
            it.title == goal.title &&
                    it.category == goal.category
        }

        if (entity != null) {
            goalDao.update(
                entity.copy(
                    title = goal.title,
                    category = goal.category,
                    targetDate = goal.targetDate,
                    progress = goal.progress
                )
            )
        }
    }

    suspend fun deleteGoal(goal: NexoraGoal) {
        val existingGoals = goalDao.observeAllOnce()

        val entity = existingGoals.firstOrNull {
            it.title == goal.title &&
                    it.category == goal.category
        }

        if (entity != null) {
            goalDao.delete(entity)
        }
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