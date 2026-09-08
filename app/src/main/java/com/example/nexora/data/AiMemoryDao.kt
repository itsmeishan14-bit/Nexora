package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface AiMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AiMemoryEntity)

    @Query("SELECT * FROM ai_memory")
    suspend fun getAllMemory(): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE category = :category")
    suspend fun getMemoryByCategory(category: String): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): AiMemoryEntity?

    @Query("SELECT * FROM ai_memory WHERE relatedTaskId = :taskId")
    suspend fun getMemoryByTask(taskId: Long): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE relatedGoalId = :goalId")
    suspend fun getMemoryByGoal(goalId: Long): List<AiMemoryEntity>

    @Query("DELETE FROM ai_memory WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM ai_memory")
    suspend fun deleteAllMemory()
}
