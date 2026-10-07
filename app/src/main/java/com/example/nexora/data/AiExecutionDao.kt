package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiExecutionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionRecord(record: AiExecutionRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionExecutions(actions: List<AiActionExecutionEntity>)

    @Query("SELECT * FROM ai_execution_record ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentExecutionRecords(limit: Int): List<AiExecutionRecordEntity>

    @Query("SELECT * FROM ai_execution_record ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentExecutionRecords(limit: Int): Flow<List<AiExecutionRecordEntity>>

    @Query("SELECT * FROM ai_execution_record WHERE id = :id")
    suspend fun getExecutionRecordById(id: String): AiExecutionRecordEntity?

    @Query("SELECT * FROM ai_action_execution_record WHERE executionRecordId = :recordId ORDER BY executionOrder ASC")
    suspend fun getActionExecutionsForRecord(recordId: String): List<AiActionExecutionEntity>

    @Query("SELECT * FROM ai_action_execution_record WHERE executionRecordId = :recordId ORDER BY executionOrder ASC")
    fun observeActionExecutionsForRecord(recordId: String): Flow<List<AiActionExecutionEntity>>

    @Query("SELECT * FROM ai_action_execution_record ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentActionExecutions(limit: Int): List<AiActionExecutionEntity>

    @Query("DELETE FROM ai_execution_record")
    suspend fun clearAllExecutionRecords()

    @Query("DELETE FROM ai_action_execution_record")
    suspend fun clearAllActionExecutions()
}
