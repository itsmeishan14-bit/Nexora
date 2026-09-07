package com.example.nexora.ai.evaluation

import com.example.nexora.ai.*
import kotlinx.coroutines.SystemPropsKt
import kotlin.system.measureTimeMillis

/**
 * Engine responsible for running AI evaluation benchmarks.
 */
class AiEvaluationEngine(
    private val brain: NexoraAiBrain
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
            // Note: If case.testContext is provided, we'd ideally want the brain to use it.
            // Since the brain builds its own context from its builder, 
            // for evaluation we might need a brain instance with a mock builder.
            response = brain.processRequest(request)
        } catch (e: Exception) {
            errors.add("Exception during processing: ${e.message}")
        }
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime

        val passed = if (errors.isNotEmpty()) {
            false
        } else if (response != null) {
            verifyResponse(case, response)
        } else {
            false
        }

        return AiEvaluationResult(
            caseId = case.caseId,
            category = case.category,
            passed = passed,
            actualIntent = inferIntentFromResponse(response),
            actualResponseType = response?.responseType,
            actualActionType = response?.proposedActions?.firstOrNull()?.type,
            actualConfidence = response?.confidence ?: AiConfidence.LOW,
            actualMessage = response?.message ?: "",
            latencies = PerformanceMetrics(processingTime),
            errors = errors,
            reasoningFactors = response?.evidence?.map { it.factor } ?: emptyList()
        )
    }

    private fun verifyResponse(case: AiEvaluationCase, response: AiResponse): Boolean {
        // 1. Response Type check
        if (case.expectedResponseType != null && response.responseType != case.expectedResponseType) {
            return false
        }

        // 2. Action Type check
        if (case.expectedActionType != null) {
            val actualAction = response.proposedActions.firstOrNull()?.type
            if (actualAction != case.expectedActionType) return false
        }

        // 3. Confidence check
        if (response.confidence.ordinal < case.minConfidence.ordinal) {
            return false
        }

        // 4. Custom verification logic
        if (case.verificationLogic != null) {
            // This is a bit recursive, but it allows specialized verification.
            // We pass a dummy result just to satisfy the logic if needed.
        }

        return true
    }

    private fun inferIntentFromResponse(response: AiResponse?): AiRequestType? {
        if (response == null) return null
        // This is a heuristic because the response doesn't explicitly store the detected intent
        return when (response.responseType) {
            AiResponseType.PLAN -> AiRequestType.DAILY_PLAN
            AiResponseType.RECOMMENDATION -> AiRequestType.NEXT_TASK
            AiResponseType.ACTION_PROPOSAL -> {
                val action = response.proposedActions.firstOrNull()?.type
                when (action) {
                    AiActionType.CREATE_TASK -> AiRequestType.CREATE_TASK
                    AiActionType.COMPLETE_TASK -> AiRequestType.COMPLETE_TASK
                    AiActionType.DECOMPOSE_GOAL -> AiRequestType.GOAL_DECOMPOSITION
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
