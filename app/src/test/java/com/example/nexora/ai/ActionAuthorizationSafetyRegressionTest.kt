package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.util.NexoraSecurity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Focused regression tests for:
 * 1. Action Authorization & Pending-Confirmation Lifecycle
 * 2. Anti-tampering, Cancellation & Replaced Proposal Safety
 * 3. Duplicate Execution & Concurrency Safety
 * 4. Confirmation Handle Isolation & Revocation on Failed Execution
 * 5. Replay Protection & Retry Policy across Executor Recreation
 * 6. Read-only Action Execution & Legitimate Chat Confirmation Flows
 */
class ActionAuthorizationSafetyRegressionTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setUp() {
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
    }

    @Test
    fun `test unconfirmed delete action is rejected`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 101L, title = "Task to protect", category = "Work", duration = "20m"))

        // Unconfirmed delete action without proposal or security grant
        val unconfirmedAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete task without confirmation",
            taskId = task.id,
            requiresConfirmation = true
        )

        val result = actionExecutor.execute(unconfirmedAction)

        assertFalse("Unconfirmed delete action MUST be rejected", result.success)
        assertEquals("Authorization error", result.error)
        assertNotNull("Task must still exist in repository", repository.getTaskById(task.id))
    }

    @Test
    fun `test action cannot bypass authorization merely because an arbitrary caller sets userConfirmed = true`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 102L, title = "Protected Task", category = "Work", duration = "15m"))

        // Forged action attempting to bypass authorization with userConfirmed = true in parameters
        val forgedAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Forged Delete Task",
            description = "Bypass attempt",
            taskId = task.id,
            parameters = mapOf("userConfirmed" to true),
            requiresConfirmation = false
        )

        val result = actionExecutor.execute(forgedAction)

        assertFalse("Action with forged userConfirmed parameter MUST be rejected", result.success)
        assertEquals("Authorization error", result.error)
        assertNotNull("Task must not be deleted by forged confirmation", repository.getTaskById(task.id))
    }

    @Test
    fun `test calling confirmation API directly with an arbitrary action is rejected`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 103L, title = "Task unproposed", category = "Work", duration = "10m"))

        val arbitraryAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Arbitrary Delete Task",
            description = "Directly sent without proposal",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Calling confirmPendingAction directly without prior trusted proposal must be rejected
        val result = engine.confirmPendingAction(arbitraryAction)

        assertFalse("Calling confirmation API directly with arbitrary action MUST be rejected", result.success)
        assertEquals("No pending proposal", result.error)
        assertNotNull("Task must remain in repository", repository.getTaskById(task.id))
    }

    @Test
    fun `test valid pending proposal succeeds after explicit confirmation and mutates repository`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 104L, title = "Task to Confirm Delete", category = "Work", duration = "10m"))

        val rawAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Legitimate delete proposal",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Trusted application flow proposes the action
        val proposedAction = engine.proposeAction(rawAction)
        assertTrue("Proposal must be registered as pending", engine.isProposalPending(proposedAction.id))
        assertNotNull("Confirmation token must be generated", proposedAction.parameters["confirmationToken"])

        // Explicit user confirmation
        val result = engine.confirmPendingAction(proposedAction)

        assertTrue("Valid pending proposal MUST succeed after explicit confirmation: ${result.error}", result.success)
        assertNull("Task must be deleted from repository", repository.getTaskById(task.id))
        assertFalse("Proposal must no longer be pending after consumption", engine.isProposalPending(proposedAction.id))
    }

    @Test
    fun `test confirmation for one target cannot authorize another target`() = runBlocking {
        val taskA = repository.addTask(PremiumTask(id = 201L, title = "Task A", category = "Work", duration = "15m"))
        val taskB = repository.addTask(PremiumTask(id = 202L, title = "Task B", category = "Work", duration = "15m"))

        val actionA = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task A",
            description = "Delete Task A",
            taskId = taskA.id,
            requiresConfirmation = true
        )

        val proposedA = engine.proposeAction(actionA)

        // Attacker swaps the target taskId to Task B using Task A's proposal
        val tamperedAction = proposedA.copy(taskId = taskB.id)

        val result = engine.confirmPendingAction(tamperedAction)

        assertFalse("Tampered target action MUST be rejected", result.success)
        assertEquals("Target mismatch", result.error)
        assertNotNull("Task A must not be deleted", repository.getTaskById(taskA.id))
        assertNotNull("Task B must not be deleted", repository.getTaskById(taskB.id))
    }

    @Test
    fun `test modified parameters invalidate a pending confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 301L, title = "Original Title", category = "Work", duration = "30m"))

        val pendingAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Update task title",
            taskId = task.id,
            parameters = mapOf("title" to "Approved New Title"),
            requiresConfirmation = true
        )

        val proposedAction = engine.proposeAction(pendingAction)

        // Tamper with parameters after proposal
        val tamperedParameters = proposedAction.parameters.toMutableMap()
        tamperedParameters["title"] = "Malicious Hijacked Title"
        val tamperedAction = proposedAction.copy(parameters = tamperedParameters)

        val result = engine.confirmPendingAction(tamperedAction)

        assertFalse("Tampered parameters MUST invalidate confirmation", result.success)
        assertEquals("Parameters modified", result.error)
        assertEquals("Original Title", repository.getTaskById(task.id)?.title)
    }

    @Test
    fun `test modifying action type after proposal creation invalidates confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 302L, title = "Type Mismatch Task", category = "Work", duration = "30m"))

        val pendingAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task",
            taskId = task.id,
            requiresConfirmation = true
        )

        val proposedAction = engine.proposeAction(pendingAction)

        // Tamper with action type from COMPLETE_TASK to DELETE_TASK
        val tamperedAction = proposedAction.copy(type = AiActionType.DELETE_TASK)

        val result = engine.confirmPendingAction(tamperedAction)

        assertFalse("Tampered action type MUST be rejected", result.success)
        assertEquals("Action type mismatch", result.error)
        assertNotNull("Task must not be deleted", repository.getTaskById(task.id))
    }

    @Test
    fun `test cancelled or dismissed proposal cannot execute`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 401L, title = "Task to Cancel", category = "Work", duration = "25m"))

        val pendingAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete task",
            taskId = task.id,
            requiresConfirmation = true
        )

        val proposedAction = engine.proposeAction(pendingAction)
        assertTrue(engine.isProposalPending(proposedAction.id))

        // User dismisses / cancels proposal
        engine.cancelProposal(proposedAction.id)
        assertFalse(engine.isProposalPending(proposedAction.id))

        val result = engine.confirmPendingAction(proposedAction)

        assertFalse("Cancelled proposal cannot execute", result.success)
        assertEquals("Proposal cancelled", result.error)
        assertNotNull("Persisted state must remain unchanged after cancellation", repository.getTaskById(task.id))
    }

    @Test
    fun `test replaced proposal cannot execute`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 402L, title = "Replaced Proposal Task", category = "Work", duration = "15m"))

        val actionId = UUID.randomUUID().toString()
        val action1 = AiAction(
            id = actionId,
            type = AiActionType.DELETE_TASK,
            title = "Delete Task Old",
            description = "Old proposal",
            taskId = task.id,
            parameters = mapOf("param" to "old"),
            requiresConfirmation = true
        )

        val proposed1 = engine.proposeAction(action1)

        // Application flow issues a newer proposal replacing actionId
        val action2 = AiAction(
            id = actionId,
            type = AiActionType.DELETE_TASK,
            title = "Delete Task New",
            description = "New proposal",
            taskId = task.id,
            parameters = mapOf("param" to "new"),
            requiresConfirmation = true
        )

        val proposed2 = engine.proposeAction(action2)

        // Attempting to confirm the superseded proposed1 MUST fail
        val resultOld = engine.confirmPendingAction(proposed1)
        assertFalse("Older replaced proposal MUST be rejected", resultOld.success)

        // New proposed2 succeeds
        val resultNew = engine.confirmPendingAction(proposed2)
        assertTrue("Newest proposal MUST succeed", resultNew.success)
        assertNull("Task must be deleted after newest proposal confirmation", repository.getTaskById(task.id))
    }

    @Test
    fun `test expired proposal cannot execute`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 403L, title = "Expiring Task", category = "Work", duration = "10m"))

        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Expiring Task",
            description = "Will expire",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Register proposal with very short TTL (1 ms)
        val proposed = engine.proposeAction(action, ttlMs = 1L)
        Thread.sleep(15) // Wait for expiration

        val result = engine.confirmPendingAction(proposed)

        assertFalse("Expired proposal MUST be rejected", result.success)
        assertEquals("Proposal expired", result.error)
        assertNotNull("Task must not be deleted by expired proposal", repository.getTaskById(task.id))
    }

    @Test
    fun `test repeated confirmation attempts cannot perform mutation twice`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 501L, title = "Single Confirmation Task", category = "Personal", duration = "15m"))

        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete task once",
            taskId = task.id,
            requiresConfirmation = true
        )

        val proposedAction = engine.proposeAction(action)

        // First confirmation succeeds
        val result1 = engine.confirmPendingAction(proposedAction)
        assertTrue("First confirmation must succeed", result1.success)
        assertNull("Task must be deleted", repository.getTaskById(task.id))

        // Repeated confirmation attempt of the exact same proposal
        val result2 = engine.confirmPendingAction(proposedAction)
        assertFalse("Repeated confirmation MUST be rejected as already consumed", result2.success)
        assertEquals("Proposal already consumed", result2.error)
    }

    @Test
    fun `test two concurrent confirmation attempts cannot perform mutation twice`() = runBlocking {
        val ruleName = "Unique Concurrent Rule"
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Rule",
            description = "Create automation rule",
            parameters = mapOf("name" to ruleName),
            requiresConfirmation = true
        )

        val proposedAction = engine.proposeAction(action)

        // Fire 10 concurrent confirmation attempts simultaneously
        val deferredResults = (1..10).map {
            async {
                engine.confirmPendingAction(proposedAction)
            }
        }

        val results = deferredResults.awaitAll()
        val successCount = results.count { it.success }
        val failCount = results.count { !it.success }

        assertEquals("Exactly one confirmation attempt MUST succeed", 1, successCount)
        assertEquals("Nine confirmation attempts MUST be rejected", 9, failCount)

        // Verify rejected attempts were due to consumption
        results.filter { !it.success }.forEach {
            assertEquals("Proposal already consumed", it.error)
        }

        // Verify persisted state: exactly 1 rule created
        val matchingRules = engine.getAutomationRules().filter { it.name == ruleName }
        assertEquals("Exactly one automation rule must exist in repository", 1, matchingRules.size)
    }

    @Test
    fun `test confirmation handle cannot be reused for another action`() = runBlocking {
        val taskA = repository.addTask(PremiumTask(id = 601L, title = "Task A", category = "Work", duration = "15m"))
        val taskB = repository.addTask(PremiumTask(id = 602L, title = "Task B", category = "Work", duration = "15m"))

        val actionA = engine.proposeAction(AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task A",
            description = "Delete Task A",
            taskId = taskA.id,
            requiresConfirmation = true
        ))

        val confirmationToken = actionA.parameters["confirmationToken"]
        assertNotNull(confirmationToken)

        // Attacker creates unproposed actionB targeting Task B and attaches actionA's confirmationToken
        val actionB = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task B",
            description = "Illegitimate copy of token",
            taskId = taskB.id,
            parameters = mapOf("confirmationToken" to confirmationToken!!),
            requiresConfirmation = true
        )

        val result = engine.confirmPendingAction(actionB)

        assertFalse("Confirmation handle cannot be reused for another action", result.success)
        assertEquals("No pending proposal", result.error)
        assertNotNull("Task B must not be deleted", repository.getTaskById(taskB.id))
        assertNotNull("Task A must still exist", repository.getTaskById(taskA.id))
    }

    @Test
    fun `test failed execution cannot accidentally reuse consumed approval`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 603L, title = "Failing Task", category = "Work", duration = "10m"))

        val action = engine.proposeAction(AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Will fail initially",
            taskId = task.id,
            requiresConfirmation = true
        ))

        // Force repository failure during execution
        repository.failDeleteTask = true

        val failResult = engine.confirmPendingAction(action)
        assertFalse("Execution should fail when repository fails", failResult.success)

        // Resolve repository failure
        repository.failDeleteTask = false

        // Attempting to confirm again MUST be rejected as already consumed
        val retryConfirmResult = engine.confirmPendingAction(action)
        assertFalse("Consumed approval cannot be reused after failure", retryConfirmResult.success)
        assertEquals("Proposal already consumed", retryConfirmResult.error)

        // Attempting direct execution via actionExecutor must also fail because authorization was revoked
        val directResult = actionExecutor.execute(action)
        assertFalse("Authorization must have been revoked after execution attempt", directResult.success)
        assertEquals("Authorization error", directResult.error)

        assertNotNull("Task must still exist in repository", repository.getTaskById(task.id))
    }

    @Test
    fun `test calling executeAction alone cannot grant authorization on destructive action and does not mutate data`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 701L, title = "Sensitive Task", category = "Work", duration = "30m"))

        val unconfirmedDelete = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Sensitive Task",
            description = "Attempting deletion directly",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Calling engine.executeAction alone must NOT grant authorization
        val result = engine.executeAction(unconfirmedDelete)

        assertFalse("Calling executeAction alone MUST be rejected for destructive actions", result.success)
        assertEquals("Authorization error", result.error)
        assertNotNull("Task MUST remain intact in repository", repository.getTaskById(task.id))
    }

    @Test
    fun `test calling executeAction alone cannot grant authorization for delete all tasks`() = runBlocking {
        repository.addTask(PremiumTask(id = 702L, title = "Task 1", category = "Work", duration = "15m"))
        repository.addTask(PremiumTask(id = 703L, title = "Task 2", category = "Work", duration = "15m"))

        val deleteAllAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_ALL_TASKS,
            title = "Delete All Tasks",
            description = "Unconfirmed wipe",
            requiresConfirmation = true
        )

        val result = engine.executeAction(deleteAllAction)

        assertFalse("Calling executeAction alone cannot wipe all tasks", result.success)
        assertEquals("Authorization error", result.error)
        assertEquals("Tasks must not be deleted", 2, repository.observeTasksOnce().size)
    }

    @Test
    fun `test distinct intentional actions with identical parameters each succeed`() = runBlocking {
        val taskAction1 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Create Task",
            description = "Create daily task",
            parameters = mapOf("title" to "Morning Routine", "category" to "Personal", "duration" to "15m"),
            requiresConfirmation = false
        )
        val taskResult1 = actionExecutor.execute(taskAction1)
        assertTrue("First intentional action must succeed", taskResult1.success)

        // Complete the first task to permit creating a new task with identical parameters
        val createdTask1 = repository.getTaskById(taskResult1.affectedTaskId!!)!!
        repository.updateTask(createdTask1.copy(completed = true))

        val taskAction2 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Create Task",
            description = "Create daily task",
            parameters = mapOf("title" to "Morning Routine", "category" to "Personal", "duration" to "15m"),
            requiresConfirmation = false
        )
        val taskResult2 = actionExecutor.execute(taskAction2)
        assertTrue("Subsequent intentional action with identical parameters must succeed", taskResult2.success)
        assertNotEquals("Tasks must have distinct IDs", taskResult1.affectedTaskId, taskResult2.affectedTaskId)
    }

    @Test
    fun `test missing target fails safely and leaves state unchanged`() = runBlocking {
        val missingTargetAction = engine.proposeAction(AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_GOAL,
            title = "Delete Missing Goal",
            description = "Delete nonexistent goal",
            goalId = 999999L,
            requiresConfirmation = true
        ))

        val result = engine.confirmPendingAction(missingTargetAction)

        assertFalse("Action targeting nonexistent goal MUST fail", result.success)
        assertTrue("Message must indicate target not found", result.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `test replaying completed action with altered parameters is rejected after executor recreation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 801L, title = "Original Name", category = "Work", duration = "10m"))

        val actionId = UUID.randomUUID().toString()
        val originalAction = AiAction(
            id = actionId,
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Update task",
            taskId = task.id,
            parameters = mapOf("title" to "Approved Update"),
            requiresConfirmation = false
        )

        val result1 = actionExecutor.execute(originalAction)
        assertTrue("Initial execution succeeds", result1.success)
        assertEquals("Approved Update", repository.getTaskById(task.id)?.title)

        // Verify outcome was saved with original fingerprint in evidence
        val savedOutcomes = repository.getRecentOutcomes(10)
        val outcome = savedOutcomes.find { it.actionId == actionId && it.type == AiOutcomeType.SUCCESS }
        assertNotNull("Successful outcome must be persisted", outcome)
        assertTrue("Evidence must contain original fingerprint", outcome?.evidence?.startsWith("fp:") == true)

        // Simulate app restart / new executor instance
        val freshExecutor = AiActionExecutor(repository)

        // Attempt to replay the completed action with tampered/altered parameters
        val tamperedReplayAction = originalAction.copy(
            parameters = mapOf("title" to "Tampered Across Restart")
        )

        val replayResult = freshExecutor.execute(tamperedReplayAction)
        assertFalse("Replaying completed action with altered parameters after recreation MUST be rejected", replayResult.success)
        assertEquals("Replay with modified parameters rejected", replayResult.error)
        assertEquals("Approved Update", repository.getTaskById(task.id)?.title)
    }

    @Test
    fun `test replaying completed action with identical parameters is rejected as duplicate after executor recreation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 802L, title = "Task Once", category = "Work", duration = "10m"))

        val actionId = UUID.randomUUID().toString()
        val action = AiAction(
            id = actionId,
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task Once",
            description = "Complete task",
            taskId = task.id,
            requiresConfirmation = false
        )

        val result1 = actionExecutor.execute(action)
        assertTrue("Initial execution succeeds", result1.success)

        // Simulate app restart / new executor instance
        val freshExecutor = AiActionExecutor(repository)

        val duplicateResult = freshExecutor.execute(action)
        assertFalse("Replaying completed action after recreation MUST be rejected as duplicate", duplicateResult.success)
        assertEquals("Duplicate execution rejected", duplicateResult.error)
    }

    @Test
    fun `test historical outcome lacking fingerprint fails closed upon replay after executor recreation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 803L, title = "Legacy Task", category = "Work", duration = "10m"))

        val actionId = UUID.randomUUID().toString()
        val action = AiAction(
            id = actionId,
            type = AiActionType.UPDATE_TASK,
            title = "Update Legacy Task",
            description = "Legacy outcome replay attempt",
            taskId = task.id,
            parameters = mapOf("title" to "New Name"),
            requiresConfirmation = false
        )

        // Manually simulate a legacy outcome saved without fingerprint in evidence
        repository.saveOutcome(
            AiOutcome(
                id = UUID.randomUUID().toString(),
                recommendationId = null,
                actionId = actionId,
                type = AiOutcomeType.SUCCESS,
                timestamp = System.currentTimeMillis(),
                relatedTaskId = task.id,
                expectedResult = action.title,
                actualResult = "Action completed successfully.",
                evidence = "Legacy outcome evidence without fingerprint"
            )
        )

        // Simulate new executor instance
        val freshExecutor = AiActionExecutor(repository)

        val replayResult = freshExecutor.execute(action)
        assertFalse("Historical outcome lacking fingerprint MUST fail closed", replayResult.success)
        assertEquals("Replay verification failed: missing original fingerprint", replayResult.error)
    }

    @Test
    fun `test legitimate failed action can be retried across executor instances`() = runBlocking {
        val actionId = UUID.randomUUID().toString()
        val action = AiAction(
            id = actionId,
            type = AiActionType.CREATE_TASK,
            title = "Retryable Task Across Restarts",
            description = "Create task with retry across restart",
            parameters = mapOf("title" to "Retryable Task Across Restarts", "category" to "Work", "duration" to "30m"),
            requiresConfirmation = false
        )

        // Simulate failed attempt in executor 1
        repository.failAddTask = true
        val failResult = actionExecutor.execute(action)
        assertFalse(failResult.success)

        val savedFailOutcome = repository.getRecentOutcomes(5).find { it.actionId == actionId }
        assertNotNull("Failed outcome should be recorded", savedFailOutcome)
        assertEquals(AiOutcomeType.FAILED, savedFailOutcome?.type)

        // Resolve transient failure
        repository.failAddTask = false

        // Simulate app restart / new executor instance
        val freshExecutor = AiActionExecutor(repository)

        val retryResult = freshExecutor.execute(action)
        assertTrue("Legitimate failed action MUST be allowed to retry across executor instances", retryResult.success)
        assertNotNull("Task must exist in repository after retry", repository.observeTasksOnce().find { it.title == "Retryable Task Across Restarts" })
    }

    @Test
    fun `test read-only and safe actions execute without confirmation dialog`() = runBlocking {
        val insightAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.SHOW_INSIGHT,
            title = "Show Insight",
            description = "Display productivity insight",
            requiresConfirmation = false
        )

        val result = engine.executeAction(insightAction)
        assertTrue("Read-only insight action must execute without requiring confirmation", result.success)

        val task = repository.addTask(PremiumTask(id = 850L, title = "Task to Open", category = "Work", duration = "10m"))
        val openTaskAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.OPEN_TASK,
            title = "Open Task",
            description = "Navigate to task",
            taskId = task.id,
            requiresConfirmation = false
        )
        val openResult = engine.executeAction(openTaskAction)
        assertTrue("Open task action must execute without requiring confirmation: ${openResult.error}", openResult.success)
    }

    @Test
    fun `test legitimate chat confirmation flow registers proposal and executes on user confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 901L, title = "Study Rust", category = "Study", duration = "45m"))

        // Step 1: User asks AI to delete task
        val chatResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete Study Rust"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, chatResponse.responseType)
        val proposed = chatResponse.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertTrue("Chat action proposal must be registered as pending", engine.isProposalPending(proposed!!.id))

        // Step 2: User confirms
        val confirmResult = engine.confirmPendingAction(proposed)
        assertTrue("Explicit confirmation of chat proposal must succeed", confirmResult.success)
        assertNull("Task must be deleted after confirmation", repository.getTaskById(task.id))
    }
}
