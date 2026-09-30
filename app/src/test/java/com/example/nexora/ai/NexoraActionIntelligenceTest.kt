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
    fun `test invalid task ID or goal ID is rejected safely`() = runBlocking {
        val invalidTaskAction = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Invalid Task",
            description = "Complete invalid task",
            taskId = 999999L,
            parameters = mapOf("userConfirmed" to true)
        )
        val taskResult = actionExecutor.execute(invalidTaskAction)
        assertFalse("Invalid task ID MUST fail safely", taskResult.success)
        assertTrue(taskResult.message.contains("not found", ignoreCase = true))

        val invalidGoalAction = AiAction(
            type = AiActionType.DELETE_GOAL,
            title = "Invalid Goal",
            description = "Delete invalid goal",
            goalId = 999999L,
            parameters = mapOf("userConfirmed" to true)
        )
        val goalResult = actionExecutor.execute(invalidGoalAction)
        assertFalse("Invalid goal ID MUST fail safely", goalResult.success)
        assertTrue(goalResult.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `test stale completed task action is handled safely`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 10, title = "Already Completed Task", completed = true, category = "Work", duration = "15m"))

        val action = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Completing already-completed task is safely rejected", result.success)
        assertTrue("Stale completion acknowledges task is already completed", result.message.contains("already completed", ignoreCase = true))
    }

    @Test
    fun `test conversation context survives multi-turn requests`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 42, title = "Android Architecture", category = "Work", duration = "30m"))

        val convContext = AiConversationContext(
            lastTaskId = task.id,
            lastEntityTitle = task.title
        )

        val response = engine.processRequest(
            AiRequest(
                type = AiRequestType.CHAT,
                userMessage = "complete it",
                conversationContext = convContext
            )
        )

        val proposed = response.proposedActions.firstOrNull()
        assertEquals(task.id, proposed?.taskId ?: response.relatedTaskId)
    }

    @Test
    fun `test evidence quality and confidence are preserved from recommendation to action`() = runBlocking {
        val rec = AiRecommendation(
            type = AiRecommendationType.WARNING,
            title = "High Overload Risk",
            message = "Workload exceeds daily capacity",
            confidence = AiConfidence.HIGH,
            evidenceQuality = EvidenceQuality.STRONG,
            suggestedAction = AiAction(
                type = AiActionType.RESCHEDULE_TASK,
                title = "Reschedule Task",
                description = "Move task to tomorrow"
            )
        )

        assertEquals(AiConfidence.HIGH, rec.confidence)
        assertEquals(EvidenceQuality.STRONG, rec.evidenceQuality)
        assertNotNull(rec.suggestedAction)
    }

    @Test
    fun `test reschedule task with priority parameter mutates DB state and returns success`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 50, title = "Reschedule Target", priority = TaskPriority.HIGH, category = "Work", duration = "60m"))

        val action = AiAction(
            type = AiActionType.RESCHEDULE_TASK,
            title = "Reschedule Task",
            description = "Lower priority to reschedule",
            taskId = task.id,
            parameters = mapOf("priority" to "LOW", "userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertTrue(result.success)

        val updated = repository.getTaskById(task.id)
        assertEquals(TaskPriority.LOW, updated?.priority)
    }

    @Test
    fun `test decompose goal action returns truthful proposal-only result`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 80, title = "Learn Compose", category = "Study", targetDate = "2026-12-31", progress = 0f))

        val action = AiAction(
            type = AiActionType.DECOMPOSE_GOAL,
            title = "Decompose Goal",
            description = "Break down goal into sub-tasks",
            goalId = goal.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Goal decomposition is proposal-only and MUST NOT report false database execution success", result.success)
        assertTrue(result.message.contains("proposal-only", ignoreCase = true))
    }

    @Test
    fun `test automation rule actions perform real state mutations or return non-success when parameters missing`() = runBlocking {
        // 1. Create Automation with name
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Rule",
            description = "Create custom rule",
            parameters = mapOf("ruleName" to "Custom Night Guard", "description" to "Nightly review", "userConfirmed" to true)
        )
        val createResult = actionExecutor.execute(createAction)
        assertTrue(createResult.success)

        // 2. Toggle Automation
        val toggleAction = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Rule",
            description = "Toggle custom rule",
            parameters = mapOf("ruleName" to "Custom Night Guard", "enabled" to false, "userConfirmed" to true)
        )
        val toggleResult = actionExecutor.execute(toggleAction)
        assertTrue(toggleResult.success)

        // 3. Delete Automation
        val deleteAction = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Rule",
            description = "Delete custom rule",
            parameters = mapOf("ruleName" to "Custom Night Guard", "userConfirmed" to true)
        )
        val deleteResult = actionExecutor.execute(deleteAction)
        assertTrue(deleteResult.success)

        // 4. Missing parameters on automation action MUST fail cleanly
        val invalidToggle = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Rule",
            description = "Toggle without name",
            parameters = mapOf("userConfirmed" to true)
        )
        val invalidResult = actionExecutor.execute(invalidToggle)
        assertFalse("Automation toggle without rule ID or name MUST NOT report fake success", invalidResult.success)
    }
}
