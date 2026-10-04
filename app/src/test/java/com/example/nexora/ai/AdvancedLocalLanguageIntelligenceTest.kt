package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedLocalLanguageIntelligenceTest {

    private val pipeline = AdvancedLocalLanguagePipeline()
    private val context = AiContext()

    @Test
    fun `test normalization and intent detection`() {
        val message = "Could you please plan my day!"
        val result = pipeline.process(message, context)
        
        assertEquals(AiDecisionType.DAILY_PLAN, result.intent)
        assertEquals(AiConfidence.HIGH, result.confidence)
    }

    @Test
    fun `test task creation extraction`() {
        val message = "Create a high priority task to study Java for 45 minutes"
        val result = pipeline.process(message, context)
        
        assertEquals(AiDecisionType.CREATE_TASK, result.intent)
        // Heuristic title extraction includes "study java for 45 minutes" in this version
        assertTrue(result.entities["title"].toString().contains("study java", ignoreCase = true))
        assertEquals("HIGH", result.entities["priority"])
        assertEquals("45 minutes", result.entities["duration"])
    }

    @Test
    fun `test contextual reference resolution`() {
        val convContext = AiConversationContext(
            lastTaskId = 123L,
            lastEntityTitle = "Java Task"
        )
        
        val message = "complete it"
        val result = pipeline.process(message, context, convContext)
        
        assertEquals(AiDecisionType.COMPLETE_TASK, result.intent)
        
        // Final attempt to debug what's happening
        val taskId = result.entities["taskId"]
        assertTrue("Entities: ${result.entities}", taskId == 123L)
    }

    @Test
    fun `test clarification follow up`() {
        val clar = AiClarification(
            question = "What should the task be called?",
            intent = AiDecisionType.CREATE_TASK,
            missingField = "title",
            originalQuery = "Create a task"
        )
        val convContext = AiConversationContext(activeClarification = clar)
        
        val message = "Buy milk"
        val result = pipeline.process(message, context, convContext)
        
        assertEquals(AiDecisionType.CREATE_TASK, result.intent)
        assertEquals("buy milk", result.entities["title"])
    }

    @Test
    fun `test Complete the Java task with multiple matching tasks preserves requested action and asks to complete`() {
        val task1 = PremiumTask(id = 101L, title = "Java Basics", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 102L, title = "Java Advanced", priority = TaskPriority.MEDIUM, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1, task2))

        val result = pipeline.process("Complete the Java task", testContext)

        assertEquals("Intent should be AMBIGUOUS when multiple entities match", AiDecisionType.AMBIGUOUS, result.intent)
        assertEquals("Requested action must be preserved as COMPLETE_TASK", AiActionType.COMPLETE_TASK, result.requestedAction)
        assertEquals(AiDecisionType.COMPLETE_TASK, result.clarificationNeeded?.intent)
        org.junit.Assert.assertFalse("Mutation must be prohibited while entity is ambiguous", result.requiresMutation)
        assertTrue("Clarification must be required", result.requiresClarification)
        assertNotNull("Clarification object must be provided", result.clarificationNeeded)
        assertTrue(
            "Clarification must ask to 'complete' and mention matching candidates. Got: ${result.clarificationNeeded?.question}",
            result.clarificationNeeded?.question?.contains("Which one would you like to complete?") == true
        )
    }

    @Test
    fun `test Delete the Java task with multiple matching tasks preserves requested action and asks to delete`() {
        val task1 = PremiumTask(id = 101L, title = "Java Basics", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 102L, title = "Java Advanced", priority = TaskPriority.MEDIUM, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1, task2))

        val result = pipeline.process("Delete the Java task", testContext)

        assertEquals("Intent should be AMBIGUOUS when multiple entities match", AiDecisionType.AMBIGUOUS, result.intent)
        assertEquals("Requested action must be preserved as DELETE_TASK", AiActionType.DELETE_TASK, result.requestedAction)
        assertEquals(AiDecisionType.DELETE_TASK, result.clarificationNeeded?.intent)
        org.junit.Assert.assertFalse("Mutation must be prohibited while entity is ambiguous", result.requiresMutation)
        assertTrue("Clarification must be required", result.requiresClarification)
        assertNotNull("Clarification object must be provided", result.clarificationNeeded)
        assertTrue(
            "Clarification must ask to 'delete' and mention matching candidates. Got: ${result.clarificationNeeded?.question}",
            result.clarificationNeeded?.question?.contains("Which one would you like to delete?") == true
        )
    }

    @Test
    fun `test Update the Java task with multiple matching tasks preserves requested action and asks to update`() {
        val task1 = PremiumTask(id = 101L, title = "Java Basics", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 102L, title = "Java Advanced", priority = TaskPriority.MEDIUM, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1, task2))

        val result = pipeline.process("Update the Java task", testContext)

        assertEquals("Intent should be AMBIGUOUS when multiple entities match", AiDecisionType.AMBIGUOUS, result.intent)
        assertEquals("Requested action must be preserved as UPDATE_TASK", AiActionType.UPDATE_TASK, result.requestedAction)
        assertEquals(AiDecisionType.UPDATE_TASK, result.clarificationNeeded?.intent)
        org.junit.Assert.assertFalse("Mutation must be prohibited while entity is ambiguous", result.requiresMutation)
        assertTrue("Clarification must be required", result.requiresClarification)
        assertNotNull("Clarification object must be provided", result.clarificationNeeded)
        assertTrue(
            "Clarification must ask to 'update' and mention matching candidates. Got: ${result.clarificationNeeded?.question}",
            result.clarificationNeeded?.question?.contains("Which one would you like to update?") == true
        )
    }

    @Test
    fun `test unnamed destructive action with multiple possible targets triggers clarification and prevents mutation`() {
        val task1 = PremiumTask(id = 101L, title = "Work on Report", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 102L, title = "Fix Production Bug", priority = TaskPriority.URGENT, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1, task2))

        val result = pipeline.process("Delete task", testContext)

        assertEquals("Intent should be AMBIGUOUS for unnamed destructive action with multiple targets",
            AiDecisionType.AMBIGUOUS, result.intent)
        assertEquals("Requested action must be preserved as DELETE_TASK", AiActionType.DELETE_TASK, result.requestedAction)
        assertEquals(AiDecisionType.DELETE_TASK, result.clarificationNeeded?.intent)
        org.junit.Assert.assertFalse("Mutation must not occur without identified target", result.requiresMutation)
        assertTrue("Clarification must be required", result.requiresClarification)
        assertTrue(
            "Clarification must ask which task to delete. Got: ${result.clarificationNeeded?.question}",
            result.clarificationNeeded?.question?.contains("Which one would you like to delete?") == true
        )
    }

    @Test
    fun `test unique matching task resolves directly with mutation allowed`() {
        val task1 = PremiumTask(id = 42L, title = "Java Basics", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 43L, title = "Prepare Presentation", priority = TaskPriority.LOW, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1, task2))

        val result = pipeline.process("Complete the Java task", testContext)

        assertEquals("Intent should be COMPLETE_TASK for unique match", AiDecisionType.COMPLETE_TASK, result.intent)
        assertEquals(42L, result.targetTaskId)
        assertTrue("Mutation must be required for unique match", result.requiresMutation)
        org.junit.Assert.assertFalse("No clarification needed when entity is unique", result.requiresClarification)
    }

    @Test
    fun `test nonexistent task produces no target and forbids mutation without guessing`() {
        val task1 = PremiumTask(id = 42L, title = "Java Basics", priority = TaskPriority.HIGH, category = "Work", duration = "30m")
        val testContext = AiContext(tasks = listOf(task1))

        val result = pipeline.process("Complete the Rust task", testContext)

        org.junit.Assert.assertNull("targetTaskId must be null for nonexistent task", result.targetTaskId)
        org.junit.Assert.assertNull("resolvedEntities should not have taskId for nonexistent task", result.entities["taskId"])
        org.junit.Assert.assertFalse("Mutation must be forbidden when target task does not exist", result.requiresMutation)
    }

    @Test
    fun `test goal ambiguity preserves requested action and asks to delete`() {
        val goal1 = com.example.nexora.uii.NexoraGoal(id = 1L, title = "Fitness Running", category = "Health", targetDate = "2026-12-31", progress = 0f)
        val goal2 = com.example.nexora.uii.NexoraGoal(id = 2L, title = "Fitness Swimming", category = "Health", targetDate = "2026-12-31", progress = 0f)
        val testContext = AiContext(goals = listOf(goal1, goal2))

        val result = pipeline.process("Delete the Fitness goal", testContext)

        assertEquals(AiDecisionType.AMBIGUOUS, result.intent)
        assertEquals(AiActionType.DELETE_GOAL, result.requestedAction)
        assertEquals(AiDecisionType.DELETE_GOAL, result.clarificationNeeded?.intent)
        org.junit.Assert.assertFalse("Mutation must be false for ambiguous goal", result.requiresMutation)
        assertTrue(result.clarificationNeeded?.question?.contains("Which one would you like to delete?") == true)
    }
}
