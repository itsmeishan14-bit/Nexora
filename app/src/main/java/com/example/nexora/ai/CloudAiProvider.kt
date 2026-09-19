package com.example.nexora.ai

import com.example.nexora.util.NexoraLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust Cloud-based LLM provider (OpenAI-compatible).
 * Uses standard Java/Android networking to remain lightweight.
 * 
 * IMPORTANT:
 * - Real API keys are never hardcoded.
 * - LocalFallback is triggered on any failure.
 */
class CloudAiProvider(
    private val config: CloudAiConfig
) : AiModelProvider {

    override val providerName: String = "Nexora Cloud Intelligence"

    override suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse = withContext(Dispatchers.IO) {
        val response = callLlm(prompt, structured = false)
        AiModelResponse(
            text = response,
            modelName = config.modelName ?: "cloud-llm"
        )
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext
    ): AiModelStructuredResponse = withContext(Dispatchers.IO) {
        val response = callLlm(prompt, structured = true)
        
        try {
            parseStructuredResponse(response)
        } catch (e: Exception) {
            NexoraLogger.e("LLM", "Failed to parse structured response", e)
            throw e // Let ProviderManager handle fallback
        }
    }

    private fun callLlm(prompt: String, structured: Boolean): String {
        val apiKey = config.apiKey ?: throw IllegalStateException("API Key missing")
        val endpoint = config.endpoint ?: "https://api.openai.com/v1/chat/completions"

        val systemPrompt = if (structured) {
            "You are Nexora OS Brain. Return ONLY JSON matching this schema: { \"decision_type\": \"...\", \"title\": \"...\", \"reason\": \"...\", \"text_response\": \"...\", \"task_id\": null, \"goal_id\": null }"
        } else {
            "You are Nexora OS Brain. Answer helpfully."
        }

        val requestBody = JSONObject().apply {
            put("model", config.modelName ?: "gpt-3.5-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
        }

        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("LLM call failed with code: $responseCode")
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseStructuredResponse(content: String): AiModelStructuredResponse {
        // Clean markdown code blocks if present
        val cleanContent = content.trim().removePrefix("```json").removeSuffix("```").trim()
        val json = JSONObject(cleanContent)
        
        val typeStr = json.optString("decision_type", "NO_ACTION")
        val type = try { AiDecisionType.valueOf(typeStr) } catch (e: Exception) { AiDecisionType.NO_ACTION }
        
        val decision = AiDecision(
            type = type,
            title = json.optString("title", "AI Suggestion"),
            reason = json.optString("reason", "Based on analysis."),
            taskId = if (json.isNull("task_id")) null else json.optLong("task_id"),
            goalId = if (json.isNull("goal_id")) null else json.optLong("goal_id")
        )

        return AiModelStructuredResponse(
            decision = decision,
            textResponse = if (json.isNull("text_response")) null else json.optString("text_response"),
            modelName = config.modelName ?: "cloud-llm"
        )
    }

    override suspend fun isAvailable(): Boolean {
        return config.isConfigured && config.apiKey != null
    }
}

data class CloudAiConfig(
    val endpoint: String? = null,
    val apiKey: String? = null,
    val modelName: String? = null,
    val isConfigured: Boolean = false
)
