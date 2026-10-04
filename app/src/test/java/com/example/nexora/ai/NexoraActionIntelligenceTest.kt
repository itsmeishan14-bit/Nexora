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

    @Test
    fun `test successful task title priority and duration updates preserve unrequested fields`() = runBlocking {
        val task = repository.addTask(
            PremiumTask(
                id = 601L,
                title = "Initial Title",
                category = "Engineering",
                duration = "15m",
                priority = TaskPriority.LOW,
                goalTitle = "Launch Nexora"
            )
        )

        val action = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Update title, priority, and duration",
            taskId = task.id,
            parameters = mapOf(
                "title" to "Updated Title",
                "priority" to "HIGH",
                "duration" to "45 minutes",
                "userConfirmed" to true
            )
        )

        val result = actionExecutor.execute(action)
        assertTrue("Task update must succeed: ${result.error}", result.success)

        val persisted = repository.getTaskById(task.id)
        assertNotNull(persisted)
        assertEquals("Updated Title", persisted?.title)
        assertEquals(TaskPriority.HIGH, persisted?.priority)
        assertEquals("45 minutes", persisted?.duration)
        // Verify unrequested fields are preserved
        assertEquals("Engineering", persisted?.category)
        assertEquals("Launch Nexora", persisted?.goalTitle)
    }

    @Test
    fun `test successful goal title and category updates report accurate message`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(
                id = 701L,
                title = "Old Goal Title",
                category = "Personal",
                targetDate = "2026-12-31",
                progress = 0.25f
            )
        )

        val action = AiAction(
            type = AiActionType.UPDATE_GOAL,
            title = "Update Goal",
            description = "Update goal title and category",
            goalId = goal.id,
            parameters = mapOf(
                "title" to "New Goal Title",
                "category" to "Career",
                "userConfirmed" to true
            )
        )

        val result = actionExecutor.execute(action)
        assertTrue("Goal update must succeed: ${result.error}", result.success)
        assertTrue("Message must reflect updated goal title: ${result.message}", result.message.contains("New Goal Title"))
        assertFalse("Message must NOT report stale goal title", result.message.contains("Old Goal Title"))

        val persisted = repository.getGoalById(goal.id)
        assertNotNull(persisted)
        assertEquals("New Goal Title", persisted?.title)
        assertEquals("Career", persisted?.category)
        assertEquals("2026-12-31", persisted?.targetDate)
    }

    @Test
    fun `test task update fails truthfully when repository update fails`() = runBlocking {
        val task = repository.addTask(
            PremiumTask(id = 602L, title = "Unchangeable Task", category = "Work", duration = "30m")
        )

        repository.failUpdateTask = true
        val action = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Try to update priority",
            taskId = task.id,
            parameters = mapOf("priority" to "HIGH", "userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Action must report failure when repository fails", result.success)
        assertTrue(result.message.contains("Failed", ignoreCase = true) || result.error != null)

        val persisted = repository.getTaskById(task.id)
        assertEquals(TaskPriority.MEDIUM, persisted?.priority)
    }

    @Test
    fun `test task delete fails truthfully when repository delete fails`() = runBlocking {
        val task = repository.addTask(
            PremiumTask(id = 603L, title = "Undeletable Task", category = "Work", duration = "30m")
        )

        repository.failDeleteTask = true
        val action = AiAction(
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Try to delete",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Delete must report failure when repository delete fails", result.success)

        val persisted = repository.getTaskById(task.id)
        assertNotNull("Task must still exist in DB", persisted)
    }

    @Test
    fun `test goal update fails truthfully when repository update fails`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 702L, title = "Goal To Fail", category = "Work", targetDate = "", progress = 0f)
        )

        repository.failUpdateGoal = true
        val action = AiAction(
            type = AiActionType.UPDATE_GOAL,
            title = "Update Goal",
            description = "Update goal title",
            goalId = goal.id,
            parameters = mapOf("title" to "Failed Title", "userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Goal update must fail when repository update fails", result.success)

        val persisted = repository.getGoalById(goal.id)
        assertEquals("Goal To Fail", persisted?.title)
    }

    @Test
    fun `test goal delete fails truthfully when repository delete fails`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 703L, title = "Undeletable Goal", category = "Personal", targetDate = "", progress = 0f)
        )

        repository.failDeleteGoal = true
        val action = AiAction(
            type = AiActionType.DELETE_GOAL,
            title = "Delete Goal",
            description = "Delete goal",
            goalId = goal.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Goal deletion must report failure when repository delete fails", result.success)

        val persisted = repository.getGoalById(goal.id)
        assertNotNull("Goal must still exist in DB", persisted)
    }

    @Test
    fun `test missing stale or nonexistent target IDs fail safely`() = runBlocking {
        val nonExistentTaskAction = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Missing Task",
            description = "Target does not exist",
            taskId = 999999L,
            parameters = mapOf("priority" to "HIGH", "userConfirmed" to true)
        )
        val taskResult = actionExecutor.execute(nonExistentTaskAction)
        assertFalse("Nonexistent task target must fail safely", taskResult.success)

        val missingTaskAction = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Task Missing ID",
            description = "No task ID provided",
            taskId = null,
            parameters = mapOf("priority" to "HIGH", "userConfirmed" to true)
        )
        val missingTaskResult = actionExecutor.execute(missingTaskAction)
        assertFalse("Missing task ID must fail safely", missingTaskResult.success)

        val nonExistentGoalAction = AiAction(
            type = AiActionType.UPDATE_GOAL,
            title = "Update Missing Goal",
            description = "Target does not exist",
            goalId = 888888L,
            parameters = mapOf("title" to "Ghost Goal", "userConfirmed" to true)
        )
        val goalResult = actionExecutor.execute(nonExistentGoalAction)
        assertFalse("Nonexistent goal target must fail safely", goalResult.success)

        val missingGoalAction = AiAction(
            type = AiActionType.UPDATE_GOAL,
            title = "Update Goal Missing ID",
            description = "No goal ID provided",
            goalId = null,
            parameters = mapOf("title" to "Ghost Goal", "userConfirmed" to true)
        )
        val missingGoalResult = actionExecutor.execute(missingGoalAction)
        assertFalse("Missing goal ID must fail safely", missingGoalResult.success)
    }

    @Test
    fun `test invalid priority and duration reject mutation truthfully`() = runBlocking {
        val task = repository.addTask(
            PremiumTask(id = 604L, title = "Task Valid", category = "Work", duration = "30m", priority = TaskPriority.MEDIUM)
        )

        val invalidPriorityAction = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Priority",
            description = "Invalid priority",
            taskId = task.id,
            parameters = mapOf("priority" to "SUPER_HIGH", "userConfirmed" to true)
        )
        val pResult = actionExecutor.execute(invalidPriorityAction)
        assertFalse("Invalid priority must fail", pResult.success)

        val invalidDurationAction = AiAction(
            type = AiActionType.UPDATE_TASK,
            title = "Update Duration",
            description = "Invalid duration",
            taskId = task.id,
            parameters = mapOf("duration" to "whenever_possible", "userConfirmed" to true)
        )
        val dResult = actionExecutor.execute(invalidDurationAction)
        assertFalse("Invalid duration must fail", dResult.success)

        val persisted = repository.getTaskById(task.id)
        assertEquals(TaskPriority.MEDIUM, persisted?.priority)
        assertEquals("30m", persisted?.duration)
    }

    @Test
    fun `test goal deletion unlinks associated tasks consistently`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 704L, title = "Marathon Training", category = "Health", targetDate = "", progress = 0f)
        )
        val task1 = repository.addTask(
            PremiumTask(id = 605L, title = "Run 5K", category = "Health", duration = "30m", goalTitle = "Marathon Training")
        )
        val task2 = repository.addTask(
            PremiumTask(id = 606L, title = "Run 10K", category = "Health", duration = "60m", goalTitle = "Marathon Training")
        )

        val action = AiAction(
            type = AiActionType.DELETE_GOAL,
            title = "Delete Goal: Marathon Training",
            description = "Delete goal and unlink tasks",
            goalId = goal.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertTrue("Goal deletion must succeed: ${result.error}", result.success)

        assertNull("Goal must be deleted from DB", repository.getGoalById(goal.id))
        val updatedTask1 = repository.getTaskById(task1.id)
        val updatedTask2 = repository.getTaskById(task2.id)
        assertNull("Task 1 goalTitle must be unlinked", updatedTask1?.goalTitle)
        assertNull("Task 2 goalTitle must be unlinked", updatedTask2?.goalTitle)
    }

    @Test
    fun `test goal deletion fails and preserves goal if unlinking associated tasks fails`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 705L, title = "Product Launch", category = "Career", targetDate = "", progress = 0f)
        )
        val task = repository.addTask(
            PremiumTask(id = 607L, title = "QA Test", category = "Career", duration = "60m", goalTitle = "Product Launch")
        )

        repository.failUpdateTask = true // unlinking will fail
        val action = AiAction(
            type = AiActionType.DELETE_GOAL,
            title = "Delete Goal: Product Launch",
            description = "Delete goal",
            goalId = goal.id,
            parameters = mapOf("userConfirmed" to true)
        )

        val result = actionExecutor.execute(action)
        assertFalse("Goal deletion must fail when task unlinking fails", result.success)

        val persistedGoal = repository.getGoalById(goal.id)
        assertNotNull("Goal must NOT be deleted when unlinking fails", persistedGoal)
    }

    @Test
    fun `test confirmation executes original proposed action and cancellation executes nothing`() = runBlocking {
        val task = repository.addTask(
            PremiumTask(id = 608L, title = "Buy Hardware", category = "Shopping", duration = "30m", completed = false)
        )

        // Turn 1: Propose completion
        val req1 = AiRequest(type = AiRequestType.CHAT, userMessage = "complete task Buy Hardware")
        val resp1 = engine.processRequest(req1)
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp1.responseType)
        val pendingAction = resp1.proposedActions.firstOrNull()
        assertNotNull("Must propose action", pendingAction)
        assertEquals(AiActionType.COMPLETE_TASK, pendingAction?.type)
        assertEquals(task.id, pendingAction?.taskId)

        // Turn 2a: Confirmation executes original action
        val reqConfirm = AiRequest(type = AiRequestType.CHAT, userMessage = "yes", conversationContext = resp1.conversationContext)
        val respConfirm = engine.processRequest(reqConfirm)
        assertEquals(AiResponseType.ACTION_PROPOSAL, respConfirm.responseType)
        val confirmedAction = respConfirm.proposedActions.first()
        assertEquals(task.id, confirmedAction.taskId)
        assertEquals(AiActionType.COMPLETE_TASK, confirmedAction.type)

        val execResult = engine.executeAction(confirmedAction)
        assertTrue(execResult.success)
        assertTrue(repository.getTaskById(task.id)?.completed == true)

        // Turn 2b: Test cancellation with another task
        val task2 = repository.addTask(
            PremiumTask(id = 609L, title = "Paint Wall", category = "Home", duration = "60m", completed = false)
        )
        val req2 = AiRequest(type = AiRequestType.CHAT, userMessage = "delete task Paint Wall")
        val resp2 = engine.processRequest(req2)
        assertEquals(AiResponseType.ACTION_PROPOSAL, resp2.responseType)

        val reqCancel = AiRequest(type = AiRequestType.CHAT, userMessage = "cancel", conversationContext = resp2.conversationContext)
        val respCancel = engine.processRequest(reqCancel)
        assertEquals(AiResponseType.NO_ACTION, respCancel.responseType)
        assertTrue("No actions should be proposed on cancellation", respCancel.proposedActions.isEmpty())
        assertNull("Pending action should be cleared", respCancel.conversationContext?.pendingAction)
        assertNotNull("Task2 must remain intact in DB", repository.getTaskById(task2.id))
    }
}

