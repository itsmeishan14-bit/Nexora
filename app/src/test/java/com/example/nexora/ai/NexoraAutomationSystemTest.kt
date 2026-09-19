package com.example.nexora.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoraAutomationSystemTest {

    private val automationSystem = NexoraAutomationSystem()

    @Test
    fun `test evaluate triggers fires workload manager`() {
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 5,
                confidence = AdaptiveConfidence.HIGH
            )
        )
        
        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.WORKLOAD_RISK })
    }

    @Test
    fun `test automation cooldown prevents repeated firing`() {
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )
        
        // First trigger
        val signals1 = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(1, signals1.size)
        
        // Immediate second trigger
        val signals2 = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        // Should be empty due to default 1 hour cooldown
        assertEquals(0, signals2.size)
    }

    @Test
    fun `test automation fires task breakdown assistant on carry forward`() {
        val context = AiContext(
            carriedTasks = 5,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )
        
        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_CARRIED_FORWARD, context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.CARRY_FORWARD_PATTERN })
    }

    @Test
    fun `test automation ignores disabled rules`() {
        val system = NexoraAutomationSystem()
        val rule = system.getRules().first()
        
        system.updateRule(rule.copy(enabled = false))
        
        val context = AiContext(tasksPlannedToday = 20, adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 2, confidence = AdaptiveConfidence.HIGH))
        
        val signals = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(0, signals.size)
    }
}
