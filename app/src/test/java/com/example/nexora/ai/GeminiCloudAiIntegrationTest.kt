package com.example.nexora.ai

import com.example.nexora.util.NetworkUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class GeminiCloudAiIntegrationTest {

    private lateinit var localProvider: LocalAiProvider

    @Before
    fun setUp() {
        NetworkUtils.setNetworkAvailableForTesting(true)
        localProvider = LocalAiProvider()
    }

    @After
    fun tearDown() {
        NetworkUtils.setNetworkAvailableForTesting(null)
    }

    private fun createMockConnection(
        responseCode: Int,
        responseBody: String = "",
        errorBody: String = "",
        throwOnConnect: Exception? = null
    ): Pair<HttpURLConnection, ByteArrayOutputStream> {
        val capturedOutputStream = ByteArrayOutputStream()

        val mockConnection = object : HttpURLConnection(URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")) {
            override fun connect() {
                if (throwOnConnect != null) throw throwOnConnect
            }

            override fun disconnect() {}

            override fun usingProxy(): Boolean = false

            override fun getResponseCode(): Int {
                if (throwOnConnect != null) throw throwOnConnect
                return responseCode
            }

            override fun getOutputStream(): java.io.OutputStream {
                return capturedOutputStream
            }

            override fun getInputStream(): java.io.InputStream {
                if (throwOnConnect != null) throw throwOnConnect
                if (responseCode >= 400) throw IOException("Server returned HTTP response code: $responseCode")
                return ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
            }

            override fun getErrorStream(): java.io.InputStream? {
                return if (errorBody.isNotEmpty()) ByteArrayInputStream(errorBody.toByteArray(Charsets.UTF_8)) else null
            }
        }

        return Pair(mockConnection, capturedOutputStream)
    }

    @Test
    fun `test gemini request formatting for structured response mode`() = runBlocking {
        val validGeminiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"decision_type\": \"GENERAL_CONVERSATION\", \"title\": \"Answer\", \"reason\": \"Cloud answered\", \"text_response\": \"Gemini is ready!\"}"
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()

        val (connection, capturedOutput) = createMockConnection(HttpURLConnection.HTTP_OK, validGeminiResponse)

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-1.5-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(
            config = config,
            connectionFactory = { connection }
        )

        val convContext = AiConversationContext()
            .appendMessage(true, "Hi, I am Ishan")
            .appendMessage(false, "Hello Ishan!")

        val response = cloudProvider.generateStructuredResponse(
            prompt = "What can you help me with?",
            context = AiContext(),
            conversationContext = convContext
        )

        assertEquals("Nexora Gemini Cloud AI", cloudProvider.providerName)
        assertEquals("Gemini is ready!", response.textResponse)
        assertEquals(AiDecisionType.GENERAL_CONVERSATION, response.decision.type)

        // Inspect captured HTTP payload
        val sentJson = JSONObject(capturedOutput.toString(Charsets.UTF_8.name()))
        assertTrue(sentJson.has("contents"))
        assertTrue(sentJson.has("systemInstruction"))
        assertTrue(sentJson.has("generationConfig"))

        val genConfig = sentJson.getJSONObject("generationConfig")
        assertEquals("application/json", genConfig.getString("responseMimeType"))

        val contents = sentJson.getJSONArray("contents")
        assertTrue("Contents should have history plus user prompt", contents.length() >= 2)
    }

    @Test
    fun `test gemini conversational text response parsing`() = runBlocking {
        val geminiChatResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Nexora helps you optimize focus, manage tasks, and track high-impact goals."
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()

        val (connection, _) = createMockConnection(HttpURLConnection.HTTP_OK, geminiChatResponse)

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-1.5-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })

        val response = cloudProvider.generateResponse("What is Nexora?", AiContext())
        assertEquals("Nexora helps you optimize focus, manage tasks, and track high-impact goals.", response.text)
        assertEquals("gemini-1.5-flash", response.modelName)
    }

    @Test
    fun `test fallback to local AI when api key is missing`() = runBlocking {
        val unconfiguredConfig = CloudAiConfig(
            apiKey = null,
            isConfigured = false
        )
        val cloudProvider = CloudAiProvider(config = unconfiguredConfig)
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        assertFalse("Cloud provider should be unavailable without key", cloudProvider.isAvailable())
        assertFalse("Manager should report cloud inactive", manager.isCloudActive())

        val active = manager.getActiveProvider()
        assertEquals("Nexora Local AI", active.providerName)

        val response = manager.generateResponse("Hello Nexora", AiContext())
        assertNotNull(response.text)
        assertEquals("nexora-local-v1", response.modelName)
    }

    @Test
    fun `test fallback to local AI when offline`() = runBlocking {
        NetworkUtils.setNetworkAvailableForTesting(false)

        val config = CloudAiConfig(
            apiKey = "test-free-key",
            isConfigured = true
        )
        // Pass a dummy mock context or rely on NetworkUtils
        val cloudProvider = CloudAiProvider(
            config = config,
            context = null // context null with NetworkUtils override
        )
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        // When network is forced offline via NetworkUtils
        NetworkUtils.setNetworkAvailableForTesting(false)
        assertFalse(NetworkUtils.isNetworkAvailable(null))

        // Simulate offline execution through manager
        val offlineFailingCloud = object : AiModelProvider {
            override val providerName: String = "Nexora Gemini Cloud AI"
            override suspend fun isAvailable(): Boolean = NetworkUtils.isNetworkAvailable(null)
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                throw IOException("No internet connection")
            }
            override suspend fun generateStructuredResponse(prompt: String, context: AiContext, conversationContext: AiConversationContext?): AiModelStructuredResponse {
                throw IOException("No internet connection")
            }
        }

        val offlineManager = AiProviderManager(localProvider = localProvider, cloudProvider = offlineFailingCloud)
        assertFalse("Manager should not be active when offline", offlineManager.isCloudActive())

        val response = offlineManager.generateStructuredResponse("What are my tasks?", AiContext())
        assertEquals("nexora-local-v1", response.modelName)
        assertNotNull(response.textResponse)
    }

    @Test
    fun `test http 429 quota exhaustion gracefully falls back to local AI without crashing`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 429,
            errorBody = """{"error": {"code": 429, "message": "Resource has been exhausted (e.g. check quota)."}}"""
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-1.5-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        assertTrue(cloudProvider.isAvailable())

        // Executing request triggers 429 -> CloudAiProvider catches 429, logs quota exhausted, Manager falls back to LocalAiProvider
        val response = manager.generateStructuredResponse("How am I doing on goals?", AiContext())

        assertTrue("Local AI should fulfill request when free quota exhausted", response.modelName.contains("local"))
        assertNotNull(response.textResponse)

        // Verify that cloud provider entered cooldown and is no longer available
        assertFalse("Cloud provider should be in cooldown after 429", cloudProvider.isAvailable())
        assertFalse("Manager reports cloud inactive during quota cooldown", manager.isCloudActive())
    }

    @Test
    fun `test network timeout gracefully falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 0,
            throwOnConnect = SocketTimeoutException("Read timed out after 10000ms")
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-1.5-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Plan my day", AiContext())
        assertEquals("nexora-local-v1", response.modelName)
        assertNotNull(response.text)
    }

    @Test
    fun `test recovery when network or cloud availability returns`() = runBlocking {
        var simulateNetworkFailure = true

        val recoveringCloud = object : AiModelProvider {
            override val providerName: String = "Nexora Gemini Cloud AI"
            override suspend fun isAvailable(): Boolean = !simulateNetworkFailure
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                if (simulateNetworkFailure) throw IOException("Network unreachable")
                return AiModelResponse(text = "Cloud is back online!", modelName = "gemini-1.5-flash")
            }
            override suspend fun generateStructuredResponse(prompt: String, context: AiContext, conversationContext: AiConversationContext?): AiModelStructuredResponse {
                if (simulateNetworkFailure) throw IOException("Network unreachable")
                return AiModelStructuredResponse(
                    decision = AiDecision(type = AiDecisionType.GENERAL_CONVERSATION, title = "Cloud Online", reason = "Online"),
                    textResponse = "Cloud is back online!",
                    modelName = "gemini-1.5-flash"
                )
            }
        }

        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = recoveringCloud)

        // 1. Offline phase -> local fallback
        assertFalse(manager.isCloudActive())
        val offlineResp = manager.generateResponse("Hello", AiContext())
        assertEquals("nexora-local-v1", offlineResp.modelName)

        // 2. Connectivity restored -> automatically returns to cloud
        simulateNetworkFailure = false
        assertTrue(manager.isCloudActive())
        val onlineResp = manager.generateResponse("Hello again", AiContext())
        assertEquals("gemini-1.5-flash", onlineResp.modelName)
        assertEquals("Cloud is back online!", onlineResp.text)
    }

    @Test
    fun `test bounded conversation history maintains alternating turns`() {
        val config = CloudAiConfig(apiKey = "key", isConfigured = true)
        val provider = CloudAiProvider(config)

        val convContext = AiConversationContext()
            .appendMessage(false, "Initial welcome message") // model turn first
            .appendMessage(true, "My name is Alice")
            .appendMessage(false, "Nice to meet you Alice")
            .appendMessage(true, "I want to finish a report")

        val contents = provider.buildContentsPayload("Can you help me break it down?", convContext)

        assertTrue(contents.length() >= 2)
        // First entry must be user role
        val firstTurn = contents.getJSONObject(0)
        assertEquals("user", firstTurn.getString("role"))

        // Last entry must be user role containing the prompt
        val lastTurn = contents.getJSONObject(contents.length() - 1)
        assertEquals("user", lastTurn.getString("role"))
        val lastText = lastTurn.getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(lastText.contains("Can you help me break it down?"))
    }
}
