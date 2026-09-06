package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReliabilityTest {

    private val decisionGate = AiDecisionGate(NexoraRepository(null))

    @Test
    fun `test decision gate permits safe read with low confidence`() {
        val action = AiAction(type = AiActionType.OPEN_TASK, title = "Open", description = "")
        val result = decisionGate.evaluateAction(action, AiConfidence.LOW, 0.5f)
        
        assertTrue(result.success)
        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `test decision gate blocks destructive action without confirmation`() {
        val action = AiAction(
            type = AiActionType.DELETE_TASK, 
            title = "Delete", 
            description = "",
            requiresConfirmation = false
        )
        val result = decisionGate.evaluateAction(action, AiConfidence.HIGH, 0.8f)
        
        assertFalse(result.success)
        assertEquals(ToolResultStatus.NEEDS_CONFIRMATION, result.status)
    }

    @Test
    fun `test decision gate blocks low confidence write action`() {
        val action = AiAction(type = AiActionType.CREATE_TASK, title = "Create", description = "")
        val result = decisionGate.evaluateAction(action, AiConfidence.LOW, 0.5f)
        
        assertFalse(result.success)
        assertEquals(AiFailureType.CONFIDENCE_FAILURE, result.evaluation.failureType)
    }

    @Test
    fun `test decision gate blocks inconsistent confidence and evidence`() {
        val action = AiAction(type = AiActionType.CREATE_TASK, title = "Create", description = "")
        // High confidence but very low evidence score
        val result = decisionGate.evaluateAction(action, AiConfidence.HIGH, 0.1f)
        
        assertFalse(result.success)
        assertEquals(AiFailureType.CONFIDENCE_FAILURE, result.evaluation.failureType)
    }
}
