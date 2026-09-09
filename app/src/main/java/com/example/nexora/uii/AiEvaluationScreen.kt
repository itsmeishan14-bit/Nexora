package com.example.nexora.uii

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.nexora.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEvaluationScreen(
    viewModel: AiEvaluationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = NexoraBackground,
        topBar = {
            TopAppBar(
                title = { Text("Benchmarks", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NexoraPrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.runFullBenchmark() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run All", tint = NexoraPrimaryGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NexoraPrimaryGreen, trackColor = NexoraSoftGreen)
            }

            if (uiState.report == null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No benchmark data yet.", style = MaterialTheme.typography.bodyLarge, color = NexoraMutedText)
                }
            }

            uiState.report?.let { report ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        NexoraCard(containerColor = NexoraPrimaryGreen) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Overall Quality Score", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                                Text(
                                    "${(report.overallPassRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                                Text("Based on ${report.results.size} scenarios", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }

                    item {
                        Text(
                            "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraPrimaryText
                        )
                    }

                    items(report.categoryMetrics) { metric ->
                        MetricRow(metric)
                    }

                    item {
                        Text(
                            "Detailed Results",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoraPrimaryText
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
fun MetricRow(metric: AiEvaluationMetric) {
    NexoraCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(metric.category, style = MaterialTheme.typography.titleSmall, color = NexoraPrimaryText)
                Text("${metric.passedCases}/${metric.totalCases} passed", style = MaterialTheme.typography.labelMedium, color = NexoraMutedText)
            }
            Text(
                "${(metric.passRate * 100).toInt()}%",
                color = if (metric.passRate >= 0.8f) NexoraPrimaryGreen else NexoraError,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResultItem(result: AiEvaluationResult) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result.passed) NexoraPrimaryGreen else NexoraError,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(result.caseId, style = MaterialTheme.typography.labelLarge, color = NexoraPrimaryText)
            Spacer(Modifier.weight(1f))
            Text("${result.latencies.processingTimeMs}ms", style = MaterialTheme.typography.labelSmall, color = NexoraMutedText)
        }
        Spacer(Modifier.height(4.dp))
        Text(result.actualMessage, style = MaterialTheme.typography.bodyMedium, color = NexoraMutedText, maxLines = 2)
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = NexoraBorder)
    }
}
