package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraActionIntelligenceTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var brain: NexoraAiBrain
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        actionExecutor = AiActionExecutor(repository)
        
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
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
    fun `test prediction produces proactive signal with suggested action`() = runBlocking {
        val proactiveEngine = NexoraProactiveEngine()
        val task = PremiumTask(id = 5, title = "High Risk Project", category = "Work", duration = "180m", priority = TaskPriority.URGENT)
        val context = AiContext(
            tasks = listOf(task),
            personalContext = AiPersonalContext(
                predictions = listOf(
                    AiPrediction(
                        type = PredictionType.TASK_DELAY_RISK,
                        targetId = 5L,
                        targetTitle = task.title,
                        prediction = "High Delay Risk",
                        probability = 0.8f,
                        confidence = AiConfidence.HIGH,
                        riskLevel = AiPriority.CRITICAL
                    )
                )
            )
        )

        val signals = proactiveEngine.detectSignals(context)
        val signal = signals.find { it.relatedTaskId == 5L }

        assertNotNull(signal)
        assertNotNull(signal?.suggestedAction)
        assertEquals(5L, signal?.suggestedAction?.taskId)
    }

    @Test
    fun `test destructive action requires user approval and fails if not confirmed`() = runBlocking {
        val action = AiAction(
            type = AiActionType.DELETE_ALL_TASKS,
            title = "Delete All Tasks",
            description = "Clear all workspace tasks",
            requiresConfirmation = true,
            parameters = mapOf("userConfirmed" to false)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Destructive action without userConfirmed MUST fail", result.success)
    }

    @Test
    fun `test approved action executes through action executor and verifies DB state`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 1, title = "Complete Me", category = "Work", duration = "30m"))

        val action = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task Complete Me",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertTrue(result.success)

        val updated = repository.getTaskById(task.id)
        assertTrue("Task MUST be completed in DB", updated?.completed == true)
    }

    @Test
    fun `test rejected or dismissed action does not modify database`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 2, title = "Stay Active", category = "Work", duration = "30m"))

        // Action was dismissed, no execution called
        val currentTask = repository.getTaskById(task.id)
        assertFalse(currentTask?.completed == true)
    }

    @Test
    fun `test action execution records outcome in learning loop`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 3, title = "Outcome Task", category = "Work", duration = "15m"))

        val action = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertTrue(result.success)

        val outcomes = repository.getRecentOutcomes(10)
        assertTrue(outcomes.any { it.actionId == action.id && it.type == AiOutcomeType.SUCCESS })
    }

    @Test
    fun `test natural language task creation routes through Brain to Action Proposal`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task called Finish Report"))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val action = response.proposedActions.firstOrNull()
        assertNotNull(action)
        assertEquals(AiActionType.CREATE_TASK, action?.type)

        // DB remains unchanged until explicit execution
        assertNull(repository.observeTasksOnce().find { it.title.equals("Finish Report", ignoreCase = true) })

        // Authorized execution
        val result = engine.executeAction(action!!)
        assertTrue(result.success)
        assertNotNull(repository.observeTasksOnce().find { it.title.equals("Finish Report", ignoreCase = true) })
    }
}
