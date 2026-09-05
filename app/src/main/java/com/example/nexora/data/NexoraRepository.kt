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

    // ─────────────────────────────────────
    // TASKS
    // ─────────────────────────────────────

    fun observeTasks(): Flow<List<PremiumTask>> {
        return taskDao.observeAll().map { entities ->
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

    suspend fun observeTasksOnce(): List<PremiumTask> {
        return taskDao.observeAllOnce().map { entity ->
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

    suspend fun addTask(task: PremiumTask): PremiumTask {
        val id = taskDao.insert(
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

    suspend fun updateTask(task: PremiumTask) {
        taskDao.update(
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

    suspend fun deleteTask(task: PremiumTask) {
        taskDao.delete(
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

    suspend fun observeGoalsOnce(): List<NexoraGoal> {
        return goalDao.observeAllOnce().map { entity ->
            NexoraGoal(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                targetDate = entity.targetDate,
                progress = entity.progress
            )
        }
    }

    suspend fun addGoal(goal: NexoraGoal): NexoraGoal {
        val id = goalDao.insert(
            GoalEntity(
                title = goal.title,
                category = goal.category,
                targetDate = goal.targetDate,
                progress = goal.progress
            )
        )

        return goal.copy(id = id)
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

    // ─────────────────────────────────────
    // DAILY PROGRESS
    // ─────────────────────────────────────

    fun observeDailyProgress(): Flow<List<DailyProgressEntity>> {
        return dailyProgressDao.observeAll()
    }

    suspend fun getDailyProgress(
        date: String
    ): DailyProgressEntity? {
        return dailyProgressDao.getByDate(date)
    }

    suspend fun saveDailyProgress(
        progress: DailyProgressEntity
    ) {
        dailyProgressDao.insert(progress)
    }
}