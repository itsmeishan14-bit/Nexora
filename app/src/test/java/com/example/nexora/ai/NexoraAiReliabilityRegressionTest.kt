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

    @Test
    fun `11 - end to end goal planning workflow - understand request, create plan, approve, save tasks without duplicates, refresh context`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Step 1: User describes a goal
        viewModel.sendMessage("Plan goal Launch MVP")
        delay(300)

        // Step 2: Nexora understands request and creates a structured plan
        val stateAfterPlan = viewModel.uiState.value
        assertTrue("Proposed plan must contain multiple actions", stateAfterPlan.proposedPlan.size >= 2)
        val goalAction = stateAfterPlan.proposedPlan.find { it.type == AiActionType.CREATE_GOAL }
        assertNotNull("Plan must propose creating the goal", goalAction)
        val taskActions = stateAfterPlan.proposedPlan.filter { it.type == AiActionType.CREATE_TASK }
        assertTrue("Plan must propose tasks", taskActions.isNotEmpty())

        // Step 3: User reviews plan and approves via production ViewModel path
        viewModel.confirmAction()
        delay(400)

        // Step 4: Verify all intended actions executed and reported success
        val stateAfterConfirm = viewModel.uiState.value
        assertNotNull("lastActionResult must be populated", stateAfterConfirm.lastActionResult)
        assertTrue("Plan execution must succeed: ${stateAfterConfirm.lastActionResult?.error}", stateAfterConfirm.lastActionResult!!.success)
        assertNull("Pending proposed action must be cleared", stateAfterConfirm.proposedAction)
        assertTrue("Pending plan list must be empty", stateAfterConfirm.proposedPlan.isEmpty())

        // Step 5: Verify parent goal exists in database and dependent tasks are linked to it with real IDs
        val savedGoals = repository.observeGoalsOnce()
        val savedGoal = savedGoals.find { it.title.equals("Launch MVP", ignoreCase = true) }
        assertNotNull("Parent goal must exist in repository", savedGoal)
        assertTrue("Goal ID must be valid positive ID", savedGoal!!.id > 0L)

        val savedTasks = repository.observeTasksOnce()
        val createdTasks = savedTasks.filter { it.goalTitle.equals("Launch MVP", ignoreCase = true) }
        assertEquals("All proposed tasks must be persisted in repository", taskActions.size, createdTasks.size)
        assertTrue("All task IDs must be valid positive IDs", createdTasks.all { it.id > 0L })

        // Step 6: Retrying must NOT create duplicate tasks
        val firstTaskAction = taskActions.first()
        val retryResult = engine.executeAction(firstTaskAction)
        assertFalse("Retry must not succeed or duplicate existing task", retryResult.success)
        assertEquals("Task count must remain unchanged after retry", savedTasks.size, repository.observeTasksOnce().size)

        // Step 7: Context refreshes with real data
        val context = engine.getContext()
        val tasksInContext = context.tasks.filter { it.goalTitle.equals("Launch MVP", ignoreCase = true) }
        assertEquals("Refreshed context must contain all new tasks", createdTasks.size, tasksInContext.size)
    }

    @Test
    fun `12 - conversational confirmation executes action and updates database and UI state`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 88L, title = "Deploy Beta", category = "Work", duration = "25m"))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Step 1: User asks to complete the task conversationally
        viewModel.sendMessage("Complete task Deploy Beta")
        delay(250)

        // Proposal should be pending
        val stateAfterRequest = viewModel.uiState.value
        assertNotNull("Should propose action", stateAfterRequest.proposedAction)
        assertEquals(AiActionType.COMPLETE_TASK, stateAfterRequest.proposedAction?.type)

        // Step 2: User confirms conversationally: "yes"
        viewModel.sendMessage("yes")
        delay(350)

        // Step 3: Action executed, UI state updated, Room DB updated
        val stateAfterConfirm = viewModel.uiState.value
        assertNotNull("lastActionResult must be set", stateAfterConfirm.lastActionResult)
        assertTrue("Action execution must succeed: ${stateAfterConfirm.lastActionResult?.error}", stateAfterConfirm.lastActionResult!!.success)

        val updatedTask = repository.getTaskById(task.id)
        assertNotNull(updatedTask)
        assertTrue("Task in database must be marked complete", updatedTask!!.completed)
    }

    @Test
    fun `13 - clarification requested when required information is missing`() = runBlocking {
        // Goal action with no title or candidate specified
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "break down goal"))
        assertEquals("Missing title should request clarification", AiResponseType.CLARIFICATION_NEEDED, response.responseType)
        assertTrue("Message should ask for clarification", response.message.contains("Which goal", ignoreCase = true) || response.message.contains("specify", ignoreCase = true))
    }

    @Test
    fun `14 - confirm the same plan twice cannot execute twice`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Learn Rust")
        delay(300)

        val originalPlan = viewModel.uiState.value.proposedPlan
        assertTrue("Must have proposed plan", originalPlan.isNotEmpty())

        // First confirmation
        viewModel.confirmAction()
        delay(400)

        val firstState = viewModel.uiState.value
        assertTrue("First confirmation must succeed", firstState.lastActionResult!!.success)
        val initialTaskCount = repository.observeTasksOnce().size
        val initialGoalCount = repository.observeGoalsOnce().size

        // Attempt second confirmation with the same original plan
        val secondResults = engine.confirmPendingPlan(originalPlan)
        assertFalse("Second confirmation must be rejected", secondResults.first().success)
        assertTrue("Second confirmation must report proposal already consumed: ${secondResults.first().error}",
            secondResults.first().error?.contains("consumed", ignoreCase = true) == true ||
            secondResults.first().message.contains("consumed", ignoreCase = true)
        )

        // Database records must not have duplicated
        assertEquals("Task count must not increase on double confirmation", initialTaskCount, repository.observeTasksOnce().size)
        assertEquals("Goal count must not increase on double confirmation", initialGoalCount, repository.observeGoalsOnce().size)
    }

    @Test
    fun `15 - cancel pending plan prevents any mutations`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Build Skynet")
        delay(300)

        val plan = viewModel.uiState.value.proposedPlan
        assertTrue("Must propose plan", plan.isNotEmpty())

        // User cancels
        viewModel.dismissAction()
        delay(100)

        val stateAfterDismiss = viewModel.uiState.value
        assertNull("Proposed action must be cleared", stateAfterDismiss.proposedAction)
        assertTrue("Proposed plan must be empty", stateAfterDismiss.proposedPlan.isEmpty())

        // Verify proposals are cancelled and cannot be confirmed
        val confirmResults = engine.confirmPendingPlan(plan)
        assertFalse("Cancelled plan cannot be confirmed", confirmResults.first().success)
        assertTrue("Must report cancelled proposal",
            confirmResults.first().error?.contains("cancelled", ignoreCase = true) == true ||
            confirmResults.first().message.contains("cancelled", ignoreCase = true)
        )

        // Verify repository has no mutations
        assertTrue("No goal should be created", repository.observeGoalsOnce().none { it.title.contains("Skynet", ignoreCase = true) })
        assertTrue("No tasks should be created", repository.observeTasksOnce().none { it.goalTitle?.contains("Skynet", ignoreCase = true) == true })
    }

    @Test
    fun `16 - fail first required action ensures dependent actions do not run`() = runBlocking {
        // Goal creation will fail
        repository.failAddGoal = true

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Master Kotlin")
        delay(300)

        val plan = viewModel.uiState.value.proposedPlan
        assertTrue("Plan must contain actions", plan.size >= 2)

        viewModel.confirmAction()
        delay(400)

        val state = viewModel.uiState.value
        assertNotNull(state.lastActionResult)
        assertFalse("Execution must not report overall success when first required action fails", state.lastActionResult!!.success)

        val planResults = state.lastPlanResults
        assertTrue("Plan results must be populated", planResults.isNotEmpty())
        assertFalse("First action (CREATE_GOAL) must fail", planResults.first().success)

        // Subsequent task actions must be skipped due to dependency failure
        val taskResults = planResults.drop(1)
        assertTrue("Dependent tasks must report dependency failure",
            taskResults.all { !it.success && it.error == "Parent goal dependency failed" }
        )

        // Database must have 0 tasks created
        assertEquals("No tasks should be created when parent goal fails", 0, repository.observeTasksOnce().size)
    }

    @Test
    fun `17 - fail later action reports partial success truthfully`() = runBlocking {
        var tasksAdded = 0
        val testRepo = object : MockNexoraRepository() {
            override suspend fun addTask(task: PremiumTask): PremiumTask {
                tasksAdded++
                if (tasksAdded > 1) {
                    return task.copy(id = 0L) // Fail on second task
                }
                return super.addTask(task)
            }
        }
        val customExecutor = AiActionExecutor(testRepo)
        val customEngine = NexoraAiEngine(
            contextBuilder = AiContextBuilder(testRepo),
            aiService = LocalNexoraAiService(providerManager = providerManager),
            providerManager = providerManager,
            actionExecutor = customExecutor,
            toolRegistry = AiToolRegistry(testRepo, customExecutor),
            repository = testRepo
        )

        val customViewModel = NexoraAiViewModel(engine = customEngine, coroutineScope = testScope)
        customViewModel.sendMessage("Plan goal Read Books")
        delay(300)

        customViewModel.confirmAction()
        delay(400)

        val state = customViewModel.uiState.value
        assertNotNull("Must have action result", state.lastActionResult)
        assertFalse("Must NOT claim complete success when a task fails", state.lastActionResult!!.success)
        assertTrue("Message must truthfully report partial execution",
            state.lastActionResult!!.message.contains("Partially executed", ignoreCase = true)
        )
        assertNotNull("Error must be populated with failure reason", state.error)

        // First task succeeded in DB
        val tasksInDb = testRepo.observeTasksOnce()
        assertEquals("Exactly 1 task should be persisted", 1, tasksInDb.size)
    }

    @Test
    fun `18 - retry after partial failure does not duplicate already-completed actions`() = runBlocking {
        var failSecondTask = true
        var taskAddCount = 0
        val testRepo = object : MockNexoraRepository() {
            override suspend fun addTask(task: PremiumTask): PremiumTask {
                taskAddCount++
                if (failSecondTask && taskAddCount > 1) {
                    return task.copy(id = 0L)
                }
                return super.addTask(task)
            }
        }
        val customExecutor = AiActionExecutor(testRepo)
        val customEngine = NexoraAiEngine(
            contextBuilder = AiContextBuilder(testRepo),
            aiService = LocalNexoraAiService(providerManager = providerManager),
            providerManager = providerManager,
            actionExecutor = customExecutor,
            toolRegistry = AiToolRegistry(testRepo, customExecutor),
            repository = testRepo
        )
        val customViewModel = NexoraAiViewModel(engine = customEngine, coroutineScope = testScope)
        customViewModel.sendMessage("Plan goal Fitness Journey")
        delay(300)

        val firstPlan = customViewModel.uiState.value.proposedPlan
        customViewModel.confirmAction()
        delay(400)

        // Attempt 1 was partial failure: 1 goal + 1 task succeeded, 2nd task failed
        assertEquals(1, testRepo.observeGoalsOnce().size)
        assertEquals(1, testRepo.observeTasksOnce().size)

        // Now fix the failure condition and retry the plan
        failSecondTask = false
        val retryPlan = firstPlan.map { customEngine.proposeAction(it.copy(id = UUID.randomUUID().toString())) }
        customViewModel.proposePlan(retryPlan)
        customViewModel.confirmAction()
        delay(400)

        // On retry, goal was not duplicated
        assertEquals("Goal must not be duplicated on retry", 1, testRepo.observeGoalsOnce().size)
        // First task was already existing (skipped), remaining task was created
        val allTasks = testRepo.observeTasksOnce()
        val distinctTaskTitles = allTasks.map { it.title.lowercase().trim() }.distinct()
        assertEquals("Tasks must not have duplicates on retry", distinctTaskTitles.size, allTasks.size)
    }

    @Test
    fun `19 - confirmation for one plan cannot authorize a different plan`() = runBlocking {
        // Propose Plan A
        val planARequest = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Plan goal Project Alpha"))
        val planA = planARequest.proposedActions

        // Propose Plan B
        val planBRequest = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Plan goal Project Beta"))
        val planB = planBRequest.proposedActions

        assertTrue(planA.isNotEmpty())
        assertTrue(planB.isNotEmpty())

        // Attempt to execute Plan B using confirmation tokens from Plan A
        val spoofedPlanB = planB.mapIndexed { index, bAction ->
            val aToken = planA.getOrNull(index)?.parameters?.get("confirmationToken")?.toString() ?: ""
            val params = bAction.parameters.toMutableMap()
            params["confirmationToken"] = aToken
            bAction.copy(parameters = params)
        }

        val results = engine.confirmPendingPlan(spoofedPlanB)
        assertFalse("Spoofed confirmation must fail", results.first().success)
        assertTrue("Must be rejected due to mismatch or invalid token",
            results.first().error?.contains("mismatch", ignoreCase = true) == true ||
            results.first().error?.contains("token", ignoreCase = true) == true ||
            results.first().error?.contains("handle", ignoreCase = true) == true ||
            results.first().error?.contains("proposal", ignoreCase = true) == true
        )

        // No actions from Plan B should have executed
        assertTrue(repository.observeGoalsOnce().none { it.title.contains("Project Beta", ignoreCase = true) })
        assertTrue(repository.observeTasksOnce().none { it.goalTitle?.contains("Project Beta", ignoreCase = true) == true })
    }

    @Test
    fun `20 - conversational confirmation executes entire multi-action plan end to end`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Step 1: User requests plan in chat
        viewModel.sendMessage("Plan goal Launch Podcast")
        delay(300)

        val stateAfterRequest = viewModel.uiState.value
        assertTrue("Plan must propose multiple actions", stateAfterRequest.proposedPlan.size >= 2)

        // Step 2: User confirms conversationally
        viewModel.sendMessage("yes")
        delay(400)

        // Step 3: Verify all actions executed
        val stateAfterConfirm = viewModel.uiState.value
        assertNotNull(stateAfterConfirm.lastActionResult)
        assertTrue("Plan execution must succeed: ${stateAfterConfirm.lastActionResult?.error}", stateAfterConfirm.lastActionResult!!.success)

        val savedGoals = repository.observeGoalsOnce()
        assertTrue("Goal must be created in DB", savedGoals.any { it.title.equals("Launch Podcast", ignoreCase = true) })

        val savedTasks = repository.observeTasksOnce().filter { it.goalTitle.equals("Launch Podcast", ignoreCase = true) }
        assertTrue("Tasks must be created in DB and linked to goal", savedTasks.isNotEmpty())
    }
}
