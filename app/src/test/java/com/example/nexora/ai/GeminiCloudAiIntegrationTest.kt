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

        val mockConnection = object : HttpURLConnection(URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")) {
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
            modelName = "gemini-3.8-flash",
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
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })

        val response = cloudProvider.generateResponse("What is Nexora?", AiContext())
        assertEquals("Nexora helps you optimize focus, manage tasks, and track high-impact goals.", response.text)
        assertEquals("gemini-3.8-flash", response.modelName)
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
        assertEquals(CloudAiDiagnosticCode.MISSING_KEY, cloudProvider.lastDiagnostic.code)
        assertFalse(cloudProvider.lastDiagnostic.hasKey)

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
        val cloudProvider = CloudAiProvider(
            config = config,
            context = null
        )
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

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
            modelName = "gemini-3.8-flash",
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
        assertEquals(CloudAiDiagnosticCode.QUOTA_EXCEEDED, cloudProvider.lastDiagnostic.code)
    }

    @Test
    fun `test http 404 model unavailable produces diagnostic and falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 404,
            errorBody = """{"error": {"code": 404, "message": "models/gemini-1.5-flash is not found for API version v1beta"}}"""
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-1.5-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Hello Nexora", AiContext())

        assertEquals("nexora-local-v1", response.modelName)
        assertNotNull(response.text)
        assertEquals(CloudAiDiagnosticCode.MODEL_UNAVAILABLE, cloudProvider.lastDiagnostic.code)
        assertEquals(404, cloudProvider.lastDiagnostic.httpCode)
    }

    @Test
    fun `test http 401 auth error produces diagnostic and falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 401,
            errorBody = """{"error": {"code": 401, "message": "API key not valid."}}"""
        )

        val config = CloudAiConfig(
            apiKey = "invalid-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateStructuredResponse("Explain my day", AiContext())
        assertTrue("Local AI should fulfill request on auth fallback", response.modelName.contains("local"))
        assertEquals(CloudAiDiagnosticCode.AUTH_ERROR, cloudProvider.lastDiagnostic.code)
        assertEquals(401, cloudProvider.lastDiagnostic.httpCode)
    }

    @Test
    fun `test http 503 high demand produces server busy diagnostic and falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 503,
            errorBody = """{"error": {"code": 503, "message": "This model is currently experiencing high demand."}}"""
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Summarize tasks", AiContext())

        assertEquals("nexora-local-v1", response.modelName)
        assertEquals(CloudAiDiagnosticCode.SERVER_BUSY, cloudProvider.lastDiagnostic.code)
        assertEquals(503, cloudProvider.lastDiagnostic.httpCode)
    }

    @Test
    fun `test malformed json response produces parsing error diagnostic`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = HttpURLConnection.HTTP_OK,
            responseBody = """{"candidates": []}"""
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Hello", AiContext())

        assertEquals("nexora-local-v1", response.modelName)
        assertEquals(CloudAiDiagnosticCode.PARSING_ERROR, cloudProvider.lastDiagnostic.code)
    }

    @Test
    fun `test network timeout gracefully falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 0,
            throwOnConnect = SocketTimeoutException("Read timed out after 10000ms")
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Plan my day", AiContext())
        assertEquals("nexora-local-v1", response.modelName)
        assertNotNull(response.text)
        assertEquals(CloudAiDiagnosticCode.TIMEOUT_ERROR, cloudProvider.lastDiagnostic.code)
    }

    @Test
    fun `test recovery when network or cloud availability returns`() = runBlocking {
        var simulateNetworkFailure = true

        val recoveringCloud = object : AiModelProvider {
            override val providerName: String = "Nexora Gemini Cloud AI"
            override suspend fun isAvailable(): Boolean = !simulateNetworkFailure
            override suspend fun generateResponse(prompt: String, context: AiContext): AiModelResponse {
                if (simulateNetworkFailure) throw IOException("Network unreachable")
                return AiModelResponse(text = "Cloud is back online!", modelName = "gemini-3.8-flash")
            }
            override suspend fun generateStructuredResponse(prompt: String, context: AiContext, conversationContext: AiConversationContext?): AiModelStructuredResponse {
                if (simulateNetworkFailure) throw IOException("Network unreachable")
                return AiModelStructuredResponse(
                    decision = AiDecision(type = AiDecisionType.GENERAL_CONVERSATION, title = "Cloud Online", reason = "Online"),
                    textResponse = "Cloud is back online!",
                    modelName = "gemini-3.8-flash"
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
        assertEquals("gemini-3.8-flash", onlineResp.modelName)
        assertEquals("Cloud is back online!", onlineResp.text)
    }

    @Test
    fun `test bounded conversation history maintains alternating turns`() {
        val config = CloudAiConfig(apiKey = "key", isConfigured = true)
        val provider = CloudAiProvider(config)

        val convContext = AiConversationContext()
            .appendMessage(false, "Initial welcome message")
            .appendMessage(true, "My name is Alice")
            .appendMessage(false, "Nice to meet you Alice")
            .appendMessage(true, "I want to finish a report")

        val contents = provider.buildContentsPayload("Can you help me break it down?", convContext)

        assertTrue(contents.length() >= 2)
        val firstTurn = contents.getJSONObject(0)
        assertEquals("user", firstTurn.getString("role"))

        val lastTurn = contents.getJSONObject(contents.length() - 1)
        assertEquals("user", lastTurn.getString("role"))
        val lastText = lastTurn.getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(lastText.contains("Can you help me break it down?"))
    }

    @Test
    fun `test default model configuration is gemini 3 8 flash`() {
        assertEquals("gemini-3.8-flash", CloudAiConfig.DEFAULT_MODEL)
        val config = CloudAiConfig()
        assertEquals("gemini-3.8-flash", config.modelName)
    }

    @Test
    fun `test safe diagnostics never expose api key value`() {
        val secretKey = "AQ.SuperSecretKey1234567890"
        val config = CloudAiConfig(apiKey = secretKey, isConfigured = true)
        val provider = CloudAiProvider(config)

        val diag = provider.lastDiagnostic
        assertTrue("Diagnostic must indicate key is present", diag.hasKey)
        assertFalse("Diagnostic summary must NOT contain secret key", diag.summary.contains(secretKey))
        assertFalse("Diagnostic summary must NOT contain secret key prefix", diag.summary.contains("SuperSecret"))
        assertEquals(CloudAiDiagnosticCode.CONFIGURED, diag.code)
    }

    @Test
    fun `test provider selection chooses cloud when configured and local when not`() = runBlocking {
        val configuredCloud = CloudAiProvider(CloudAiConfig(apiKey = "valid-key", isConfigured = true))
        val managerWithCloud = AiProviderManager(localProvider = localProvider, cloudProvider = configuredCloud)
        assertTrue(managerWithCloud.isCloudActive())
        assertEquals("Nexora Gemini Cloud AI", managerWithCloud.getActiveProvider().providerName)

        val unconfiguredCloud = CloudAiProvider(CloudAiConfig(apiKey = null, isConfigured = false))
        val managerWithoutCloud = AiProviderManager(localProvider = localProvider, cloudProvider = unconfiguredCloud)
        assertFalse(managerWithoutCloud.isCloudActive())
        assertEquals("Nexora Local AI", managerWithoutCloud.getActiveProvider().providerName)
    }

    @Test
    fun `test dns resolution failure produces dns error diagnostic with actionable hint and falls back to local AI`() = runBlocking {
        val dnsException = java.net.UnknownHostException("Unable to resolve host \"generativelanguage.googleapis.com\": No address associated with hostname")
        val (connection, _) = createMockConnection(
            responseCode = 0,
            throwOnConnect = dnsException
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateStructuredResponse("Check priorities", AiContext())

        // 1. Verifies local AI fallback fulfilled the request
        assertTrue("Local AI should fulfill request when DNS fails", response.modelName.contains("local"))
        assertNotNull(response.textResponse)

        // 2. Verifies specific DNS_ERROR diagnostic classification
        assertEquals(CloudAiDiagnosticCode.DNS_ERROR, cloudProvider.lastDiagnostic.code)
        assertTrue(cloudProvider.lastDiagnostic.summary.contains("generativelanguage.googleapis.com"))
        assertNotNull(cloudProvider.lastDiagnostic.actionableHint)
        assertTrue(cloudProvider.lastDiagnostic.actionableHint!!.contains("Private DNS"))

        // 3. Verifies that isCloudActive correctly reports false on DNS failure
        assertFalse("Manager must report cloud inactive when DNS resolution fails", manager.isCloudActive())
    }

    @Test
    fun `test http 400 bad request produces bad request diagnostic and falls back to local AI`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 400,
            errorBody = """{"error": {"code": 400, "message": "Invalid JSON payload received."}}"""
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Hello Nexora", AiContext())

        assertEquals("nexora-local-v1", response.modelName)
        assertEquals(CloudAiDiagnosticCode.BAD_REQUEST, cloudProvider.lastDiagnostic.code)
        assertEquals(400, cloudProvider.lastDiagnostic.httpCode)
        assertFalse("Manager must report cloud inactive on HTTP 400 bad request", manager.isCloudActive())
    }

    @Test
    fun `test general io network error produces network error diagnostic`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 0,
            throwOnConnect = IOException("Broken pipe")
        )

        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            modelName = "gemini-3.8-flash",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        val response = manager.generateResponse("Plan my day", AiContext())
        assertEquals("nexora-local-v1", response.modelName)
        assertEquals(CloudAiDiagnosticCode.NETWORK_ERROR, cloudProvider.lastDiagnostic.code)
        assertFalse(manager.isCloudActive())
    }

    @Test
    fun `test probe connectivity with mock connection skips network and preserves state`() = runBlocking {
        val (connection, _) = createMockConnection(responseCode = HttpURLConnection.HTTP_OK)
        val config = CloudAiConfig(
            apiKey = "test-free-api-key",
            isConfigured = true
        )
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })

        val diag = cloudProvider.probeConnectivity()
        assertEquals(CloudAiDiagnosticCode.CONFIGURED, diag.code)
        assertTrue(diag.hasKey)
    }

    @Test
    fun `test isCloudActive returns false on all error diagnostic states`() = runBlocking {
        val (connection, _) = createMockConnection(
            responseCode = 401,
            errorBody = """{"error": {"code": 401, "message": "API key not valid."}}"""
        )
        val config = CloudAiConfig(apiKey = "invalid-key", isConfigured = true)
        val cloudProvider = CloudAiProvider(config = config, connectionFactory = { connection })
        val manager = AiProviderManager(localProvider = localProvider, cloudProvider = cloudProvider)

        assertTrue("Initially configured before execution", manager.isCloudActive())

        manager.generateResponse("Test", AiContext())

        assertFalse("Should be inactive after auth error", manager.isCloudActive())
        assertEquals(CloudAiDiagnosticCode.AUTH_ERROR, cloudProvider.lastDiagnostic.code)
    }
}
