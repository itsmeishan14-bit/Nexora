package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoraProactiveEngineTest {

    private val engine = NexoraProactiveEngine()

    @Test
    fun `test detect overload signal`() {
        val context = AiContext(
            tasksPlannedToday = 10,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 3,
                confidence = AdaptiveConfidence.HIGH
            )
        )
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.OVERLOAD })
        val overload = signals.find { it.type == ProactiveSignalType.OVERLOAD }!!
        assertEquals(AiPriority.HIGH, overload.severity)
        assertEquals(AiConfidence.HIGH, overload.confidence)
    }

    @Test
    fun `test detect urgent task conflict`() {
        val task = PremiumTask(
            id = 1,
            title = "CRITICAL FIX",
            priority = TaskPriority.URGENT,
            category = "Work",
            duration = "1h"
        )
        val context = AiContext(tasks = listOf(task))
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT })
        assertEquals(AiPriority.CRITICAL, signals.find { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT }?.severity)
    }

    @Test
    fun `test detect balanced workload`() {
        val context = AiContext(
            tasksPlannedToday = 3,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 4,
                confidence = AdaptiveConfidence.HIGH
            )
        )
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.WORKLOAD_BALANCED })
        assertEquals(AiPriority.LOW, signals.find { it.type == ProactiveSignalType.WORKLOAD_BALANCED }?.severity)
    }

    @Test
    fun `test signal deduplication and limit`() {
        // Create context that would trigger many signals
        val task = PremiumTask(id = 1, title = "Urgent", priority = TaskPriority.URGENT, category = "Work", duration = "1h")
        val context = AiContext(
            tasks = listOf(task),
            tasksPlannedToday = 10,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 2, confidence = AdaptiveConfidence.HIGH)
        )
        
        val signals = engine.detectSignals(context)
        
        // Should return no more than 3 signals
        assertTrue(signals.size <= 3)
        // Highest priority (URGENT conflict) should be first
        assertEquals(ProactiveSignalType.HIGH_PRIORITY_CONFLICT, signals.first().type)
    }
}
