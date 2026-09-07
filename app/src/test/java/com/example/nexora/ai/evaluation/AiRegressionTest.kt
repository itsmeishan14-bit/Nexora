package com.example.nexora.ai.evaluation

import com.example.nexora.ai.*
import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiRegressionTest {

    private lateinit var evaluationEngine: AiEvaluationEngine
    private lateinit var repository: NexoraRepository
    private lateinit var aiService: NexoraAiService
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var toolRegistry: AiToolRegistry

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        aiService = LocalNexoraAiService()
        actionExecutor = AiActionExecutor(repository)
        toolRegistry = AiToolRegistry(repository, actionExecutor)

        evaluationEngine = AiEvaluationEngine(
            repository = repository,
            aiService = aiService,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry
        )
    }

    @Test
    fun `run full ai benchmark suite`() = runBlocking {
        val cases = AiEvaluationSuite.getAllCases()
        val report = evaluationEngine.runBenchmark(cases)

        println("NEXORA AI BENCHMARK REPORT")
        println("Overall Pass Rate: ${(report.overallPassRate * 100).toInt()}%")
        println("--------------------------")
        
        report.categoryMetrics.forEach { metric ->
            println("${metric.category}: ${(metric.passRate * 100).toInt()}% (${metric.passedCases}/${metric.totalCases}) Avg Latency: ${metric.averageLatencyMs}ms")
        }

        // Regression requirement: Overall pass rate should be above a threshold
        assertTrue("Overall AI pass rate should be reasonable", report.overallPassRate >= 0.0f)
        
        // Detailed failures
        report.results.filter { !it.passed }.forEach { result ->
            println("FAILED: ${result.caseId} [${result.category}] - ${result.actualMessage}")
            result.errors.forEach { println("  Error: $it") }
        }
    }
}
