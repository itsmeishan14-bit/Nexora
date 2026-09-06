package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Nexora's enhanced local AI intelligence.
 * Verifies deterministic behavior without any external API dependencies.
 */
class LocalAiIntelligenceTest {

    private val planner = AiPlanner()
    private val entityResolver = AiEntityResolver
    private val intentResolver = LocalAiIntentResolver()

    @Test
    fun `test goal decomposition by category`() {
        // Learning goal
        val learningGoal = planner.decomposeGoal("Learn Android", "Study Compose", "Learning")
        assertTrue("Learning goal should mention resources", 
            learningGoal.steps.any { it.title.contains("resource", ignoreCase = true) })

        // Software goal
        val softwareGoal = planner.decomposeGoal("Build Nexora", "", "Software")
        assertTrue("Software goal should mention architecture", 
            softwareGoal.steps.any { it.title.contains("Architecture", ignoreCase = true) })

        // Fitness goal
        val fitnessGoal = planner.decomposeGoal("Run Marathon", "", "Fitness")
        assertTrue("Fitness goal should mention routine", 
            fitnessGoal.steps.any { it.title.contains("routine", ignoreCase = true) })
        
        // General goal
        val generalGoal = planner.decomposeGoal("Clean House", "", "General")
        assertTrue("General goal should mention planning", 
            generalGoal.steps.any { it.title.contains("planning", ignoreCase = true) })
    }

    @Test
    fun `test entity resolution robust matching`() {
        val tasks = listOf(
            PremiumTask(id = 1, title = "Finish the Android project", category = "Work", duration = "60 min"),
            PremiumTask(id = 2, title = "Buy groceries", category = "Personal", duration = "30 min"),
            PremiumTask(id = 3, title = "Call the bank", category = "Personal", duration = "15 min")
        )

        // Exact match
        val res1 = entityResolver.resolveTask("Buy groceries", tasks)
        assertTrue(res1 is ResolutionResult.Success && res1.entity.id == 2L)

        // Normalized match with symbols and case
        val res2 = entityResolver.resolveTask("FINISH the ANDROID project!!!", tasks)
        assertTrue(res2 is ResolutionResult.Success && res2.entity.id == 1L)

        // Partial match
        val res3 = entityResolver.resolveTask("Android project", tasks)
        assertTrue(res3 is ResolutionResult.Success && res3.entity.id == 1L)

        // Ambiguous match
        val tasksAmbiguous = tasks + PremiumTask(id = 4, title = "Finish the iOS project", category = "Work", duration = "60 min")
        val res4 = entityResolver.resolveTask("finish project", tasksAmbiguous)
        assertTrue("Matching 'finish project' should be ambiguous when two exist", 
            res4 is ResolutionResult.Ambiguous)
    }

    @Test
    fun `test task scoring with goal alignment`() {
        val goal = NexoraGoal(id = 1, title = "Master Kotlin", category = "Learning", targetDate = "", progress = 0.1f)
        val tasks = listOf(
            PremiumTask(id = 1, title = "Task 1", goalTitle = "Master Kotlin", priority = TaskPriority.MEDIUM, category = "Learning", duration = "30 min"),
            PremiumTask(id = 2, title = "Task 2", priority = TaskPriority.HIGH, category = "Work", duration = "30 min")
        )
        val context = AiContext(tasks = tasks, goals = listOf(goal))

        // Task 1 should have higher score due to goal alignment (low progress boost)
        // Task 1: Medium(40) + Goal(50) + LowProgress(30) = 120
        // Task 2: High(80) = 80
        val recommendations = planner.analyze(context)
        val nextTask = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }
        
        assertNotNull("Next task should be recommended", nextTask)
        assertEquals("Task 1 should be prioritized due to goal alignment", 1L, nextTask?.relatedTaskId)
    }

    @Test
    fun `test natural language intent expansion`() {
        val goal = NexoraGoal(id = 1, title = "Study Goal", category = "Learning", targetDate = "", progress = 0f)
        val context = AiContext(tasks = emptyList(), goals = listOf(goal))
        
        // Planning query
        val res1 = intentResolver.resolve("what should I do next", context)
        assertEquals(AiDecisionType.START_TASK, res1.decision.type)

        // Progress query
        val res2 = intentResolver.resolve("how am I doing today", context)
        assertEquals(AiDecisionType.SHOW_INSIGHT, res2.decision.type)
        assertTrue("Response should mention plan or tasks", 
            res2.textResponse?.lowercase()?.contains("plan") == true || res2.textResponse?.contains("tasks") == true)

        // Decomposition query
        val res3 = intentResolver.resolve("break down my study goal", context)
        assertEquals(AiDecisionType.UPDATE_GOAL, res3.decision.type)
        assertTrue(res3.actions.any { it.type == AiActionType.DECOMPOSE_GOAL })
        
        // Task completion query
        val res4 = intentResolver.resolve("complete my work task", context)
        // Note: this depends on entity resolver finding a match, but here tasks are empty
        assertEquals("Decision should be NO_ACTION when task not found. Actual type: ${res4.decision.type}, text: ${res4.textResponse}", 
            AiDecisionType.NO_ACTION, res4.decision.type) 
        assertTrue("Response should explain task was not found. Actual text: ${res4.textResponse}",
            res4.textResponse?.contains("couldn't find") == true)
    }

    @Test
    fun `test proactive insights for neglected goals`() {
        val goal = NexoraGoal(id = 1, title = "Fitness Goal", category = "Fitness", targetDate = "", progress = 0.2f)
        val context = AiContext(
            tasks = emptyList(), 
            goals = listOf(goal),
            tasksPlannedToday = 0
        )
        
        val insights = planner.getProactiveInsights(context)
        assertTrue("Should detect neglected goal", 
            insights.any { it.type == AiRecommendationType.GOAL_ACTION && it.title.contains("Neglected") })
    }
}
