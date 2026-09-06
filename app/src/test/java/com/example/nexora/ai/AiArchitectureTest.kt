package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiArchitectureTest {

    private val planner = AiPlanner()
    private val intentResolver = LocalAiIntentResolver()

    @Test
    fun `test empty context behavior`() {
        val context = AiContext(
            tasks = emptyList(),
            goals = emptyList(),
            memory = AiMemory(patterns = emptyList(), analyzedDays = 0)
        )

        val recommendations = planner.analyze(context)
        // Should return a basic greeting or empty list, not crash
        assertTrue(recommendations.isEmpty() || recommendations.any { it.type == AiRecommendationType.NEXT_TASK })
        
        val plan = planner.createDailyPlan(context)
        assertTrue(plan.tasks.isEmpty())
        assertNotNull(plan.summary)
    }

    @Test
    fun `test next task prioritization`() {
        val tasks = listOf(
            PremiumTask(id = 1, title = "Low Task", category = "Work", duration = "30 min", priority = TaskPriority.LOW),
            PremiumTask(id = 2, title = "Urgent Task", category = "Work", duration = "30 min", priority = TaskPriority.URGENT),
            PremiumTask(id = 3, title = "High Task", category = "Work", duration = "30 min", priority = TaskPriority.HIGH)
        )
        val context = AiContext(tasks = tasks, goals = emptyList())

        val recommendations = planner.analyze(context)
        val nextTaskRec = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertNotNull(nextTaskRec)
        assertEquals(2L, nextTaskRec?.relatedTaskId)
    }

    @Test
    fun `test workload warning`() {
        val tasks = (1..10).map { 
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "30 min", priority = TaskPriority.MEDIUM)
        }
        val context = AiContext(
            tasks = tasks,
            goals = emptyList(),
            tasksPlannedToday = 10,
            memory = AiMemory(analyzedDays = 5) // Low avg completion assumed
        )

        val insights = planner.getProactiveInsights(context)
        assertTrue(insights.any { it.type == AiRecommendationType.WARNING && it.title.contains("Workload") })
    }

    @Test
    fun `test goal neglected insight`() {
        val goals = listOf(
            NexoraGoal(id = 1, title = "Important Goal", category = "Work", targetDate = "", progress = 0.1f)
        )
        val context = AiContext(tasks = emptyList(), goals = goals)

        val insights = planner.getProactiveInsights(context)
        assertTrue(insights.any { it.type == AiRecommendationType.GOAL_ACTION && it.title.contains("Neglected") })
    }

    @Test
    fun `test intent resolution for task creation`() {
        val context = AiContext(tasks = emptyList(), goals = emptyList())
        val response = intentResolver.resolve("Create a task to study Java", context)
        
        assertEquals(AiDecisionType.CREATE_TASK, response.decision.type)
        assertTrue(response.actions.any { it.type == AiActionType.CREATE_TASK })
        val taskTitle = response.actions.first().parameters["title"] as String
        assertTrue(taskTitle.lowercase().contains("study java"))
    }

    @Test
    fun `test intent resolution for completion`() {
        val tasks = listOf(PremiumTask(id = 1, title = "Study Kotlin", category = "Work", duration = "30 min"))
        val context = AiContext(tasks = tasks, goals = emptyList())
        
        val response = intentResolver.resolve("Mark Study Kotlin as complete", context)
        
        assertEquals(AiDecisionType.COMPLETE_TASK, response.decision.type)
        assertEquals(1L, response.decision.taskId)
    }

    @Test
    fun `test malformed intent resolution`() {
        val context = AiContext(tasks = emptyList(), goals = emptyList())
        val response = intentResolver.resolve("", context)
        
        assertEquals(AiDecisionType.NO_ACTION, response.decision.type)
        assertNotNull(response.textResponse)
    }

    @Test
    fun `test daily planning realism`() {
        val tasks = (1..20).map { 
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "60 minutes")
        }
        val context = AiContext(tasks = tasks, goals = emptyList())
        
        val plan = planner.createDailyPlan(context)
        
        // Should limit tasks to a realistic amount (max 6 in our code)
        assertTrue(plan.tasks.size <= 6)
        // Total duration should not exceed max limit significantly
        assertTrue(plan.totalDurationMinutes <= 360) 
    }
}
