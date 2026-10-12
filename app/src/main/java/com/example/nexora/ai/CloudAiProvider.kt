package com.example.nexora.ai

import android.content.Context
import com.example.nexora.util.NetworkUtils
import com.example.nexora.util.NexoraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thrown when Gemini Developer API free quota or rate limit is exhausted (HTTP 429).
 * Signals graceful fallback to local on-device AI without charging or failure.
 */
class GeminiQuotaExceededException(message: String) : Exception(message)

/**
 * Thrown when Gemini Developer API authentication fails (HTTP 401, 403).
 */
class GeminiAuthException(message: String) : Exception(message)

/**
 * Thrown when the requested Gemini model is not found or unsupported (HTTP 404).
 */
class GeminiModelUnavailableException(message: String) : Exception(message)

/**
 * Thrown when Gemini service experiences high demand or temporary server errors (HTTP 500, 503).
 */
class GeminiServerException(message: String) : Exception(message)

/**
 * Thrown when network connectivity to the Gemini API fails.
 */
class GeminiNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when Gemini response parsing fails.
 */
class GeminiParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when Gemini API key configuration is missing.
 */
class GeminiConfigException(message: String) : Exception(message)

/**
 * Categorized status codes for non-sensitive diagnostics.
 */
enum class CloudAiDiagnosticCode {
    CONFIGURED,
    MISSING_KEY,
    AUTH_ERROR,
    MODEL_UNAVAILABLE,
    QUOTA_EXCEEDED,
    SERVER_BUSY,
    NETWORK_ERROR,
    PARSING_ERROR,
    SUCCESS
}

/**
 * Non-sensitive diagnostic record. Never contains the actual API key or user content.
 */
