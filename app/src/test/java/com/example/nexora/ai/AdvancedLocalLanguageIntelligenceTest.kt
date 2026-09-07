package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import org.junit.Assert.assertEquals
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
        assertTrue(result.entities["title"].toString().contains("study java"))
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
}
