package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraRealConversationalAiTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine

    @Before
    fun setup() {
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
    fun `test hello returns natural greeting and NOT unsolicited task recommendation`() = runBlocking {
        repository.addTask(PremiumTask(title = "Study DSA", category = "Work", duration = "30m", priority = TaskPriority.HIGH))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "hello"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("Nexora", ignoreCase = true) || response.message.contains("help", ignoreCase = true) || response.message.contains("Hey", ignoreCase = true))
        assertFalse("Greeting MUST NOT return unsolicited task recommendation text", response.message.contains("all caught up", ignoreCase = true))
        assertFalse("Greeting MUST NOT return pending tasks text", response.message.contains("tasks pending", ignoreCase = true))
    }

    @Test
    fun `test hi returns natural greeting`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "hi"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("Hi", ignoreCase = true) || response.message.contains("help", ignoreCase = true))
        assertFalse(response.message.contains("all caught up", ignoreCase = true))
    }

    @Test
    fun `test capability question returns actual Nexora capabilities`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "what can you do?"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("manage tasks", ignoreCase = true) || response.message.contains("goals", ignoreCase = true))
        assertFalse(response.message.contains("all caught up", ignoreCase = true))
    }

    @Test
    fun `test thanks returns polite acknowledgement`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "thanks"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("welcome", ignoreCase = true) || response.message.contains("assistance", ignoreCase = true))
        assertFalse(response.message.contains("all caught up", ignoreCase = true))
    }

    @Test
    fun `test goodbye returns polite farewell`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "goodbye"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("Goodbye", ignoreCase = true) || response.message.contains("productive", ignoreCase = true))
    }

    @Test
    fun `test what should I do next returns real task recommendation`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Study Kotlin Coroutines", category = "Work", duration = "45m", priority = TaskPriority.URGENT))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "what should I do next?"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("Kotlin Coroutines", ignoreCase = true))
    }

    @Test
    fun `test why am I falling behind returns productivity analysis`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "why am I falling behind?"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("analysis", ignoreCase = true) || response.message.contains("behavior", ignoreCase = true) || response.message.contains("learning", ignoreCase = true))
    }

    @Test
    fun `test complete task command routes to action flow`() = runBlocking {
        val task = repository.addTask(PremiumTask(title = "Task to finish", category = "Work", duration = "20m"))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "complete my Task to finish task"))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val proposed = response.proposedActions.firstOrNull()
        assertNotNull(proposed)
        assertEquals(AiActionType.COMPLETE_TASK, proposed?.type)
        assertEquals(task.id, proposed?.taskId)
    }

    @Test
    fun `test break down goal command routes to goal decomposition`() = runBlocking {
        val goal = repository.addGoal(NexoraGoal(title = "Learn AI OS", category = "Study", targetDate = "2026-12-31", progress = 0f))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "break down my Learn AI OS goal"))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(response.message.contains("Learn AI OS", ignoreCase = true) || response.proposedActions.any { it.type == AiActionType.DECOMPOSE_GOAL || it.type == AiActionType.CREATE_TASK })
    }

    @Test
    fun `test plan my day command invokes planner`() = runBlocking {
        repository.addTask(PremiumTask(title = "Work Task", category = "Work", duration = "30m", priority = TaskPriority.HIGH))

        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "plan my day"))

        assertEquals(AiResponseType.PLAN, response.responseType)
        assertTrue(response.message.contains("Work Task", ignoreCase = true) || response.message.contains("plan", ignoreCase = true))
    }

    @Test
    fun `test show my automations returns real automations`() = runBlocking {
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = "show my automations"))

        assertNotNull(response.message)
        assertTrue(response.message.contains("Workload Manager", ignoreCase = true) || response.message.contains("automations", ignoreCase = true))
    }
}
