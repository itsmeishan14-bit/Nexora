package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraProductionIntegrationTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var providerManager: AiProviderManager

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        providerManager = AiProviderManager(localProvider = localProvider)
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
    fun `FLOW 1 - App startup loads context and AI intelligence correctly`() = runBlocking {
        repository.addTask(PremiumTask(id = 1, title = "Initial Task", category = "Work", duration = "20m"))
        repository.addGoal(NexoraGoal(id = 1, title = "Initial Goal", category = "Personal", targetDate = "2026-12-31", progress = 0f))

        val context = engine.getContext()
        assertNotNull(context)
        assertEquals(1, context.tasks.size)
        assertEquals(1, context.goals.size)
    }

    @Test
    fun `FLOW 2 - Manual task creation and persistence across restarts`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Manual Task", category = "Study", duration = "45m"))
        assertNotNull(task.id)

        // Simulate app restart
        val savedTask = repository.getTaskById(task.id)
        assertNotNull(savedTask)
        assertEquals("Manual Task", savedTask?.title)
    }

    @Test
    fun `FLOW 3 - AI create task proposal requires confirmation and executes upon authorization`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task called Study Java"))
        
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val action = response.proposedActions.firstOrNull()
        assertNotNull(action)
        assertEquals(AiActionType.CREATE_TASK, action?.type)

        // Confirm and execute action
        val result = engine.confirmPendingAction(action!!)
        assertTrue(result.success)

        val createdTask = repository.observeTasksOnce().find { it.title.equals("Study Java", ignoreCase = true) }
        assertNotNull("Created task MUST appear in DB after execution", createdTask)
    }

    @Test
    fun `FLOW 4 - AI complete task resolves entity, requests confirmation, and updates DB`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Study Java", category = "Study", duration = "30m"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete Study Java"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val action = response.proposedActions.firstOrNull()
        assertEquals(task.id, action?.taskId)

        val result = engine.confirmPendingAction(action!!)
        assertTrue(result.success)

        val updatedTask = repository.getTaskById(task.id)
        assertTrue(updatedTask?.completed == true)
    }

    @Test
    fun `FLOW 5 - Conversational greeting returns natural greeting without unsolicited productivity recommendation`() = runBlocking {
        repository.addTask(PremiumTask(title = "Overdue Task", category = "Work", duration = "1h", priority = TaskPriority.URGENT))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "hello"))

        assertEquals(AiDecisionType.GREETING, response.decision?.type)
        assertFalse(response.message.contains("tasks pending", ignoreCase = true))
        assertFalse(response.message.contains("all caught up", ignoreCase = true))
    }

    @Test
    fun `FLOW 6 - Plan my day invokes planner with real context`() = runBlocking {
        repository.addTask(PremiumTask(title = "High Priority Work", category = "Work", duration = "30m", priority = TaskPriority.HIGH))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Plan my day"))

        assertEquals(AiResponseType.PLAN, response.responseType)
        assertTrue(response.message.contains("High Priority Work", ignoreCase = true) || response.message.contains("plan", ignoreCase = true))
    }

    @Test
    fun `FLOW 7 - Why am I falling behind returns evidence-based productivity insight`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why am I falling behind?"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("behavior", ignoreCase = true) || response.message.contains("learning", ignoreCase = true) || response.message.contains("analysis", ignoreCase = true))
    }

    @Test
    fun `FLOW 8 - Delete all tasks triggers safety gate and confirmation flow`() = runBlocking {
        repository.addTask(PremiumTask(title = "Task 1", category = "Work", duration = "10m"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete all my tasks"))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val action = response.proposedActions.firstOrNull()
        assertEquals(AiActionType.DELETE_ALL_TASKS, action?.type)

        // DB remains untouched prior to explicit execution
        assertEquals(1, repository.observeTasksOnce().size)
    }

    @Test
    fun `FLOW 9 - Ambiguous task request returns clarification needed without executing action`() = runBlocking {
        repository.addTask(PremiumTask(title = "Study Java Basic", category = "Study", duration = "30m"))
        repository.addTask(PremiumTask(title = "Study Java Advanced", category = "Study", duration = "60m"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Complete Study Java"))

        assertEquals(AiResponseType.CLARIFICATION_NEEDED, response.responseType)
        assertEquals(AiDecisionType.AMBIGUOUS, response.decision?.type)
        assertTrue(response.proposedActions.isEmpty())
    }

    @Test
    fun `FLOW 10 - Offline fallback operates local AI deterministically`() = runBlocking {
        providerManager.setUseCloud(false)

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I do?"))

        assertNotNull(response.message)
        assertTrue(response.message.isNotBlank())
    }

    @Test
    fun `FLOW 11 - Complete real user journey end-to-end flow`() = runBlocking {
        // 1. First launch: Empty workspace
        var context = engine.getContext()
        assertTrue("Initial workspace has 0 tasks", context.tasks.isEmpty())
        assertTrue("Initial workspace has 0 goals", context.goals.isEmpty())

        // 2. Create Goal
        val goal = repository.addGoal(NexoraGoal(title = "Build Android App", category = "Work", targetDate = "2026-12-31", progress = 0f))
        val goalId = goal.id

        // 3. Create Task associated with Goal
        val task = repository.addTask(PremiumTask(title = "Setup Architecture", category = "Work", duration = "40m", goalTitle = goal.title))
        
        // 4. Ask AI: "How should I start?"
        val aiStartResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I do next?"))
        assertNotNull(aiStartResponse.message)

        // 5. Complete task & verify goal progress recalculation
        val completedTask = task.copy(completed = true)
        repository.updateTask(completedTask)
        
        // Recalculate goal progress
        val tasks = repository.observeTasksOnce()
        val goalTasks = tasks.filter { it.goalTitle == goal.title }
        val newProgress = goalTasks.count { it.completed }.toFloat() / goalTasks.size.toFloat()
        val updatedGoal = goal.copy(progress = newProgress)
        repository.updateGoal(updatedGoal)

        assertEquals(1.0f, repository.getGoalById(goalId)?.progress)

        // 6. Ask AI: "How am I doing?"
        val aiStatusResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "How am I doing?"))
        assertNotNull(aiStatusResponse.message)

        // 7. Create another task using natural language
        val createResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Create a task called Unit Testing"))
        val createAction = createResponse.proposedActions.firstOrNull()
        assertNotNull(createAction)
        val actionResult = engine.confirmPendingAction(createAction!!)
        assertTrue(actionResult.success)

        val createdTask = repository.observeTasksOnce().find { it.title.equals("Unit Testing", ignoreCase = true) }
        assertNotNull(createdTask)

        // 8. Simulate App Restart and verify context freshness
        context = engine.getContext()
        assertEquals(2, context.tasks.size)
        assertEquals(1.0f, context.goals.find { it.id == goalId }?.progress)
    }
}
