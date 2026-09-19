package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoraProactiveEngineTest {

    private val engine = NexoraProactiveEngine()

    @Test
    fun `test detect workload risk signal`() {
        val context = AiContext(
            tasksPlannedToday = 10,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 3,
                confidence = AdaptiveConfidence.HIGH
            )
        )
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.WORKLOAD_RISK })
        val signal = signals.find { it.type == ProactiveSignalType.WORKLOAD_RISK }!!
        assertEquals(AiPriority.CRITICAL, signal.severity)
        assertEquals(AiConfidence.HIGH, signal.confidence)
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
    fun `test detect goal neglect`() {
        val context = AiContext(
            goals = listOf(com.example.nexora.uii.NexoraGoal(id = 1, title = "Java", category = "Learn", progress = 0.1f, targetDate = "")),
            personalContext = AiPersonalContext(
                goalHealth = listOf(GoalHealthAssessment(1L, "Java", GoalHealthState.AT_RISK, 0.1f, ActivityLevel.NONE, 7, "Stagnating"))
            )
        )
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.GOAL_NEGLECT })
        assertEquals(AiPriority.HIGH, signals.find { it.type == ProactiveSignalType.GOAL_NEGLECT }?.severity)
    }

    @Test
    fun `test detect low completion pace`() {
        val context = AiContext(
            tasksPlannedToday = 10,
            tasksCompletedToday = 1,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )
        
        val signals = engine.detectSignals(context)
        
        assertTrue(signals.any { it.type == ProactiveSignalType.LOW_COMPLETION_RATE })
    }

    @Test
    fun `test signal ranking and limit`() {
        // Create context that would trigger many signals
        val urgentTask = PremiumTask(id = 1, title = "Urgent", priority = TaskPriority.URGENT, category = "Work", duration = "1h")
        val context = AiContext(
            tasks = listOf(urgentTask),
            tasksPlannedToday = 4, // 2x capacity, but URGENT conflict is more specific
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 2, confidence = AdaptiveConfidence.HIGH)
        )
        
        val signals = engine.detectSignals(context)
        
        // Should return no more than 3 signals
        assertTrue(signals.size <= 3)
        // Highest priority (URGENT conflict) should be first
        assertEquals(ProactiveSignalType.HIGH_PRIORITY_CONFLICT, signals.first().type)
    }

    @Test
    fun `test confidence gate`() {
        val context = AiContext(
            tasksPlannedToday = 10,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.LOW)
        )
        
        val signals = engine.detectSignals(context)
        
        // Workload risk should be filtered out if confidence is LOW (less than MEDIUM)
        assertTrue(signals.none { it.type == ProactiveSignalType.WORKLOAD_RISK })
    }
}
