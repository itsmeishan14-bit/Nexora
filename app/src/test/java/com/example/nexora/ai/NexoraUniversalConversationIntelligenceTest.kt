package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.util.NexoraSecurity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraUniversalConversationIntelligenceTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var localProvider: LocalAiProvider
    private lateinit var providerManager: AiProviderManager
    private lateinit var contextBuilder: AiContextBuilder
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
        NexoraSecurity.clearAllAuthorizations()
        repository = MockNexoraRepository()
        localProvider = LocalAiProvider()
        providerManager = AiProviderManager(localProvider = localProvider)
        contextBuilder = AiContextBuilder(repository)
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

    @After
    fun tearDown() {
        NexoraSecurity.clearAllAuthorizations()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GENERAL QUESTIONS & STRENGTHS (NO GENERIC FALLBACK!)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test what are your strengths returns real capabilities and NOT generic command fallback`() = runBlocking {
        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "What are your strengths?")
        )

        assertNotNull(response.message)
        assertFalse(
            "Must NOT return generic fallback telling user to try predefined commands",
            response.message.contains("Try asking me 'What should I do next?'", ignoreCase = true)
        )
        assertTrue(
            "Response should describe real strengths (task management, productivity, automation)",
            response.message.contains("strengths", ignoreCase = true) ||
            response.message.contains("task", ignoreCase = true) ||
            response.message.contains("productivity", ignoreCase = true) ||
            response.message.contains("automation", ignoreCase = true)
        )
    }

    @Test
    fun `test what are your limitations returns honest local boundaries`() = runBlocking {
        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "What are your limitations?")
        )

        assertNotNull(response.message)
        assertFalse(
            "Must NOT return generic fallback",
            response.message.contains("Try asking me 'What should I do next?'", ignoreCase = true)
        )
        assertTrue(
            "Response should honestly explain limitations (web browsing, privacy, authorization)",
            response.message.contains("limitations", ignoreCase = true) ||
            response.message.contains("web", ignoreCase = true) ||
            response.message.contains("authorization", ignoreCase = true)
        )
    }

    @Test
    fun `test who are you returns assistant identity without mutating state`() = runBlocking {
        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "Who are you?")
        )

        assertNotNull(response.message)
        assertTrue(response.message.contains("Nexora", ignoreCase = true))
        assertTrue("Must be INFORMATION response", response.responseType == AiResponseType.INFORMATION)
        assertTrue("Must not propose mutations", response.proposedActions.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CONFIGURED CAPABLE LANGUAGE MODEL INTEGRATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test configured cloud language model is invoked for general conversation`() = runBlocking {
        var cloudModelInvoked = false
        val mockCloudModel = object : AiModelProvider {
            override val providerName: String = "Test Cloud Intelligence"
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                cloudModelInvoked = true
                return AiModelResponse("Cloud response text", "test-cloud-model")
            }
            override suspend fun generateStructuredResponse(
                prompt: String,
                context: AiContext,
                conversationContext: AiConversationContext?
            ): AiModelStructuredResponse {
                cloudModelInvoked = true
                return AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.GENERAL_CONVERSATION,
                        title = "Cloud Reasoning",
                        reason = "Open-ended reasoning generated by cloud model."
                    ),
                    textResponse = "I can discuss complex creative ideas and answer deep questions about productivity and science.",
                    modelName = "test-cloud-model"
                )
            }
        }

        val managerWithCloud = AiProviderManager(localProvider = localProvider, cloudProvider = mockCloudModel)
        val brainWithCloud = NexoraAiBrain(
            contextBuilder = contextBuilder,
            aiService = LocalNexoraAiService(providerManager = managerWithCloud),
            providerManager = managerWithCloud,
            toolRegistry = AiToolRegistry(repository, AiActionExecutor(repository)),
            repository = repository,
            automationSystem = NexoraAutomationSystem()
        )

        val response = brainWithCloud.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "Can you explain how neural networks learn?")
        )

        assertTrue("Configured cloud language model MUST be invoked for general questions", cloudModelInvoked)
        assertEquals(
            "I can discuss complex creative ideas and answer deep questions about productivity and science.",
            response.message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PROVIDER FAILURE & GRACEFUL FALLBACK
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test cloud provider failure gracefully falls back to local provider without error`() = runBlocking {
        val failingCloudModel = object : AiModelProvider {
            override val providerName: String = "Failing Cloud"
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                throw RuntimeException("Network timeout to cloud API")
            }
            override suspend fun generateStructuredResponse(
                prompt: String,
                context: AiContext,
                conversationContext: AiConversationContext?
            ): AiModelStructuredResponse {
                throw RuntimeException("Connection refused (503 Service Unavailable)")
            }
        }

        val managerWithFallback = AiProviderManager(localProvider = localProvider, cloudProvider = failingCloudModel)
        val brainWithFallback = NexoraAiBrain(
            contextBuilder = contextBuilder,
            aiService = LocalNexoraAiService(providerManager = managerWithFallback),
            providerManager = managerWithFallback,
            toolRegistry = AiToolRegistry(repository, AiActionExecutor(repository)),
            repository = repository,
            automationSystem = NexoraAutomationSystem()
        )

        repository.addTask(PremiumTask(id = 1L, title = "Important Work Task", category = "Work", duration = "30m"))

        val response = brainWithFallback.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "What are your strengths?")
        )

        assertNotNull("Response message must not be null even when cloud fails", response.message)
        assertTrue(
            "Fallback response should give local capabilities gracefully",
            response.message.contains("strengths", ignoreCase = true) ||
            response.message.contains("task", ignoreCase = true) ||
            response.message.contains("Nexora", ignoreCase = true)
        )
    }

    @Test
    fun `test offline unknown question produces graceful honest fallback with active workspace context`() = runBlocking {
        repository.addTask(PremiumTask(id = 5L, title = "Design Homepage", category = "Work", duration = "45m", priority = TaskPriority.HIGH))

        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "What is the capital of ancient Assyria?")
        )

        assertNotNull(response.message)
        assertTrue(
            "Response should honestly explain offline mode and offer task help",
            response.message.contains("local offline mode", ignoreCase = true) ||
            response.message.contains("tasks", ignoreCase = true)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. BOUNDED CONVERSATION HISTORY & MULTI-TURN CONTINUITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test multi-turn conversation retains bounded history in conversationContext`() = runBlocking {
        // Turn 1: Greeting
        val turn1 = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "Hello Nexora"))
        assertNotNull(turn1.conversationContext)
        val conv1 = turn1.conversationContext!!
        assertEquals(2, conv1.recentMessages.size) // User + Assistant
        assertEquals("Hello Nexora", conv1.recentMessages[0].second)
        assertTrue(conv1.recentMessages[0].first) // isUser = true
        assertFalse(conv1.recentMessages[1].first) // isUser = false

        // Turn 2: Strengths
        val turn2 = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "What are your strengths?", conversationContext = conv1)
        )
        assertNotNull(turn2.conversationContext)
        val conv2 = turn2.conversationContext!!
        assertEquals(4, conv2.recentMessages.size)
        assertEquals("What are your strengths?", conv2.recentMessages[2].second)

        // Verify recentHistoryAsText formatting
        val historyText = conv2.recentHistoryAsText(2)
        assertTrue(historyText.contains("User: Hello Nexora"))
        assertTrue(historyText.contains("User: What are your strengths?"))
    }

    @Test
    fun `test conversation history bounds at 20 messages`() {
        var context = AiConversationContext()
        for (i in 1..25) {
            context = context.appendMessage(true, "User message $i")
            context = context.appendMessage(false, "Assistant response $i")
        }

        assertEquals("History must be bounded to max 20 messages (10 turns)", 20, context.recentMessages.size)
        assertEquals("User message 16", context.recentMessages.first().second)
        assertEquals("Assistant response 25", context.recentMessages.last().second)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. TASK-ACTION REGRESSION (PRESERVATION OF PRODUCTIVITY INTELLIGENCE)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `test task creation remains fully functional`() = runBlocking {
        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "create a task to Review Code for 30 minutes")
        )

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.CREATE_TASK, proposed?.type)
        assertTrue(proposed?.parameters?.get("title")?.toString()?.contains("Review Code", ignoreCase = true) == true)
    }

    @Test
    fun `test complete task remains fully functional`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 101L, title = "Refactor Database", category = "Work", duration = "1h"))

        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "complete task Refactor Database")
        )

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.COMPLETE_TASK, proposed?.type)
        assertEquals(task.id, proposed?.taskId)
    }

    @Test
    fun `test delete task requires explicit confirmation`() = runBlocking {
        val task = repository.addTask(PremiumTask(id = 202L, title = "Obsolete Task", category = "Work", duration = "15m"))

        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "delete task Obsolete Task")
        )

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.DELETE_TASK, proposed?.type)
        assertTrue("Deletion must require confirmation", proposed?.requiresConfirmation == true)
    }

    @Test
    fun `test plan my day invokes planner with personalized schedule`() = runBlocking {
        repository.addTask(PremiumTask(id = 1L, title = "Urgent Client Presentation", category = "Work", duration = "45m", priority = TaskPriority.URGENT))
        repository.addTask(PremiumTask(id = 2L, title = "Team Sync", category = "Work", duration = "30m", priority = TaskPriority.MEDIUM))

        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "plan my day")
        )

        assertEquals(AiResponseType.PLAN, response.responseType)
        assertTrue(response.message.contains("Client Presentation", ignoreCase = true) || response.message.contains("plan", ignoreCase = true))
    }

    @Test
    fun `test goal decomposition decomposes goal into actionable tasks`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(id = 50L, title = "Build Android Architecture", category = "Learning", targetDate = "2026-11-30", progress = 0f))

        val response = engine.processRequest(
            AiRequest(AiRequestType.CHAT, userMessage = "break down my Build Android Architecture goal")
        )

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(
            response.proposedActions.any { it.type == AiActionType.DECOMPOSE_GOAL || it.type == AiActionType.CREATE_TASK } ||
            response.message.contains("Build Android Architecture", ignoreCase = true)
        )
    }
}
