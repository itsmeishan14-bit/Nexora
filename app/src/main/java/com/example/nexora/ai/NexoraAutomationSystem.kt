package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.util.NexoraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Manages automation rules, dynamic condition evaluation, and execution history logging.
 * Backed by persistent Room storage through [NexoraRepository].
 */
class NexoraAutomationSystem(
    private val repository: NexoraRepository? = null
) {
    private val rulesLock = Any()
    private val rules = mutableListOf<AiAutomationRule>()
    private val executionLogs = mutableListOf<AutomationExecutionRecord>()
    private val proactiveEngine = NexoraProactiveEngine()

    private val _rulesFlow = MutableStateFlow<List<AiAutomationRule>>(emptyList())
    val rulesFlow: StateFlow<List<AiAutomationRule>> = _rulesFlow.asStateFlow()

    companion object {
        val DEFAULT_RULES = listOf(
            AiAutomationRule(
                name = "Workload Manager",
                description = "Suggests workload reorganization when overload is detected.",
                triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
                cooldownMillis = 3600000 // 1 hour
            ),
            AiAutomationRule(
                name = "Morning Plan Assistant",
                description = "Prepares your daily plan automatically every morning.",
                triggerType = AutomationTriggerType.DAY_STARTED,
                cooldownMillis = 72000000 // 20 hours
            ),
            AiAutomationRule(
                name = "Task Completion Next Action",
                description = "Recommends your next best task right after you finish a task.",
                triggerType = AutomationTriggerType.TASK_COMPLETED,
                cooldownMillis = 10000 // 10 seconds
            ),
            AiAutomationRule(
                name = "Goal Progress Guard",
                description = "Triggers when a goal shows no activity for several days.",
                triggerType = AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED,
                cooldownMillis = 86400000 // 24 hours
            ),
            AiAutomationRule(
                name = "Task Breakdown Assistant",
                description = "Suggests breaking down tasks that are repeatedly carried forward.",
                triggerType = AutomationTriggerType.TASK_CARRIED_FORWARD,
                cooldownMillis = 14400000 // 4 hours
            ),
            AiAutomationRule(
                name = "Urgent Conflict Detector",
                description = "Notifies when multiple urgent tasks are pending.",
                triggerType = AutomationTriggerType.TASK_CREATED,
                cooldownMillis = 1800000 // 30 minutes
            )
        )
    }

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        synchronized(rulesLock) {
            if (repository != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        val existing = repository.getAutomationRules()
                        if (existing.isEmpty()) {
                            // Safe initial setup: insert default core rules once
                            for (defaultRule in DEFAULT_RULES) {
                                repository.insertAutomationRule(defaultRule)
                            }
                            rules.clear()
                            rules.addAll(repository.getAutomationRules())
                        } else {
                            // Load existing persisted rules (preserves user automations, custom state, runCounts, and cooldowns)
                            rules.clear()
                            rules.addAll(existing)
                        }
                        val recentLogs = repository.getRecentAutomationExecutions(50)
                        executionLogs.clear()
                        executionLogs.addAll(recentLogs.reversed())
                        _rulesFlow.value = rules.toList()
                    }
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to load rules from persistent repository, falling back to defaults", e)
                    rules.clear()
                    rules.addAll(DEFAULT_RULES)
                    _rulesFlow.value = rules.toList()
                }
            } else {
                rules.clear()
                rules.addAll(DEFAULT_RULES)
                _rulesFlow.value = rules.toList()
            }
        }
    }

    /**
     * Observes automation rules reactively via Flow.
     * Backed directly by Room if repository is provided.
     */
    fun observeRules(): Flow<List<AiAutomationRule>> {
        return repository?.observeAutomationRules() ?: _rulesFlow
    }

    /**
     * Evaluates whether any automation rules should trigger based on a context change.
     * Returns a list of proactive signals or responses produced by automations.
     * Successfully triggered rules and executions are atomically persisted.
     */
    fun evaluateTriggers(trigger: AutomationTriggerType, context: AiContext): List<AiProactiveSignal> {
        val signals = proactiveEngine.detectSignals(context)
        val matchingRules = synchronized(rulesLock) {
            rules.filter { it.enabled && it.triggerType == trigger }
        }
        
        val triggeredSignals = mutableListOf<AiProactiveSignal>()
        val now = System.currentTimeMillis()

        for (rule in matchingRules) {
            if (now - rule.lastTriggeredAt < rule.cooldownMillis) {
                continue
            }

            val signal = evaluateRuleSignal(rule, signals, context, trigger)

            if (signal != null) {
                val updatedRule = rule.copy(
                    lastTriggeredAt = now,
                    lastTriggeredFingerprint = signal.fingerprint,
                    lastRunReason = signal.evidence,
                    runCount = rule.runCount + 1
                )

                val log = AutomationExecutionRecord(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    timestamp = now,
                    triggerType = trigger,
                    conditionMatched = signal.title,
                    evidence = signal.evidence,
                    actionTaken = rule.description,
                    success = true
                )

                if (repository != null) {
                    val persisted = try {
                        runBlocking(Dispatchers.IO) {
                            val ok = repository.updateAutomationRule(updatedRule)
                            if (ok) {
                                repository.insertAutomationExecution(log)
                                repository.trimAutomationExecutions(50)
                            }
                            ok
                        }
                    } catch (e: Exception) {
                        NexoraLogger.e("AUTOMATION", "Failed to persist trigger execution for rule: ${rule.name}", e)
                        false
                    }

                    if (!persisted) {
                        NexoraLogger.w("AUTOMATION", "Skipping trigger emission due to persistence failure: ${rule.name}")
                        continue
                    }
                }

                synchronized(rulesLock) {
                    val index = rules.indexOfFirst { it.id == rule.id }
                    if (index != -1) {
                        rules[index] = updatedRule
                    }
                    executionLogs.add(log)
                    if (executionLogs.size > 50) {
                        executionLogs.removeAt(0)
                    }
                    _rulesFlow.value = rules.toList()
                }

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

    fun addRule(rule: AiAutomationRule): Boolean {
        synchronized(rulesLock) {
            if (rules.any { it.name.equals(rule.name, ignoreCase = true) }) {
                return false // Duplicate rule name
            }
            if (repository != null) {
                val inserted = try {
                    runBlocking(Dispatchers.IO) {
                        repository.insertAutomationRule(rule)
                    }
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist new rule: ${rule.name}", e)
                    false
                }
                if (!inserted) return false
            }
            rules.add(rule)
            _rulesFlow.value = rules.toList()
            NexoraLogger.d("AUTOMATION", "Added new rule: ${rule.name}")
            return true
        }
    }

    fun deleteRule(idOrName: String): Boolean {
        synchronized(rulesLock) {
            val clean = idOrName.trim().removeSuffix(".")
            val target = rules.find { 
                it.id.equals(clean, ignoreCase = true) || 
                it.name.equals(clean, ignoreCase = true) ||
                (clean.length >= 3 && it.name.contains(clean, ignoreCase = true))
            } ?: return false

            if (repository != null) {
                val deleted = try {
                    runBlocking(Dispatchers.IO) {
                        repository.deleteAutomationRule(target.id)
                    }
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to delete persistent rule: $idOrName", e)
                    false
                }
                if (!deleted) return false
            }

            val removed = rules.removeAll { it.id == target.id }
            if (removed) {
                _rulesFlow.value = rules.toList()
                NexoraLogger.d("AUTOMATION", "Deleted rule: ${target.name}")
            }
            return removed
        }
    }

    fun toggleRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? {
        synchronized(rulesLock) {
            val clean = idOrName.trim().removeSuffix(".")
            val index = rules.indexOfFirst { 
                it.id.equals(clean, ignoreCase = true) || 
                it.name.equals(clean, ignoreCase = true) ||
                (clean.length >= 3 && it.name.contains(clean, ignoreCase = true))
            }
            if (index == -1) return null

            val existing = rules[index]
            val newEnabled = enabled ?: !existing.enabled
            val updated = existing.copy(enabled = newEnabled)

            if (repository != null) {
                val persisted = try {
                    runBlocking(Dispatchers.IO) {
                        repository.updateAutomationRule(updated)
                    }
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist toggle for rule: ${existing.name}", e)
                    false
                }
                if (!persisted) return null
            }

            rules[index] = updated
            _rulesFlow.value = rules.toList()
            return updated
        }
    }

    fun updateRule(updatedRule: AiAutomationRule) {
        synchronized(rulesLock) {
            val index = rules.indexOfFirst { it.id == updatedRule.id }
            if (index != -1) {
                if (repository != null) {
                    try {
                        runBlocking(Dispatchers.IO) {
                            repository.updateAutomationRule(updatedRule)
                        }
                    } catch (e: Exception) {
                        NexoraLogger.e("AUTOMATION", "Failed to persist updated rule: ${updatedRule.name}", e)
                    }
                }
                rules[index] = updatedRule
                _rulesFlow.value = rules.toList()
            }
        }
    }

    fun getRules(): List<AiAutomationRule> = synchronized(rulesLock) { rules.toList() }

    fun findRule(query: String): AiAutomationRule? {
        val q = query.lowercase().trim()
        val currentRules = getRules()
        val direct = currentRules.find { 
            it.id.equals(q, ignoreCase = true) ||
            it.name.equals(q, ignoreCase = true) ||
            it.name.lowercase().contains(q) || 
            q.contains(it.name.lowercase()) ||
            it.description.lowercase().contains(q) 
        }
        if (direct != null) return direct

        val words = q.split(" ", "_", "-", ".").filter { 
            it.length > 2 && it !in listOf("the", "my", "rule", "automation", "turn", "disable", "enable", "delete", "remove") 
        }
        return currentRules.find { rule ->
            val ruleWords = rule.name.lowercase().split(" ", "_", "-")
            words.any { w -> ruleWords.any { rw -> rw.contains(w) || w.contains(rw) } }
        }
    }

    fun explainLastRun(query: String? = null): String {
        val logs = if (repository != null) {
            try {
                runBlocking(Dispatchers.IO) {
                    repository.getRecentAutomationExecutions(50)
                }
            } catch (e: Exception) {
                synchronized(rulesLock) { executionLogs.toList() }
            }
        } else {
            synchronized(rulesLock) { executionLogs.toList() }
        }

        val targetLog = if (!query.isNullOrBlank()) {
            logs.find { it.ruleName.lowercase().contains(query.lowercase()) }
        } else {
            logs.firstOrNull()
        }

        if (targetLog == null) {
            val currentRules = getRules()
            val recentRule = currentRules.filter { it.lastTriggeredAt > 0 }.maxByOrNull { it.lastTriggeredAt }
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
