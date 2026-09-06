package com.example.nexora.ai

import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPersonalContextTest {

    private val builder = AiPersonalContextBuilder()

    @Test
    fun `test build with high workload`() {
        val tasks = listOf(
            createTask(1, "Task 1", duration = "1 hour"),
            createTask(2, "Task 2", duration = "2 hours"),
            createTask(3, "Task 3", duration = "1 hour"),
            createTask(4, "Task 4", duration = "2 hours"),
            createTask(5, "Task 5", duration = "1 hour"),
            createTask(6, "Task 6", duration = "2 hours")
        )
        val profile = AdaptiveProfile(preferredDailyWorkload = 3)
        val today = DailyProgressEntity("2023-10-27", tasksPlanned = 6, tasksCompleted = 0)

        val context = builder.build(tasks, emptyList(), today, emptyList(), profile, AiMemory())

        assertEquals(WorkloadState.VERY_HIGH, context.workload.state)
        assertTrue(context.risks.any { it.type == RiskType.OVERLOAD })
    }

    @Test
    fun `test build with neglected goal`() {
        val goals = listOf(
            NexoraGoal(1, "Goal 1", "Work", "2023-12-31", 0.2f)
        )
        val tasks = emptyList<PremiumTask>()
        val profile = AdaptiveProfile(preferredDailyWorkload = 5)

        val context = builder.build(tasks, goals, null, emptyList(), profile, AiMemory())

        val goalHealth = context.goalHealth.find { it.goalId == 1L }
        assertEquals(GoalHealthState.AT_RISK, goalHealth?.state)
        assertTrue(context.risks.any { it.type == RiskType.NEGLECTED_GOAL })
    }

    @Test
    fun `test build with improving trend`() {
        val history = listOf(
            DailyProgressEntity("2023-10-27", tasksPlanned = 5, tasksCompleted = 5),
            DailyProgressEntity("2023-10-26", tasksPlanned = 5, tasksCompleted = 5),
            DailyProgressEntity("2023-10-25", tasksPlanned = 5, tasksCompleted = 5),
            DailyProgressEntity("2023-10-24", tasksPlanned = 5, tasksCompleted = 1),
            DailyProgressEntity("2023-10-23", tasksPlanned = 5, tasksCompleted = 1),
            DailyProgressEntity("2023-10-22", tasksPlanned = 5, tasksCompleted = 1)
        )
        val profile = AdaptiveProfile(preferredDailyWorkload = 3)

        val context = builder.build(emptyList(), emptyList(), null, history, profile, AiMemory())

        assertEquals(ProductivityTrend.IMPROVING, context.productivityTrend)
    }

    @Test
    fun `test build detects quick wins`() {
        val tasks = listOf(
            createTask(1, "Quick Task", duration = "10 min")
        )
        val profile = AdaptiveProfile(preferredDailyWorkload = 5)

        val context = builder.build(tasks, emptyList(), null, emptyList(), profile, AiMemory())

        assertTrue(context.opportunities.any { it.type == OpportunityType.QUICK_WIN })
    }

    private fun createTask(id: Long, title: String, completed: Boolean = false, duration: String = "30 min"): PremiumTask {
        return PremiumTask(
            id = id,
            title = title,
            completed = completed,
            duration = duration,
            priority = TaskPriority.MEDIUM,
            category = "Personal"
        )
    }
}
