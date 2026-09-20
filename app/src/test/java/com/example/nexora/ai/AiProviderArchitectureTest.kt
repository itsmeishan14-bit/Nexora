package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiProviderArchitectureTest {

    private lateinit var providerManager: AiProviderManager
    private lateinit var localProvider: LocalAiProvider

    @Before
    fun setup() {
        localProvider = LocalAiProvider()
        providerManager = AiProviderManager(localProvider = localProvider)
    }

    @Test
    fun `test local fallback when cloud is unconfigured`() = runBlocking {
        val cloudConfig = CloudAiConfig(isConfigured = false)
        val cloudProvider = CloudAiProvider(cloudConfig)
        val manager = AiProviderManager(localProvider, cloudProvider)
        
        val active = manager.getActiveProvider()
        assertEquals("Nexora Local AI", active.providerName)
    }

    @Test
    fun `test local fallback on cloud exception`() = runBlocking {
        val cloudProvider = object : AiModelProvider {
            override val providerName: String = "Failing Cloud"
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                throw Exception("Network Timeout")
            }
            override suspend fun generateStructuredResponse(prompt: String, context: AiContext, conversationContext: AiConversationContext?): AiModelStructuredResponse {
                throw Exception("Network Timeout")
            }
        }
        
        val manager = AiProviderManager(localProvider, cloudProvider)
        val context = AiContext()
        
        val response = manager.generateResponse("test", context)
        assertEquals("nexora-local-v1", response.modelName)
    }

    @Test
    fun `test factual grounding prevents ID hallucination`() = runBlocking {
        val cloudProvider = object : AiModelProvider {
            override val providerName: String = "Hallucinating Cloud"
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse = AiModelResponse("", "")
            override suspend fun generateStructuredResponse(prompt: String, context: AiContext, conversationContext: AiConversationContext?): AiModelStructuredResponse {
                return AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.COMPLETE_TASK,
                        title = "Hallucinate",
                        reason = "",
                        taskId = 9999L // Non-existent task ID
                    ),
                    modelName = "hallucinator"
                )
            }
        }
        
        val manager = AiProviderManager(localProvider, cloudProvider)
        val context = AiContext(tasks = emptyList())
        
        val result = manager.generateStructuredResponse("test", context)
        assertNull("Hallucinated Task ID should be nullified", result.decision.taskId)
    }

    @Test
    fun `test chat uses provider manager`() = runBlocking {
        val contextBuilder = object : AiContextBuilder(null) {
            override suspend fun build(request: AiRequest?): AiContext = AiContext()
        }
        
        val brain = NexoraAiBrain(
            contextBuilder = contextBuilder,
            aiService = LocalNexoraAiService(providerManager),
            providerManager = providerManager,
            toolRegistry = AiToolRegistry(null, null),
            repository = NexoraRepository(null)
        )
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "What should I do?")
        val response = brain.processRequest(request)
        
        assertNotNull(response.message)
        // Since we are using local fallback, it should return something from local heuristics
        assertTrue(response.message.contains("tasks pending") || response.message.contains("caught up"))
    }
}
