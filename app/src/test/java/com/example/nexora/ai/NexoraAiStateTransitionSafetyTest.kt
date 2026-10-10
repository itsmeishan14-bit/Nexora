package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.util.NexoraSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Production-path regression tests for AI conversation state transitions,
 * proposal binding, multi-action execution integrity, and execution safety.
 */
class NexoraAiStateTransitionSafetyTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        NexoraSecurity.clearAllAuthorizations()
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
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        NexoraSecurity.clearAllAuthorizations()
    }

    // =========================================================================
    // SCENARIO A — New request while awaiting confirmation
    // =========================================================================
    @Test
    fun `Scenario A - New request while awaiting confirmation cancels stale proposal and prevents execution on later yes`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Turn 1: Propose task
        viewModel.sendMessage("Create a task to study Java")
        delay(300)
        val state1 = viewModel.uiState.value
        assertNotNull("Should have proposed action", state1.proposedAction)
        assertTrue("Task title check", state1.proposedAction?.title?.contains("study java", ignoreCase = true) == true)
        val proposedActionId = state1.proposedAction!!.id
        assertTrue("Proposal should be pending in security", NexoraSecurity.isProposalPending(proposedActionId))

        // Turn 2: Interrupted by new informational request
        viewModel.sendMessage("What should I focus on?")
        delay(300)
        val state2 = viewModel.uiState.value
        // Proposed action card in UI must be cleared
        assertNull("Stale proposed action must be cleared from UI state", state2.proposedAction)
        assertTrue("Proposed plan must be empty", state2.proposedPlan.isEmpty())
        assertFalse("Security proposal must be cancelled/invalidated", NexoraSecurity.isProposalPending(proposedActionId))

        // Turn 3: User says "yes" later
        viewModel.sendMessage("Yes")
        delay(300)
        val state3 = viewModel.uiState.value
        // Must explain nothing awaiting confirmation
        val lastMessage = state3.chatMessages.last()
        assertTrue(
            "Must state no pending action awaiting confirmation, got: ${lastMessage.text}",
            lastMessage.text.contains("no pending", ignoreCase = true)
        )
        // Verify database: no task named "study java" was created!
        val tasks = repository.observeTasksOnce()
        assertTrue("Stale task must NOT have been executed into DB", tasks.none { it.title.contains("study java", ignoreCase = true) })
    }

    // =========================================================================
    // SCENARIO B — Clarification interrupted by a new request
    // =========================================================================
    @Test
    fun `Scenario B - Clarification interrupted by a new directive does not swallow new request`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "Task 1", category = "Work", duration = "30m", priority = TaskPriority.HIGH))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Turn 1: Trigger clarification
        viewModel.sendMessage("Break down my goal")
        delay(300)
        val state1 = viewModel.uiState.value
        assertNotNull("Must have active clarification", state1.conversationalState.activeClarification)

        // Turn 2: User interrupts with "Plan my day"
        viewModel.sendMessage("Plan my day")
        delay(400)
        val state2 = viewModel.uiState.value
        // Must not be asking which goal; must generate daily plan
        assertNotNull("Daily plan should be generated", state2.dailyPlan)
        assertNull("Old clarification must not linger in conversational state", state2.conversationalState.activeClarification)
        val lastAiMessage = state2.chatMessages.last { !it.isFromUser }
        assertFalse("Must not ask which goal", lastAiMessage.text.contains("Which goal would you like to decompose", ignoreCase = true))

        // Turn 3: Subsequent message does not silently resume the wrong intent
        viewModel.sendMessage("What is goal decomposition?")
        delay(300)
        val state3 = viewModel.uiState.value
        val explainMsg = state3.chatMessages.last { !it.isFromUser }
        assertTrue("Must answer conceptual explanation", explainMsg.text.contains("Goal decomposition is the process", ignoreCase = true))
    }

    // =========================================================================
    // SCENARIO C — Confirmation without a pending proposal
    // =========================================================================
    @Test
    fun `Scenario C - Confirmation without pending proposal performs no mutation and explains clearly`() = runBlocking {
        val initialTasks = repository.observeTasksOnce().size
        val initialGoals = repository.observeGoalsOnce().size

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Yes"))
        assertEquals(AiResponseType.NO_ACTION, response.responseType)
        assertTrue(
            "Response should explain nothing is awaiting confirmation",
            response.message.contains("no pending actions or plans", ignoreCase = true)
        )
        assertEquals(0, response.proposedActions.size)

        // Verify no DB mutations
        assertEquals(initialTasks, repository.observeTasksOnce().size)
        assertEquals(initialGoals, repository.observeGoalsOnce().size)
    }

    // =========================================================================
    // SCENARIO D — Cancellation followed by confirmation
    // =========================================================================
    @Test
    fun `Scenario D - Cancellation invalidates authorization and subsequent yes cannot execute`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Turn 1: Propose task
        viewModel.sendMessage("Create a task to write tests")
        delay(300)
        val state1 = viewModel.uiState.value
        assertNotNull(state1.proposedAction)
        val actionId = state1.proposedAction!!.id

        // Turn 2: User says Cancel
        viewModel.sendMessage("Cancel")
        delay(300)
        val state2 = viewModel.uiState.value
        assertNull("Proposed action must be cleared from UI", state2.proposedAction)
        assertFalse("Security authorization/proposal must be cancelled", NexoraSecurity.isProposalPending(actionId))

        // Turn 3: User says Yes
        viewModel.sendMessage("Yes")
        delay(300)
        val state3 = viewModel.uiState.value
        val lastMsg = state3.chatMessages.last { !it.isFromUser }
        assertTrue("Must report no pending actions", lastMsg.text.contains("no pending", ignoreCase = true))

        // Verify database: no task was created
        val tasks = repository.observeTasksOnce()
        assertTrue("Cancelled action must not be executed", tasks.none { it.title.contains("write tests", ignoreCase = true) })
    }

    // =========================================================================
    // SCENARIO E — Expired context and authorization
    // =========================================================================
    @Test
    fun `Scenario E - Expired context cannot execute on confirmation`() = runBlocking {
        // Propose an action with a short 1ms TTL
        val baseAction = AiAction(
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Expired Test",
            description = "Create task Expired Test",
            parameters = mapOf("title" to "Expired Test"),
            requiresConfirmation = true
        )
        val registered = engine.proposeAction(baseAction, ttlMs = 1L)
        // Sleep to ensure expiration
        Thread.sleep(10)
        assertFalse("Proposal should now be expired", engine.isProposalPending(registered.id))

        // Attempt confirmation via engine
        val confirmResponse = engine.processRequest(AiRequest(
            type = AiRequestType.CHAT,
            userMessage = "Yes",
            conversationContext = AiConversationContext(
                pendingAction = registered,
                pendingPlan = listOf(registered),
                timestamp = System.currentTimeMillis() - 10 * 60 * 1000L // 10 minutes ago
            )
        ))

        // Expiration must be caught
        assertEquals(AiResponseType.NO_ACTION, confirmResponse.responseType)
        assertTrue(
            "Must indicate expired proposal or no pending actions",
            confirmResponse.message.contains("no pending", ignoreCase = true) || confirmResponse.message.contains("expired", ignoreCase = true)
        )

        // Verify DB: no task created
        val tasks = repository.observeTasksOnce()
        assertTrue(tasks.none { it.title.contains("Expired Test", ignoreCase = true) })
    }

    // =========================================================================
    // SECTION 3 & 4 — Exact Proposal Binding & Deterministic Interpretation
    // =========================================================================
    @Test
    fun `Double tap and duplicate execution protection`() = runBlocking {
        val createdTask = repository.addTask(PremiumTask(id = 50L, title = "Task to complete", category = "Work", duration = "10m", completed = false))

        val action = AiAction(
            id = "complete-action-1",
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task: Task to complete",
            description = "Complete task",
            taskId = createdTask.id,
            requiresConfirmation = true
        )
        val proposed = engine.proposeAction(action)

        // First confirmation succeeds
        val results1 = engine.confirmPendingPlan(listOf(proposed))
        assertTrue("First execution should succeed", results1.first().success)
        assertTrue(repository.getTaskById(createdTask.id)?.completed == true)

        // Duplicate submission of same action is rejected
        val results2 = engine.confirmPendingPlan(listOf(proposed))
        assertFalse("Second execution must be rejected as already executed/consumed", results2.first().success)
    }

    @Test
    fun `Tampered parameter execution is rejected`() = runBlocking {
        val originalAction = AiAction(
            id = "create-task-tamper",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Original Title",
            description = "Create task Original Title",
            parameters = mapOf("title" to "Original Title"),
            requiresConfirmation = true
        )
        val proposed = engine.proposeAction(originalAction)

        // Modify parameter before confirmation
        val tamperedAction = proposed.copy(parameters = proposed.parameters + ("title" to "Tampered Title"))

        val results = engine.confirmPendingPlan(listOf(tamperedAction))
        assertFalse("Tampered execution must be rejected due to fingerprint mismatch", results.first().success)
        assertEquals("Parameters modified", results.first().error)
    }

    @Test
    fun `Deterministic clarification follow-ups - ordinals and specific names`() = runBlocking {
        val goal1 = repository.addGoal(NexoraGoal(id = 101L, title = "Learn Android", category = "Work", targetDate = "2026-12-31", progress = 0f))
        val goal2 = repository.addGoal(NexoraGoal(id = 102L, title = "Master Kotlin", category = "Work", targetDate = "2026-12-31", progress = 0f))

        val pipeline = AdvancedLocalLanguagePipeline()
        val context = AiContext(goals = listOf(goal1, goal2))

        val clar = AiClarification(
            question = "Which goal would you like to decompose?",
            intent = AiDecisionType.DECOMPOSE_GOAL,
            missingField = "goalId",
            candidates = listOf(goal1.id, goal2.id),
            originalQuery = "Break down my goal"
        )
        val convContext = AiConversationContext(activeClarification = clar)

        // "The first one"
        val r1 = pipeline.process("The first one", context, convContext)
        assertEquals(AiDecisionType.DECOMPOSE_GOAL, r1.intent)
        assertEquals(goal1.id, r1.targetGoalId)

        // "The second goal"
        val r2 = pipeline.process("The second goal", context, convContext)
        assertEquals(AiDecisionType.DECOMPOSE_GOAL, r2.intent)
        assertEquals(goal2.id, r2.targetGoalId)

        // "Actually, plan my day"
        val r3 = pipeline.process("Actually, plan my day", context, convContext)
        assertEquals(AiDecisionType.DAILY_PLAN, r3.intent)

        // "Forget that; show my goals"
        val r4 = pipeline.process("Forget that; show my goals", context, convContext)
        assertEquals(AiDecisionType.SHOW_INSIGHT, r4.intent)

        // "What about tomorrow?"
        val r5 = pipeline.process("What about tomorrow?", context, convContext)
        assertEquals(AiDecisionType.SHOW_INSIGHT, r5.intent)
        assertNotNull(r5.temporalRange)
    }

    // =========================================================================
    // SECTION 5 — Multi-Action Execution Integrity
    // =========================================================================
    @Test
    fun `Multi-action execution parent goal and dependent tasks linkage`() = runBlocking {
        val parentGoalAction = AiAction(
            id = "multi-goal-1",
            type = AiActionType.CREATE_GOAL,
            title = "Create Goal: Cloud Certification",
            description = "Create Cloud Certification goal",
            parameters = mapOf("title" to "Cloud Certification", "category" to "Career"),
            requiresConfirmation = true
        )
        val depTaskAction1 = AiAction(
            id = "multi-task-1",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Study Networking",
            description = "Create Study Networking task",
            parameters = mapOf("title" to "Study Networking", "goalTitle" to "Cloud Certification"),
            requiresConfirmation = true
        )
        val depTaskAction2 = AiAction(
            id = "multi-task-2",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Practice Exams",
            description = "Create Practice Exams task",
            parameters = mapOf("title" to "Practice Exams", "goalTitle" to "Cloud Certification"),
            requiresConfirmation = true
        )

        val plan = listOf(parentGoalAction, depTaskAction1, depTaskAction2)
        val proposedPlan = plan.map { engine.proposeAction(it) }

        // Confirm and execute plan
        val results = engine.confirmPendingPlan(proposedPlan)
        assertEquals(3, results.size)
        assertTrue("Goal creation must succeed", results[0].success)
        assertTrue("Task 1 creation must succeed", results[1].success)
        assertTrue("Task 2 creation must succeed", results[2].success)

        val createdGoal = repository.observeGoalsOnce().find { it.title == "Cloud Certification" }
        assertNotNull("Goal must exist in DB", createdGoal)

        // Verify dependent tasks used the real persisted goal ID
        assertEquals("Task 1 must link to real persisted goal ID", createdGoal!!.id, results[1].affectedGoalId)
        assertEquals("Task 2 must link to real persisted goal ID", createdGoal.id, results[2].affectedGoalId)

        // Verify execution history recorded truthful SUCCESS
        val history = engine.getRecentExecutionRecords(5)
        assertTrue("History must record the execution", history.isNotEmpty())
        val lastRecord = history.first()
        assertEquals(ExecutionOverallStatus.SUCCESS, lastRecord.overallStatus)
        assertEquals(3, lastRecord.successActionCount)
    }

    @Test
    fun `Multi-action failed prerequisite skips dependent actions truthfully`() = runBlocking {
        // Goal creation will fail due to blank title
        val invalidGoalAction = AiAction(
            id = "fail-goal-1",
            type = AiActionType.CREATE_GOAL,
            title = "Create Goal: ",
            description = "Create Goal blank",
            parameters = mapOf("title" to ""),
            requiresConfirmation = true
        )
        val depTaskAction = AiAction(
            id = "dep-task-1",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Dependent Task",
            description = "Create dependent task",
            parameters = mapOf("title" to "Dependent Task", "goalTitle" to ""),
            requiresConfirmation = true
        )

        val proposedPlan = listOf(invalidGoalAction, depTaskAction).map { engine.proposeAction(it) }

        val results = engine.confirmPendingPlan(proposedPlan)
        assertEquals(2, results.size)
        assertFalse("Goal creation must fail", results[0].success)
        assertFalse("Dependent task must be skipped", results[1].success)
        assertTrue(
            "Dependent task must explain prerequisite failure",
            results[1].message.contains("Skipped", ignoreCase = true) || results[1].error?.contains("dependency", ignoreCase = true) == true
        )

        // Verify execution record is FAILURE or PARTIAL
        val history = engine.getRecentExecutionRecords(1)
        assertEquals(ExecutionOverallStatus.FAILURE, history.first().overallStatus)
    }

    @Test
    fun `Retrying partially completed plan does not duplicate successful mutations`() = runBlocking {
        val goalAction = AiAction(
            id = "retry-goal-1",
            type = AiActionType.CREATE_GOAL,
            title = "Create Goal: AI Mastery",
            description = "Create goal AI Mastery",
            parameters = mapOf("title" to "AI Mastery", "category" to "Study"),
            requiresConfirmation = true
        )
        val taskAction1 = AiAction(
            id = "retry-task-1",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Read ML papers",
            description = "Create task Read ML papers",
            parameters = mapOf("title" to "Read ML papers", "goalTitle" to "AI Mastery"),
            requiresConfirmation = true
        )
        // Task 2 initially has invalid duration -> fails execution
        val taskAction2 = AiAction(
            id = "retry-task-2",
            type = AiActionType.CREATE_TASK,
            title = "Create Task: Implement neural net",
            description = "Create task Implement neural net",
            parameters = mapOf("title" to "Implement neural net", "duration" to "invalid-time", "goalTitle" to "AI Mastery"),
            requiresConfirmation = true
        )

        val proposedPlan1 = listOf(goalAction, taskAction1, taskAction2).map { engine.proposeAction(it) }

        // Run 1: Goal and Task 1 succeed, Task 2 fails
        val results1 = engine.confirmPendingPlan(proposedPlan1)
        assertTrue(results1[0].success)
        assertTrue(results1[1].success)
        assertFalse(results1[2].success)

        val goalsCountAfterRun1 = repository.observeGoalsOnce().size
        val tasksCountAfterRun1 = repository.observeTasksOnce().size
        assertEquals(1, goalsCountAfterRun1)
        assertEquals(1, tasksCountAfterRun1)

        // Fix Task 2 parameters for retry
        val fixedTask2 = taskAction2.copy(
            parameters = mapOf("title" to "Implement neural net", "duration" to "45 minutes", "goalTitle" to "AI Mastery")
        )
        val retryPlan = listOf(goalAction, taskAction1, fixedTask2).map { engine.proposeAction(it) }

        // Run 2: Retry
        val results2 = engine.confirmPendingPlan(retryPlan)
        // Goal and Task 1 must not be duplicated into database
        val goalsCountAfterRun2 = repository.observeGoalsOnce().size
        val tasksCountAfterRun2 = repository.observeTasksOnce().size
        assertEquals("Goal must not be duplicated", 1, goalsCountAfterRun2)
        assertEquals("Tasks must now be 2 (Task 1 not duplicated, Task 2 added)", 2, tasksCountAfterRun2)
    }

    // =========================================================================
    // SECTION 7 — UI Behavior & Loading Recovery
    // =========================================================================
    @Test
    fun `ViewModel recovers loading state after exceptions and errors`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Dispatch a message that triggers clear response
        viewModel.sendMessage("What is carry forward?")
        delay(300)
        val state = viewModel.uiState.value
        assertFalse("isChatLoading must be false", state.isChatLoading)
        assertFalse("isLoading must be false", state.isLoading)
        assertTrue("Chat messages should be populated", state.chatMessages.isNotEmpty())

        // Perform cancellation
        viewModel.sendMessage("Cancel")
        delay(300)
        val cancelState = viewModel.uiState.value
        assertFalse("isChatLoading must be false", cancelState.isChatLoading)
        assertFalse("isLoading must be false", cancelState.isLoading)
        assertNull("Proposed action must be null", cancelState.proposedAction)
    }
}
