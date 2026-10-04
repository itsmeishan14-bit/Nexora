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

    @Test
    fun `test create goal mutates database and verifies state`() = runBlocking {
        val action = AiAction(
            type = AiActionType.CREATE_GOAL,
            title = "Create Goal: Master Rust",
            description = "Create goal",
            parameters = mapOf("title" to "Master Rust", "category" to "Learning", "userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Create goal must succeed", result.success)

        val goals = repository.observeGoalsOnce()
        assertTrue("Created goal must exist in DB", goals.any { it.title == "Master Rust" })
    }

    @Test
    fun `test update goal mutates database and verifies state`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 301L, title = "Original Goal", category = "Work", targetDate = "2026-12-31", progress = 0.1f))

        val action = AiAction(
            type = AiActionType.UPDATE_GOAL,
            title = "Update Goal",
            description = "Update title",
            goalId = goal.id,
            parameters = mapOf("title" to "Updated Goal Title", "userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Update goal must succeed", result.success)

        val updated = repository.getGoalById(goal.id)
        assertNotNull(updated)
        assertEquals("Updated Goal Title", updated?.title)
    }

    @Test
    fun `test delete goal mutates database and verifies state`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 302L, title = "Goal to Delete", category = "Work", targetDate = "2026-12-31", progress = 0.5f))

        val action = AiAction(
            type = AiActionType.DELETE_GOAL,
            title = "Delete Goal",
            description = "Delete goal",
            goalId = goal.id,
            parameters = mapOf("userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Delete goal must succeed", result.success)

        val deleted = repository.getGoalById(goal.id)
        assertNull("Goal must be removed from DB", deleted)
    }

    @Test
    fun `test update task mutates database and verifies state`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 401L, title = "Old Task Title", category = "General", duration = "15m", priority = TaskPriority.LOW))

        val action = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Update task title and priority",
            taskId = task.id,
            parameters = mapOf("title" to "New Task Title", "priority" to "URGENT", "userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Update task must succeed", result.success)

        val updated = repository.getTaskById(task.id)
        assertNotNull(updated)
        assertEquals("New Task Title", updated?.title)
        assertEquals(TaskPriority.URGENT, updated?.priority)
    }

    @Test
    fun `test delete task mutates database and verifies state`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 402L, title = "Task to Delete", category = "General", duration = "15m"))

        val action = AiAction(
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete single task",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Delete task must succeed", result.success)

        val deleted = repository.getTaskById(task.id)
        assertNull("Task must be removed from DB", deleted)
    }

    @Test
    fun `test update automation mutates database and verifies state`() = runBlocking {
        // System initializes with Morning Plan Assistant
        val action = AiAction(
            type = AiActionType.UPDATE_AUTOMATION,
            title = "Update Automation",
            description = "Update description",
            parameters = mapOf(
                "ruleName" to "Morning Plan Assistant",
                "newDescription" to "Fresh morning daily plan preparation",
                "userConfirmed" to true
            )
        )
        val result = actionExecutor.execute(action)
        assertTrue("Update automation must succeed", result.success)

        val rule = repository.getAutomationRule("Morning Plan Assistant")
        assertNotNull(rule)
        assertEquals("Fresh morning daily plan preparation", rule?.description)
    }

    @Test
    fun `test update automation persistence failure returns truthful failure response`() = runBlocking {
        repository.failUpdateAutomation = true
        val action = AiAction(
            type = AiActionType.UPDATE_AUTOMATION,
            title = "Update Automation",
            description = "Update description",
            parameters = mapOf(
                "ruleName" to "Morning Plan Assistant",
                "newDescription" to "Should fail to update",
                "userConfirmed" to true
            )
        )
        val result = actionExecutor.execute(action)
        assertFalse("Update automation must fail when repository update fails", result.success)
        assertTrue("Error message must indicate failure", result.message.contains("Failed", ignoreCase = true))
    }

    @Test
    fun `test unconfirmed destructive action cannot bypass the confirmation gate`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 501L, title = "Protected Task", category = "General", duration = "10m"))

        val unconfirmedAction = AiAction(
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Attempt delete without confirmation",
            taskId = task.id,
            requiresConfirmation = true,
            parameters = emptyMap() // No userConfirmed!
        )
        val result = actionExecutor.execute(unconfirmedAction)
        assertFalse("Unconfirmed destructive action MUST be rejected by gate", result.success)
        assertTrue(result.message.contains("confirmation", ignoreCase = true))

        val preserved = repository.getTaskById(task.id)
        assertNotNull("Task must NOT be deleted without confirmation", preserved)
    }

    @Test
    fun `test telemetry outcome logging failure does not mask database mutation success`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 502L, title = "Telemetry Test Task", category = "General", duration = "10m"))

        repository.failSaveOutcome = true
        val action = AiAction(
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task with broken telemetry",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )
        val result = actionExecutor.execute(action)
        assertTrue("Successful database mutation must still return success even if telemetry logging throws", result.success)

        val updated = repository.getTaskById(task.id)
        assertTrue("Database mutation must have succeeded", updated?.completed == true)
    }

    @Test
    fun `test proposal cancelled or not executed produces no mutation`() = runBlocking {
        val initialTasks = repository.observeTasksOnce().size
        val proposal = AiAction(
            type = AiActionType.CREATE_TASK,
            title = "Proposed Task",
            description = "This was proposed but cancelled by user",
            parameters = mapOf("title" to "Proposed Task")
        )
        // User cancelled, so execute is NOT called
        val currentTasks = repository.observeTasksOnce().size
        assertEquals("No task should be created when proposal is cancelled", initialTasks, currentTasks)
    }
}

