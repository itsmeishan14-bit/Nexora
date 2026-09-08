package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.util.PrivacyPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PrivacySecurityTest {

    private lateinit var repository: NexoraRepository

    @Before
    fun setup() {
        repository = com.example.nexora.ai.evaluation.MockNexoraRepository()
    }

    @Test
    fun `test local-first default`() {
        assertTrue("Nexora must be local-first", PrivacyPolicy.LOCAL_FIRST)
    }

    @Test
    fun `test local ai processing default`() {
        assertTrue("Nexora AI must process locally", PrivacyPolicy.LOCAL_AI_PROCESSING)
    }

    @Test
    fun `test data minimization logic`() = runBlocking {
        // Mock request that doesn't need goals
        val request = AiRequest(type = AiRequestType.PRODUCTIVITY_ANALYSIS)
        
        val builder = AiContextBuilder(null)
        val context = builder.build(request)
        
        assertTrue("Context should minimize data: tasks should be empty", 
            context.tasks.isEmpty())
        assertTrue("Context should minimize data: goals should be empty", 
            context.goals.isEmpty())
    }

    @Test
    fun `test action risk mapping`() {
        val gate = AiDecisionGate(repository)
        
        val deleteAction = AiAction(
            type = AiActionType.DELETE_TASK,
            title = "Delete",
            description = "Delete task",
            requiresConfirmation = false // Try to bypass
        )
        
        val result = gate.evaluateAction(deleteAction, AiConfidence.HIGH, 1.0f)
        
        assertFalse("Destructive actions must NOT proceed without confirmation", result.success)
        assertEquals(ToolResultStatus.NEEDS_CONFIRMATION, result.status)
    }

    @Test
    fun `test low confidence action rejection`() {
        val gate = AiDecisionGate(repository)
        
        val createAction = AiAction(
            type = AiActionType.CREATE_TASK,
            title = "Create",
            description = "Create task",
            requiresConfirmation = true
        )
        
        // Low confidence for a LOW_RISK action (requires MEDIUM)
        val result = gate.evaluateAction(createAction, AiConfidence.LOW, 0.5f)
        
        assertFalse("Low confidence actions should be rejected", result.success)
        assertEquals(ToolResultStatus.FAILED, result.status)
        assertTrue(result.message.contains("Insufficient confidence"))
    }

    @Test
    fun `test memory deletion`() = runBlocking {
        val item = AiMemoryItem(category = AiMemoryCategory.TASK_PATTERN, title = "Test", content = "Content")
        repository.saveMemory(item)
        
        var allMemory = repository.getAllMemory()
        assertTrue(allMemory.any { it.id == item.id })
        
        repository.deleteMemory(item.id)
        allMemory = repository.getAllMemory()
        assertFalse("Memory must be removed from storage upon deletion", allMemory.any { it.id == item.id })
    }

    @Test
    fun `test clear all memory`() = runBlocking {
        repository.saveMemory(AiMemoryItem(category = AiMemoryCategory.TASK_PATTERN, title = "T1", content = "C1"))
        repository.saveMemory(AiMemoryItem(category = AiMemoryCategory.GOAL_PATTERN, title = "T2", content = "C2"))
        
        repository.clearAllMemory()
        val allMemory = repository.getAllMemory()
        assertTrue("All memory should be wiped", allMemory.isEmpty())
    }

    @Test
    fun `test tool destructive confirmation requirement`() {
        val action = AiAction(
            type = AiActionType.DELETE_TASK,
            title = "Delete",
            description = "Delete task",
            requiresConfirmation = false
        )
        
        assertFalse("Destructive tool actions MUST require confirmation", 
            com.example.nexora.util.NexoraSecurity.isAuthorized(action))
    }
}
