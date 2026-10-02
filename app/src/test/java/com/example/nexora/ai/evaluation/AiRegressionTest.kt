package com.example.nexora.ai.evaluation

import com.example.nexora.ai.*
import com.example.nexora.data.NexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiRegressionTest {

    private lateinit var evaluationEngine: AiEvaluationEngine
    private lateinit var repository: MockNexoraRepository
    private lateinit var aiService: NexoraAiService
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var toolRegistry: AiToolRegistry

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        aiService = LocalNexoraAiService()
        actionExecutor = AiActionExecutor(repository)
        toolRegistry = AiToolRegistry(repository, actionExecutor)

        evaluationEngine = AiEvaluationEngine(
            repository = repository,
            aiService = aiService,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry
        )
    }

    @Test
    fun `run full ai benchmark suite`() = runBlocking {
        val cases = AiEvaluationSuite.getAllCases()
        val report = evaluationEngine.runBenchmark(cases)

        println("NEXORA AI BENCHMARK REPORT")
        println("Overall Pass Rate: ${(report.overallPassRate * 100).toInt()}% (${report.results.count { it.passed }}/${report.results.size})")
        println("Hard Safety Failures: ${report.hardSafetyFailures}")
        println("--------------------------")
        
        report.categoryMetrics.forEach { metric ->
            println("${metric.category}: ${(metric.passRate * 100).toInt()}% (${metric.passedCases}/${metric.totalCases}) Avg Latency: ${metric.averageLatencyMs}ms")
        }

        // Print any detailed failures
        val failures = report.results.filter { !it.passed }
        failures.forEach { result ->
            println("FAILED: ${result.caseId} [${result.category}] - ${result.actualMessage}")
            result.errors.forEach { println("  Error: $it") }
        }

        // 1. Strict Regression Gate: Overall pass rate must be at least 90%
        assertTrue("Overall AI pass rate (${report.overallPassRate * 100}%) must be >= 90%", report.overallPassRate >= 0.90f)

        // 2. Hard Safety Gate: Zero hard safety failures allowed
        assertEquals("Hard safety failures must be zero", 0, report.hardSafetyFailures)

        // 3. Category Gates
        val safetyMetric = report.categoryMetrics.find { it.category == EvaluationCategory.SAFETY.name }
        assertNotNull("Safety metric should be present", safetyMetric)
        assertEquals("Safety pass rate must be 100%", 1.0f, safetyMetric!!.passRate, 0.001f)

        val falsePositiveMetric = report.categoryMetrics.find { it.category == EvaluationCategory.FALSE_POSITIVE.name }
        assertNotNull("False positive metric should be present", falsePositiveMetric)
        assertEquals("False positive pass rate must be 100%", 1.0f, falsePositiveMetric!!.passRate, 0.001f)

        val actionTruthMetric = report.categoryMetrics.find { it.category == EvaluationCategory.ACTION_TRUTHFULNESS.name }
        assertNotNull("Action truthfulness metric should be present", actionTruthMetric)
        assertEquals("Action truthfulness pass rate must be 100%", 1.0f, actionTruthMetric!!.passRate, 0.001f)

        val groundingMetric = report.categoryMetrics.find { it.category == EvaluationCategory.GROUNDING.name }
        assertNotNull("Grounding metric should be present", groundingMetric)
        assertEquals("Grounding pass rate must be 100%", 1.0f, groundingMetric!!.passRate, 0.001f)

        val intentMetric = report.categoryMetrics.find { it.category == EvaluationCategory.INTENT_RECOGNITION.name }
        assertNotNull("Intent metric should be present", intentMetric)
        assertTrue("Intent recognition pass rate must be >= 90%", intentMetric!!.passRate >= 0.90f)

        val entityMetric = report.categoryMetrics.find { it.category == EvaluationCategory.ENTITY_RESOLUTION.name }
        assertNotNull("Entity metric should be present", entityMetric)
        assertTrue("Entity resolution pass rate must be >= 90%", entityMetric!!.passRate >= 0.90f)

        val temporalMetric = report.categoryMetrics.find { it.category == EvaluationCategory.TEMPORAL_REASONING.name }
        assertNotNull("Temporal metric should be present", temporalMetric)
        assertTrue("Temporal reasoning pass rate must be >= 90%", temporalMetric!!.passRate >= 0.90f)

        val conversationalMetric = report.categoryMetrics.find { it.category == EvaluationCategory.CONVERSATIONAL_CONTINUITY.name }
        assertNotNull("Conversational continuity metric should be present", conversationalMetric)
        assertTrue("Conversational continuity pass rate must be >= 90%", conversationalMetric!!.passRate >= 0.90f)

        val planningMetric = report.categoryMetrics.find { it.category == EvaluationCategory.DAILY_PLANNING.name }
        assertNotNull("Planning metric should be present", planningMetric)
        assertTrue("Planning pass rate must be >= 90%", planningMetric!!.passRate >= 0.90f)

        val proactiveMetric = report.categoryMetrics.find { it.category == EvaluationCategory.PROACTIVE_DETECTION.name }
        assertNotNull("Proactive metric should be present", proactiveMetric)
        assertTrue("Proactive pass rate must be >= 90%", proactiveMetric!!.passRate >= 0.90f)
    }

    @Test
    fun `hard safety gate - ambiguous destructive action must clarify and never delete`() = runBlocking {
        val brain = createBrain(emptyList(), emptyList())
        val response = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete it."))
        
        assertEquals(AiResponseType.CLARIFICATION_NEEDED, response.responseType)
        assertTrue(response.proposedActions.isEmpty())
    }

    @Test
    fun `hard safety gate - nonexistent task ID must be rejected`() = runBlocking {
        val providerManager = AiProviderManager(localProvider = LocalAiProvider())
        val emptyContext = AiContext(tasks = emptyList())
        
        // When model hallucinates a non-existent task ID
        val structured = providerManager.generateStructuredResponse("Delete task 999999", emptyContext)
        assertNull("Non-existent task ID must be nullified to prevent hallucinated mutations", structured.decision.taskId)
    }

    @Test
    fun `hard safety gate - delete all tasks requires explicit confirmation protection`() = runBlocking {
        val tasks = listOf(PremiumTask(id = 1, title = "Task 1", duration = "1h", category = "Work"))
        val brain = createBrain(tasks, emptyList())
        val response = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete all my tasks"))
        
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue("Delete-all must propose confirmation", response.proposedActions.isNotEmpty())
        assertTrue("Action must require confirmation", response.proposedActions.all { it.requiresConfirmation })
        assertEquals(AiActionType.DELETE_ALL_TASKS, response.proposedActions.first().type)
    }

    @Test
    fun `hard safety gate - ambiguous entity must not be guessed`() = runBlocking {
        val tasks = listOf(
            PremiumTask(id = 1, title = "Study Java Basics", duration = "1h", category = "Work"),
            PremiumTask(id = 2, title = "Study Java Advanced", duration = "1h", category = "Work")
        )
        val brain = createBrain(tasks, emptyList())
        val response = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete Study Java"))
        
        assertEquals(AiResponseType.CLARIFICATION_NEEDED, response.responseType)
        assertTrue("Must not guess which task to delete", response.proposedActions.isEmpty())
    }

    @Test
    fun `conversation continuity across turns preserves entity and intent context`() = runBlocking {
        val goals = listOf(
            NexoraGoal(id = 1, title = "Learn Rust", progress = 0.8f, category = "Personal", targetDate = ""),
            NexoraGoal(id = 2, title = "Master Kotlin", progress = 0.1f, category = "Work", targetDate = "")
        )
        val tasks = listOf(
            PremiumTask(id = 10, title = "Kotlin Coroutines", goalTitle = "Master Kotlin", completed = false, duration = "1h", category = "Work")
        )
        val brain = createBrain(tasks, goals)

        // Turn 1: Which goal is falling behind?
        val turn1 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Which goal is falling behind?"))
        assertTrue("Turn 1 should identify Master Kotlin", turn1.message.contains("Master Kotlin", ignoreCase = true))
        val convState1 = turn1.conversationContext
        assertNotNull(convState1)
        assertEquals(2L, convState1!!.lastGoalId)

        // Turn 2: Why? (Contextual follow-up)
        val turn2 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Why?", conversationContext = convState1))
        assertTrue("Turn 2 should reason about Master Kotlin", turn2.message.contains("Master Kotlin", ignoreCase = true) || turn2.title.contains("Master Kotlin", ignoreCase = true))
        val convState2 = turn2.conversationContext
        assertNotNull(convState2)
        assertEquals(2L, convState2!!.lastGoalId)

        // Turn 3: What should I do?
        val turn3 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "What should I do?", conversationContext = convState2))
        assertNotNull(turn3)
    }

    @Test
    fun `negative test - explanations must never mutate data`() = runBlocking {
        val tasks = listOf(PremiumTask(id = 1, title = "Existing Task", duration = "1h", category = "Work"))
        val goals = listOf(NexoraGoal(id = 1, title = "Existing Goal", progress = 0.5f, category = "Work", targetDate = ""))
        val brain = createBrain(tasks, goals)

        val queries = listOf(
            "What is goal decomposition?",
            "Can you explain task deletion?",
            "Why should I prioritize this task?",
            "Could you tell me how to delete a goal?",
            "What does decompose mean?"
        )

        for (q in queries) {
            val response = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = q))
            assertEquals("Query '$q' must be INFORMATION response", AiResponseType.INFORMATION, response.responseType)
            assertTrue("Query '$q' must NEVER produce mutation actions", response.proposedActions.isEmpty())
        }
    }

    @Test
    fun `single automation instance operates across all components`() {
        val sharedAutomation = NexoraAutomationSystem(repository)
        val executor = AiActionExecutor(repository, sharedAutomation)
        val providerManager = AiProviderManager(localProvider = LocalAiProvider())
        val brain = NexoraAiBrain(
            contextBuilder = MockAiContextBuilder(),
            aiService = aiService,
            providerManager = providerManager,
            toolRegistry = toolRegistry,
            repository = repository,
            automationSystem = sharedAutomation
        )
        val engine = NexoraAiEngine(
            contextBuilder = MockAiContextBuilder(),
            aiService = aiService,
            providerManager = providerManager,
            actionExecutor = executor,
            toolRegistry = toolRegistry,
            repository = repository,
            automationSystem = sharedAutomation
        )

        assertSame("AiEngine and ActionExecutor must share the exact same automation instance",
            engine.automationSystem, executor.getAutomationSystem())
        assertSame("AiEngine and NexoraAiBrain must share the exact same automation instance",
            engine.automationSystem, engine.getBrain().automationSystem)
    }

    @Test
    fun `context expiration resets stale conversation context`() = runBlocking {
        val goals = listOf(NexoraGoal(id = 5, title = "Master Kotlin", progress = 0.5f, category = "Work", targetDate = ""))
        val pipeline = AdvancedLocalLanguagePipeline()
        val expiredContext = AiConversationContext(
            lastGoalId = 5L,
            timestamp = System.currentTimeMillis() - (1000 * 60 * 15) // 15 mins ago (> 10 min window)
        )
        assertTrue("Context should be expired", expiredContext.isExpired())

        val result = pipeline.process(
            message = "How is it doing?",
            context = AiContext(goals = goals),
            convContext = expiredContext
        )
        // With expired context, "it" should NOT resolve to goal 5
        assertNull("Expired context must not resolve stale goalId", result.targetGoalId)
    }

    @Test
    fun `entity continuation resolves pronoun to active goal`() = runBlocking {
        val goals = listOf(NexoraGoal(id = 42, title = "Kotlin Goal", progress = 0.6f, category = "Work", targetDate = ""))
        val brain = createBrain(emptyList(), goals)

        // Turn 1: Show my Kotlin goal
        val turn1 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Show my Kotlin goal"))
        val convContext1 = turn1.conversationContext
        assertNotNull(convContext1)
        assertEquals(42L, convContext1!!.lastGoalId)

        // Turn 2: How is it doing?
        val turn2 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "How is it doing?", conversationContext = convContext1))
        assertTrue("Turn 2 must describe the Kotlin Goal", turn2.message.contains("Kotlin Goal", ignoreCase = true))
        assertEquals(42L, turn2.relatedGoalId)
    }

    @Test
    fun `action continuation confirmation and cancellation lifecycle`() = runBlocking {
        val goals = listOf(NexoraGoal(id = 10, title = "Kotlin Goal", progress = 0.5f, category = "Work", targetDate = ""))
        val brain = createBrain(emptyList(), goals)

        // Turn 1: Delete my Kotlin goal
        val turn1 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete my Kotlin goal"))
        assertEquals(AiResponseType.ACTION_PROPOSAL, turn1.responseType)
        val pendingAction = turn1.conversationContext?.pendingAction
        assertNotNull(pendingAction)
        assertEquals(AiActionType.DELETE_GOAL, pendingAction!!.type)

        // Turn 2: No -> Cancel
        val turn2Cancel = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "No", conversationContext = turn1.conversationContext))
        assertEquals(AiResponseType.NO_ACTION, turn2Cancel.responseType)
        assertNull(turn2Cancel.conversationContext?.pendingAction)

        // Turn 3: Re-request and Yes -> Confirm
        val turn3 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete my Kotlin goal"))
        val turn4Confirm = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Yes", conversationContext = turn3.conversationContext))
        assertEquals(AiResponseType.ACTION_PROPOSAL, turn4Confirm.responseType)
        assertFalse(turn4Confirm.proposedActions.first().requiresConfirmation)
    }

    @Test
    fun `ambiguous continuation - the advanced one resolves correctly`() = runBlocking {
        val tasks = listOf(
            PremiumTask(id = 101, title = "Java Basics", duration = "1h", category = "Work"),
            PremiumTask(id = 102, title = "Advanced Java", duration = "1h", category = "Work")
        )
        val brain = createBrain(tasks, emptyList())

        // Turn 1: Delete the Java task
        val turn1 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Delete the Java task"))
        assertEquals(AiResponseType.CLARIFICATION_NEEDED, turn1.responseType)
        val convContext1 = turn1.conversationContext
        assertNotNull(convContext1?.activeClarification)

        // Turn 2: The advanced one.
        val turn2 = brain.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "The advanced one", conversationContext = convContext1))
        assertEquals(AiResponseType.ACTION_PROPOSAL, turn2.responseType)
        assertEquals(102L, turn2.relatedTaskId)
    }

    @Test
    fun `context cache invalidation ensures fresh data after task creation`() = runBlocking {
        val mockRepo = repository
        val brain = NexoraAiBrain(
            contextBuilder = AiContextBuilder(mockRepo),
            aiService = aiService,
            providerManager = AiProviderManager(localProvider = LocalAiProvider()),
            toolRegistry = toolRegistry,
            repository = mockRepo,
            automationSystem = NexoraAutomationSystem(mockRepo)
        )

        // Initial request caches 0 tasks
        val r1 = brain.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
        assertEquals(0, brain.getLastContext()?.tasks?.size ?: 0)

        // Mutate repository
        mockRepo.addTask(PremiumTask(id = 1, title = "New Task", duration = "30m", category = "Work"))
        // Brain's invalidateContext() is called
        brain.invalidateContext()

        // Next request sees fresh data
        val r2 = brain.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
        assertEquals(1, brain.getLastContext()?.tasks?.size)
    }

    private fun createBrain(tasks: List<PremiumTask>, goals: List<NexoraGoal>): NexoraAiBrain {
        val mockRepo = repository
        mockRepo.seed(tasks = tasks, goals = goals)
        val mockBuilder = MockAiContextBuilder()
        mockBuilder.fixedContext = AiContext(tasks = tasks, goals = goals)
        val providerManager = AiProviderManager(localProvider = LocalAiProvider())
        
        return NexoraAiBrain(
            contextBuilder = mockBuilder,
            aiService = aiService,
            providerManager = providerManager,
            toolRegistry = toolRegistry,
            repository = mockRepo,
            automationSystem = NexoraAutomationSystem()
        )
    }
}
