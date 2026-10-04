package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.TaskPriority
import com.example.nexora.util.NexoraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    private val mutationMutex = Mutex()
    var initializationState: AutomationInitState = AutomationInitState.Uninitialized
        private set

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

    private val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var initJob: Job? = null

    init {
        if (repository == null) {
            synchronized(rulesLock) {
                rules.clear()
                rules.addAll(DEFAULT_RULES)
                _rulesFlow.value = rules.toList()
            }
            initializationState = AutomationInitState.Ready
        } else {
            initJob = persistenceScope.launch {
                loadInitialState()
            }
        }
    }

    /**
     * Awaits completion of initial asynchronous loading from repository.
     */
    suspend fun awaitInitialization() {
        initJob?.join()
    }

    /**
     * Cancels background persistence jobs.
     */
    fun cleanup() {
        persistenceScope.coroutineContext[Job]?.cancel()
    }

    /**
     * Asynchronously loads persistent state from the repository into memory.
     */
    suspend fun loadInitialState(): Boolean = withContext(Dispatchers.IO) {
        val repo = repository ?: run {
            synchronized(rulesLock) {
                if (rules.isEmpty()) {
                    rules.addAll(DEFAULT_RULES)
                    _rulesFlow.value = rules.toList()
                }
            }
            initializationState = AutomationInitState.Ready
            return@withContext true
        }

        mutationMutex.withLock {
            try {
                initializationState = AutomationInitState.Initializing
                val existing = repo.getAutomationRules()
                if (existing.isEmpty()) {
                    val recentLogs = repo.getRecentAutomationExecutions(1)
                    if (recentLogs.isEmpty()) {
                        for (defaultRule in DEFAULT_RULES) {
                            repo.insertAutomationRule(defaultRule)
                        }
                        val loaded = repo.getAutomationRules()
                        synchronized(rulesLock) {
                            rules.clear()
                            rules.addAll(if (loaded.isNotEmpty()) loaded else DEFAULT_RULES)
                            _rulesFlow.value = rules.toList()
                        }
                    } else {
                        synchronized(rulesLock) {
                            rules.clear()
                            _rulesFlow.value = emptyList()
                        }
                    }
                } else {
                    synchronized(rulesLock) {
                        rules.clear()
                        rules.addAll(existing)
                        _rulesFlow.value = rules.toList()
                    }
                }
                val recentLogs = repo.getRecentAutomationExecutions(50)
                synchronized(rulesLock) {
                    executionLogs.clear()
                    executionLogs.addAll(recentLogs.reversed())
                }
                initializationState = AutomationInitState.Ready
                true
            } catch (e: Exception) {
                NexoraLogger.e("AUTOMATION", "Failed to load rules from persistent repository", e)
                initializationState = AutomationInitState.Failed(e)
                false
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
     * Returns a list of proactive signals produced by automations.
     * Truthfully distinguishes proposed actions, executed actions, cooldown blocks, and disabled blocks.
     */
    suspend fun evaluateTriggers(trigger: AutomationTriggerType, context: AiContext): List<AiProactiveSignal> = withContext(Dispatchers.IO) {
        awaitInitialization()
        val signals = proactiveEngine.detectSignals(context)
        val allRulesForTrigger = synchronized(rulesLock) {
            rules.filter { it.triggerType == trigger }
        }

        val triggeredSignals = mutableListOf<AiProactiveSignal>()
        val now = System.currentTimeMillis()

        for (rule in allRulesForTrigger) {
            // Check disabled rule
            if (!rule.enabled) {
                val blockedRecord = AutomationExecutionRecord(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    timestamp = now,
                    triggerType = trigger,
                    conditionMatched = "Rule disabled",
                    evidence = "Rule is disabled and was not evaluated.",
                    actionTaken = "Execution blocked: rule is disabled.",
                    success = false,
                    stage = AutomationExecutionStage.BLOCKED_BY_DISABLED_RULE
                )
                recordExecution(blockedRecord)
                continue
            }

            // Condition evaluation
            val (conditionMatched, conditionEvidence) = evaluateRuleCondition(rule, signals, context, trigger)
            if (!conditionMatched) {
                if (conditionEvidence.startsWith("Unsupported") || conditionEvidence.startsWith("Malformed")) {
                    synchronized(rulesLock) {
                        val idx = rules.indexOfFirst { it.id == rule.id }
                        if (idx != -1) {
                            rules[idx] = rule.copy(lastRunReason = conditionEvidence)
                        }
                    }
                    val unsupportedRecord = AutomationExecutionRecord(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        timestamp = now,
                        triggerType = trigger,
                        conditionMatched = "Condition unsupported or malformed",
                        evidence = conditionEvidence,
                        actionTaken = "Condition evaluation failed safely: not executed.",
                        success = false,
                        stage = AutomationExecutionStage.ACTION_SKIPPED
                    )
                    recordExecution(unsupportedRecord)
                }
                continue
            }

            val signal = generateSignalForRule(rule, signals, context, trigger, conditionEvidence)
            if (signal == null) {
                continue
            }

            // Cooldown & Duplicate execution check
            val elapsed = now - rule.lastTriggeredAt
            val isWithinCooldown = elapsed < rule.cooldownMillis
            val isSameFingerprint = rule.lastTriggeredFingerprint != null && rule.lastTriggeredFingerprint == signal.fingerprint

            if (isWithinCooldown) {
                val blockedRecord = AutomationExecutionRecord(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    timestamp = now,
                    triggerType = trigger,
                    conditionMatched = signal.title,
                    evidence = "Blocked by cooldown: ${(rule.cooldownMillis - elapsed) / 1000}s remaining of ${rule.cooldownMillis / 1000}s cooldown.",
                    actionTaken = "Execution blocked: cooldown active.",
                    success = false,
                    stage = AutomationExecutionStage.BLOCKED_BY_COOLDOWN
                )
                recordExecution(blockedRecord)
                continue
            }

            // Execution allowed: propose action/signal
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
                actionTaken = "Signal proposed: ${signal.title}",
                success = true,
                stage = AutomationExecutionStage.ACTION_PROPOSED
            )

            var persisted = true
            if (repository != null) {
                try {
                    val ok = repository.updateAutomationRule(updatedRule)
                    if (ok) {
                        repository.insertAutomationExecution(log)
                        repository.trimAutomationExecutions(50)
                    }
                    persisted = ok
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist trigger execution for rule: ${rule.name}", e)
                    persisted = false
                }
            }

            if (!persisted) {
                NexoraLogger.w("AUTOMATION", "Skipping trigger emission due to persistence failure: ${rule.name}")
                continue
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

        triggeredSignals
    }

    internal suspend fun recordExecution(record: AutomationExecutionRecord) {
        var persisted = true
        if (repository != null) {
            try {
                repository.insertAutomationExecution(record)
                repository.trimAutomationExecutions(50)
            } catch (e: Exception) {
                NexoraLogger.e("AUTOMATION", "Failed to persist execution record: ${record.ruleName}", e)
                persisted = false
            }
        }
        if (persisted) {
            synchronized(rulesLock) {
                executionLogs.add(record)
                if (executionLogs.size > 50) {
                    executionLogs.removeAt(0)
                }
            }
        }
    }

    fun getRecentExecutions(limit: Int = 50): List<AutomationExecutionRecord> =
        synchronized(rulesLock) { executionLogs.takeLast(limit).reversed() }

    private fun evaluateRuleCondition(
        rule: AiAutomationRule,
        signals: List<AiProactiveSignal>,
        context: AiContext,
        trigger: AutomationTriggerType
    ): Pair<Boolean, String> {
        return when (rule.name) {
            "Workload Manager" -> {
                val match = signals.find { it.type == ProactiveSignalType.WORKLOAD_RISK || it.type == ProactiveSignalType.OVERLOAD }
                Pair(match != null, match?.evidence ?: "Workload condition not met")
            }
            "Morning Plan Assistant" -> {
                val isMorning = trigger == AutomationTriggerType.DAY_STARTED
                Pair(isMorning, if (isMorning) "Morning schedule trigger" else "Not morning")
            }
            "Task Completion Next Action" -> {
                val isCompleted = trigger == AutomationTriggerType.TASK_COMPLETED
                Pair(isCompleted, if (isCompleted) "Task completed event" else "Not task completion")
            }
            "Goal Progress Guard" -> {
                val match = signals.find { it.type == ProactiveSignalType.GOAL_NEGLECT || it.type == ProactiveSignalType.NEGLECTED_GOAL || it.type == ProactiveSignalType.MISSING_NEXT_ACTION }
                Pair(match != null, match?.evidence ?: "No neglected goals")
            }
            "Task Breakdown Assistant" -> {
                val match = signals.find { it.type == ProactiveSignalType.CARRY_FORWARD_PATTERN || it.type == ProactiveSignalType.REPEATED_CARRY_FORWARD }
                Pair(match != null, match?.evidence ?: "No carry-forward pattern")
            }
            "Urgent Conflict Detector" -> {
                val match = signals.find { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT }
                Pair(match != null, match?.evidence ?: "No urgent conflicts")
            }
            else -> {
                evaluateCustomRuleCondition(rule, context, trigger)
            }
        }
    }

    fun evaluateCustomRuleCondition(
        rule: AiAutomationRule,
        context: AiContext,
        trigger: AutomationTriggerType
    ): Pair<Boolean, String> {
        val raw = rule.conditionExpression?.trim() ?: ""
        if (raw.isBlank()) {
            return Pair(false, "Blank condition expression does not match")
        }

        val expr = raw.lowercase()

        // Check for malformed syntax
        if (raw.matches(Regex("^[!@#\\$%\\^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/? ]+$"))) {
            return Pair(false, "Malformed condition syntax: \"$raw\"")
        }

        return when {
            expr.contains("workload") || expr.contains("overload") -> {
                val overloaded = (context.incompleteTasks.size > context.adaptiveProfile.preferredDailyWorkload) ||
                                 (context.tasksPlannedToday > context.adaptiveProfile.preferredDailyWorkload)
                Pair(overloaded, if (overloaded) "Incomplete tasks (${context.incompleteTasks.size}) or planned tasks (${context.tasksPlannedToday}) exceed capacity (${context.adaptiveProfile.preferredDailyWorkload})" else "Workload within normal limits")
            }
            expr.contains("carried") || expr.contains("carry") -> {
                val hasCarried = context.carriedTasks >= 2
                Pair(hasCarried, if (hasCarried) "Carried tasks (${context.carriedTasks}) >= 2" else "Fewer than 2 carried tasks")
            }
            expr.contains("neglected") || expr.contains("stalled") || expr.contains("goal") -> {
                val neglected = context.activeGoals.any { it.progress < 0.2f }
                Pair(neglected, if (neglected) "Active goals with < 20% progress detected" else "All active goals making progress")
            }
            expr.contains("urgent") || expr.contains("conflict") -> {
                val conflict = context.incompleteTasks.count { it.priority == TaskPriority.URGENT } >= 2
                Pair(conflict, if (conflict) "Multiple urgent tasks pending" else "No urgent priority conflicts")
            }
            expr.contains("morning") || expr.contains("daily plan") || expr.contains("study") -> {
                val isMorning = trigger == AutomationTriggerType.DAY_STARTED
                Pair(isMorning, if (isMorning) "Morning schedule trigger" else "Trigger is not DAY_STARTED")
            }
            expr.contains("productivity") || expr.contains("pattern") || expr.contains("trend") -> {
                val pattern = context.personalContext.productivityTrend == ProductivityTrend.DECLINING || context.carriedTasks > 0
                Pair(pattern, if (pattern) "Productivity pattern detected" else "Productivity is stable")
            }
            else -> {
                // FAIL SAFELY: Unknown or unsupported expressions return false and record unsupported
                Pair(false, "Unsupported condition expression: \"$raw\"")
            }
        }
    }

    private fun generateSignalForRule(
        rule: AiAutomationRule,
        signals: List<AiProactiveSignal>,
        context: AiContext,
        trigger: AutomationTriggerType,
        conditionEvidence: String
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
                AiProactiveSignal(
                    type = ProactiveSignalType.GENERAL_INSIGHT,
                    title = rule.name,
                    message = rule.description,
                    severity = AiPriority.MEDIUM,
                    confidence = AiConfidence.HIGH,
                    evidence = conditionEvidence,
                    fingerprint = "user_rule_${rule.id}_${nowSec()}"
                )
            }
        }
    }

    suspend fun addRule(rule: AiAutomationRule): Boolean = withContext(Dispatchers.IO) {
        awaitInitialization()
        mutationMutex.withLock {
            val exists = synchronized(rulesLock) {
                rules.any { it.name.equals(rule.name, ignoreCase = true) || it.id.equals(rule.id, ignoreCase = true) }
            }
            if (exists) {
                return@withContext false
            }
            if (repository != null) {
                val inserted = try {
                    repository.insertAutomationRule(rule)
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist new rule: ${rule.name}", e)
                    false
                }
                if (!inserted) return@withContext false
            }
            synchronized(rulesLock) {
                if (rules.none { it.name.equals(rule.name, ignoreCase = true) || it.id.equals(rule.id, ignoreCase = true) }) {
                    rules.add(rule)
                    _rulesFlow.value = rules.toList()
                }
            }
            NexoraLogger.d("AUTOMATION", "Added new rule: ${rule.name}")
            true
        }
    }

    suspend fun deleteRule(idOrName: String): Boolean = withContext(Dispatchers.IO) {
        awaitInitialization()
        mutationMutex.withLock {
            val clean = idOrName.trim().removeSuffix(".")
            val target = synchronized(rulesLock) {
                rules.find { 
                    it.id.equals(clean, ignoreCase = true) || 
                    it.name.equals(clean, ignoreCase = true) ||
                    (clean.length >= 3 && it.name.contains(clean, ignoreCase = true))
                }
            } ?: return@withContext false

            if (repository != null) {
                val deleted = try {
                    repository.deleteAutomationRule(target.id)
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to delete persistent rule: $idOrName", e)
                    false
                }
                if (!deleted) return@withContext false
            }

            val removed = synchronized(rulesLock) {
                val wasRemoved = rules.removeAll { it.id == target.id }
                if (wasRemoved) {
                    _rulesFlow.value = rules.toList()
                    NexoraLogger.d("AUTOMATION", "Deleted rule: ${target.name}")
                }
                wasRemoved
            }
            removed
        }
    }

    suspend fun toggleRule(idOrName: String, enabled: Boolean? = null): AiAutomationRule? = withContext(Dispatchers.IO) {
        awaitInitialization()
        mutationMutex.withLock {
            val clean = idOrName.trim().removeSuffix(".")
            val target = synchronized(rulesLock) {
                rules.find { 
                    it.id.equals(clean, ignoreCase = true) || 
                    it.name.equals(clean, ignoreCase = true) ||
                    (clean.length >= 3 && it.name.contains(clean, ignoreCase = true))
                }
            } ?: return@withContext null

            val newEnabled = enabled ?: !target.enabled
            val updated = target.copy(enabled = newEnabled)

            if (repository != null) {
                val persisted = try {
                    repository.updateAutomationRule(updated)
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist toggle for rule: ${target.name}", e)
                    false
                }
                if (!persisted) return@withContext null
            }

            synchronized(rulesLock) {
                val index = rules.indexOfFirst { it.id == target.id }
                if (index != -1) {
                    rules[index] = updated
                    _rulesFlow.value = rules.toList()
                }
            }
            updated
        }
    }

    suspend fun updateRule(updatedRule: AiAutomationRule): Boolean = withContext(Dispatchers.IO) {
        awaitInitialization()
        mutationMutex.withLock {
            val exists = synchronized(rulesLock) {
                rules.any { it.id == updatedRule.id }
            }
            if (!exists) return@withContext false

            if (repository != null) {
                val persisted = try {
                    repository.updateAutomationRule(updatedRule)
                } catch (e: Exception) {
                    NexoraLogger.e("AUTOMATION", "Failed to persist updated rule: ${updatedRule.name}", e)
                    false
                }
                if (!persisted) return@withContext false
            }

            synchronized(rulesLock) {
                val index = rules.indexOfFirst { it.id == updatedRule.id }
                if (index != -1) {
                    rules[index] = updatedRule
                    _rulesFlow.value = rules.toList()
                }
            }
            true
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
        val logs = synchronized(rulesLock) { executionLogs.toList() }

        val targetLog = if (!query.isNullOrBlank()) {
            logs.find { it.ruleName.lowercase().contains(query.lowercase()) }
        } else {
            logs.lastOrNull()
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

        return when (targetLog.stage) {
            AutomationExecutionStage.ACTION_PROPOSED -> {
                "Automation \"${targetLog.ruleName}\" proposed an action when triggered by ${targetLog.triggerType}. Condition: \"${targetLog.conditionMatched}\". Evidence: ${targetLog.evidence}"
            }
            AutomationExecutionStage.ACTION_EXECUTED, AutomationExecutionStage.ACTION_SUCCEEDED -> {
                "Automation \"${targetLog.ruleName}\" executed when triggered by ${targetLog.triggerType}. Condition: \"${targetLog.conditionMatched}\". Evidence: ${targetLog.evidence}"
            }
            AutomationExecutionStage.BLOCKED_BY_COOLDOWN -> {
                "Automation \"${targetLog.ruleName}\" was blocked by cooldown. Reason: ${targetLog.evidence}"
            }
            AutomationExecutionStage.BLOCKED_BY_DISABLED_RULE -> {
                "Automation \"${targetLog.ruleName}\" did not run because the rule is disabled."
            }
            AutomationExecutionStage.ACTION_SKIPPED -> {
                "Automation \"${targetLog.ruleName}\" skipped execution. Reason: ${targetLog.evidence}"
            }
            AutomationExecutionStage.ACTION_FAILED -> {
                "Automation \"${targetLog.ruleName}\" failed during execution. Reason: ${targetLog.evidence}"
            }
            else -> {
                "Automation \"${targetLog.ruleName}\" triggered by ${targetLog.triggerType}. Condition: \"${targetLog.conditionMatched}\". Evidence: ${targetLog.evidence}"
            }
        }
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
}

sealed class AutomationInitState {
    object Uninitialized : AutomationInitState()
    object Initializing : AutomationInitState()
    object Ready : AutomationInitState()
    data class Failed(val cause: Throwable) : AutomationInitState()
}
