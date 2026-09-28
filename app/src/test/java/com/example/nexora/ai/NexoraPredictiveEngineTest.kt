package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraPredictiveEngineTest {

    private lateinit var predictiveEngine: NexoraPredictiveEngine
    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
        predictiveEngine = NexoraPredictiveEngine()
        repository = MockNexoraRepository()

        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
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
    fun `test task delay risk prediction for high duration and urgent task`() = runBlocking {
        val task = PremiumTask(id = 1, title = "Build Backend Auth", category = "Work", duration = "180m", priority = TaskPriority.URGENT)
        val context = AiContext(
            tasks = listOf(task),
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 3, sampleCount = 5)
        )

        val predictions = predictiveEngine.predictTaskDelayRisks(context)
        val taskPrediction = predictions.find { it.targetId == 1L }

        assertNotNull(taskPrediction)
        assertEquals(PredictionType.TASK_DELAY_RISK, taskPrediction?.type)
        assertTrue((taskPrediction?.probability ?: 0f) >= 0.4f)
        assertEquals(AiPriority.CRITICAL, taskPrediction?.riskLevel)
        assertTrue(taskPrediction?.contributingFactors?.any { it.factor == "Task Size" } == true)
    }

    @Test
    fun `test goal completion timing and risk prediction`() = runBlocking {
        val goal = NexoraGoal(id = 1, title = "Master Jetpack Compose", category = "Study", targetDate = "2026-12-31", progress = 0.2f)
        val tasks = listOf(
            PremiumTask(id = 1, title = "Layouts", goalTitle = goal.title, category = "Study", duration = "30m"),
            PremiumTask(id = 2, title = "Animations", goalTitle = goal.title, category = "Study", duration = "30m"),
            PremiumTask(id = 3, title = "Canvas", goalTitle = goal.title, category = "Study", duration = "30m")
        )

        val context = AiContext(
            goals = listOf(goal),
            tasks = tasks,
            adaptiveProfile = AdaptiveProfile(averageTasksCompleted = 2.0f, sampleCount = 5)
        )

        val predictions = predictiveEngine.predictGoalRisksAndTimings(context)
        val goalPrediction = predictions.find { it.targetId == 1L }

        assertNotNull(goalPrediction)
        assertEquals(PredictionType.GOAL_RISK, goalPrediction?.type)
        assertNotNull(goalPrediction?.estimatedDaysToCompletion)
        assertTrue(goalPrediction?.prediction?.contains("ON_TRACK") == true || goalPrediction?.prediction?.contains("Estimated") == true)
    }

    @Test
    fun `test workload overload risk prediction`() = runBlocking {
        val tasks = (1..10).map {
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "60m")
        }

        val context = AiContext(
            tasks = tasks,
            tasksPlannedToday = 10,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 3, sampleCount = 5)
        )

        val prediction = predictiveEngine.predictWorkloadOverload(context)

        assertNotNull(prediction)
        assertEquals(PredictionType.WORKLOAD_OVERLOAD_RISK, prediction?.type)
        assertTrue(prediction?.probability ?: 0f >= 0.7f)
        assertEquals(AiPriority.CRITICAL, prediction?.riskLevel)
        assertTrue(prediction?.prediction?.contains("OVERLOADED") == true || prediction?.prediction?.contains("HEAVY") == true)
    }

    @Test
    fun `test productivity trend prediction with history`() = runBlocking {
        val history = listOf(
            DailyProgressEntity(date = "2026-03-01", tasksPlanned = 5, tasksCompleted = 5),
            DailyProgressEntity(date = "2026-03-02", tasksPlanned = 5, tasksCompleted = 4),
            DailyProgressEntity(date = "2026-03-03", tasksPlanned = 5, tasksCompleted = 5),
            DailyProgressEntity(date = "2026-03-04", tasksPlanned = 5, tasksCompleted = 1),
            DailyProgressEntity(date = "2026-03-05", tasksPlanned = 5, tasksCompleted = 1)
        )

        val builder = AiPersonalContextBuilder()
        val personalContext = builder.build(
            tasks = emptyList(),
            goals = emptyList(),
            todayProgress = null,
            history = history,
            adaptiveProfile = AdaptiveProfile(sampleCount = 5),
            memory = AiMemory()
        )

        val trendPrediction = personalContext.predictions.find { it.type == PredictionType.PRODUCTIVITY_TREND_PREDICTION }

        assertNotNull(trendPrediction)
        assertEquals(PredictionType.PRODUCTIVITY_TREND_PREDICTION, trendPrediction?.type)
        assertTrue(trendPrediction?.prediction?.contains("DECLINING") == true || trendPrediction?.prediction?.contains("STABLE") == true)
    }

    @Test
    fun `test insufficient data handling for new user without inventing probabilities`() = runBlocking {
        val context = AiContext(
            tasks = listOf(PremiumTask(id = 1, title = "First Task", category = "Personal", duration = "30m")),
            adaptiveProfile = AdaptiveProfile(sampleCount = 0)
        )

        val predictions = predictiveEngine.predictTaskDelayRisks(context)
        val taskPred = predictions.find { it.targetId == 1L }

        if (taskPred != null) {
            assertEquals(AiConfidence.LOW, taskPred.confidence)
            assertTrue(taskPred.prediction.contains("INSUFFICIENT_DATA") || taskPred.prediction.contains("Low Delay Risk"))
        }
    }

    @Test
    fun `test predictive query routing in chat - Will I finish my goal`() = runBlocking {
        repository.addGoal(NexoraGoal(id = 1, title = "Master Android", category = "Study", targetDate = "2026-12-31", progress = 0.5f))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Will I finish my Master Android goal?"))

        assertEquals(AiDecisionType.PREDICT_GOAL, response.decision?.type)
        assertTrue(response.message.contains("Master Android", ignoreCase = true) || response.message.contains("predictive", ignoreCase = true))
    }

    @Test
    fun `test predictive query routing in chat - Which task am I most likely to postpone`() = runBlocking {
        repository.addTask(PremiumTask(id = 1, title = "Large Complex Refactoring", category = "Work", duration = "180m", priority = TaskPriority.URGENT))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Which task am I most likely to postpone?"))

        assertEquals(AiDecisionType.PREDICT_TASK_RISK, response.decision?.type)
        assertTrue(response.message.contains("Large Complex Refactoring", ignoreCase = true) || response.message.contains("risk", ignoreCase = true) || response.message.contains("normal", ignoreCase = true))
    }

    @Test
    fun `test predictive query routing in chat - Am I taking on too much today`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Am I taking on too much today?"))

        assertEquals(AiDecisionType.PREDICT_WORKLOAD, response.decision?.type)
        assertTrue(response.message.contains("Workload", ignoreCase = true) || response.message.contains("clear", ignoreCase = true))
    }

    @Test
    fun `test proactive engine converts severe predictive risk into proactive signals`() = runBlocking {
        val proactiveEngine = NexoraProactiveEngine()
        
        // Context with 15 planned tasks vs baseline 3 -> severe overload
        val tasks = (1..15).map {
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "60m")
        }
        val context = AiContext(
            tasks = tasks,
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 3, sampleCount = 5)
        )

        val signals = proactiveEngine.detectSignals(context)
        assertTrue(signals.any { it.type == ProactiveSignalType.WORKLOAD_RISK || it.type == ProactiveSignalType.OVERLOAD })
    }

    @Test
    fun `test planner incorporates predictive risk score into task prioritization`() = runBlocking {
        val planner = AiPlanner()
        val task1 = PremiumTask(id = 1, title = "Normal Task", priority = TaskPriority.MEDIUM, category = "Work", duration = "30m")
        val task2 = PremiumTask(id = 2, title = "High Risk Long Task", priority = TaskPriority.HIGH, category = "Work", duration = "180m")

        val context = AiContext(
            tasks = listOf(task1, task2),
            personalContext = AiPersonalContext(
                predictions = listOf(
                    AiPrediction(
                        type = PredictionType.TASK_DELAY_RISK,
                        targetId = 2L,
                        targetTitle = task2.title,
                        prediction = "High Delay Risk",
                        probability = 0.8f,
                        riskLevel = AiPriority.CRITICAL
                    )
                )
            )
        )

        val recommendations = planner.analyze(context)
        val nextRec = recommendations.find { it.type == AiRecommendationType.NEXT_TASK }

        assertNotNull(nextRec)
        assertEquals(2L, nextRec?.relatedTaskId)
    }

    @Test
    fun `test predictive engine performance with 500 tasks`() = runBlocking {
        val largeTasks = (1..500).map {
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "30m", completed = it % 2 == 0)
        }

        val context = AiContext(tasks = largeTasks, tasksPlannedToday = 250)

        val start = System.currentTimeMillis()
        val predictions = predictiveEngine.generatePredictions(context)
        val duration = System.currentTimeMillis() - start

        assertNotNull(predictions)
        assertTrue("Predictive calculations on 500 tasks must take under 100ms", duration < 100)
    }
}
