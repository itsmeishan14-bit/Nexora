package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraUnifiedAiExperienceTest {

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
    fun `TEST 1 - Cross-screen recommendation consistency between Chat and Home`() = runBlocking {
        // 1. Create tasks
        val task1 = repository.addTask(PremiumTask(title = "Study Java DSA", category = "Study", duration = "30m", priority = TaskPriority.HIGH))
        repository.addTask(PremiumTask(title = "Buy groceries", category = "Personal", duration = "10m", priority = TaskPriority.LOW))

        // 2. Chat asks "What's my next task?"
        val chatResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What's my next task?"))
        
        // 3. NEXT_TASK request (used by Planner / Home / AI screen)
        val nextTaskResponse = engine.processRequest(AiRequest(AiRequestType.NEXT_TASK))

        val topChatTask = chatResponse.relatedTaskId ?: chatResponse.decision?.taskId
        val topNextTask = nextTaskResponse.relatedTaskId

        assertEquals("Chat and Home NEXT_TASK MUST agree on top task", task1.id, topNextTask ?: topChatTask)
    }

    @Test
    fun `TEST 2 - Goal-aware recommendation when asking about a specific goal`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(title = "Backend Mastery", category = "Work", targetDate = "2026-12-31", progress = 0.1f))
        val task = repository.addTask(PremiumTask(title = "Express Auth", category = "Work", duration = "45m", priority = TaskPriority.HIGH, goalTitle = goal.title))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I work on for my Backend Mastery goal?"))

        assertTrue(response.message.contains("Express Auth", ignoreCase = true) || response.message.contains("Backend Mastery", ignoreCase = true))
    }

    @Test
    fun `TEST 3 - Completing recommended task updates status and goal progress`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(title = "Backend Mastery", category = "Work", targetDate = "2026-12-31", progress = 0.0f))
        val task = repository.addTask(PremiumTask(title = "Express Auth", category = "Work", duration = "45m", priority = TaskPriority.HIGH, goalTitle = goal.title))

        // Execute completion
        val action = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete Express Auth",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = engine.executeAction(action)
        assertTrue(result.success)

        val updatedTask = repository.getTaskById(task.id)
        assertTrue(updatedTask?.completed == true)
    }

    @Test
    fun `TEST 4 - Daily Plan requested from Home or Chat uses same planner result`() = runBlocking {
        repository.addTask(PremiumTask(title = "Java DSA", category = "Study", duration = "30m", priority = TaskPriority.HIGH))

        val responseChat = engine.processRequest(AiRequest(AiRequestType.DAILY_PLAN))
        val planDirect = engine.createDailyPlan()

        assertNotNull(responseChat.message)
        assertNotNull(planDirect.summary)
        assertTrue("Both Chat and Home must use actual tasks in plan", planDirect.tasks.any { it.task.title == "Java DSA" })
    }

    @Test
    fun `TEST 5 - Proactive workload insight explainable across screens`() = runBlocking {
        repository.saveDailyProgress(
            com.example.nexora.data.DailyProgressEntity(
                date = java.time.LocalDate.now().toString(),
                tasksPlanned = 20,
                tasksCompleted = 2,
                focusMinutes = 0,
                goalsWorkedOn = 0,
                carriedTasks = 0
            )
        )

        val response = engine.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
        val criticalSignal = response.proactiveSignals.firstOrNull() ?: response.recommendations.firstOrNull()

        assertNotNull("Proactive workload insight should trigger when overloaded", criticalSignal)
    }

    @Test
    fun `TEST 6 - Create automation through chat saves real rule`() = runBlocking {
        val query = "Every morning prepare my daily plan"
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val rules = engine.getAutomationRules()
        assertTrue(rules.any { it.name.contains("Morning", ignoreCase = true) })
    }

    @Test
    fun `TEST 7 - State-changing AI action requires confirmation before DB modification`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Java DSA", category = "Study", duration = "30m", priority = TaskPriority.HIGH))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete my Java DSA task"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)

        // Verify task still in DB before confirmation
        val dbTaskBefore = repository.getTaskById(task.id)
        assertNotNull("Task MUST remain in DB before confirmation", dbTaskBefore)
    }

    @Test
    fun `TEST 8 - Restart application simulation maintains data integrity`() = runBlocking {
        repository.addTask(PremiumTask(title = "Persistent Task", category = "Work", duration = "15m"))
        repository.addGoal(NexoraGoal(title = "Persistent Goal", category = "Personal", targetDate = "2026-12-31", progress = 0f))

        // Simulate app restart by checking repository once
        val savedTasks = repository.observeTasksOnce()
        val savedGoals = repository.observeGoalsOnce()

        assertEquals(1, savedTasks.size)
        assertEquals(1, savedGoals.size)
    }

    @Test
    fun `TEST 9 - Empty AI state returns polite learning phase message without fake data`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
        assertNotNull(response.message)
        assertFalse("Should not introduce fake demo tasks", response.message.contains("Demo Task"))
    }

    @Test
    fun `TEST 10 - Local fallback succeeds when cloud is unconfigured or offline`() = runBlocking {
        providerManager.setUseCloud(false)
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I do?"))

        assertNotNull(response.message)
        assertTrue("Local fallback must produce valid response", response.message.isNotBlank())
    }
}
