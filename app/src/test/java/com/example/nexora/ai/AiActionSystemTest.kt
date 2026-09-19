package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiActionSystemTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var aiService: LocalNexoraAiService
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var toolRegistry: AiToolRegistry
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
        aiService = LocalNexoraAiService(providerManager = providerManager)
        actionExecutor = AiActionExecutor(repository)
        toolRegistry = AiToolRegistry(repository, actionExecutor)

        engine = NexoraAiEngine(
            contextBuilder = contextBuilder,
            aiService = aiService,
            providerManager = providerManager,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry,
            repository = repository
        )
    }

    @Test
    fun `TEST 1 - CREATE task flow with confirmation and verification`() = runBlocking {
        val query = "Create a task to study Java for 30 minutes."
        
        // 1. AI processes request
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
        
        // 2. AI proposes CREATE_TASK action requiring confirmation
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertFalse(response.proposedActions.isEmpty())
        val proposed = response.proposedActions.first()
        assertEquals(AiActionType.CREATE_TASK, proposed.type)
        assertEquals("Study java", proposed.parameters["title"])
        assertEquals("30 minutes", proposed.parameters["duration"])
        assertTrue("Action MUST require confirmation before DB modification", proposed.requiresConfirmation)

        // Verify task NOT in DB before confirmation
        val tasksBefore = repository.observeTasksOnce()
        assertFalse("Task must NOT be in DB before confirmation", tasksBefore.any { it.title.equals("Study java", ignoreCase = true) })

        // 3. User confirms (engine executes authorized action)
        val result = engine.executeAction(proposed)
        
        // 4. Verify post-action DB state
        assertTrue("Action execution result should be successful", result.success)
        val tasksAfter = repository.observeTasksOnce()
        val createdTask = tasksAfter.find { it.title.equals("Study java", ignoreCase = true) }
        assertNotNull("Task must exist in DB after confirmation", createdTask)
        assertEquals("30 minutes", createdTask?.duration)
    }

    @Test
    fun `TEST 2 - COMPLETE task flow with verification and idempotency`() = runBlocking {
        // Seed task
        val task = repository.addTask(PremiumTask(title = "Study Java", category = "Personal", duration = "30 minutes", completed = false))
        
        // 1. Send request
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Mark Study Java as complete"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.first()
        assertEquals(AiActionType.COMPLETE_TASK, proposed.type)
        assertEquals(task.id, proposed.taskId)

        // 2. User confirms
        val result = engine.executeAction(proposed)
        assertTrue(result.success)
        
        // 3. Verify in DB
        val updatedTask = repository.getTaskById(task.id)
        assertNotNull(updatedTask)
        assertTrue("Task must be marked completed in DB", updatedTask!!.completed)

        // 4. Query again for completed task
        val response2 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Mark Study Java as complete"))
        assertEquals(AiResponseType.NO_ACTION, response2.responseType)
        assertTrue("Should report already completed", response2.message.contains("already completed", ignoreCase = true))
    }

    @Test
    fun `TEST 3 - DUPLICATE task creation prevention`() = runBlocking {
        repository.addTask(PremiumTask(title = "Study java", category = "Personal", duration = "30 minutes", completed = false))
        
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task to study Java for 30 minutes"))
        
        assertTrue("Should warn about duplicate task", response.message.contains("already exists", ignoreCase = true))
        assertTrue("Should not propose action for duplicate", response.proposedActions.isEmpty())
    }

    @Test
    fun `TEST 4 - DELETE task flow with confirmation and verification`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Study Java", category = "Personal", duration = "30 minutes", completed = false))
        
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete Study Java"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.first()
        assertEquals(AiActionType.DELETE_TASK, proposed.type)
        assertEquals(task.id, proposed.taskId)

        // User confirms
        val result = engine.executeAction(proposed)
        assertTrue(result.success)
        
        // Verify task removed from DB
        val deletedTask = repository.getTaskById(task.id)
        assertNull("Task must be deleted from DB", deletedTask)
    }

    @Test
    fun `TEST 5 - AMBIGUOUS task resolution asks for clarification`() = runBlocking {
        repository.addTask(PremiumTask(title = "Study Java Basic", category = "Personal", duration = "30 min"))
        repository.addTask(PremiumTask(title = "Study Java Advanced", category = "Personal", duration = "60 min"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete Study Java"))

        assertTrue("Should ask clarification for ambiguous tasks", response.message.contains("multiple", ignoreCase = true) || response.message.contains("Which", ignoreCase = true))
        assertTrue("Should not propose action when ambiguous", response.proposedActions.isEmpty())
    }

    @Test
    fun `TEST 6 - INVALID task request returns clear not found message`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete task XYZ non existent"))

        assertTrue("Should state task not found", response.message.contains("couldn't find", ignoreCase = true) || response.message.contains("not found", ignoreCase = true))
        assertTrue("Should not propose action when task not found", response.proposedActions.isEmpty())
    }

    @Test
    fun `TEST 7 - CANCEL clears proposed action without modifying DB`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task to revise Java"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertFalse(response.proposedActions.isEmpty())

        // Verify DB untouched
        val tasks = repository.observeTasksOnce()
        assertFalse("Task should not exist in DB before confirmation", tasks.any { it.title.contains("revise Java", ignoreCase = true) })

        // User cancels via chat
        val cancelResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "cancel"))
        assertEquals(AiDecisionType.CANCEL, cancelResponse.decision?.type)
        assertTrue("Cancellation message shown", cancelResponse.message.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun `TEST 8 - RAPID ACTION prevents duplicate executions`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task to study Java for 30 minutes"))
        val proposed = response.proposedActions.first()

        // First execution
        val result1 = engine.executeAction(proposed)
        assertTrue(result1.success)

        // Second rapid execution (duplicate active task)
        val result2 = engine.executeAction(proposed)
        assertFalse("Second execution should fail due to duplicate active task", result2.success)

        val tasks = repository.observeTasksOnce().filter { it.title.equals("Study java", ignoreCase = true) }
        assertEquals("Only one task should exist in DB", 1, tasks.size)
    }
}
