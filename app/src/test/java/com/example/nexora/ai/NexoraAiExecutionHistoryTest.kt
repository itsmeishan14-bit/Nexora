package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.util.NexoraSecurity
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class NexoraAiExecutionHistoryTest {

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

    // ─────────────────────────────────────────────────────────────────────────
    // TEST A: Single successful action
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test A - single successful action creates and persists complete execution record`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 10L, title = "Write Documentation", category = "Work", duration = "30m"))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Complete task Write Documentation")
        delay(250)

        val pendingAction = viewModel.uiState.value.proposedAction
        assertNotNull("Action should be proposed", pendingAction)
        assertEquals(AiActionType.COMPLETE_TASK, pendingAction?.type)

        viewModel.confirmAction()
        delay(300)

        val state = viewModel.uiState.value
        assertNotNull("lastActionResult should be present", state.lastActionResult)
        assertTrue("Action execution should succeed", state.lastActionResult!!.success)

        val record = state.lastExecutionRecord
        assertNotNull("lastExecutionRecord should be populated", record)
        assertEquals(ExecutionOverallStatus.SUCCESS, record!!.overallStatus)
        assertEquals(1, record.totalProposedActions)
        assertEquals(1, record.executedActionCount)
        assertEquals(true, record.userConfirmed)
        assertEquals(1, record.actionExecutions.size)

        val childAction = record.actionExecutions.first()
        assertEquals(ActionExecutionStatus.SUCCESS, childAction.status)
        assertEquals(1, childAction.executionOrder)
        assertEquals(task.id, childAction.affectedTaskId)
        assertNull(childAction.failureReason)

        // Verify persisted in repository
        val persistedRecords = repository.getRecentExecutionRecords(5)
        assertTrue("Execution record must be persisted in repository", persistedRecords.any { it.id == record.id })
        val persisted = repository.getExecutionRecordById(record.id)
        assertNotNull("Record must be retrievable by ID", persisted)
        assertEquals(ExecutionOverallStatus.SUCCESS, persisted!!.overallStatus)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST B: Successful multi-action plan
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test B - successful multi-action plan preserves relationship and child execution details`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Plan goal Launch Podcast")
        delay(300)

        val proposedPlan = viewModel.uiState.value.proposedPlan
        assertTrue("Multi-action plan should be proposed", proposedPlan.size >= 2)

        viewModel.confirmAction()
        delay(400)

        val state = viewModel.uiState.value
        assertNotNull("Plan result must be populated", state.lastActionResult)
        assertTrue("Plan execution should succeed", state.lastActionResult!!.success)

        val record = state.lastExecutionRecord
        assertNotNull("Execution record must be present", record)
        assertEquals(ExecutionOverallStatus.SUCCESS, record!!.overallStatus)
        assertEquals(proposedPlan.size, record.totalProposedActions)
        assertEquals(proposedPlan.size, record.executedActionCount)
        assertEquals(proposedPlan.size, record.actionExecutions.size)
        assertEquals(true, record.userConfirmed)

        // Verify child actions
        record.actionExecutions.forEachIndexed { index, child ->
            assertEquals(index + 1, child.executionOrder)
            assertEquals(ActionExecutionStatus.SUCCESS, child.status)
            assertNull("No failure reason on successful action", child.failureReason)
            if (child.actionType == AiActionType.CREATE_GOAL) {
                assertNotNull("Goal action must record affectedGoalId", child.affectedGoalId)
                assertTrue(child.affectedGoalId!! > 0L)
            } else if (child.actionType == AiActionType.CREATE_TASK) {
                assertNotNull("Task action must record affectedTaskId", child.affectedTaskId)
                assertTrue(child.affectedTaskId!! > 0L)
            }
        }

        // Verify persisted in repository
        val persisted = repository.getExecutionRecordById(record.id)
        assertNotNull(persisted)
        assertEquals(proposedPlan.size, persisted!!.actionExecutions.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST C: Partial plan failure
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test C - partial plan failure records individual success, failure, and skipped status accurately`() = runBlocking {
        var tasksAdded = 0
        val testRepo = object : MockNexoraRepository() {
            override suspend fun addTask(task: PremiumTask): PremiumTask {
                tasksAdded++
                if (tasksAdded > 1) {
                    // Fail on second task
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
        val viewModel = NexoraAiViewModel(engine = customEngine, coroutineScope = testScope)

        viewModel.sendMessage("Plan goal Read Books")
        delay(300)

        viewModel.confirmAction()
        delay(400)

        val state = viewModel.uiState.value
        assertNotNull("lastActionResult must be present", state.lastActionResult)
        assertFalse("Plan must NOT report success when a task fails", state.lastActionResult!!.success)

        val record = state.lastExecutionRecord
        assertNotNull("Execution record must exist", record)
        assertEquals("Overall status must be PARTIAL", ExecutionOverallStatus.PARTIAL, record!!.overallStatus)
        assertTrue("At least one action succeeded", record.actionExecutions.any { it.status == ActionExecutionStatus.SUCCESS })
        assertTrue("At least one action failed", record.actionExecutions.any { it.status == ActionExecutionStatus.FAILED })

        val failedAction = record.actionExecutions.find { it.status == ActionExecutionStatus.FAILED }
        assertNotNull("Failed action must have failure reason", failedAction?.failureReason)
        assertEquals(ActionFailureReason.DATABASE_FAILURE, failedAction?.failureReason)

        // Explainable lines contain transparent breakdown
        val lines = record.getExplainableLines()
        assertTrue("Explainable lines must contain success indicator", lines.any { it.startsWith("✓") })
        assertTrue("Explainable lines must contain failure indicator", lines.any { it.startsWith("✕") })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST D: Complete plan failure
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test D - complete plan failure records overall FAILURE and marks dependent actions skipped`() = runBlocking {
        repository.failAddGoal = true

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Master Kotlin")
        delay(300)

        viewModel.confirmAction()
        delay(400)

        val state = viewModel.uiState.value
        assertNotNull(state.lastActionResult)
        assertFalse("Execution must not report overall success", state.lastActionResult!!.success)

        val record = state.lastExecutionRecord
        assertNotNull(record)
        assertEquals("Overall status must be FAILURE", ExecutionOverallStatus.FAILURE, record!!.overallStatus)

        val firstAction = record.actionExecutions.first()
        assertEquals(AiActionType.CREATE_GOAL, firstAction.actionType)
        assertEquals(ActionExecutionStatus.FAILED, firstAction.status)
        assertEquals(ActionFailureReason.DATABASE_FAILURE, firstAction.failureReason)

        // Dependent task actions were skipped due to parent goal dependency failure
        val skippedActions = record.actionExecutions.drop(1)
        assertTrue("All subsequent tasks must be SKIPPED", skippedActions.all { it.status == ActionExecutionStatus.SKIPPED })
        assertTrue("Skipped tasks must have DEPENDENCY_FAILURE reason", skippedActions.all { it.failureReason == ActionFailureReason.DEPENDENCY_FAILURE })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST E: Cancellation
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test E - cancellation records CANCELLED status and makes no database mutations`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Run Marathon")
        delay(300)

        val proposed = viewModel.uiState.value.proposedPlan
        assertTrue("Plan should be proposed", proposed.isNotEmpty())

        viewModel.dismissAction()
        delay(200)

        val state = viewModel.uiState.value
        assertNull("Proposed action should be cleared", state.proposedAction)
        assertTrue("Proposed plan should be cleared", state.proposedPlan.isEmpty())

        val record = state.lastExecutionRecord
        assertNotNull("Cancellation record should be recorded", record)
        assertEquals(ExecutionOverallStatus.CANCELLED, record!!.overallStatus)
        assertEquals(false, record.userConfirmed)
        assertEquals(proposed.size, record.totalProposedActions)
        assertEquals(0, record.executedActionCount)

        // Verify no goals or tasks created
        assertEquals(0, repository.observeGoalsOnce().size)
        assertEquals(0, repository.observeTasksOnce().size)

        // Verify record persisted in repository
        val persisted = repository.getExecutionRecordById(record.id)
        assertNotNull("Cancellation record must be persisted", persisted)
        assertEquals(ExecutionOverallStatus.CANCELLED, persisted!!.overallStatus)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST F: Authorization rejection
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test F - authorization rejection produces REJECTED execution record`() = runBlocking {
        val unproposedAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete unproposed task",
            description = "Attempting unconfirmed delete",
            taskId = 999L,
            requiresConfirmation = true
        )

        // Direct confirmation call on unproposed action
        val result = engine.confirmPendingAction(unproposedAction)
        assertFalse("Unproposed action confirmation must fail", result.success)

        val record = engine.lastExecutionRecord
        assertNotNull("Rejection execution record must be generated", record)
        assertEquals(ExecutionOverallStatus.REJECTED, record!!.overallStatus)
        assertEquals(ActionExecutionStatus.FAILED, record.actionExecutions.first().status)
        assertEquals(ActionFailureReason.AUTHORIZATION_FAILURE, record.actionExecutions.first().failureReason)

        val persisted = repository.getExecutionRecordById(record.id)
        assertNotNull(persisted)
        assertEquals(ExecutionOverallStatus.REJECTED, persisted!!.overallStatus)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST G: Duplicate action
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test G - duplicate action execution captures DUPLICATE failure reason`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 55L, title = "Original Task", category = "Work", duration = "15m"))

        val duplicateCreateAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Original Task",
            description = "Duplicate task creation",
            parameters = mapOf("title" to "Original Task", "category" to "Work", "duration" to "15m"),
            requiresConfirmation = true
        )

        val proposed = engine.proposeAction(duplicateCreateAction)
        val result = engine.confirmPendingAction(proposed)

        assertFalse("Duplicate task creation must fail", result.success)
        assertTrue(
            "Error or message should mention duplicate or already exists: ${result.error} / ${result.message}",
            result.error?.contains("duplicate", ignoreCase = true) == true ||
                result.message.contains("already exists", ignoreCase = true)
        )

        val record = engine.lastExecutionRecord
        assertNotNull(record)
        val child = record!!.actionExecutions.first()
        assertEquals(ActionExecutionStatus.FAILED, child.status)
        assertEquals(ActionFailureReason.DUPLICATE, child.failureReason)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST H: Retry after partial failure
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test H - retry after partial failure does not duplicate already-completed actions`() = runBlocking {
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

        val firstRecord = customViewModel.uiState.value.lastExecutionRecord
        assertNotNull(firstRecord)
        assertEquals(ExecutionOverallStatus.PARTIAL, firstRecord!!.overallStatus)

        // Fix repository failure
        failSecondTask = false

        // User asks to retry or complete remaining tasks
        customViewModel.sendMessage("Plan goal Fitness Journey")
        delay(300)

        val retryPlan = customViewModel.uiState.value.proposedPlan
        customViewModel.confirmAction()
        delay(400)

        val secondRecord = customViewModel.uiState.value.lastExecutionRecord
        assertNotNull(secondRecord)
        assertNotEquals(firstRecord.id, secondRecord!!.id)

        // All tasks exist without duplicates
        val tasksInDb = testRepo.observeTasksOnce()
        val distinctTitles = tasksInDb.map { it.title.trim().lowercase() }.distinct()
        assertEquals("Duplicate tasks must not be created upon retry", distinctTitles.size, tasksInDb.size)

        // Both execution records are preserved in history
        val allHistory = testRepo.getRecentExecutionRecords(10)
        assertTrue("First execution record preserved in history", allHistory.any { it.id == firstRecord.id })
        assertTrue("Second execution record preserved in history", allHistory.any { it.id == secondRecord.id })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST I: Persisted execution record contains correct action count
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test I - persisted execution record contains correct action count`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Write Novel")
        delay(300)

        val proposedCount = viewModel.uiState.value.proposedPlan.size
        assertTrue(proposedCount >= 2)

        viewModel.confirmAction()
        delay(400)

        val record = viewModel.uiState.value.lastExecutionRecord
        assertNotNull(record)

        val persisted = repository.getExecutionRecordById(record!!.id)
        assertNotNull(persisted)
        assertEquals(proposedCount, persisted!!.totalProposedActions)
        assertEquals(proposedCount, persisted.executedActionCount)
        assertEquals(proposedCount, persisted.actionExecutions.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST J: Execution order is preserved
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test J - execution order is preserved sequentially 1 to N`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Launch Product")
        delay(300)

        viewModel.confirmAction()
        delay(400)

        val record = viewModel.uiState.value.lastExecutionRecord
        assertNotNull(record)

        val expectedOrders = (1..record!!.actionExecutions.size).toList()
        val actualOrders = record.actionExecutions.map { it.executionOrder }
        assertEquals("Execution orders must be strictly sequential 1..N", expectedOrders, actualOrders)

        // Also verify in repository persistence
        val persisted = repository.getExecutionRecordById(record.id)
        val persistedOrders = persisted!!.actionExecutions.map { it.executionOrder }
        assertEquals(expectedOrders, persistedOrders)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST K: Correct task and goal IDs are recorded
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test K - correct task and goal IDs are recorded in child execution records`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Plan goal Build Portfolio")
        delay(300)

        viewModel.confirmAction()
        delay(400)

        val record = viewModel.uiState.value.lastExecutionRecord
        assertNotNull(record)

        val goalAction = record!!.actionExecutions.find { it.actionType == AiActionType.CREATE_GOAL }
        assertNotNull("Goal action record should exist", goalAction)
        assertNotNull("affectedGoalId should not be null", goalAction?.affectedGoalId)
        val createdGoal = repository.getGoalById(goalAction!!.affectedGoalId!!)
        assertNotNull("Goal with recorded ID must exist in DB", createdGoal)

        val taskActions = record.actionExecutions.filter { it.actionType == AiActionType.CREATE_TASK }
        assertTrue("Task action records should exist", taskActions.isNotEmpty())
        for (taskAct in taskActions) {
            assertNotNull("affectedTaskId should not be null", taskAct.affectedTaskId)
            val createdTask = repository.getTaskById(taskAct.affectedTaskId!!)
            assertNotNull("Task with recorded ID must exist in DB", createdTask)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST L: UI receives the truthful final result
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test L - UI receives truthful final result for all execution outcomes`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // 1. Cancellation result
        viewModel.sendMessage("Plan goal Morning Routine")
        delay(300)
        assertNotNull(viewModel.uiState.value.proposedPlan.isNotEmpty())
        viewModel.dismissAction()
        delay(150)

        // Check cancelled result
        assertEquals(ExecutionOverallStatus.CANCELLED, viewModel.uiState.value.lastExecutionRecord?.overallStatus)

        // 2. Failure execution
        repository.failAddGoal = true
        viewModel.sendMessage("Plan goal Doomed Goal")
        delay(300)
        viewModel.confirmAction()
        delay(400)

        val failedState = viewModel.uiState.value
        assertNotNull(failedState.lastActionResult)
        assertFalse("UI must NOT report success when action fails", failedState.lastActionResult!!.success)
        assertEquals(ExecutionOverallStatus.FAILURE, failedState.lastExecutionRecord?.overallStatus)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST M: History cannot execute an old action (read-only)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test M - execution history is strictly read-only and cannot execute an old action`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 200L, title = "Secure Task", category = "Work", duration = "10m"))
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Secure Task",
            description = "Delete task",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Legitimate execution
        val proposed = engine.proposeAction(action)
        val result = engine.confirmPendingAction(proposed)
        assertTrue(result.success)

        val record = engine.lastExecutionRecord
        assertNotNull(record)
        val child = record!!.actionExecutions.first()

        // Attempting to construct an AiAction from historical record and execute directly
        val forgedActionFromHistory = AiAction(
            id = child.actionId,
            type = child.actionType,
            title = child.actionTitle,
            description = "Replay attempt",
            taskId = child.affectedTaskId,
            goalId = child.affectedGoalId,
            requiresConfirmation = true
        )

        // Direct executor call without authorization must fail
        val directResult = actionExecutor.execute(forgedActionFromHistory)
        assertFalse("Direct execution from history must be rejected by security", directResult.success)
        assertTrue(
            "Direct execution without authorization must be rejected: ${directResult.error}",
            directResult.error == "Authorization error" || directResult.error == "Duplicate execution rejected"
        )

        // Engine confirm call without new proposal must fail
        val engineResult = engine.confirmPendingAction(forgedActionFromHistory)
        assertFalse("Engine confirmation without active proposal must be rejected", engineResult.success)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST N: Conversational confirmation still records the complete plan
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `test N - conversational confirmation records complete multi-action plan execution`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // User requests goal plan
        viewModel.sendMessage("Plan goal Learn French")
        delay(300)

        val stateAfterPlan = viewModel.uiState.value
        assertTrue("Multi-action plan proposed", stateAfterPlan.proposedPlan.size >= 2)

        // Conversational confirmation: "yes"
        viewModel.sendMessage("yes")
        delay(400)

        val stateAfterConfirm = viewModel.uiState.value
        assertNotNull("Result should be set", stateAfterConfirm.lastActionResult)
        assertTrue("Conversational confirmation must succeed: ${stateAfterConfirm.lastActionResult?.error}", stateAfterConfirm.lastActionResult!!.success)

        val record = stateAfterConfirm.lastExecutionRecord
        assertNotNull("Execution record must be recorded from conversational confirmation", record)
        assertEquals(ExecutionOverallStatus.SUCCESS, record!!.overallStatus)
        assertEquals(true, record.userConfirmed)
        assertEquals(stateAfterPlan.proposedPlan.size, record.actionExecutions.size)

        // Verify persisted in repository
        val persisted = repository.getExecutionRecordById(record.id)
        assertNotNull(persisted)
        assertEquals(ExecutionOverallStatus.SUCCESS, persisted!!.overallStatus)
    }
}
