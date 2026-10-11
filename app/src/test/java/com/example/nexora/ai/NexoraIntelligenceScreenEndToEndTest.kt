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
import java.util.UUID

class NexoraIntelligenceScreenEndToEndTest {

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
    // 1. PLAN MY DAY
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `1 - Plan my day creates structured personalized daily plan and updates UI`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "High Priority Core Task", category = "Work", duration = "45m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(id = 2L, title = "Urgent Bugfix", category = "Work", duration = "30m", priority = TaskPriority.URGENT))
        repository.addTask(PremiumTask(id = 3L, title = "Low Priority Cleanup", category = "Work", duration = "15m", priority = TaskPriority.LOW))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.createDailyPlan()
        delay(400)

        val state = viewModel.uiState.value
        assertNotNull("Daily plan should be generated in UI state", state.dailyPlan)
        assertTrue("Daily plan should include tasks", state.dailyPlan!!.tasks.isNotEmpty())
        assertEquals("Top planned task should be the urgent task", "Urgent Bugfix", state.dailyPlan!!.tasks.first().task.title)

        // Canonical Chat equivalent
        val chatResp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Plan my day"))
        assertNotNull("Chat response should include dailyPlan", chatResp.dailyPlan)
        assertEquals(AiResponseType.PLAN, chatResp.responseType)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. WHAT SHOULD I WORK ON NEXT?
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `2 - What should I work on next returns WHAT, WHY, and NEXT`() = runBlocking {
        repository.addTask(PremiumTask(id = 10L, title = "Deploy Release v2", category = "Work", duration = "30m", priority = TaskPriority.URGENT))
        repository.addTask(PremiumTask(id = 11L, title = "Organize desk", category = "Personal", duration = "10m", priority = TaskPriority.LOW))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I work on next?"))
        assertEquals(AiResponseType.RECOMMENDATION, resp.responseType)
        assertTrue("Must contain WHAT section", resp.message.contains("WHAT:"))
        assertTrue("Must contain WHY section", resp.message.contains("WHY:"))
        assertTrue("Must contain NEXT section", resp.message.contains("NEXT:"))
        assertTrue("Must recommend highest priority task", resp.message.contains("Deploy Release v2"))
        assertNotNull("Must propose action for task", resp.proposedActions.firstOrNull())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. WHAT SHOULD I FOCUS ON?
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `3 - What should I focus on recommends priority task and sets focus context`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 5L, title = "Backend System", category = "Work", targetDate = "2026-12-31", progress = 0.2f))
        val task = repository.addTask(PremiumTask(id = 20L, title = "Build Auth API", category = "Work", duration = "60m", priority = TaskPriority.HIGH, goalTitle = goal.title))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I focus on?"))
        assertEquals(AiResponseType.RECOMMENDATION, resp.responseType)
        assertTrue("Message should focus on high priority task", resp.message.contains("Build Auth API"))
        assertEquals(task.id, resp.relatedTaskId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. WHY AM I BEHIND?
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `4 - Why am I behind returns diagnosis, evidence, and next step based on context`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "Task 1", category = "Work", duration = "30m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(id = 2L, title = "Task 2", category = "Work", duration = "45m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(id = 3L, title = "Task 3", category = "Work", duration = "15m", priority = TaskPriority.MEDIUM))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why am I behind?"))
        assertTrue("Diagnosis must explain incomplete tasks", resp.message.contains("behind because", ignoreCase = true))
        assertTrue("Must provide evidence bullets", resp.message.contains("Evidence:", ignoreCase = true))
        assertTrue("Must contain Next recommendation", resp.message.contains("Next:", ignoreCase = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REVIEW MY GOALS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `5 - Review my goals analyzes real goals with progress, at-risk flags, and next action`() = runBlocking {
        val goal1 = repository.addGoal(NexoraGoal(id = 1L, title = "Learn Kotlin", category = "Study", targetDate = "2026-12-31", progress = 0.1f))
        val goal2 = repository.addGoal(NexoraGoal(id = 2L, title = "Fitness Goal", category = "Health", targetDate = "2026-12-31", progress = 0.0f))
        repository.addTask(PremiumTask(id = 1L, title = "Kotlin Basics", category = "Study", duration = "30m", goalTitle = goal1.title))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Review my goals"))
        assertTrue("Should include goals review", resp.message.contains("Goals Review"))
        assertTrue("Should list Learn Kotlin", resp.message.contains("Learn Kotlin"))
        assertTrue("Should list Fitness Goal", resp.message.contains("Fitness Goal"))
        assertTrue("Should identify goals needing tasks", resp.message.contains("Needs Actionable Tasks") || resp.message.contains("Suggested Next Action"))
        assertNotNull("Should provide proposed action", resp.proposedActions.firstOrNull())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. CHECK MY WORKLOAD
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `6 - Check my workload analyzes tasks and returns human-readable assessment with reasons`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "Task A", category = "Work", duration = "60m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(id = 2L, title = "Task B", category = "Work", duration = "45m", priority = TaskPriority.HIGH))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Check my workload"))
        assertTrue("Should include Workload Level", resp.message.contains("Workload Level:"))
        assertTrue("Should explain Why", resp.message.contains("Why:"))
        assertTrue("Should detail incomplete tasks count", resp.message.contains("incomplete task"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. BREAK DOWN A GOAL (EXISTING)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `7 - Break down existing goal resolves goal, creates sub-tasks requiring confirmation`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 100L, title = "Learn Java", category = "Study", targetDate = "2026-12-31", progress = 0.0f))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Break down my Learn Java goal"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        assertTrue("Should propose subtasks", resp.proposedActions.isNotEmpty())
        assertTrue("All proposed actions must require confirmation", resp.proposedActions.all { it.requiresConfirmation })
        assertTrue("Subtasks should link to Learn Java", resp.proposedActions.any { it.parameters["goalTitle"] == goal.title })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. NATURAL LANGUAGE CREATE TASK
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `8 - Natural language create task proposes task with title and priority`() = runBlocking {
        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a high priority task to study Java"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.CREATE_TASK }
        assertNotNull("Should propose CREATE_TASK", action)
        assertTrue("Title should include Java", action!!.parameters["title"]?.toString()?.contains("Java", ignoreCase = true) == true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. NATURAL LANGUAGE COMPLETE TASK
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `9 - Natural language complete task targets existing task`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 50L, title = "DBMS Assignment", category = "Study", duration = "40m"))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete my DBMS Assignment task"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.COMPLETE_TASK }
        assertNotNull("Should propose COMPLETE_TASK", action)
        assertEquals(task.id, action!!.taskId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. NATURAL LANGUAGE UPDATE TASK
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `10 - Natural language update task proposes update for existing task`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 60L, title = "Clean Room", category = "Home", duration = "20m", priority = TaskPriority.LOW))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Change Clean Room task to high priority"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.UPDATE_TASK }
        assertNotNull("Should propose UPDATE_TASK", action)
        assertEquals(task.id, action!!.taskId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. NATURAL LANGUAGE DELETE TASK
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `11 - Natural language delete task proposes delete with confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 70L, title = "Temporary Note", category = "Misc", duration = "5m"))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete my Temporary Note task"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.DELETE_TASK }
        assertNotNull("Should propose DELETE_TASK", action)
        assertEquals(task.id, action!!.taskId)
        assertTrue("Delete must require confirmation", action.requiresConfirmation)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. NATURAL LANGUAGE CREATE GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `12 - Natural language create goal proposes goal creation`() = runBlocking {
        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a goal called Learn Kotlin"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.CREATE_GOAL }
        assertNotNull("Should propose CREATE_GOAL", action)
        assertTrue("Goal title should be Learn Kotlin", action!!.parameters["title"]?.toString()?.contains("Learn Kotlin", ignoreCase = true) == true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. NATURAL LANGUAGE UPDATE GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `13 - Natural language update goal updates target date or progress`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 80L, title = "Fitness Journey", category = "Health", targetDate = "2026-10-01", progress = 0.1f))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Update Fitness Journey goal deadline to 2026-12-31"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.UPDATE_GOAL }
        assertNotNull("Should propose UPDATE_GOAL", action)
        assertEquals(goal.id, action!!.goalId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14. NATURAL LANGUAGE DELETE GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `14 - Natural language delete goal proposes deletion with confirmation`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 90L, title = "Obsolete Objective", category = "Work", targetDate = "2026-12-31", progress = 0.0f))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete my Obsolete Objective goal"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp.responseType)
        val action = resp.proposedActions.firstOrNull { it.type == AiActionType.DELETE_GOAL }
        assertNotNull("Should propose DELETE_GOAL", action)
        assertEquals(goal.id, action!!.goalId)
        assertTrue("Deleting a goal must require confirmation", action.requiresConfirmation)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 15. CONVERSATIONAL CONFIRMATION
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `15 - Conversational confirmation executes proposed action and updates repository`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 101L, title = "Ship Feature", category = "Work", duration = "40m"))
        assertFalse(task.completed)

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Complete task Ship Feature")
        delay(250)

        assertNotNull("Action should be proposed", viewModel.uiState.value.proposedAction)

        viewModel.sendMessage("yes")
        delay(350)

        val updatedTask = repository.getTaskById(task.id)
        assertTrue("Task should be completed in database after confirmation", updatedTask?.completed == true)
        assertTrue("UI result should report success", viewModel.uiState.value.lastActionResult?.success == true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 16. CONVERSATIONAL CLARIFICATION (MISSING GOAL FOLLOW-UP)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `16 - Nonexistent goal decomposition asks clarification and creates plan on confirmation`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Break down my Java goal")
        delay(250)

        val stateAfterQuery = viewModel.uiState.value
        val lastMsg = stateAfterQuery.chatMessages.lastOrNull { !it.isFromUser }?.text ?: ""
        assertTrue("Must ask helpful clarification about missing goal", lastMsg.contains("create a new goal called \"Java\"", ignoreCase = true) || lastMsg.contains("couldn't find", ignoreCase = true))

        // Confirm clarification
        viewModel.sendMessage("yes")
        delay(350)

        val stateAfterConfirm = viewModel.uiState.value
        assertTrue("Plan should now be proposed", stateAfterConfirm.proposedPlan.isNotEmpty() || stateAfterConfirm.proposedAction != null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 17. CANCELLATION
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `17 - Cancellation discards pending proposal and makes no database mutations`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 102L, title = "Protect This Task", category = "Work", duration = "25m"))

        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)
        viewModel.sendMessage("Delete task Protect This Task")
        delay(250)

        assertNotNull("Delete action should be proposed", viewModel.uiState.value.proposedAction)

        viewModel.sendMessage("cancel")
        delay(250)

        assertNull("Proposed action must be cleared on cancel", viewModel.uiState.value.proposedAction)
        val taskStillExists = repository.getTaskById(task.id) != null
        assertTrue("Task must NOT be deleted after cancellation", taskStillExists)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 18. AMBIGUOUS TASK
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `18 - Ambiguous task query identifies multiple candidates and asks user to clarify`() = runBlocking {
        repository.addTask(PremiumTask(id = 111L, title = "Sync Meeting", category = "Work", duration = "15m"))
        repository.addTask(PremiumTask(id = 112L, title = "Sync Meeting", category = "Personal", duration = "15m"))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete task Sync Meeting"))
        assertTrue("Should detect ambiguity or request clarification",
            resp.responseType == AiResponseType.CLARIFICATION_NEEDED || resp.decision?.type == AiDecisionType.AMBIGUOUS || resp.decision?.type == AiDecisionType.CLARIFY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 19. AMBIGUOUS GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `19 - Ambiguous goal query prompts user for clarification`() = runBlocking {
        repository.addGoal(NexoraGoal(id = 201L, title = "Health", category = "Personal", targetDate = "2026-12-31", progress = 0f))
        repository.addGoal(NexoraGoal(id = 202L, title = "Health", category = "Fitness", targetDate = "2026-12-31", progress = 0f))

        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete goal Health"))
        assertTrue("Should detect ambiguity or ask which Health goal",
            resp.responseType == AiResponseType.CLARIFICATION_NEEDED || resp.decision?.type == AiDecisionType.AMBIGUOUS || resp.decision?.type == AiDecisionType.CLARIFY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 20. NONEXISTENT TASK OR GOAL
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `20 - Nonexistent task returns clear error or clarification without crashing`() = runBlocking {
        val resp = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete task Phantom Nowhere 404"))
        assertTrue("Should cleanly communicate task not found",
            resp.message.contains("couldn't find", ignoreCase = true) || resp.message.contains("not found", ignoreCase = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 21. MULTI-ACTION GOAL PLAN
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `21 - Multi-action goal plan executes all actions and creates linked tasks`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Plan goal Launch Podcast")
        delay(300)

        val proposedPlan = viewModel.uiState.value.proposedPlan
        assertTrue("Plan must contain multiple actions", proposedPlan.size >= 2)
        val goalAction = proposedPlan.find { it.type == AiActionType.CREATE_GOAL }
        assertNotNull("Plan must propose creating the goal", goalAction)

        viewModel.confirmAction()
        delay(400)

        val stateAfterExec = viewModel.uiState.value
        assertTrue("Execution must report success", stateAfterExec.lastActionResult?.success == true)
        val createdGoal = repository.observeGoalsOnce().find { it.title.contains("Launch Podcast", ignoreCase = true) }
        assertNotNull("Goal should be created in repository", createdGoal)
        val createdTasks = repository.observeTasksOnce().filter { it.goalTitle == createdGoal?.title }
        assertTrue("Subtasks linked to goal must be created", createdTasks.isNotEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 22. DEMO COMMANDS & EXACT WORKFLOW SUITE
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `22 - Create a task to study Java creates and persists in repository`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Create a task to study Java.")
        delay(300)

        val stateAfterProposal = viewModel.uiState.value
        val action = stateAfterProposal.proposedAction
        assertNotNull("Should propose CREATE_TASK", action)
        assertEquals(AiActionType.CREATE_TASK, action!!.type)
        assertTrue("Title should contain Study Java", action.parameters["title"]?.toString()?.contains("Study Java", ignoreCase = true) == true)

        viewModel.confirmAction()
        delay(350)

        val stateAfterConfirm = viewModel.uiState.value
        assertTrue("Action execution should succeed", stateAfterConfirm.lastActionResult?.success == true)
        val persisted = repository.observeTasksOnce().find { it.title.contains("Java", ignoreCase = true) }
        assertNotNull("Task must be persisted in repository", persisted)
        assertEquals(false, persisted!!.completed)
    }

    @Test
    fun `23 - Create a high-priority DBMS assignment creates and persists with high priority`() = runBlocking {
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Create a high-priority DBMS assignment.")
        delay(300)

        val stateAfterProposal = viewModel.uiState.value
        val action = stateAfterProposal.proposedAction
        assertNotNull("Should propose CREATE_TASK", action)
        assertEquals(AiActionType.CREATE_TASK, action!!.type)
        assertEquals("HIGH", action.parameters["priority"])
        assertTrue("Title should contain DBMS", action.parameters["title"]?.toString()?.contains("DBMS", ignoreCase = true) == true)

        viewModel.confirmAction()
        delay(350)

        val persisted = repository.observeTasksOnce().find { it.title.contains("DBMS", ignoreCase = true) }
        assertNotNull("Task must be persisted in repository", persisted)
        assertEquals(TaskPriority.HIGH, persisted!!.priority)
    }

    @Test
    fun `24 - Mark my Java task as complete marks task complete in repository`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 301L, title = "Study Java", category = "Study", duration = "45m", completed = false))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Mark my Java task as complete.")
        delay(300)

        val action = viewModel.uiState.value.proposedAction
        assertNotNull("Should propose COMPLETE_TASK", action)
        assertEquals(task.id, action!!.taskId)

        viewModel.confirmAction()
        delay(350)

        val updated = repository.getTaskById(task.id)
        assertNotNull(updated)
        assertTrue("Task must be marked complete in database", updated!!.completed)
        assertTrue("UI result must report success", viewModel.uiState.value.lastActionResult?.success == true)
    }

    @Test
    fun `25 - Update my DBMS task clarifies update fields and executes update on confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 302L, title = "DBMS assignment", category = "Study", duration = "30m", priority = TaskPriority.LOW))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        // Step 1: User says "Update my DBMS task."
        viewModel.sendMessage("Update my DBMS task.")
        delay(300)

        val stateAfterFirst = viewModel.uiState.value
        val botMsg1 = stateAfterFirst.chatMessages.lastOrNull { !it.isFromUser }?.text ?: ""
        assertTrue("Bot must ask what to update", botMsg1.contains("What would you like to update", ignoreCase = true))

        // Step 2: User provides update fields: "high priority"
        viewModel.sendMessage("high priority")
        delay(350)

        val stateAfterClarification = viewModel.uiState.value
        val action = stateAfterClarification.proposedAction
        assertNotNull("Should now propose UPDATE_TASK with high priority", action)
        assertEquals(task.id, action!!.taskId)
        assertEquals("HIGH", action.parameters["priority"])

        // Step 3: User confirms
        viewModel.confirmAction()
        delay(350)

        val updated = repository.getTaskById(task.id)
        assertNotNull(updated)
        assertEquals("Priority must be updated to HIGH in repository", TaskPriority.HIGH, updated!!.priority)
        assertTrue("UI result must report success", viewModel.uiState.value.lastActionResult?.success == true)
    }

    @Test
    fun `26 - Delete my Java task proposes deletion and removes from repository on confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 303L, title = "Study Java", category = "Study", duration = "45m", completed = false))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Delete my Java task.")
        delay(300)

        val action = viewModel.uiState.value.proposedAction
        assertNotNull("Should propose DELETE_TASK", action)
        assertEquals(task.id, action!!.taskId)
        assertTrue("Deletion must require confirmation", action.requiresConfirmation)

        viewModel.confirmAction()
        delay(350)

        val deleted = repository.getTaskById(task.id)
        assertNull("Task must be deleted from repository", deleted)
        assertTrue("UI result must report success", viewModel.uiState.value.lastActionResult?.success == true)
    }

    @Test
    fun `27 - Break down my goal to learn Java decomposes existing goal and persists linked subtasks`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 401L, title = "Learn Java", category = "Study", targetDate = "2026-12-31", progress = 0.0f))
        val viewModel = NexoraAiViewModel(engine = engine, coroutineScope = testScope)

        viewModel.sendMessage("Break down my goal to learn Java.")
        delay(350)

        val proposedPlan = viewModel.uiState.value.proposedPlan
        assertTrue("Should propose actionable subtasks", proposedPlan.isNotEmpty())
        assertTrue("All subtasks must require confirmation", proposedPlan.all { it.requiresConfirmation })
        assertTrue("Subtasks should link to Learn Java goal", proposedPlan.all { it.parameters["goalTitle"] == goal.title })

        viewModel.confirmAction()
        delay(400)

        val tasksInDb = repository.observeTasksOnce().filter { it.goalTitle == goal.title }
        assertTrue("Subtasks must be created in repository", tasksInDb.isNotEmpty())
        assertTrue("UI result must report success", viewModel.uiState.value.lastActionResult?.success == true)
    }
}