data class CloudAiDiagnostic(
    val code: CloudAiDiagnosticCode,
    val hasKey: Boolean,
    val model: String,
    val summary: String,
    val httpCode: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Robust Cloud-based AI provider integrating the official Google Gemini Developer API.
 * Uses official Gemini REST generateContent endpoint, free-tier eligible model (gemini-3.8-flash),
 * and transparent local fallback on network failure, timeouts, or exhausted quota.
 *
 * ZERO-COST & SECURITY GUARANTEE:
 * - API keys are never hardcoded or logged.
 * - Loaded securely from local.properties / BuildConfig / SharedPreferences / environment.
 * - HTTP 429 quota exhaustion immediately triggers local fallback; no paid billing is ever used.
 */
class CloudAiProvider(
    private val config: CloudAiConfig,
    private val context: Context? = null,
    private val connectionFactory: ((URL) -> HttpURLConnection)? = null
) : AiModelProvider {

    override val providerName: String = "Nexora Gemini Cloud AI"

    @Volatile
    private var quotaCooldownUntil: Long = 0L

    @Volatile
    var lastDiagnostic: CloudAiDiagnostic = if (config.isConfigured && !config.apiKey.isNullOrBlank()) {
        CloudAiDiagnostic(
            code = CloudAiDiagnosticCode.CONFIGURED,
            hasKey = true,
            model = config.modelName ?: CloudAiConfig.DEFAULT_MODEL,
            summary = "Gemini key configured (${config.apiKey.length} chars). Ready."
        )
    } else {
        CloudAiDiagnostic(
            code = CloudAiDiagnosticCode.MISSING_KEY,
            hasKey = false,
            model = config.modelName ?: CloudAiConfig.DEFAULT_MODEL,
            summary = "Gemini API key is not configured in local.properties or environment."
        )
    }
        private set

    companion object {
        private const val QUOTA_COOLDOWN_MS = 60_000L // 1 minute backoff for 429 rate limit
    }

    override suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse = withContext(Dispatchers.IO) {
        val response = callGeminiApi(prompt, structured = false, conversationContext = null)
        AiModelResponse(
            text = response,
            modelName = config.modelName ?: CloudAiConfig.DEFAULT_MODEL
        )
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext,
        conversationContext: AiConversationContext?
    ): AiModelStructuredResponse = withContext(Dispatchers.IO) {
        val response = callGeminiApi(prompt, structured = true, conversationContext = conversationContext)

        try {
            parseStructuredResponse(response)
        } catch (e: Exception) {
            val clean = response.trim()
            if (clean.isNotBlank() && !clean.startsWith("{") && !clean.startsWith("[") && !clean.startsWith("```json")) {
                AiModelStructuredResponse(
                    decision = AiDecision(
                        type = AiDecisionType.GENERAL_CONVERSATION,
                        title = "Assistant Response",
                        reason = clean
                    ),
                    textResponse = clean,
                    modelName = config.modelName ?: CloudAiConfig.DEFAULT_MODEL,
                    conversationContext = conversationContext
                )
            } else {
                NexoraLogger.e("GEMINI", "Failed to parse structured response from Gemini", e)
                throw e // Let AiProviderManager handle fallback
            }
        }
    }

    /**
     * Executes the HTTP request to the Google Gemini Developer API.
     */
    fun callGeminiApi(
        prompt: String,
        structured: Boolean,
        conversationContext: AiConversationContext?
    ): String {
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() }
        if (apiKey == null) {
            val diag = CloudAiDiagnostic(
                code = CloudAiDiagnosticCode.MISSING_KEY,
                hasKey = false,
                model = config.modelName ?: CloudAiConfig.DEFAULT_MODEL,
                summary = "Gemini API key is missing or not configured"
            )
            lastDiagnostic = diag
            NexoraLogger.w("GEMINI", "Cloud AI request aborted: ${diag.summary}")
            throw GeminiConfigException(diag.summary)
        }

        if (System.currentTimeMillis() < quotaCooldownUntil) {
            val remainingSec = (quotaCooldownUntil - System.currentTimeMillis()) / 1000
            val diag = CloudAiDiagnostic(
                code = CloudAiDiagnosticCode.QUOTA_EXCEEDED,
                hasKey = true,
                model = config.modelName ?: CloudAiConfig.DEFAULT_MODEL,
                summary = "Gemini quota cooling down for ${remainingSec}s after HTTP 429",
                httpCode = 429
            )
            lastDiagnostic = diag
            NexoraLogger.i("GEMINI", diag.summary)
            throw GeminiQuotaExceededException(diag.summary)
        }

        val effectiveModel = config.modelName ?: CloudAiConfig.DEFAULT_MODEL
        val endpointUrl = config.endpoint ?: "${CloudAiConfig.GEMINI_BASE_URL}/$effectiveModel:generateContent"

        val systemPrompt = if (structured) {
            """
            You are Nexora OS Brain, a personal AI operating system assistant.
            Analyze the user's message in context of their tasks and goals.
            Return ONLY a valid JSON object strictly matching this schema:
            {
              "decision_type": "GENERAL_CONVERSATION|SHOW_INSIGHT|CREATE_TASK|COMPLETE_TASK|UPDATE_TASK|DELETE_TASK|CREATE_GOAL|UPDATE_GOAL|DELETE_GOAL|DECOMPOSE_GOAL|NO_ACTION|CLARIFY",
              "title": "<short title>",
              "reason": "<reason or explanation>",
              "text_response": "<direct conversational reply to user>",
              "task_id": null,
              "goal_id": null,
              "actions": []
            }
            For general questions, discussions, introductions, or capability explanations, use decision_type "GENERAL_CONVERSATION" and place your answer in "text_response".
            Never fabricate non-existent task_id or goal_id values.
            """.trimIndent()
        } else {
            "You are Nexora OS Brain, a knowledgeable and concise personal AI operating system assistant. Answer clearly and helpfully."
        }

        val contents = buildContentsPayload(prompt, conversationContext)

        val requestBody = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                if (structured) {
                    put("responseMimeType", "application/json")
                }
            })
        }

        val maxAttempts = if (connectionFactory != null) 1 else 2
        var currentAttempt = 0

        while (currentAttempt < maxAttempts) {
            currentAttempt++
            val url = URL(endpointUrl)
            val connection = connectionFactory?.invoke(url) ?: (url.openConnection() as HttpURLConnection)

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", apiKey)
                connection.doOutput = true
                connection.connectTimeout = config.connectTimeoutMs
                connection.readTimeout = config.readTimeoutMs

                connection.outputStream.use { os ->
                    val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode

                if (responseCode == 429) {
                    quotaCooldownUntil = System.currentTimeMillis() + QUOTA_COOLDOWN_MS
                    val errBody = readStreamQuietly(connection.errorStream)
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.QUOTA_EXCEEDED,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Gemini free-tier quota exhausted (HTTP 429)",
                        httpCode = 429
                    )
                    lastDiagnostic = diag
                    NexoraLogger.w("GEMINI", "${diag.summary}: $errBody")
                    throw GeminiQuotaExceededException(diag.summary)
                }

                if (responseCode == 401 || responseCode == 403) {
                    val errBody = readStreamQuietly(connection.errorStream)
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.AUTH_ERROR,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Gemini authorization failed ($responseCode). Check API key.",
                        httpCode = responseCode
                    )
                    lastDiagnostic = diag
                    NexoraLogger.w("GEMINI", "${diag.summary}: $errBody")
                    throw GeminiAuthException(diag.summary)
                }

                if (responseCode == 404) {
                    val errBody = readStreamQuietly(connection.errorStream)
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.MODEL_UNAVAILABLE,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Gemini model '$effectiveModel' not found (HTTP 404)",
                        httpCode = 404
                    )
                    lastDiagnostic = diag
                    NexoraLogger.w("GEMINI", "${diag.summary}: $errBody")
                    throw GeminiModelUnavailableException(diag.summary)
                }

                if (responseCode == 503) {
                    val errBody = readStreamQuietly(connection.errorStream)
                    if (currentAttempt < maxAttempts) {
                        NexoraLogger.i("GEMINI", "Gemini 503 high demand received, retrying in 1500ms (attempt $currentAttempt/$maxAttempts)...")
                        try { Thread.sleep(1500) } catch (_: InterruptedException) {}
                        continue
                    }
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.SERVER_BUSY,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Gemini service temporarily experiencing high demand (HTTP 503)",
                        httpCode = 503
                    )
                    lastDiagnostic = diag
                    NexoraLogger.w("GEMINI", "${diag.summary}: $errBody")
                    throw GeminiServerException(diag.summary)
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errBody = readStreamQuietly(connection.errorStream)
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.SERVER_BUSY,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Gemini API call failed with code $responseCode",
                        httpCode = responseCode
                    )
                    lastDiagnostic = diag
                    throw Exception("Gemini API call failed with code $responseCode: $errBody")
                }

                val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val parsedText = try {
                    parseGeminiResponse(responseBody)
                } catch (e: Exception) {
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.PARSING_ERROR,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Failed to parse Gemini response: ${e.message}",
                        httpCode = HttpURLConnection.HTTP_OK
                    )
                    lastDiagnostic = diag
                    throw GeminiParsingException(diag.summary, e)
                }

                lastDiagnostic = CloudAiDiagnostic(
                    code = CloudAiDiagnosticCode.SUCCESS,
                    hasKey = true,
                    model = effectiveModel,
                    summary = "Gemini request succeeded (model: $effectiveModel)",
                    httpCode = HttpURLConnection.HTTP_OK
                )
                return parsedText
            } catch (e: Exception) {
                if (e is GeminiQuotaExceededException || e is GeminiAuthException ||
                    e is GeminiModelUnavailableException || e is GeminiServerException ||
                    e is GeminiParsingException || e is GeminiConfigException) {
                    throw e
                }
                if (e is java.io.IOException) {
                    val diag = CloudAiDiagnostic(
                        code = CloudAiDiagnosticCode.NETWORK_ERROR,
                        hasKey = true,
                        model = effectiveModel,
                        summary = "Network error connecting to Gemini: ${e.javaClass.simpleName}: ${e.message}"
                    )
                    lastDiagnostic = diag
                    NexoraLogger.w("GEMINI", diag.summary)
                    throw GeminiNetworkException(diag.summary, e)
                }
                throw e
            } finally {
                connection.disconnect()
            }
        }

        throw GeminiServerException("Gemini API call failed after $maxAttempts attempts")
    }

    /**
     * Builds the Gemini contents array respecting bounded conversation turns.
     */
    fun buildContentsPayload(
        prompt: String,
        conversationContext: AiConversationContext?
    ): JSONArray {
        val contents = JSONArray()
        val rawHistory = conversationContext?.recentMessages?.takeLast(8) ?: emptyList()

        // Gemini requires the contents array to start with a user role
        val firstUserIdx = rawHistory.indexOfFirst { it.first }
        val validHistory = if (firstUserIdx >= 0) rawHistory.subList(firstUserIdx, rawHistory.size) else emptyList()

        var lastRole: String? = null
        for ((isUser, text) in validHistory) {
            if (text.isBlank()) continue
            val role = if (isUser) "user" else "model"
            if (role == lastRole) {
                // Merge consecutive turns of same role to satisfy Gemini turn rules
                val lastTurn = contents.getJSONObject(contents.length() - 1)
                val parts = lastTurn.getJSONArray("parts")
                val prev = parts.getJSONObject(0).optString("text", "")
                parts.getJSONObject(0).put("text", "$prev\n$text")
            } else {
                contents.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
                lastRole = role
            }
        }

        if (lastRole == "user") {
            val lastTurn = contents.getJSONObject(contents.length() - 1)
            val parts = lastTurn.getJSONArray("parts")
            val prev = parts.getJSONObject(0).optString("text", "")
            parts.getJSONObject(0).put("text", "$prev\n\n$prompt")
        } else {
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            })
        }

        return contents
    }

    /**
     * Extracts response text from Gemini's JSON structure:
     * candidates[0].content.parts[*].text
     */
    fun parseGeminiResponse(responseBody: String): String {
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")

        if (candidates == null || candidates.length() == 0) {
            val promptFeedback = json.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            if (!blockReason.isNullOrBlank()) {
                throw Exception("Gemini prompt blocked: $blockReason")
            }
            throw Exception("Gemini returned empty candidates")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val finishReason = firstCandidate.optString("finishReason", "")
        if (finishReason == "SAFETY" || finishReason == "RECITATION") {
            throw Exception("Gemini generation halted: $finishReason")
        }

        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            throw Exception("Gemini candidate content has no parts")
        }

        val textBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            textBuilder.append(part.optString("text", ""))
        }
        return textBuilder.toString()
    }

    /**
     * Parses structured response matching Nexora's brain contract.
     */
    fun parseStructuredResponse(content: String): AiModelStructuredResponse {
        val cleanContent = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(cleanContent)

        val typeStr = json.optString("decision_type", "NO_ACTION")
        val type = try {
            AiDecisionType.valueOf(typeStr)
        } catch (_: Exception) {
            AiDecisionType.GENERAL_CONVERSATION
        }

        val decision = AiDecision(
            type = type,
            title = json.optString("title", "Nexora Assistant"),
            reason = json.optString("reason", "Based on analysis."),
            taskId = if (json.isNull("task_id")) null else json.optLong("task_id"),
            goalId = if (json.isNull("goal_id")) null else json.optLong("goal_id")
        )

        val actionsList = mutableListOf<AiAction>()
        val actionsArray = json.optJSONArray("actions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val actionObj = actionsArray.optJSONObject(i) ?: continue
                val actionTypeStr = actionObj.optString("type", "")
                val actionType = try {
                    AiActionType.valueOf(actionTypeStr)
                } catch (_: Exception) {
                    null
                }
                if (actionType != null) {
                    val title = actionObj.optString("title", "Action")
                    val desc = actionObj.optString("description", "")
                    val paramsObj = actionObj.optJSONObject("parameters")
                    val params = mutableMapOf<String, Any>()
                    if (paramsObj != null) {
                        val keys = paramsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            params[k] = paramsObj.get(k)
                        }
                    }
                    actionsList.add(
                        AiAction(
                            type = actionType,
                            title = title,
                            description = desc,
                            parameters = params,
                            taskId = if (actionObj.isNull("task_id")) null else actionObj.optLong("task_id"),
                            goalId = if (actionObj.isNull("goal_id")) null else actionObj.optLong("goal_id"),
                            requiresConfirmation = actionObj.optBoolean("requires_confirmation", true)
                        )
                    )
                }
            }
        }

        val textResponse = if (json.isNull("text_response")) null else json.optString("text_response")

        return AiModelStructuredResponse(
            decision = decision,
            actions = actionsList,
            textResponse = textResponse,
            modelName = config.modelName ?: CloudAiConfig.DEFAULT_MODEL
        )
    }

    override suspend fun isAvailable(): Boolean {
        if (!config.isConfigured || config.apiKey.isNullOrBlank()) return false
        if (System.currentTimeMillis() < quotaCooldownUntil) return false
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) return false
        return true
    }

    fun resetCooldownForTesting() {
        quotaCooldownUntil = 0L
    }

    private fun readStreamQuietly(inputStream: java.io.InputStream?): String {
        if (inputStream == null) return ""
        return try {
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
}

/**
 * Configuration for Cloud AI Provider.
 */
data class CloudAiConfig(
    val endpoint: String? = null,
    val apiKey: String? = null,
    val modelName: String? = DEFAULT_MODEL,
    val isConfigured: Boolean = false,
    val connectTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 25000
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Loads Cloud AI configuration from secure sources outside git:
         * 1. BuildConfig.GEMINI_API_KEY (populated via local.properties at build time)
         * 2. System environment variable GEMINI_API_KEY
         * 3. SharedPreferences ("nexora_ai_prefs")
         */
        fun loadFromEnvironment(context: Context? = null): CloudAiConfig {
            var key: String? = null

            // 1. Check BuildConfig.GEMINI_API_KEY
            try {
                val clazz = Class.forName("com.example.nexora.BuildConfig")
                val field = clazz.getField("GEMINI_API_KEY")
                val value = field.get(null) as? String
                if (!value.isNullOrBlank()) {
                    key = value.trim()
                }
            } catch (_: Exception) {}

            // 2. Check System environment variable
            if (key.isNullOrBlank()) {
                val envKey = System.getenv("GEMINI_API_KEY")
                if (!envKey.isNullOrBlank()) {
                    key = envKey.trim()
                }
            }

            // 3. Check SharedPreferences if context is available
            if (key.isNullOrBlank() && context != null) {
                try {
                    val prefs = context.getSharedPreferences("nexora_ai_prefs", Context.MODE_PRIVATE)
                    val prefKey = prefs.getString("gemini_api_key", null)
                    if (!prefKey.isNullOrBlank()) {
                        key = prefKey.trim()
                    }
                } catch (_: Exception) {}
            }

            val isConfigured = !key.isNullOrBlank()
            val model = DEFAULT_MODEL
            val endpoint = if (isConfigured) "$GEMINI_BASE_URL/$model:generateContent" else null

            return CloudAiConfig(
                endpoint = endpoint,
                apiKey = key,
                modelName = model,
                isConfigured = isConfigured
            )
        }
    }
}
