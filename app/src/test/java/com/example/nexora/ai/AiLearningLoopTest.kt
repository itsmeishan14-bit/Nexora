package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AiLearningLoopTest {

    private lateinit var repository: FakeNexoraRepository
    private lateinit var learningLoop: AiLearningLoop

    @Before
    fun setUp() {
        repository = FakeNexoraRepository()
        learningLoop = AiLearningLoop(repository)
    }

    @Test
    fun testSuccessfulTaskRecommendation() = runBlocking {
        val taskId = 1L
        val recId = "rec-1"
        val recommendation = AiRecommendationHistory(
            id = recId,
            type = AiRecommendationType.NEXT_TASK,
            title = "Work on Java",
            message = "Test message",
            relatedTaskId = taskId
        )

        val task = PremiumTask(
            id = taskId, 
            title = "Java", 
            completed = true, 
            priority = TaskPriority.MEDIUM,
            category = "Work",
            duration = "30 min"
        )
        
        repository.recommendations = listOf(recommendation)
        repository.tasks = listOf(task)

        learningLoop.evaluateOutcomes()

        val savedOutcome = repository.savedOutcomes.firstOrNull()
        assertNotNull(savedOutcome)
        assertEquals(recId, savedOutcome?.recommendationId)
        assertEquals(AiOutcomeType.SUCCESS, savedOutcome?.type)
    }

    @Test
    fun testPlanEvaluationTooLarge() = runBlocking {
        val date = "2023-10-27"
        val progress = com.example.nexora.data.DailyProgressEntity(
            date = date,
            tasksPlanned = 10,
            tasksCompleted = 2
        )

        repository.history = listOf(progress)

        learningLoop.evaluateOutcomes()

        val savedEvaluation = repository.savedEvaluations.firstOrNull()
        assertNotNull(savedEvaluation)
        assertEquals(AiOutcomeType.PLAN_TOO_LARGE, savedEvaluation?.outcome)
        assertTrue(savedEvaluation?.title?.contains(date) == true)
    }

    private class FakeNexoraRepository : NexoraRepository(null) {
        var recommendations = emptyList<AiRecommendationHistory>()
        var tasks = emptyList<PremiumTask>()
        var history = emptyList<com.example.nexora.data.DailyProgressEntity>()
        val savedOutcomes = mutableListOf<AiOutcome>()
        val savedEvaluations = mutableListOf<AiEvaluation>()

        override suspend fun getRecentRecommendations(limit: Int) = recommendations
        override suspend fun observeTasksOnce() = tasks
        override suspend fun getRecentOutcomes(limit: Int) = emptyList<AiOutcome>()
        override suspend fun saveOutcome(outcome: AiOutcome) {
            savedOutcomes.add(outcome)
        }
        override suspend fun getHistoricalProgress(limit: Int) = history
        override suspend fun getRecentEvaluations(limit: Int) = emptyList<AiEvaluation>()
        override suspend fun saveEvaluation(evaluation: AiEvaluation) {
            savedEvaluations.add(evaluation)
        }
    }
}
