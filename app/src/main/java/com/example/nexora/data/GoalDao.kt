package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY id ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY id ASC")
    suspend fun observeAllOnce(): List<GoalEntity>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity): Int

    @Delete
    suspend fun delete(goal: GoalEntity): Int

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GoalEntity?

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks WHERE goalTitle = :goalTitle COLLATE NOCASE")
    suspend fun getTasksByGoalTitle(goalTitle: String): List<TaskEntity>

    @Query("UPDATE tasks SET goalTitle = NULL WHERE goalTitle = :goalTitle COLLATE NOCASE")
    suspend fun unlinkTasksByGoalTitle(goalTitle: String): Int

    @Transaction
    suspend fun deleteGoalAndUnlinkTasks(goalId: Long): Pair<GoalEntity, Int>? {
        val goal = getById(goalId) ?: return null
        val unlinked = unlinkTasksByGoalTitle(goal.title)
        val deleted = delete(goal)
        if (deleted <= 0) {
            throw IllegalStateException("Failed to delete goal $goalId from database.")
        }
        return Pair(goal, unlinked)
    }
}