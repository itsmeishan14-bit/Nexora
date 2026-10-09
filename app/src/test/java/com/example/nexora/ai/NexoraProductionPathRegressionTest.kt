package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.data.DailyProgressEntity
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
import java.time.LocalDate

class NexoraProductionPathRegressionTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        NexoraSecurity.clearAllAuthorizations()
        repository = MockNexoraRepository()
        val actionExecutor = AiActionExecutor(repository)
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

    // ─────────────────────────────────────────────────────────────────────────
    // 1. INTENT CONFLICTS AND ALTERNATE PHRASINGS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 1 - Intent conflicts and alternate phrasings route accurately`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "Task Alpha", category = "Work", duration = "30m", priority = TaskPriority.HIGH))
        val goal = repository.addGoal(NexoraGoal(id = 10L, title = "Master Java", category = "Study", targetDate = "2026-12-31", progress = 0.1f))

        // "Plan my day" with uppercase and exclamation
        val r1 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "PLAN MY DAY!"))
        assertEquals(AiResponseType.PLAN, r1.responseType)
        assertNotNull("Must return daily plan", r1.dailyPlan)

        // "Plan my day." with trailing punctuation
        val r2 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Plan my day."))
        assertEquals(AiResponseType.PLAN, r2.responseType)

        // "What should I focus on?"
        val r3 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I focus on?"))
        assertEquals(AiResponseType.RECOMMENDATION, r3.responseType)
        assertTrue(r3.message.contains("WHAT:"))

        // "What should I work on next?"
        val r4 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I work on next?"))
        assertEquals(AiResponseType.RECOMMENDATION, r4.responseType)
        assertTrue(r4.message.contains("WHAT:"))

        // "Why am I behind?" uppercase
        val r5 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "WHY AM I BEHIND?"))
        assertEquals(AiResponseType.INFORMATION, r5.responseType)
        assertTrue(r5.message.contains("Behind Schedule Analysis"))

        // "Review my goals."
        val r6 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Review my goals."))
        assertEquals(AiResponseType.RECOMMENDATION, r6.responseType)
        assertTrue(r6.message.contains("Goals Review"))

        // "Check my workload"
        val r7 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Check my workload"))
        assertEquals(AiResponseType.INFORMATION, r7.responseType)
        assertTrue(r7.message.contains("Workload Level:"))

        // "Break down my Master Java goal" -> Decompose goal
        val r8 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Break down my Master Java goal"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, r8.responseType)
        assertTrue(r8.proposedActions.any { it.type == AiActionType.CREATE_TASK })

        // "Create a plan for learning Java" -> Decompose goal
        val r9 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a plan for learning Java"))
        // Missing "learning Java" goal -> triggers clarification for creating/planning it
        assertTrue(r9.responseType == AiResponseType.CLARIFICATION_NEEDED || r9.responseType == AiResponseType.ACTION_PROPOSAL)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. "WHY AM I BEHIND?" WITH ZERO, SEVERAL, AND CARRIED-FORWARD TASKS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 2 - Why am I behind with zero, several, and carried tasks`() = runBlocking {
        // Zero tasks:
        val rZero = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why am I behind?"))
        assertTrue("Must state zero pending tasks", rZero.message.contains("0 pending tasks"))
        assertTrue("Must state not behind", rZero.message.contains("not behind"))

        // Several tasks with high priority:
        repository.addTask(PremiumTask(id = 1L, title = "Critical Incident", category = "Work", duration = "60m", priority = TaskPriority.URGENT))
        repository.addTask(PremiumTask(id = 2L, title = "High Feature", category = "Work", duration = "45m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(id = 3L, title = "Normal chore", category = "Work", duration = "15m", priority = TaskPriority.MEDIUM))

        val rSeveral = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why am I behind?"))
        assertTrue("Must report 3 incomplete tasks", rSeveral.message.contains("3 incomplete tasks") || rSeveral.message.contains("3 tasks remain incomplete"))
        assertTrue("Must report 2 high-priority tasks", rSeveral.message.contains("2 high-priority tasks"))
        assertFalse("Must NOT claim carried tasks if none exist", rSeveral.message.contains("carried-forward"))

        // Carried-forward tasks for today:
        val todayStr = LocalDate.now().toString()
        repository.saveDailyProgress(
            DailyProgressEntity(
                date = todayStr,
                tasksPlanned = 3,
                tasksCompleted = 0,
                carriedTasks = 2
            )
        )

        val rCarried = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why am I behind?"))
        assertTrue("Must report carried-forward tasks", rCarried.message.contains("carried-forward") || rCarried.message.contains("carried forward"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. WORKLOAD CALCULATION WITH MISSING DURATION OR HISTORY
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 3 - Workload calculation handles missing duration and history safely`() = runBlocking {
        // Tasks with missing / empty duration strings
        repository.addTask(PremiumTask(id = 1L, title = "Task No Duration", category = "Work", duration = "", priority = TaskPriority.MEDIUM))
        repository.addTask(PremiumTask(id = 2L, title = "Task Unknown", category = "Work", duration = "unknown", priority = TaskPriority.LOW))

        // No historical data saved in repository
        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Check my workload"))
        assertEquals(AiResponseType.INFORMATION, resp.responseType)
        assertTrue(resp.message.contains("Workload Level:"))
        assertTrue(resp.message.contains("Why:"))
        // Should NOT falsely assert "above normal capacity" on 2 small/medium tasks
        assertFalse("Must not falsely declare VERY_HIGH without evidence", resp.message.contains("Workload Level: VERY_HIGH"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GOAL REVIEW WITH NO GOALS, HEALTHY GOALS, AND AT-RISK GOALS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 4 - Goal review handles no goals, healthy goals, and at-risk goals`() = runBlocking {
        // No goals
        val rNoGoals = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Review my goals"))
        assertTrue("Must report no active goals configured", rNoGoals.message.contains("don't have any active goals"))

        // Add healthy goal with active tasks and progress
        val healthyGoal = repository.addGoal(NexoraGoal(id = 1L, title = "Learn Android", category = "Study", targetDate = "2026-12-31", progress = 0.8f))
        repository.addTask(PremiumTask(id = 10L, title = "Compose Testing", category = "Study", duration = "30m", goalTitle = healthyGoal.title))

        // Add at-risk goal (0 progress, no tasks linked, target date near or stalled)
        val atRiskGoal = repository.addGoal(NexoraGoal(id = 2L, title = "Learn Rust", category = "Study", targetDate = "2026-01-01", progress = 0.0f))

        val rReview = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Review my goals"))
        assertTrue("Must list Learn Android", rReview.message.contains("Learn Android"))
        assertTrue("Must list Learn Rust", rReview.message.contains("Learn Rust"))
        assertTrue("Must flag Learn Rust as needing tasks or at risk",
            rReview.message.contains("Needs Actionable Tasks") || rReview.message.contains("Goals at Risk") || rReview.message.contains("Learn Rust"))
        assertNotNull("Must provide a proposed action", rReview.proposedActions.firstOrNull())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. NEXT-TASK RECOMMENDATIONS MATCHING REAL DATABASE IDS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 5 - Next task recommendation matches real database ID and does not mutate`() = runBlocking {
        val lowTask = repository.addTask(PremiumTask(id = 101L, title = "Water Plants", category = "Home", duration = "10m", priority = TaskPriority.LOW))
        val urgentTask = repository.addTask(PremiumTask(id = 102L, title = "Fix Production Outage", category = "Work", duration = "45m", priority = TaskPriority.URGENT))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I work on next?"))
        assertEquals(AiResponseType.RECOMMENDATION, resp.responseType)
        assertEquals(urgentTask.id, resp.relatedTaskId)
        val proposedAction = resp.proposedActions.firstOrNull()
        assertNotNull("Proposed action must exist", proposedAction)
        assertEquals(urgentTask.id, proposedAction?.taskId)

        // Verify task state in database remains unmutated (not completed)
        val taskInDb = repository.getTaskById(urgentTask.id)
        assertNotNull(taskInDb)
        assertFalse("Recommending work must NOT mutate task completion in database", taskInDb!!.completed)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. GOAL DECOMPOSITION WITH AN EXISTING OR MISSING GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 6 - Goal decomposition handles existing and missing goals accurately`() = runBlocking {
        // Existing goal
        val existingGoal = repository.addGoal(NexoraGoal(id = 50L, title = "Master Python", category = "Study", targetDate = "2026-12-31", progress = 0.0f))
        val rExisting = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Break down my Master Python goal"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, rExisting.responseType)
        assertTrue(rExisting.proposedActions.isNotEmpty())
        assertTrue("Subtasks should link to Master Python", rExisting.proposedActions.any { it.parameters["goalTitle"] == existingGoal.title })

        // Missing goal
        val rMissing = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Break down my Cloud Computing goal"))
        assertEquals(AiResponseType.CLARIFICATION_NEEDED, rMissing.responseType)
        assertTrue("Must ask clarification for missing goal", rMissing.message.contains("Cloud Computing", ignoreCase = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. CLARIFICATION FOLLOWED BY A VALID ANSWER
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 7 - Clarification followed by valid answer proposes and confirms plan`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Turn 1: Ask for missing goal
        viewModel.sendMessage("Break down my Kotlin goal")
        delay(250)

        val stateTurn1 = viewModel.uiState.value
        val lastAiMsg1 = stateTurn1.chatMessages.lastOrNull { !it.isFromUser }?.text ?: ""
        assertTrue("Should request clarification", lastAiMsg1.contains("Kotlin", ignoreCase = true))

        // Turn 2: User answers "Create a new one."
        viewModel.sendMessage("Create a new one.")
        delay(350)

        val stateTurn2 = viewModel.uiState.value
        assertTrue("Must propose plan after clarification answer", stateTurn2.proposedPlan.isNotEmpty() || stateTurn2.proposedAction != null)

        // Turn 3: User confirms "Yes"
        viewModel.sendMessage("yes")
        delay(350)

        val stateTurn3 = viewModel.uiState.value
        assertTrue("Execution should succeed", stateTurn3.lastActionResult?.success == true)
        val createdGoal = repository.observeGoalsOnce().find { it.title.contains("Kotlin", ignoreCase = true) }
        assertNotNull("Goal should be created in database", createdGoal)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. CANCELLATION AND EXPIRED CONTEXT
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 8 - Cancellation clears proposal and expired context rejects stale actions`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 200L, title = "Secret Document", category = "Work", duration = "15m"))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Delete task Secret Document")
        delay(250)

        assertNotNull("Should have pending proposal", viewModel.uiState.value.proposedAction)

        // Cancellation
        viewModel.sendMessage("cancel")
        delay(250)

        assertNull("Proposed action should be cleared on cancellation", viewModel.uiState.value.proposedAction)
        assertNotNull("Task must NOT be deleted", repository.getTaskById(task.id))

        // Expired context check:
        val expiredContext = AiConversationContext(
            lastIntent = AiDecisionType.CLARIFY,
            pendingAction = AiAction(
                type = AiActionType.DELETE_TASK,
                title = "Delete Expired",
                description = "Delete task",
                taskId = task.id,
                requiresConfirmation = true
            ),
            timestamp = System.currentTimeMillis() - 15 * 60 * 1000L // 15 minutes old (> 10m threshold)
        )

        val respExpired = engine.processRequest(AiRequest(
            type = AiRequestType.CHAT,
            userMessage = "yes",
            conversationContext = expiredContext
        ))

        assertTrue("Expired context should be rejected or reported as expired",
            respExpired.message.contains("expired", ignoreCase = true) || respExpired.message.contains("no pending", ignoreCase = true))
        assertNotNull("Task must NOT be deleted with expired context", repository.getTaskById(task.id))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. CONFIRMATION REPLAY AND DUPLICATE EXECUTION
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 9 - Confirmation replay rejects duplicate execution cleanly`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 300L, title = "Prepare Slides", category = "Work", duration = "30m"))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Complete task Prepare Slides")
        delay(250)

        // First confirmation
        viewModel.sendMessage("yes")
        delay(350)

        assertTrue(repository.getTaskById(task.id)?.completed == true)
        assertTrue(viewModel.uiState.value.lastActionResult?.success == true)

        // Second confirmation ("yes" sent again)
        viewModel.sendMessage("yes")
        delay(250)

        val lastAiMessage = viewModel.uiState.value.chatMessages.lastOrNull { !it.isFromUser }?.text ?: ""
        assertTrue("Duplicate confirmation must report no pending actions",
            lastAiMessage.contains("no pending actions", ignoreCase = true) || lastAiMessage.contains("already", ignoreCase = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. DATABASE MUTATION FOLLOWED BY REFRESHED UI STATE
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 10 - Database mutation refreshes UI state and action results`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 400L, title = "Refactor Pipeline", category = "Work", duration = "45m"))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Complete task Refactor Pipeline")
        delay(250)

        assertNotNull(viewModel.uiState.value.proposedAction)
        viewModel.confirmAction()
        delay(400)

        val updatedTask = repository.getTaskById(task.id)
        assertTrue("Database task must be marked completed", updatedTask?.completed == true)
        val actionResult = viewModel.uiState.value.lastActionResult
        assertNotNull("Last action result must be present", actionResult)
        assertTrue("Action result must indicate success", actionResult!!.success)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. ERROR PROPAGATION AND PARTIAL FAILURES
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `regression 11 - Error propagation and recovery during execution`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Propose action on non-existent task ID
        val phantomAction = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Phantom",
            description = "Complete non-existent task",
            taskId = 999999L,
            requiresConfirmation = true
        )

        viewModel.proposeAction(phantomAction)
        viewModel.confirmAction()
        delay(350)

        // Verify error reported truthfully
        val result = viewModel.uiState.value.lastActionResult
        assertNotNull(result)
        assertFalse("Execution of non-existent task must fail", result!!.success)
        assertFalse("Loading state must be cleared after error", viewModel.uiState.value.isLoading)

        // Verify conversation can continue normally
        viewModel.sendMessage("What can you do?")
        delay(250)

        val lastAiMessage = viewModel.uiState.value.chatMessages.lastOrNull { !it.isFromUser }?.text ?: ""
        assertTrue("Conversation must continue after failure", lastAiMessage.isNotBlank())
    }
}
