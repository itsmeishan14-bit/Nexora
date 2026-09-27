package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiAgentSystemTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        val actionExecutor = AiActionExecutor(repository)
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
    fun `test agent multi-step goal decomposition`() = runBlocking {
        // 1. Setup goal
        val goal = repository.addGoal(NexoraGoal(title = "Study Android", category = "Study", targetDate = "", progress = 0f))
        
        // 2. Request help with goal
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Help me with my Android goal"))
        
        // 3. Verify Agent started workflow
        assertNotNull(response.workflow)
        
        // Should have found goal, decomposed it, and proposed first task
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull("Should have proposed an action", proposed)
        assertEquals(AiActionType.CREATE_TASK, proposed?.type)
    }

    @Test
    fun `test agent cleanup workflow`() = runBlocking {
        // 1. Setup messy context
        repository.addTask(PremiumTask(title = "Low Priority 1", priority = com.example.nexora.uii.TaskPriority.LOW, category = "Work", duration = "10m"))
        repository.addTask(PremiumTask(title = "Low Priority 2", priority = com.example.nexora.uii.TaskPriority.LOW, category = "Work", duration = "10m"))
        
        // 2. Request cleanup
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Clean up my tasks"))
        
        // 3. Verify Agent proposed rescheduling
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val action = response.proposedActions.first()
        assertEquals(AiActionType.RESCHEDULE_TASK, action.type)
    }

    @Test
    fun `test agent task completion reasoning`() = runBlocking {
        // 1. Setup task and agent
        val task = repository.addTask(PremiumTask(title = "Complete Java Project", category = "Study", duration = "1h"))
        val actionExecutor = AiActionExecutor(repository)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)
        val agent = NexoraAiAgent(toolRegistry = toolRegistry)

        val context = engine.getContext()
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Finish my Java project")

        // 2. Execute agent
        val response = agent.execute(request, context)

        // 3. Verify Agent resolved and proposed completion
        assertNotNull(response.workflow)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull("Agent should propose completion action", proposed)
        assertEquals(AiActionType.COMPLETE_TASK, proposed?.type)
        assertEquals(task.id, proposed?.taskId)
    }

    @Test
    fun `test agent handle non-existent task failure`() = runBlocking {
        val actionExecutor = AiActionExecutor(repository)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)
        val agent = NexoraAiAgent(toolRegistry = toolRegistry)

        val context = engine.getContext()
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Finish my Java nonexistent project")

        val response = agent.execute(request, context)

        assertEquals(WorkflowStatus.FAILED, response.workflow?.status)
        assertTrue("Message should explain task was not found. Got: ${response.message}",
            response.message.contains("No task found", ignoreCase = true) || response.message.contains("not found", ignoreCase = true))
    }
}
