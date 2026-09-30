package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "ai_automation_rule")
data class AiAutomationRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val triggerType: String,
    val enabled: Boolean,
    val cooldownMillis: Long,
    val lastTriggeredAt: Long,
    val lastTriggeredFingerprint: String?,
    val isStateChanging: Boolean,
    val targetActionType: String?,
    val conditionExpression: String?,
    val lastRunReason: String?,
    val runCount: Int,
    val createdAt: Long
)

@Entity(tableName = "automation_execution_record")
data class AutomationExecutionEntity(
    @PrimaryKey
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val timestamp: Long,
    val triggerType: String,
    val conditionMatched: String,
    val evidence: String,
    val actionTaken: String,
    val success: Boolean
)
