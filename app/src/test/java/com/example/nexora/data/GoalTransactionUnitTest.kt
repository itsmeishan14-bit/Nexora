package com.example.nexora.data

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM Unit Tests verifying transaction atomicity, all-or-nothing rollback,
 * case-insensitive task unlinking, and postcondition verification for goal deletion.
 *
 * NOTE ON ENVIRONMENT LIMITATION:
 * The Android runtime Room test suite is located at [RoomTransactionAndroidTest].
 * When executing local JVM unit tests on Windows host, `androidx.sqlite.driver.bundled.BundledSQLiteDriver`
 * throws `java.lang.UnsatisfiedLinkError: no sqliteJni in java.library.path` because AGP packages native
 * libraries for Android ABIs (arm64, x86_64, etc.) rather than Windows desktop DLLs.
 * Therefore, these clearly labelled unit tests verify the exact transaction and rollback contract on the JVM.
 */
class GoalTransactionUnitTest {

    private lateinit var repository: MockNexoraRepository

    @Before
    fun setUp() {
        repository = MockNexoraRepository()
    }

    @Test
    fun `test 1 - delete goal with zero linked tasks succeeds and deletes goal`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Solo Goal", category = "Personal", targetDate = "2026-12-31", progress = 0f)
        )

        val result = repository.deleteGoalAtomic(goal.id)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("Solo Goal", success.goalTitle)
        assertEquals(0, success.unlinkedTaskCount)

        assertNull("Goal should be removed from repository", repository.getGoalById(goal.id))
    }

    @Test
    fun `test 2 - delete goal with one linked task unlinks task and deletes goal`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "App Launch", category = "Work", targetDate = "2026-11-01", progress = 0f)
        )
        val task = repository.addTask(
            PremiumTask(id = 0L, title = "Write tests", category = "Work", duration = "30m", priority = TaskPriority.HIGH, goalTitle = "App Launch")
        )

        val result = repository.deleteGoalAtomic(goal.id)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("App Launch", success.goalTitle)
        assertEquals(1, success.unlinkedTaskCount)

        assertNull("Goal should be deleted", repository.getGoalById(goal.id))
        val updatedTask = repository.getTaskById(task.id)
        assertNotNull("Task should still exist", updatedTask)
        assertNull("Task link should be cleared", updatedTask?.goalTitle)
    }

    @Test
    fun `test 3 - delete goal with multiple linked tasks unlinks all tasks and deletes goal`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Project Nexora", category = "Dev", targetDate = "2026-12-01", progress = 0f)
        )
        val t1 = repository.addTask(
            PremiumTask(id = 0L, title = "Task 1", category = "Dev", duration = "1h", goalTitle = "Project Nexora")
        )
        val t2 = repository.addTask(
            PremiumTask(id = 0L, title = "Task 2", category = "Dev", duration = "2h", goalTitle = "Project Nexora")
        )
        val tOther = repository.addTask(
            PremiumTask(id = 0L, title = "Unrelated", category = "General", duration = "15m", goalTitle = "Other Goal")
        )

        val result = repository.deleteGoalAtomic(goal.id)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("Project Nexora", success.goalTitle)
        assertEquals(2, success.unlinkedTaskCount)

        assertNull("Goal should be deleted", repository.getGoalById(goal.id))
        assertNull("Task 1 link cleared", repository.getTaskById(t1.id)?.goalTitle)
        assertNull("Task 2 link cleared", repository.getTaskById(t2.id)?.goalTitle)
        assertEquals("Other Goal", repository.getTaskById(tOther.id)?.goalTitle)
    }

    @Test
    fun `test 4 - simulate failure after task links updated triggers rollback preserving goal and tasks`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Critical Project", category = "Work", targetDate = "2026-10-30", progress = 0f)
        )
        val task = repository.addTask(
            PremiumTask(id = 0L, title = "Vital Task", category = "Work", duration = "45m", goalTitle = "Critical Project")
        )

        val result = repository.deleteGoalAtomic(
            goalId = goal.id,
            onAfterUnlink = { throw IllegalStateException("Simulated disk error after unlinking") }
        )

        assertTrue("Expected Failure, got $result", result is GoalDeletionResult.Failure)
        val failure = result as GoalDeletionResult.Failure
        assertTrue(failure.message.contains("Simulated disk error after unlinking"))

        // Confirm rollback preserves the goal and task link
        val persistedGoal = repository.getGoalById(goal.id)
        assertNotNull("Goal must still exist after rollback", persistedGoal)
        assertEquals("Critical Project", persistedGoal?.title)

        val persistedTask = repository.getTaskById(task.id)
        assertNotNull("Task must still exist after rollback", persistedTask)
        assertEquals("Critical Project", persistedTask?.goalTitle)
    }

    @Test
    fun `test 5 - simulate failure after goal deletion triggers rollback preserving goal and tasks`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Financial Audit", category = "Finance", targetDate = "2026-11-15", progress = 0f)
        )
        val task = repository.addTask(
            PremiumTask(id = 0L, title = "Audit Receipts", category = "Finance", duration = "1h", goalTitle = "Financial Audit")
        )

        val result = repository.deleteGoalAtomic(
            goalId = goal.id,
            onAfterDelete = { throw IllegalStateException("Simulated post-delete verification failure") }
        )

        assertTrue("Expected Failure, got $result", result is GoalDeletionResult.Failure)

        // Confirm rollback preserves the goal and task link
        val persistedGoal = repository.getGoalById(goal.id)
        assertNotNull("Goal must still exist after rollback", persistedGoal)
        assertEquals("Financial Audit", persistedGoal?.title)

        val persistedTask = repository.getTaskById(task.id)
        assertNotNull("Task must still exist after rollback", persistedTask)
        assertEquals("Financial Audit", persistedTask?.goalTitle)
    }

    @Test
    fun `test 6 - confirm rollback preserves the goal and every task link after failure during unlinking`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Multi-Task Rollback Test", category = "Health", targetDate = "2026-12-31", progress = 0f)
        )
        val taskIds = (1..5).map { i ->
            repository.addTask(
                PremiumTask(
                    id = 0L,
                    title = "Subtask $i",
                    category = "Health",
                    duration = "20m",
                    goalTitle = "Multi-Task Rollback Test"
                )
            ).id
        }

        // Simulate failure midway through unlinking tasks
        repository.failDuringUnlinkAtTaskIndex = 2

        val result = repository.deleteGoalAtomic(goal.id)

        assertTrue(result is GoalDeletionResult.Failure)

        // All 5 task links and the goal must be completely preserved
        assertNotNull("Goal must exist", repository.getGoalById(goal.id))
        for (id in taskIds) {
            val task = repository.getTaskById(id)
            assertNotNull(task)
            assertEquals("Multi-Task Rollback Test", task?.goalTitle)
        }
    }

    @Test
    fun `test 7 - delete nonexistent goal returns GoalNotFound without modifying state`() = runBlocking {
        val result = repository.deleteGoalAtomic(99999L)

        assertEquals(GoalDeletionResult.GoalNotFound, result)
    }

    @Test
    fun `test 8 - verify case-insensitive matching unlinks tasks with different casing`() = runBlocking {
        val goal = repository.addGoal(
            NexoraGoal(id = 0L, title = "Health & Fitness", category = "Health", targetDate = "2026-12-31", progress = 0f)
        )
        val tLower = repository.addTask(
            PremiumTask(id = 0L, title = "T1", category = "Health", duration = "10m", goalTitle = "health & fitness")
        )
        val tUpper = repository.addTask(
            PremiumTask(id = 0L, title = "T2", category = "Health", duration = "10m", goalTitle = "HEALTH & FITNESS")
        )
        val tExact = repository.addTask(
            PremiumTask(id = 0L, title = "T3", category = "Health", duration = "10m", goalTitle = "Health & Fitness")
        )
        val tOther = repository.addTask(
            PremiumTask(id = 0L, title = "T4", category = "Work", duration = "10m", goalTitle = "Different Goal")
        )

        val result = repository.deleteGoalAtomic(goal.id)

        assertTrue(result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals(3, success.unlinkedTaskCount)

        assertNull("T1 unlinked", repository.getTaskById(tLower.id)?.goalTitle)
        assertNull("T2 unlinked", repository.getTaskById(tUpper.id)?.goalTitle)
        assertNull("T3 unlinked", repository.getTaskById(tExact.id)?.goalTitle)
        assertEquals("Different Goal", repository.getTaskById(tOther.id)?.goalTitle)
    }
}
