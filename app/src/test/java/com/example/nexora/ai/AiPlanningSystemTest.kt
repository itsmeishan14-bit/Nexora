package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AiPlanningSystemTest {

    private val planner = AiPlanner()

    @Test
    fun `TEST A - ONE TASK`() {
        val task = PremiumTask(id = 1, title = "Only task", category = "Work", duration = "30 min", priority = TaskPriority.MEDIUM)
        val context = AiContext(tasks = listOf(task))
        
        val recommendations = planner.analyze(context)
        val nextTask = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertNotNull(nextTask)
        assertEquals(1L, nextTask?.relatedTaskId)
        assertTrue(nextTask?.message?.contains("Only task") == true)
    }

    @Test
    fun `TEST B - COMPLETED TASK`() {
        val task1 = PremiumTask(id = 1, title = "Completed", category = "Work", duration = "30 min", completed = true)
        val task2 = PremiumTask(id = 2, title = "Incomplete", category = "Work", duration = "30 min", completed = false)
        val context = AiContext(tasks = listOf(task1, task2))
        
        val recommendations = planner.analyze(context)
        val nextTask = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertNotNull(nextTask)
        assertEquals(2L, nextTask?.relatedTaskId)
    }

    @Test
    fun `TEST C - PRIORITY`() {
        val lowTask = PremiumTask(id = 1, title = "Low", category = "Work", duration = "30 min", priority = TaskPriority.LOW)
        val highTask = PremiumTask(id = 2, title = "High", category = "Work", duration = "30 min", priority = TaskPriority.HIGH)
        val context = AiContext(tasks = listOf(lowTask, highTask))
        
        val recommendations = planner.analyze(context)
        val nextTask = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertEquals(2L, nextTask?.relatedTaskId)
    }

    @Test
    fun `TEST D - GOAL relationship`() {
        val task1 = PremiumTask(id = 1, title = "Task without goal", category = "Work", duration = "30 min", priority = TaskPriority.MEDIUM)
        val task2 = PremiumTask(id = 2, title = "Task with goal", category = "Work", duration = "30 min", priority = TaskPriority.MEDIUM, goalTitle = "Study Goal")
        val goal = NexoraGoal(id = 1, title = "Study Goal", category = "Learning", progress = 0.1f, targetDate = "")
        val context = AiContext(tasks = listOf(task1, task2), goals = listOf(goal))
        
        val recommendations = planner.analyze(context)
        val nextTask = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertEquals(2L, nextTask?.relatedTaskId)
        assertTrue(nextTask?.evidence?.any { it.factor == "Goal Alignment" } == true)
    }

    @Test
    fun `TEST E - MANY TASKS creates focused plan`() {
        val tasks = (1..15).map { 
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "30 min", priority = TaskPriority.MEDIUM)
        }
        val context = AiContext(tasks = tasks)
        
        val plan = planner.createDailyPlan(context)
        
        assertTrue("Plan should be limited (actual: ${plan.tasks.size})", plan.tasks.size in 5..7)
        assertTrue(plan.summary.contains("achievable") || plan.summary.contains("focused") || plan.summary.contains("balanced"))
    }

    @Test
    fun `TEST F - DIFFERENT DURATIONS`() {
        // High priority but very long
        val task1 = PremiumTask(id = 1, title = "Long", category = "Work", duration = "4 hours", priority = TaskPriority.HIGH)
        // Medium priority but quick win
        val task2 = PremiumTask(id = 2, title = "Short", category = "Work", duration = "15 min", priority = TaskPriority.MEDIUM)
        
        val context = AiContext(
            tasks = listOf(task1, task2),
            personalContext = AiPersonalContext(
                workload = WorkloadAssessment(state = WorkloadState.VERY_HIGH, taskCount = 10)
            )
        )
        
        val plan = planner.createDailyPlan(context)
        assertTrue(plan.tasks.any { it.task.id == 2L })
    }

    @Test
    fun `TEST G - NO TASKS`() {
        val context = AiContext(tasks = emptyList())
        val recommendations = planner.analyze(context)
        
        assertTrue(recommendations.none { it.type == AiRecommendationType.NEXT_TASK })
        
        val plan = planner.createDailyPlan(context)
        assertTrue(plan.tasks.isEmpty())
        assertTrue(plan.summary.contains("no unfinished tasks", ignoreCase = true))
    }

    @Test
    fun `TEST H - ALL COMPLETED`() {
        val task = PremiumTask(id = 1, title = "Done", category = "Work", duration = "30 min", completed = true)
        val context = AiContext(tasks = listOf(task))
        val recommendations = planner.analyze(context)
        
        assertTrue(recommendations.none { it.type == AiRecommendationType.NEXT_TASK })
    }

    @Test
    fun `TEST I - HISTORY`() {
        val history = listOf(
            com.example.nexora.data.DailyProgressEntity("2024-05-01", 10, 8, 120, 1, 0),
            com.example.nexora.data.DailyProgressEntity("2024-05-02", 10, 9, 150, 1, 0),
            com.example.nexora.data.DailyProgressEntity("2024-05-03", 10, 8, 130, 1, 0)
        )
        val memory = planner.detectPatterns(history)
        
        assertTrue(memory.legacyPatterns.any { it.title.contains("High Execution") })
    }

    @Test
    fun `TEST J - CHAT consistency`() {
        val task = PremiumTask(id = 42, title = "Chat Task", category = "Work", duration = "30 min", priority = TaskPriority.HIGH)
        val context = AiContext(tasks = listOf(task))
        val resolver = LocalAiIntentResolver()
        
        val response = resolver.resolve("What should I do next?", context)
        
        assertEquals(AiDecisionType.START_TASK, response.decision.type)
        assertTrue(response.textResponse?.contains("Chat Task") == true)
        assertEquals(42L, response.decision.taskId)
    }
}
