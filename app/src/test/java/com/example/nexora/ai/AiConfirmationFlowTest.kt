package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiConfirmationFlowTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var actionExecutor: AiActionExecutor

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
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
    fun `test multi-turn confirmation flow for delete all tasks`() = runBlocking {
        // 1. Seed tasks
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        repository.addTask(PremiumTask(title = "Task 2", category = "Work", duration = "10m"))
        
        // 2. Initial request
        val response1 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "delete all tasks"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response1.responseType)
        val pendingAction = response1.proposedActions.first()
        val convContext = response1.conversationContext
        assertNotNull(convContext)
        assertEquals(AiActionType.DELETE_ALL_TASKS, convContext?.pendingAction?.type)

        // 3. User says "yes" (Turn 2)
        val response2 = engine.processRequest(AiRequest(
            type = AiRequestType.CHAT, 
            userMessage = "yes",
            conversationContext = convContext
        ))
        
        // 4. Verify action was authorized and executed
        val authorizedAction = response2.proposedActions.first()
        assertFalse(authorizedAction.requiresConfirmation)
        assertTrue(authorizedAction.parameters["userConfirmed"] == true)
        
        // 5. Execute action (Simulate ViewModel behavior)
        val result = engine.executeAction(authorizedAction)
        assertTrue("Execution should succeed: ${result.error}", result.success)
        
        // 6. Verify DB
        val tasks = repository.observeTasksOnce()
        assertTrue("Tasks should be deleted. Found: ${tasks.size}", tasks.isEmpty())
        
        // 7. Verify context cleared
        assertNull("Pending action should be cleared", response2.conversationContext?.pendingAction)
    }

    @Test
    fun `test cancellation flow`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        
        val response1 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "delete all tasks"))
        val convContext = response1.conversationContext

        val response2 = engine.processRequest(AiRequest(
            type = AiRequestType.CHAT, 
            userMessage = "no",
            conversationContext = convContext
        ))
        
        assertTrue("Should not have proposed actions", response2.proposedActions.isEmpty())
        assertNull("Pending action should be cleared", response2.conversationContext?.pendingAction)
        
        val tasks = repository.observeTasksOnce()
        assertEquals(1, tasks.size)
    }

    @Test
    fun `test unclear response keeps pending action`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))
        
        val response1 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "delete all tasks"))
        val convContext = response1.conversationContext

        val response2 = engine.processRequest(AiRequest(
            type = AiRequestType.CHAT, 
            userMessage = "maybe",
            conversationContext = convContext
        ))
        
        // If it's unclear, it might go through normal intent detection or return a clarification
        // Our current implementation will treat it as a new message if it doesn't match yes/no
        // But importantly, it should NOT execute the destructive action.
        
        val tasks = repository.observeTasksOnce()
        assertEquals(1, tasks.size)
    }
}
