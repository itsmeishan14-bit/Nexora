package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiBrainTest {

    private lateinit var fakeService: FakeNexoraAiService

    @Before
    fun setup() {
        fakeService = FakeNexoraAiService()
    }

    @Test
    fun `test brain handles next task request with reasoning`() = runBlocking {
        // Use "Large" task size to match profile
        val task = PremiumTask(id = 1, title = "Urgent Task", priority = TaskPriority.URGENT, category = "Work", duration = "2 hours")
        val context = AiContext(
            tasks = listOf(task),
            adaptiveProfile = AdaptiveProfile(
                confidence = AdaptiveConfidence.HIGH, 
                preferredTaskSize = "Large",
                preferredDailyWorkload = 5,
                averageTasksCompleted = 4f
            )
        )
        
        val testBrain = createTestBrain(context)
        
        val request = AiRequest(AiRequestType.NEXT_TASK)
        val response = testBrain.processRequest(request)
        
        assertEquals(AiResponseType.RECOMMENDATION, response.responseType)
        assertEquals(1L, response.relatedTaskId)
        
        val factors = response.evidence.map { it.factor }
        assertTrue("Expected Priority factor in $factors", response.evidence.any { it.factor == "Priority" })
        assertTrue("Expected Focus Fit factor in $factors", response.evidence.any { it.factor == "Focus Fit" })
    }

    @Test
    fun `test brain handles chat intent for task creation via proposal`() = runBlocking {
        val context = AiContext()
        val testBrain = createTestBrain(context)
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Create a task to buy milk")
        val response = testBrain.processRequest(request)
        
        // Creating a task proposes an action requiring user confirmation
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(response.proposedActions.any { it.type == AiActionType.CREATE_TASK })
    }

    @Test
    fun `test brain proactive analysis detects neglected goal`() = runBlocking {
        val goal = NexoraGoal(id = 1, title = "Big Goal", category = "Work", targetDate = "", progress = 0.1f)
        val context = AiContext(
            goals = listOf(goal), 
            tasks = emptyList(),
            personalContext = AiPersonalContext(
                goalHealth = listOf(
                    GoalHealthAssessment(1L, "Big Goal", GoalHealthState.AT_RISK, 0.1f, ActivityLevel.NONE, 5, "Stagnating")
                )
            )
        )
        
        val testBrain = createTestBrain(context)
        
        val request = AiRequest(AiRequestType.PROACTIVE_ANALYSIS)
        val response = testBrain.processRequest(request)
        
        assertEquals(AiResponseType.WARNING, response.responseType)
        assertEquals(1L, response.relatedGoalId)
        assertTrue(response.title.contains("Stagnating", ignoreCase = true))
    }

    @Test
    fun `test brain handles no action state`() = runBlocking {
        val context = AiContext()
        val testBrain = createTestBrain(context)
        
        val request = AiRequest(AiRequestType.NEXT_TASK)
        val response = testBrain.processRequest(request)
        
        assertEquals(AiResponseType.NO_ACTION, response.responseType)
        assertNotNull(response.message)
    }

    private fun createTestBrain(context: AiContext): NexoraAiBrain {
        val mockContextBuilder = object : AiContextBuilder(null) {
            override suspend fun build(request: AiRequest?): AiContext = context
        }
        
        // Create a registry that definitely has the tools needed for the tests
        val toolRegistry = object : AiToolRegistry(null, null) {
            override fun getTool(name: String): AiTool? {
                return when (name) {
                    "createTask" -> object : AiTool {
                        override val name = "createTask"
                        override val description = ""
                        override val riskLevel = ToolRiskLevel.LOW_RISK
                        override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                            return ToolResult(true, message = "Task created")
                        }
                    }
                    else -> null
                }
            }
        }

        return NexoraAiBrain(
            contextBuilder = mockContextBuilder,
            aiService = fakeService,
            toolRegistry = toolRegistry,
            repository = com.example.nexora.data.NexoraRepository(null)
        )
    }

    private class FakeNexoraAiService : NexoraAiService {
        override suspend fun generateRecommendations(context: AiContext): List<AiRecommendation> {
            return AiPlanner().analyze(context)
        }
        override suspend fun generateDailyPlan(context: AiContext): NexoraDailyPlan = NexoraDailyPlan("", emptyList(), 0, "")
        override suspend fun analyzeGoals(context: AiContext): List<AiRecommendation> = emptyList()
        override suspend fun analyzeProductivity(context: AiContext): List<AiRecommendation> = emptyList()
        override suspend fun decomposeGoal(goalTitle: String, goalDescription: String, category: String): AiGoalDecomposition = AiGoalDecomposition("", "", emptyList())
        override suspend fun askNexora(context: AiContext, userMessage: String): AiModelStructuredResponse = AiModelStructuredResponse(AiDecision(AiDecisionType.NO_ACTION, "", ""), modelName = "fake")
        override suspend fun generateProactiveInsights(context: AiContext): List<AiRecommendation> {
            return NexoraProactiveEngine().detectSignals(context).map { signal ->
                AiRecommendation(
                    id = signal.fingerprint,
                    type = AiRecommendationType.GENERAL,
                    title = signal.title,
                    message = signal.message
                )
            }
        }
    }
}
