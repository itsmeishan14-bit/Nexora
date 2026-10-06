package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.util.NexoraSecurity
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class NexoraAiReliabilityRegressionTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var engine: NexoraAiEngine
    private lateinit var providerManager: AiProviderManager
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        NexoraSecurity.clearAllAuthorizations()
        repository = MockNexoraRepository()
        actionExecutor = AiActionExecutor(repository)
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        providerManager = AiProviderManager(localProvider = localProvider)
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
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        NexoraSecurity.clearAllAuthorizations()
    }

    @Test
    fun `1 - successful confirmed action updates persistent data and reports success`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 1L, title = "Write Report", category = "Work", duration = "30m"))
        assertFalse(task.completed)

        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Write Report",
            description = "Complete the write report task",
            taskId = task.id,
            requiresConfirmation = true
        )

        val proposed = engine.proposeAction(action)
        assertTrue(engine.isProposalPending(proposed.id))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.proposeAction(proposed)
        viewModel.confirmAction()

        // Wait for coroutine completion
        delay(250)

        val state = viewModel.uiState.value
        assertNotNull("lastActionResult must be populated", state.lastActionResult)
        assertTrue("Action must report success: ${state.lastActionResult?.error}", state.lastActionResult!!.success)
        assertNull("Error must be null on success", state.error)

        val updatedTask = repository.getTaskById(task.id)
        assertNotNull(updatedTask)
        assertTrue("Persistent task in repository must be completed", updatedTask!!.completed)
    }

    @Test
    fun `2 - rejected or failed action reports failure accurately`() = runBlocking {
        // Attempt executing unconfirmed destructive action directly
        val unconfirmedDelete = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Unauthorized Delete",
            description = "Delete task without confirmation",
            taskId = 999L,
            requiresConfirmation = true
        )

        val result = engine.executeAction(unconfirmedDelete)
        assertFalse("Unconfirmed destructive action must fail", result.success)
        assertNotNull("Failure must have an error description", result.error)

        // Attempt confirming action where repository mutation fails
        repository.failUpdateTask = true
        val task = repository.addTask(PremiumTask(id = 2L, title = "Task Fail", category = "Work", duration = "15m"))
        val completeAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Fail",
            description = "Complete fail task",
            taskId = task.id,
            requiresConfirmation = true
        )
        val proposed = engine.proposeAction(completeAction)

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.proposeAction(proposed)
        viewModel.confirmAction()

        delay(250)

        val state = viewModel.uiState.value
        assertNotNull(state.lastActionResult)
        assertFalse("Failed action must not report success", state.lastActionResult!!.success)
        assertNotNull("Error state must be set on failure", state.error)
    }

    @Test
    fun `3 - failed home action does not produce a success state or trigger completion callback`() = runBlocking {
        val nonExistentTaskAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Nonexistent",
            description = "Complete task that does not exist",
            taskId = 99999L,
            requiresConfirmation = true
        )
        val proposed = engine.proposeAction(nonExistentTaskAction)

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        var onCompleteTriggered = false

        viewModel.executeHomeAction(proposed) {
            onCompleteTriggered = true
        }

        delay(250)

        assertFalse("onComplete callback must NOT be called on failure", onCompleteTriggered)
        val state = viewModel.uiState.value
        assertNotNull("Action result must be populated on failure", state.lastActionResult)
        assertFalse("Action result must indicate failure", state.lastActionResult!!.success)
        assertNull("Failed action must not remain pending on home screen", state.homeProposedAction)
        assertNotNull("Error must be visible to user", state.error)
        assertFalse("Loading state must be reset", state.isLoading)
    }

    @Test
    fun `4 - loading state is reset when execution throws an exception`() = runBlocking {
        val crashingRepo = object : MockNexoraRepository() {
            override suspend fun updateTask(task: PremiumTask): Boolean {
                throw IllegalStateException("Database connection broken")
            }
        }
        val task = crashingRepo.addTask(PremiumTask(id = 1L, title = "Crash Task", category = "Work", duration = "10m"))
        val crashingExecutor = AiActionExecutor(crashingRepo)
        val crashingEngine = NexoraAiEngine(
            contextBuilder = AiContextBuilder(crashingRepo),
            aiService = LocalNexoraAiService(providerManager),
            providerManager = providerManager,
            actionExecutor = crashingExecutor,
            toolRegistry = AiToolRegistry(crashingRepo, crashingExecutor),
            repository = crashingRepo
        )

        val viewModel = NexoraAiViewModel(engine = crashingEngine, coroutineScope = testScope)
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Crash Action",
            description = "Action that triggers crash",
            taskId = task.id,
            requiresConfirmation = true
        )
        val proposed = crashingEngine.proposeAction(action)

        var onCompleteCalled = false
        viewModel.executeHomeAction(proposed) {
            onCompleteCalled = true
        }

        delay(250)

        assertFalse("onComplete must NOT be called on exception", onCompleteCalled)
        val state = viewModel.uiState.value
        assertFalse("isLoading MUST be false even after exception", state.isLoading)
        assertNotNull(state.lastActionResult)
        assertFalse(state.lastActionResult!!.success)
        assertTrue(
            "Error must capture the failure reason",
            state.error?.contains("Database connection broken") == true ||
                state.lastActionResult!!.error?.contains("Database connection broken") == true ||
                state.lastActionResult!!.message.contains("Crash Action")
        )
    }

    @Test
    fun `5 - cancelled expired or consumed proposal cannot be executed again`() = runBlocking {
        // A. Cancelled
        val cancelAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Cancel Action",
            description = "Action to be cancelled",
            taskId = 1L,
            requiresConfirmation = true
        )
        val proposedCancel = engine.proposeAction(cancelAction)
        engine.cancelProposal(proposedCancel.id)
        val cancelResult = engine.confirmPendingAction(proposedCancel)
        assertFalse("Cancelled proposal must not be executable", cancelResult.success)

        // B. Consumed
        val consumeAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Once Only Task",
            description = "Create once only task",
            parameters = mapOf("title" to "Once Only Task"),
            requiresConfirmation = true
        )
        val proposedConsume = engine.proposeAction(consumeAction)
        val firstResult = engine.confirmPendingAction(proposedConsume)
        assertTrue("First execution succeeds: ${firstResult.error}", firstResult.success)

        val retryResult = engine.confirmPendingAction(proposedConsume)
        assertFalse("Consumed proposal cannot be re-executed", retryResult.success)

        // C. Expired
        val expiredAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_ALL_TASKS,
            title = "Delete All Tasks",
            description = "Delete all tasks expired",
            requiresConfirmation = true
        )
        val registeredExpired = NexoraSecurity.registerProposal(expiredAction, ttlMs = -1000L)
        val expiredResult = engine.confirmPendingAction(registeredExpired)
        assertFalse("Expired proposal must not be executable", expiredResult.success)
    }

    @Test
    fun `6 - ambiguous request does not perform mutation and requests clarification`() = runBlocking {
        repository.addTask(PremiumTask(id = 10L, title = "Prepare Presentation Part 1", category = "Work", duration = "30m"))
        repository.addTask(PremiumTask(id = 11L, title = "Prepare Presentation Part 2", category = "Work", duration = "30m"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete Prepare Presentation"))

        assertEquals(AiResponseType.CLARIFICATION_NEEDED, response.responseType)
        assertTrue("No executable mutations should be proposed for ambiguous requests", response.proposedActions.isEmpty())

        val tasksRemaining = repository.observeTasksOnce()
        assertEquals("Both tasks must remain untouched in the database", 2, tasksRemaining.size)
    }

    @Test
    fun `7 - unsupported proactive workload operation is not falsely presented as executable`() = runBlocking {
        repeat(8) { i ->
            repository.addTask(PremiumTask(id = (i + 1).toLong(), title = "Task $i", category = "Work", duration = "10m"))
        }

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        delay(250)

        val state = viewModel.uiState.value
        // Verify no fake RESCHEDULE_TASK is proposed as executable
        assertNull("Home proposed action must be null for unsupported operations", state.homeProposedAction)

        // Verify truthful proactive advisory signal is generated instead
        val workloadSignal = state.proactiveSignals.find { it.type == ProactiveSignalType.WORKLOAD_RISK }
        assertNotNull("Truthful workload advisory signal should be present", workloadSignal)
        assertNull("Advisory signal must not propose unsupported executable action", workloadSignal?.suggestedAction)
    }

    @Test
    fun `8 - provider fallback does not bypass response validation or action authorization`() = runBlocking {
        val failingCloudProvider = object : AiModelProvider {
            override val providerName = "Failing Cloud Provider"
            override suspend fun isAvailable() = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                throw RuntimeException("Cloud API timeout")
            }
            override suspend fun generateStructuredResponse(
                prompt: String,
                context: AiContext,
                conversationContext: AiConversationContext?
            ): AiModelStructuredResponse {
                throw RuntimeException("Cloud API timeout")
            }
        }

        val localProvider = LocalAiProvider()
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = failingCloudProvider)
        val context = AiContext(tasks = listOf(PremiumTask(id = 1L, title = "Real Task", category = "Work", duration = "10m")))

        // 1. Verify fallback works when cloud provider fails
        val fallbackResponse = manager.generateResponse("hello", context)
        assertNotNull(fallbackResponse.text)

        // 2. Verify cloud provider hallucinating non-existent ID is filtered by validateResponse
        val hallucinatingCloudProvider = object : AiModelProvider {
            override val providerName = "Hallucinating Cloud Provider"
            override suspend fun isAvailable() = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                return AiModelResponse("Hallucinated", "cloud-hallucinate")
            }
            override suspend fun generateStructuredResponse(
                prompt: String,
                context: AiContext,
                conversationContext: AiConversationContext?
            ): AiModelStructuredResponse {
                return AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.COMPLETE_TASK,
                        title = "Complete 999999",
                        reason = "Hallucinated reason"
                    ),
                    actions = listOf(
                        AiAction(
                            id = "hallucinated_action",
                            type = AiActionType.COMPLETE_TASK,
                            title = "Complete 999999",
                            description = "Complete task 999999",
                            taskId = 999999L,
                            parameters = mapOf("taskId" to 999999L),
                            requiresConfirmation = true
                        )
                    ),
                    modelName = "cloud-hallucinate"
                )
            }
        }

        val managerWithHallucinatingCloud = AiProviderManager(localProvider = localProvider, cloudProvider = hallucinatingCloudProvider)
        val structuredResponse = managerWithHallucinatingCloud.generateStructuredResponse("complete task", context)

        assertTrue(
            "Hallucinated action targeting non-existent task must be filtered during response validation",
            structuredResponse.actions.isEmpty()
        )
    }

    @Test
    fun `9 - context and visible UI state are refreshed after successful mutations`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 50L, title = "Task To Complete", category = "Work", duration = "20m"))
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete 50",
            description = "Complete task 50",
            taskId = task.id,
            requiresConfirmation = true
        )
        val proposed = engine.proposeAction(action)

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.proposeAction(proposed)
        viewModel.confirmAction()

        delay(300)

        val state = viewModel.uiState.value
        assertNotNull(state.lastActionResult)
        assertTrue(state.lastActionResult!!.success)

        val freshContext = engine.getContext()
        val completedTaskInContext = freshContext.tasks.find { it.id == task.id }
        assertNotNull(completedTaskInContext)
        assertTrue("Context must reflect mutation", completedTaskInContext!!.completed)
    }

    @Test
    fun `10 - existing safe operations continue to work without confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 1L, title = "Open Target", category = "Work", duration = "10m"))
        val openAction = AiAction(
            type = AiActionType.OPEN_TASK,
            title = "Open Task",
            description = "Open task",
            taskId = task.id,
            requiresConfirmation = false
        )
        val openResult = engine.executeAction(openAction)
        assertTrue("OPEN_TASK is a safe read-only action and must succeed", openResult.success)

        val insightAction = AiAction(
            type = AiActionType.SHOW_INSIGHT,
            title = "Show Productive Insight",
            description = "Show insight to user",
            requiresConfirmation = false
        )
        val insightResult = engine.executeAction(insightAction)
        assertTrue("SHOW_INSIGHT is a safe action and must succeed", insightResult.success)
    }
}
