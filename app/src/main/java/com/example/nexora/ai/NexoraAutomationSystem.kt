package com.example.nexora.ai

import com.example.nexora.util.NexoraLogger

/**
 * Manages automation rules, dynamic condition evaluation, and execution history logging.
 */
class NexoraAutomationSystem {
    private val rules = mutableListOf<AiAutomationRule>()
    private val executionLogs = mutableListOf<AutomationExecutionRecord>()
    private val proactiveEngine = NexoraProactiveEngine()

    init {
        // Default core automation rules
        rules.add(AiAutomationRule(
            name = "Workload Manager",
            description = "Suggests workload reorganization when overload is detected.",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
            cooldownMillis = 3600000 // 1 hour
        ))
        rules.add(AiAutomationRule(
            name = "Morning Plan Assistant",
            description = "Prepares your daily plan automatically every morning.",
            triggerType = AutomationTriggerType.DAY_STARTED,
            cooldownMillis = 72000000 // 20 hours
        ))
        rules.add(AiAutomationRule(
            name = "Task Completion Next Action",
            description = "Recommends your next best task right after you finish a task.",
            triggerType = AutomationTriggerType.TASK_COMPLETED,
            cooldownMillis = 10000 // 30 seconds
        ))
        rules.add(AiAutomationRule(
            name = "Goal Progress Guard",
            description = "Triggers when a goal shows no activity for several days.",
            triggerType = AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED,
            cooldownMillis = 86400000 // 24 hours
        ))
        rules.add(AiAutomationRule(
            name = "Task Breakdown Assistant",
            description = "Suggests breaking down tasks that are repeatedly carried forward.",
            triggerType = AutomationTriggerType.TASK_CARRIED_FORWARD,
            cooldownMillis = 14400000 // 4 hours
        ))
        rules.add(AiAutomationRule(
            name = "Urgent Conflict Detector",
            description = "Notifies when multiple urgent tasks are pending.",
            triggerType = AutomationTriggerType.TASK_CREATED,
            cooldownMillis = 1800000 // 30 minutes
        ))
    }

    /**
     * Evaluates whether any automation rules should trigger based on a context change.
     * Returns a list of proactive signals or responses produced by automations.
     */
    fun evaluateTriggers(trigger: AutomationTriggerType, context: AiContext): List<AiProactiveSignal> {
        val signals = proactiveEngine.detectSignals(context)
        val matchingRules = rules.filter { it.enabled && it.triggerType == trigger }
        
        val triggeredSignals = mutableListOf<AiProactiveSignal>()
        val now = System.currentTimeMillis()

        for (rule in matchingRules) {
            if (now - rule.lastTriggeredAt < rule.cooldownMillis) {
                continue
            }

            val signal = evaluateRuleSignal(rule, signals, context, trigger)

            if (signal != null) {
                updateRuleTriggered(rule, signal.fingerprint, signal.evidence)
                recordExecution(rule, trigger, signal.title, signal.evidence, true)
                triggeredSignals.add(signal)
            }
        }

        return triggeredSignals
    }

    private fun evaluateRuleSignal(
        rule: AiAutomationRule,
        signals: List<AiProactiveSignal>,
        context: AiContext,
        trigger: AutomationTriggerType
    ): AiProactiveSignal? {
        return when (rule.name) {
            "Workload Manager" -> {
                signals.find { it.type == ProactiveSignalType.WORKLOAD_RISK || it.type == ProactiveSignalType.OVERLOAD }
            }
            "Morning Plan Assistant" -> {
                val planner = AiPlanner()
                val plan = planner.createDailyPlan(context)
                if (plan.tasks.isNotEmpty()) {
                    AiProactiveSignal(
                        type = ProactiveSignalType.GENERAL_INSIGHT,
                        title = "Morning Plan Ready",
                        message = plan.summary,
                        severity = AiPriority.LOW,
                        confidence = AiConfidence.HIGH,
                        evidence = "Triggered by morning schedule. Selected ${plan.tasks.size} tasks.",
                        fingerprint = "morning_plan_${System.currentTimeMillis() / 86400000}"
                    )
                } else null
            }
            "Task Completion Next Action" -> {
                val planner = AiPlanner()
                val next = planner.analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
                if (next != null) {
                    AiProactiveSignal(
                        type = ProactiveSignalType.NEXT_ACTION_OPPORTUNITY,
                        title = "Next Recommended Action",
                        message = next.message,
                        severity = AiPriority.LOW,
                        confidence = next.confidence,
                        evidence = "Triggered by task completion event.",
                        relatedTaskId = next.relatedTaskId,
                        fingerprint = "task_completed_next_${next.relatedTaskId ?: 0}"
                    )
                } else null
            }
            "Goal Progress Guard" -> {
                signals.find { it.type == ProactiveSignalType.GOAL_NEGLECT || it.type == ProactiveSignalType.NEGLECTED_GOAL || it.type == ProactiveSignalType.MISSING_NEXT_ACTION }
            }
            "Task Breakdown Assistant" -> {
                signals.find { it.type == ProactiveSignalType.CARRY_FORWARD_PATTERN || it.type == ProactiveSignalType.REPEATED_CARRY_FORWARD }
            }
            "Urgent Conflict Detector" -> {
                signals.find { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT }
            }
            else -> {
                // Evaluation for user-created custom rules
                if (checkCustomRuleConditions(rule, context, trigger)) {
                    AiProactiveSignal(
                        type = ProactiveSignalType.GENERAL_INSIGHT,
                        title = rule.name,
                        message = rule.description,
                        severity = AiPriority.MEDIUM,
                        confidence = AiConfidence.HIGH,
                        evidence = "Matched condition: ${rule.conditionExpression ?: "Default condition"}",
                        fingerprint = "user_rule_${rule.id}_${nowSec()}"
                    )
                } else null
            }
        }
    }

