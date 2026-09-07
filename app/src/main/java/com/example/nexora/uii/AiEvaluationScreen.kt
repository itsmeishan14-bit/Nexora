package com.example.nexora.uii

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nexora.ai.evaluation.AiEvaluationMetric
import com.example.nexora.ai.evaluation.AiEvaluationResult
import com.example.nexora.ai.evaluation.AiEvaluationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEvaluationScreen(
    viewModel: AiEvaluationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexora AI Benchmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.runFullBenchmark() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run All")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.report == null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No benchmark data yet. Tap the play icon to start.")
                }
            }

            uiState.report?.let { report ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SummaryCard(
                            passRate = report.overallPassRate,
                            totalCases = report.results.size
                        )
                    }

                    item {
                        Text(
                            "Category Metrics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(report.categoryMetrics) { metric ->
                        MetricRow(metric)
                    }

                    item {
                        Text(
                            "Detailed Results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(report.results) { result ->
                        ResultItem(result)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(passRate: Float, totalCases: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Overall Quality Score", style = MaterialTheme.typography.titleMedium)
            Text(
                "${(passRate * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Text("Based on $totalCases deterministic scenarios", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MetricRow(metric: AiEvaluationMetric) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(metric.category, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${metric.passedCases}/${metric.totalCases} passed", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${(metric.passRate * 100).toInt()}%",
                color = if (metric.passRate >= 0.8f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResultItem(result: AiEvaluationResult) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result.passed) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(result.caseId, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("${result.latencies.processingTimeMs}ms", style = MaterialTheme.typography.labelSmall)
        }
        Text(result.actualMessage, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}
