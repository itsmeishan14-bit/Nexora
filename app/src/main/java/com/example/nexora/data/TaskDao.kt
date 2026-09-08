package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun observeAllOnce(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY id ASC")
    suspend fun getIncomplete(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE goalTitle = :goalTitle")
    suspend fun getByGoalTitle(goalTitle: String): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}