    private fun checkCustomRuleConditions(
        rule: AiAutomationRule,
        context: AiContext,
        trigger: AutomationTriggerType
    ): Boolean {
        val expr = rule.conditionExpression?.lowercase() ?: ""
        return when {
            expr.contains("workload") || expr.contains("overload") -> {
                context.incompleteTasks.size > context.adaptiveProfile.preferredDailyWorkload
            }
            expr.contains("carried") -> {
                context.carriedTasks >= 2
            }
            expr.contains("goal") -> {
                context.activeGoals.any { it.progress < 0.2f }
            }
            else -> true
        }
    }

    private fun updateRuleTriggered(rule: AiAutomationRule, fingerprint: String, reason: String) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule.copy(
                lastTriggeredAt = System.currentTimeMillis(),
                lastTriggeredFingerprint = fingerprint,
                lastRunReason = reason,
                runCount = rule.runCount + 1
            )
        }
    }

    private fun recordExecution(
        rule: AiAutomationRule,
        trigger: AutomationTriggerType,
        condition: String,
        evidence: String,
        success: Boolean
    ) {
        val log = AutomationExecutionRecord(
            ruleId = rule.id,
            ruleName = rule.name,
            triggerType = trigger,
            conditionMatched = condition,
            evidence = evidence,
            actionTaken = rule.description,
            success = success
        )
        executionLogs.add(log)
        // Keep last 50 logs max
        if (executionLogs.size > 50) {
            executionLogs.removeAt(0)
        }
    }

    fun addRule(rule: AiAutomationRule): Boolean {
        if (rules.any { it.name.equals(rule.name, ignoreCase = true) }) {
            return false // Duplicate rule name
        }
        rules.add(rule)
        NexoraLogger.d("AUTOMATION", "Added new rule: ${rule.name}")
        return true
    }

    fun deleteRule(idOrName: String): Boolean {
        val removed = rules.removeAll { it.id == idOrName || it.name.equals(idOrName, ignoreCase = true) }
        if (removed) {
            NexoraLogger.d("AUTOMATION", "Deleted rule: $idOrName")
        }
        return removed
    }

    fun toggleRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? {
        val index = rules.indexOfFirst { it.id == idOrName || it.name.contains(idOrName, ignoreCase = true) }
        if (index != -1) {
            val existing = rules[index]
            val newEnabled = enabled ?: !existing.enabled
            val updated = existing.copy(enabled = newEnabled)
            rules[index] = updated
            return updated
        }
        return null
    }

    fun updateRule(updatedRule: AiAutomationRule) {
        val index = rules.indexOfFirst { it.id == updatedRule.id }
        if (index != -1) {
            rules[index] = updatedRule
        }
    }

    fun getRules(): List<AiAutomationRule> = rules.toList()

    fun findRule(query: String): AiAutomationRule? {
        val q = query.lowercase().trim()
        return rules.find { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    fun explainLastRun(query: String? = null): String {
        val targetLog = if (!query.isNullOrBlank()) {
            executionLogs.find { it.ruleName.lowercase().contains(query.lowercase()) }
        } else {
            executionLogs.lastOrNull()
        }

        if (targetLog == null) {
            val recentRule = rules.filter { it.lastTriggeredAt > 0 }.maxByOrNull { it.lastTriggeredAt }
            return if (recentRule != null) {
                "The automation \"${recentRule.name}\" last ran because ${recentRule.lastRunReason ?: "its trigger condition was met"}."
            } else {
                "No automations have triggered recently."
            }
        }

        return "Automation \"${targetLog.ruleName}\" ran when triggered by ${targetLog.triggerType}. Condition: \"${targetLog.conditionMatched}\". Evidence: ${targetLog.evidence}"
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
}
