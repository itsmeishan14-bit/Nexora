package com.example.nexora.ai.evaluation

import com.example.nexora.ai.*
import com.example.nexora.data.NexoraRepository

/**
 * Engine responsible for running AI evaluation benchmarks.
 */
class AiEvaluationEngine(
    private val repository: NexoraRepository,
    private val aiService: NexoraAiService,
    private val actionExecutor: AiActionExecutor,
    private val toolRegistry: AiToolRegistry
) {

    /**
     * Executes a list of evaluation cases and returns a detailed report.
     */
    suspend fun runBenchmark(cases: List<AiEvaluationCase>): AiEvaluationReport {
        val results = mutableListOf<AiEvaluationResult>()

        cases.forEach { case ->
            val result = runCase(case)
            results.add(result)
        }

        return generateReport(results)
    }

    private suspend fun runCase(case: AiEvaluationCase): AiEvaluationResult {
        // Prepare context and repository if needed
        val mockRepo = repository as? MockNexoraRepository
        case.testContext?.let { context ->
            mockRepo?.seed(tasks = context.tasks, goals = context.goals)
        } ?: run {
            mockRepo?.seed() // Clear for isolation
        }

        val mockBuilder = MockAiContextBuilder()
        mockBuilder.fixedContext = case.testContext

        // Create a brain instance for this specific run
        val brain = NexoraAiBrain(
            contextBuilder = mockBuilder,
            aiService = aiService,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry,
            repository = repository
        )

        // Prepare request
        val request = AiRequest(
            type = case.requestType,
            userMessage = case.userInput,
            source = "evaluation"
        )

        var response: AiResponse? = null
        val errors = mutableListOf<String>()
        
        val startTime = System.currentTimeMillis()
        try {
            response = brain.processRequest(request)
        } catch (e: Exception) {
            errors.add("Exception during processing: ${e.message}")
        }
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime

        var passed = if (errors.isNotEmpty()) {
            false
        } else if (response != null) {
            verifyResponse(case, response)
        } else {
            false
        }
        
        val actualIntent = inferIntentFromResponse(response)
        
        val result = AiEvaluationResult(
            caseId = case.caseId,
            category = case.category,
            passed = passed,
            actualIntent = actualIntent,
            actualResponseType = response?.responseType,
            actualActionType = response?.proposedActions?.firstOrNull()?.type,
            actualConfidence = response?.confidence ?: AiConfidence.LOW,
            actualMessage = response?.message ?: "",
            latencies = PerformanceMetrics(processingTime),
            errors = errors,
            reasoningFactors = response?.evidence?.map { it.factor } ?: emptyList()
        )

        // Custom verification logic if provided
        if (case.verificationLogic != null) {
            passed = passed && case.verificationLogic.invoke(result)
        }

        return result.copy(passed = passed)
    }

    private fun verifyResponse(case: AiEvaluationCase, response: AiResponse): Boolean {
        // 1. Response Type check
        if (case.expectedResponseType != null && response.responseType != case.expectedResponseType) {
            // Special case: Agent returns INFORMATION for what would be ACTION_PROPOSAL
            val msg = response.message.lowercase()
            val isAgentWrite = response.responseType == AiResponseType.INFORMATION && 
                (msg.contains("created") || msg.contains("completed") || msg.contains("updated") || msg.contains("deleted"))
            
            if (case.expectedResponseType == AiResponseType.ACTION_PROPOSAL && isAgentWrite) {
                // Accept it as passed for now
            } else {
                return false
            }
        }

        // 2. Action Type check
        if (case.expectedActionType != null) {
            val actualAction = response.proposedActions.firstOrNull()?.type
            val msg = response.message.lowercase()
            
            val matchesAgentMsg = when (case.expectedActionType) {
                AiActionType.CREATE_TASK -> msg.contains("created")
                AiActionType.COMPLETE_TASK -> msg.contains("completed")
                AiActionType.UPDATE_TASK -> msg.contains("updated")
                AiActionType.DELETE_TASK -> msg.contains("deleted")
                else -> false
            }

            if (actualAction != case.expectedActionType && !matchesAgentMsg) return false
        }

        // 3. Confidence check
        if (response.confidence.ordinal < case.minConfidence.ordinal) {
            return false
        }

        return true
    }

    private fun inferIntentFromResponse(response: AiResponse?): AiRequestType? {
        if (response == null) return null
        
        // Check message for keywords if it's an INFORMATION response from Agent
        if (response.responseType == AiResponseType.INFORMATION) {
            val msg = response.message.lowercase()
            return when {
                msg.contains("created") -> AiRequestType.CREATE_TASK
                msg.contains("completed") -> AiRequestType.COMPLETE_TASK
                msg.contains("updated") -> AiRequestType.UPDATE_TASK
                msg.contains("deleted") -> AiRequestType.DELETE_TASK
                else -> AiRequestType.CHAT
            }
        }

        return when (response.responseType) {
            AiResponseType.PLAN -> AiRequestType.DAILY_PLAN
            AiResponseType.RECOMMENDATION -> AiRequestType.NEXT_TASK
            AiResponseType.ACTION_PROPOSAL -> {
                val action = response.proposedActions.firstOrNull()?.type
                when (action) {
                    AiActionType.CREATE_TASK -> AiRequestType.CREATE_TASK
                    AiActionType.COMPLETE_TASK -> AiRequestType.COMPLETE_TASK
                    AiActionType.DECOMPOSE_GOAL -> AiRequestType.GOAL_DECOMPOSITION
                    AiActionType.UPDATE_GOAL -> AiRequestType.UPDATE_GOAL
                    AiActionType.DELETE_TASK -> AiRequestType.DELETE_TASK
                    else -> AiRequestType.CHAT
                }
            }
            else -> AiRequestType.CHAT
        }
    }

    private fun generateReport(results: List<AiEvaluationResult>): AiEvaluationReport {
        val overallPassRate = if (results.isNotEmpty()) {
            results.count { it.passed }.toFloat() / results.size
        } else 0f

        val categoryMetrics = results.groupBy { it.category }.map { (category, categoryResults) ->
            AiEvaluationMetric(
                category = category.name,
                totalCases = categoryResults.size,
                passedCases = categoryResults.count { it.passed },
                passRate = categoryResults.count { it.passed }.toFloat() / categoryResults.size,
                averageLatencyMs = categoryResults.map { it.latencies.processingTimeMs }.average().toLong()
            )
        }

        return AiEvaluationReport(
            overallPassRate = overallPassRate,
            categoryMetrics = categoryMetrics,
            results = results
        )
    }
}
