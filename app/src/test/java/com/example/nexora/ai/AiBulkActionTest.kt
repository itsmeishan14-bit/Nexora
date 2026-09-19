package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiBulkActionTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var actionExecutor: AiActionExecutor

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val providerManager = AiProviderManager(localProvider = LocalAiProvider())
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        actionExecutor = AiActionExecutor(repository)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)

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
    fun `test delete all tasks intent detection`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        val query = "delete all tasks"
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
        
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.DELETE_ALL_TASKS, proposed?.type)
        assertTrue(proposed?.requiresConfirmation == true)
    }

    @Test
    fun `test delete all tasks variation intent detection`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        val variations = listOf(
            "delete all my tasks",
            "remove all tasks",
            "clear all tasks",
            "delete every task",
            "delete all"
        )
        
        variations.forEach { query ->
            val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
            val proposed = response.proposedActions.firstOrNull()
            assertEquals("Failed for query: $query", AiActionType.DELETE_ALL_TASKS, proposed?.type)
        }
    }

    @Test
    fun `test delete all tasks when database is empty`() = runBlocking {
        val query = "delete all tasks"
        // Ensure repository is empty (MockNexoraRepository is empty by default)
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
        
        assertEquals(AiResponseType.NO_ACTION, response.responseType)
        assertTrue(response.message.contains("no tasks to delete", ignoreCase = true))
    }

    @Test
    fun `test bulk delete requires authorization`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        
        val action = AiAction(
            type = AiActionType.DELETE_ALL_TASKS,
            title = "Delete All",
            description = "Delete all tasks",
            requiresConfirmation = true // Should be true by default for destructive
        )
        
        val result = actionExecutor.execute(action)
        assertFalse("Should fail execution without authorization (confirmation)", result.success)
        assertTrue(result.message.contains("confirmation"))
    }

    @Test
    fun `test complete all tasks intent detection`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        val query = "complete all tasks"
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
        
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.COMPLETE_ALL_TASKS, proposed?.type)
    }

    @Test
    fun `test single task delete still works`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Java Task", category = "Study", duration = "30m"))
        val query = "delete my Java task"
        
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))
        
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertEquals(AiActionType.DELETE_TASK, proposed?.type)
        assertEquals(task.id, proposed?.taskId)
    }
}
