package com.example.nexora.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room database transaction tests executed on Android runtime.
 * Tests atomic all-or-nothing goal deletion, task unlinking, failure rollback,
 * and SQLite NOCASE collation semantics.
 */
@RunWith(AndroidJUnit4::class)
class RoomTransactionAndroidTest {

    private lateinit var database: NexoraDatabase
    private lateinit var goalDao: GoalDao
    private lateinit var taskDao: TaskDao
    private lateinit var repository: NexoraRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<NexoraDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        goalDao = database.goalDao()
        taskDao = database.taskDao()
        repository = NexoraRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDeleteGoalWithZeroLinkedTasks() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Solo Goal", category = "Personal", targetDate = "2026-12-31")
        )

        val result = repository.deleteGoalAtomic(goalId)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("Solo Goal", success.goalTitle)
        assertEquals(0, success.unlinkedTaskCount)

        assertNull("Goal should be removed from database", goalDao.getById(goalId))
    }

    @Test
    fun testDeleteGoalWithOneLinkedTask() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "App Launch", category = "Work", targetDate = "2026-11-01")
        )
        val taskId = taskDao.insert(
            TaskEntity(title = "Write tests", category = "Work", duration = "30m", goalTitle = "App Launch")
        )

        val result = repository.deleteGoalAtomic(goalId)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("App Launch", success.goalTitle)
        assertEquals(1, success.unlinkedTaskCount)

        assertNull("Goal should be deleted", goalDao.getById(goalId))
        val updatedTask = taskDao.getById(taskId)
        assertNotNull("Task should still exist", updatedTask)
        assertNull("Task link should be cleared", updatedTask?.goalTitle)
    }

    @Test
    fun testDeleteGoalWithMultipleLinkedTasks() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Project Nexora", category = "Dev", targetDate = "2026-12-01")
        )
        val task1Id = taskDao.insert(
            TaskEntity(title = "Task 1", category = "Dev", duration = "1h", goalTitle = "Project Nexora")
        )
        val task2Id = taskDao.insert(
            TaskEntity(title = "Task 2", category = "Dev", duration = "2h", goalTitle = "Project Nexora")
        )
        val otherTaskId = taskDao.insert(
            TaskEntity(title = "Unrelated", category = "General", duration = "15m", goalTitle = "Other Goal")
        )

        val result = repository.deleteGoalAtomic(goalId)

        assertTrue("Expected Success, got $result", result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals("Project Nexora", success.goalTitle)
        assertEquals(2, success.unlinkedTaskCount)

        assertNull("Goal should be deleted", goalDao.getById(goalId))
        assertNull("Task 1 link cleared", taskDao.getById(task1Id)?.goalTitle)
        assertNull("Task 2 link cleared", taskDao.getById(task2Id)?.goalTitle)
        assertEquals("Other Goal", taskDao.getById(otherTaskId)?.goalTitle)
    }

    @Test
    fun testSimulateFailureAfterUnlinkingTriggersRollback() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Critical Project", category = "Work", targetDate = "2026-10-30")
        )
        val taskId = taskDao.insert(
            TaskEntity(title = "Vital Task", category = "Work", duration = "45m", goalTitle = "Critical Project")
        )

        val result = repository.deleteGoalAtomicForTesting(
            goalId = goalId,
            onAfterUnlink = { throw IllegalStateException("Simulated disk error after unlinking") }
        )

        assertTrue("Expected Failure, got $result", result is GoalDeletionResult.Failure)
        val failure = result as GoalDeletionResult.Failure
        assertTrue(failure.message.contains("Simulated disk error after unlinking"))

        // Confirm rollback preserves the goal and task link
        val goal = goalDao.getById(goalId)
        assertNotNull("Goal must still exist after rollback", goal)
        assertEquals("Critical Project", goal?.title)

        val task = taskDao.getById(taskId)
        assertNotNull("Task must still exist after rollback", task)
        assertEquals("Critical Project", task?.goalTitle)
    }

    @Test
    fun testSimulateFailureAfterGoalDeleteTriggersRollback() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Financial Audit", category = "Finance", targetDate = "2026-11-15")
        )
        val taskId = taskDao.insert(
            TaskEntity(title = "Audit Receipts", category = "Finance", duration = "1h", goalTitle = "Financial Audit")
        )

        val result = repository.deleteGoalAtomicForTesting(
            goalId = goalId,
            onAfterDelete = { throw IllegalStateException("Simulated post-delete verification failure") }
        )

        assertTrue("Expected Failure, got $result", result is GoalDeletionResult.Failure)

        // Confirm rollback preserves the goal and task link
        val goal = goalDao.getById(goalId)
        assertNotNull("Goal must still exist after rollback", goal)
        assertEquals("Financial Audit", goal?.title)

        val task = taskDao.getById(taskId)
        assertNotNull("Task must still exist after rollback", task)
        assertEquals("Financial Audit", task?.goalTitle)
    }

    @Test
    fun testConfirmRollbackPreservesGoalAndEveryTaskLink() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Multi-Task Rollback Test", category = "Health", targetDate = "2026-12-31")
        )
        val taskIds = (1..5).map { i ->
            taskDao.insert(
                TaskEntity(
                    title = "Subtask $i",
                    category = "Health",
                    duration = "20m",
                    goalTitle = "Multi-Task Rollback Test"
                )
            )
        }

        val result = repository.deleteGoalAtomicForTesting(
            goalId = goalId,
            onAfterUnlink = { throw RuntimeException("Network/IO failure between unlink and delete") }
        )

        assertTrue(result is GoalDeletionResult.Failure)

        // All 5 task links and the goal must be completely preserved
        assertNotNull("Goal must exist", goalDao.getById(goalId))
        for (id in taskIds) {
            val task = taskDao.getById(id)
            assertNotNull(task)
            assertEquals("Multi-Task Rollback Test", task?.goalTitle)
        }
    }

    @Test
    fun testDeleteNonexistentGoalReturnsGoalNotFound() = runBlocking {
        val result = repository.deleteGoalAtomic(99999L)

        assertEquals(GoalDeletionResult.GoalNotFound, result)
    }

    @Test
    fun testCaseInsensitiveMatchingAccordingToSQLiteNocase() = runBlocking {
        val goalId = goalDao.insert(
            GoalEntity(title = "Health & Fitness", category = "Health", targetDate = "2026-12-31")
        )
        val taskLower = taskDao.insert(
            TaskEntity(title = "T1", category = "Health", duration = "10m", goalTitle = "health & fitness")
        )
        val taskUpper = taskDao.insert(
            TaskEntity(title = "T2", category = "Health", duration = "10m", goalTitle = "HEALTH & FITNESS")
        )
        val taskExact = taskDao.insert(
            TaskEntity(title = "T3", category = "Health", duration = "10m", goalTitle = "Health & Fitness")
        )
        val taskOther = taskDao.insert(
            TaskEntity(title = "T4", category = "Work", duration = "10m", goalTitle = "Different Goal")
        )

        val result = repository.deleteGoalAtomic(goalId)

        assertTrue(result is GoalDeletionResult.Success)
        val success = result as GoalDeletionResult.Success
        assertEquals(3, success.unlinkedTaskCount)

        assertNull("T1 unlinked", taskDao.getById(taskLower)?.goalTitle)
        assertNull("T2 unlinked", taskDao.getById(taskUpper)?.goalTitle)
        assertNull("T3 unlinked", taskDao.getById(taskExact)?.goalTitle)
        assertEquals("Different Goal", taskDao.getById(taskOther)?.goalTitle)
    }
}
