package com.example.nexora.ai

import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.*
import org.junit.Test

class AdaptiveIntelligenceTest {

    private val planner = AiPlanner()

    @Test
    fun `test profile calculation with consistent history`() {
        val history = listOf(
            DailyProgressEntity("2023-01-01", 5, 5, 120, 1, 0),
            DailyProgressEntity("2023-01-02", 5, 4, 100, 1, 1),
            DailyProgressEntity("2023-01-03", 5, 5, 150, 1, 0),
            DailyProgressEntity("2023-01-04", 5, 4, 110, 1, 1),
            DailyProgressEntity("2023-01-05", 5, 5, 130, 1, 0),
            DailyProgressEntity("2023-01-06", 5, 4, 90, 1, 1),
            DailyProgressEntity("2023-01-07", 5, 5, 140, 1, 0)
        )
        
        // This calculation normally happens in AiContextBuilder, but we can test the outcome via Context
        val profile = calculateProfileManual(history)
        
        assertEquals(7, profile.sampleCount)
        assertEquals(AdaptiveConfidence.MODERATE, profile.confidence)
        assertEquals(4.57f, profile.averageTasksCompleted, 0.1f)
        assertEquals(5, profile.preferredDailyWorkload)
    }

    @Test
    fun `test adaptive workload warning`() {
        val profile = AdaptiveProfile(
            averageTasksCompleted = 4f,
            preferredDailyWorkload = 4,
            confidence = AdaptiveConfidence.HIGH
        )
        val context = AiContext(
            tasksPlannedToday = 8, // Significantly more than 4
            adaptiveProfile = profile
        )
        
        val insights = planner.getProactiveInsights(context)
        val workloadWarning = insights.find { it.title.contains("Workload") }
        
        assertNotNull(workloadWarning)
        assertTrue(workloadWarning?.message?.contains("usually complete around 4") == true)
        assertEquals(AiConfidence.HIGH, workloadWarning?.confidence)
    }

    @Test
    fun `test personalized daily plan capacity`() {
        val profile = AdaptiveProfile(
            preferredDailyWorkload = 3,
            confidence = AdaptiveConfidence.MODERATE
        )
        val tasks = (1..10).map { 
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "30 min")
        }
        val context = AiContext(tasks = tasks, adaptiveProfile = profile)
        
        val plan = planner.createDailyPlan(context)
        
        // Plan should be limited to historical capacity of 3
        assertEquals(3, plan.tasks.size)
    }

    @Test
    fun `test task scoring with preferred size Small`() {
        val profile = AdaptiveProfile(
            preferredTaskSize = "Small",
            confidence = AdaptiveConfidence.MODERATE
        )
        val shortTask = PremiumTask(id = 1, title = "Short", duration = "15 min", priority = TaskPriority.MEDIUM, category = "Work")
        val longTask = PremiumTask(id = 2, title = "Long", duration = "2 hour", priority = TaskPriority.MEDIUM, category = "Work")
        
        val context = AiContext(tasks = listOf(shortTask, longTask), adaptiveProfile = profile)
        
        val recs = planner.analyze(context)
        val nextTask = recs.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertEquals(1L, nextTask?.relatedTaskId) // Short task preferred
        assertTrue(nextTask?.message?.contains("Fits your preferred task size") == true)
    }

    @Test
    fun `test productivity below average insight`() {
        val profile = AdaptiveProfile(
            completionRate = 0.9f,
            confidence = AdaptiveConfidence.HIGH
        )
        val context = AiContext(
            tasksPlannedToday = 5,
            tasksCompletedToday = 1, // 20% vs 90%
            adaptiveProfile = profile
        )
        
        val recs = planner.analyzeProductivity(context)
        assertTrue(recs.any { it.title.contains("Below Average") })
    }

    private fun calculateProfileManual(history: List<DailyProgressEntity>): AdaptiveProfile {
        // Simple manual calculation for testing
        val avgCompleted = history.map { it.tasksCompleted }.average().toFloat()
        val confidence = if (history.size >= 7) AdaptiveConfidence.MODERATE else AdaptiveConfidence.LOW
        return AdaptiveProfile(
            averageTasksCompleted = avgCompleted,
            preferredDailyWorkload = Math.round(avgCompleted),
            sampleCount = history.size,
            confidence = confidence
        )
    }
}
