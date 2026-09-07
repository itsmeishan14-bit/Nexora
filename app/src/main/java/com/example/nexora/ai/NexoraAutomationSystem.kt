package com.example.nexora.ai

/**
 * Manages automation rules and handles triggering logic with cooldowns.
 */
class NexoraAutomationSystem {
    private val rules = mutableListOf<AiAutomationRule>()
    private val proactiveEngine = NexoraProactiveEngine()
    
    init {
        // Default core automation rules
        rules.add(AiAutomationRule(
            name = "Workload Manager",
            description = "Suggests workload reorganization when overload is detected.",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED
        ))
        rules.add(AiAutomationRule(
            name = "Goal Progress Guard",
            description = "Triggers when a goal shows no activity for several days.",
            triggerType = AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED
        ))
        rules.add(AiAutomationRule(
            name = "Task Breakdown Assistant",
            description = "Suggests breaking down tasks that are repeatedly carried forward.",
            triggerType = AutomationTriggerType.TASK_CARRIED_FORWARD
        ))
        rules.add(AiAutomationRule(
            name = "Urgent Conflict Detector",
            description = "Notifies when multiple urgent tasks are pending.",
            triggerType = AutomationTriggerType.TASK_CREATED
        ))
    }

    /**
     * Evaluates whether any automation rules should trigger based on a context change.
     * Returns a list of signals that passed the automation rules and cooldowns.
     */
    fun evaluateTriggers(trigger: AutomationTriggerType, context: AiContext): List<AiProactiveSignal> {
        val signals = proactiveEngine.detectSignals(context)
        val matchingRules = rules.filter { it.enabled && it.triggerType == trigger }
        
        val triggeredSignals = mutableListOf<AiProactiveSignal>()
        
        for (rule in matchingRules) {
            if (System.currentTimeMillis() - rule.lastTriggeredAt < rule.cooldownMillis) {
                continue
            }
            
            val signal = when (rule.name) {
                "Workload Manager" -> signals.find { it.type == ProactiveSignalType.OVERLOAD }
                "Goal Progress Guard" -> signals.find { it.type == ProactiveSignalType.NEGLECTED_GOAL || it.type == ProactiveSignalType.MISSING_NEXT_ACTION }
                "Task Breakdown Assistant" -> signals.find { it.type == ProactiveSignalType.REPEATED_CARRY_FORWARD }
                "Urgent Conflict Detector" -> signals.find { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT }
                else -> null
            }
            
            if (signal != null) {
                updateRuleTriggered(rule, signal.fingerprint)
                triggeredSignals.add(signal)
            }
        }
        
        return triggeredSignals
    }

    private fun updateRuleTriggered(rule: AiAutomationRule, fingerprint: String) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule.copy(
                lastTriggeredAt = System.currentTimeMillis(),
                lastTriggeredFingerprint = fingerprint
            )
        }
    }
    
    fun updateRule(updatedRule: AiAutomationRule) {
        val index = rules.indexOfFirst { it.id == updatedRule.id }
        if (index != -1) {
            rules[index] = updatedRule
        }
    }
    
    fun getRules(): List<AiAutomationRule> = rules
}
