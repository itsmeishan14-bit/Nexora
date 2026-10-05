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
 * 1. Action Authorization & Anti-tampering
 * 2. Duplicate Execution & Concurrency Safety
 * 3. Replay Protection & Retry Policy
 * 4. Distinct Intentional Actions
 */
class ActionAuthorizationSafetyRegressionTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var actionExecutor: AiActionExecutor

    @Before
    fun setUp() {
        NexoraSecurity.clearAllAuthorizations()
        repository = MockNexoraRepository()
        actionExecutor = AiActionExecutor(repository)
    }

    @Test
    fun `test unconfirmed delete action is rejected`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 101L, title = "Task to protect", category = "Work", duration = "20m"))

        // Unconfirmed delete action without security grant
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
    fun `test valid confirmation authorizes only the matching pending action`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 103L, title = "Authorized Delete Task", category = "Work", duration = "10m"))

        val pendingAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete single task",
            taskId = task.id,
            requiresConfirmation = true
        )

        // Grant explicit authorization (as done by confirmation flow)
        val authorizedAction = NexoraSecurity.grantAuthorization(pendingAction)

        val result = actionExecutor.execute(authorizedAction)

        assertTrue("Authorized action MUST succeed", result.success)
        assertNull("Task must be deleted after authorized execution", repository.getTaskById(task.id))
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

        // Grant authorization for Task A
        val authorizedActionA = NexoraSecurity.grantAuthorization(actionA)

        // Attacker swaps the target taskId to Task B using Task A's authorization
        val tamperedAction = authorizedActionA.copy(taskId = taskB.id)

        val result = actionExecutor.execute(tamperedAction)

        assertFalse("Tampered target action MUST be rejected", result.success)
        assertEquals("Authorization error", result.error)
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

        // Grant authorization for "Approved New Title"
        val authorizedAction = NexoraSecurity.grantAuthorization(pendingAction)

        // Tamper with parameters after authorization
        val tamperedParameters = authorizedAction.parameters.toMutableMap()
        tamperedParameters["title"] = "Malicious Hijacked Title"
        val tamperedAction = authorizedAction.copy(parameters = tamperedParameters)

        val result = actionExecutor.execute(tamperedAction)

        assertFalse("Tampered parameters MUST invalidate authorization", result.success)
        assertEquals("Authorization error", result.error)
        assertEquals("Original Title", repository.getTaskById(task.id)?.title)
    }

    @Test
    fun `test cancellation leaves persisted state unchanged`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 401L, title = "Task to Cancel", category = "Work", duration = "25m"))

        val pendingAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_TASK,
            title = "Delete Task",
            description = "Delete task",
            taskId = task.id,
            requiresConfirmation = true
        )

        // User cancels: revoke authorization and clear pending action
        NexoraSecurity.revokeAuthorization(pendingAction.id)

        val result = actionExecutor.execute(pendingAction)

        assertFalse("Cancelled action cannot execute", result.success)
        assertNotNull("Persisted state must remain unchanged after cancellation", repository.getTaskById(task.id))
    }

    @Test
    fun `test repeated confirmation cannot execute the same action twice`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 501L, title = "Single Execution Task", category = "Personal", duration = "15m"))

        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.COMPLETE_TASK,
            title = "Complete Task",
            description = "Complete task once",
            taskId = task.id,
            requiresConfirmation = false
        )

        // First execution succeeds
        val result1 = actionExecutor.execute(action)
        assertTrue("First execution must succeed", result1.success)

        // Repeated execution attempt of the exact same action ID
        val result2 = actionExecutor.execute(action)
        assertFalse("Repeated execution MUST be rejected as duplicate", result2.success)
        assertEquals("Duplicate execution rejected", result2.error)
    }

    @Test
    fun `test two concurrent execution attempts cannot duplicate the same mutation`() = runBlocking {
        val ruleName = "Unique Concurrent Rule"
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Automation",
            description = "Concurrent automation creation test",
            parameters = mapOf("name" to ruleName),
            requiresConfirmation = false
        )

        // Launch two concurrent execution attempts with the same action ID
        val deferred1 = async { actionExecutor.execute(action) }
        val deferred2 = async { actionExecutor.execute(action) }

        val results = listOf(deferred1.await(), deferred2.await())

        val successCount = results.count { it.success }
        val rejectedCount = results.count { !it.success && (it.error == "Duplicate execution rejected" || it.error == "Duplicate rule name") }

        assertEquals("Exactly one concurrent execution must succeed", 1, successCount)
        assertEquals("Exactly one concurrent execution must be rejected as duplicate", 1, rejectedCount)
        assertEquals("Exactly one rule must be created in automation system", 1, actionExecutor.getAutomationSystem().getRules().count { it.name == ruleName })
    }

    @Test
    fun `test failed action can be retried according to a clearly defined policy`() = runBlocking {
        val action = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Retryable Task",
            description = "Create task with retry",
            parameters = mapOf("title" to "Retryable Task", "category" to "Work", "duration" to "30m"),
            requiresConfirmation = false
        )

        // Simulate repository failure during first execution attempt
        repository.failAddTask = true
        val failResult = actionExecutor.execute(action)
        assertFalse("First attempt must fail due to repository error", failResult.success)

        // Resolve the transient failure condition
        repository.failAddTask = false

        // Retry the exact same action
        val retryResult = actionExecutor.execute(action)
        assertTrue("Retry of failed action MUST be allowed and succeed", retryResult.success)
        assertNotNull("Task must be created after successful retry", repository.observeTasksOnce().find { it.title == "Retryable Task" })
    }

    @Test
    fun `test completed action cannot be replayed with modified parameters`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 601L, title = "Original Name", category = "Work", duration = "10m"))

        val actionId = UUID.randomUUID().toString()
        val originalAction = AiAction(
            id = actionId,
            type = AiActionType.UPDATE_TASK,
            title = "Update Task",
            description = "Update task",
            taskId = task.id,
            parameters = mapOf("title" to "Legitimate Update"),
            requiresConfirmation = false
        )

        val result1 = actionExecutor.execute(originalAction)
        assertTrue("Initial execution succeeds", result1.success)

        // Attempt to replay the completed action ID with modified parameters
        val replayedActionWithModifiedParams = originalAction.copy(
            parameters = mapOf("title" to "Tampered Replay Name")
        )

        val result2 = actionExecutor.execute(replayedActionWithModifiedParams)
        assertFalse("Replay with modified parameters MUST be rejected", result2.success)
        assertEquals("Replay with modified parameters rejected", result2.error)
        assertEquals("Legitimate Update", repository.getTaskById(task.id)?.title)
    }

    @Test
    fun `test two separately initiated identical create actions remain distinct`() = runBlocking {
        // 1. Two separate goal creations with identical parameters remain distinct
        val goalAction1 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_GOAL,
            title = "Read 12 Books",
            description = "First goal intent",
            parameters = mapOf("title" to "Read 12 Books", "category" to "Personal", "targetDate" to "2026-12-31"),
            requiresConfirmation = false
        )

        val goalAction2 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_GOAL,
            title = "Read 12 Books",
            description = "Second goal intent with identical parameters",
            parameters = mapOf("title" to "Read 12 Books", "category" to "Personal", "targetDate" to "2026-12-31"),
            requiresConfirmation = false
        )

        val result1 = actionExecutor.execute(goalAction1)
        val result2 = actionExecutor.execute(goalAction2)

        assertTrue("First create goal action must succeed", result1.success)
        assertTrue("Second distinct create goal action must also succeed", result2.success)
        assertNotEquals("Created goals must have different IDs", result1.affectedGoalId, result2.affectedGoalId)

        val goals = repository.observeGoalsOnce().filter { it.title == "Read 12 Books" }
        assertEquals("Both distinct actions must have created their goals in the repository", 2, goals.size)

        // 2. Separate task creation after completion of prior identical task
        val taskAction1 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Weekly Review",
            description = "First weekly review",
            parameters = mapOf("title" to "Weekly Review", "category" to "Work", "duration" to "30m"),
            requiresConfirmation = false
        )
        val taskResult1 = actionExecutor.execute(taskAction1)
        assertTrue(taskResult1.success)

        // Complete the first task
        val createdTask1 = repository.getTaskById(taskResult1.affectedTaskId!!)!!
        repository.updateTask(createdTask1.copy(completed = true))

        // Create identical task in a new week
        val taskAction2 = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.CREATE_TASK,
            title = "Weekly Review",
            description = "Second weekly review",
            parameters = mapOf("title" to "Weekly Review", "category" to "Work", "duration" to "30m"),
            requiresConfirmation = false
        )
        val taskResult2 = actionExecutor.execute(taskAction2)
        assertTrue("Subsequent intentional action with identical parameters must succeed", taskResult2.success)
        assertNotEquals("Tasks must have distinct IDs", taskResult1.affectedTaskId, taskResult2.affectedTaskId)
    }

    @Test
    fun `test missing target fails safely and leaves state unchanged`() = runBlocking {
        val missingTargetAction = AiAction(
            id = UUID.randomUUID().toString(),
            type = AiActionType.DELETE_GOAL,
            title = "Delete Missing Goal",
            description = "Delete nonexistent goal",
            goalId = 999999L,
            requiresConfirmation = true
        )

        val authorizedAction = NexoraSecurity.grantAuthorization(missingTargetAction)
        val result = actionExecutor.execute(authorizedAction)

        assertFalse("Action targeting nonexistent goal MUST fail", result.success)
        assertTrue("Message must indicate target not found", result.message.contains("not found", ignoreCase = true))
    }
}
