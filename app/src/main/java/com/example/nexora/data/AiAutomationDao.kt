package com.example.nexora.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiAutomationDao {

    // ─────────────────────────────────────
    // AUTOMATION RULES
    // ─────────────────────────────────────

    @Query("SELECT * FROM ai_automation_rule ORDER BY createdAt ASC")
    fun observeAllRules(): Flow<List<AiAutomationRuleEntity>>

    @Query("SELECT * FROM ai_automation_rule ORDER BY createdAt ASC")
    suspend fun getAllRulesOnce(): List<AiAutomationRuleEntity>

    @Query("SELECT * FROM ai_automation_rule WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): AiAutomationRuleEntity?

    @Query("SELECT * FROM ai_automation_rule WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getRuleByName(name: String): AiAutomationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AiAutomationRuleEntity)

    @Update
    suspend fun updateRule(rule: AiAutomationRuleEntity): Int

    @Query("DELETE FROM ai_automation_rule WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("DELETE FROM ai_automation_rule")
    suspend fun deleteAllRules()

    // ─────────────────────────────────────
    // AUTOMATION EXECUTION HISTORY
    // ─────────────────────────────────────

    @Query("SELECT * FROM automation_execution_record ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentExecutions(limit: Int = 50): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_execution_record ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentExecutionsOnce(limit: Int = 50): List<AutomationExecutionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: AutomationExecutionEntity)

    @Query("DELETE FROM automation_execution_record WHERE id NOT IN (SELECT id FROM automation_execution_record ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimExecutions(keepCount: Int = 50)
}
