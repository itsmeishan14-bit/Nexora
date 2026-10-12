package com.example.nexora.uii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.evaluation.AiEvaluationMetric
import com.example.nexora.ai.evaluation.AiEvaluationResult
import com.example.nexora.ai.evaluation.AiEvaluationViewModel
import com.example.nexora.ui.theme.*

@Composable
fun AiEvaluationScreen(
    viewModel: AiEvaluationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Benchmarks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { viewModel.runFullBenchmark() }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Run All",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }

            if (uiState.report == null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No benchmark data yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.report?.let { report ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        NexoraCard {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "OVERALL QUALITY SCORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${(report.overallPassRate * 100).toInt()}%",
                                    style = NumericStyle.copy(
                                        fontSize = 38.sp,
                                        color = if (report.overallPassRate >= 0.8f) NexoraTheme.customColors.priorityLow else MaterialTheme.colorScheme.error
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Evaluated against ${report.results.size} benchmark test scenarios.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    item {
                        SectionDivider(label = "CATEGORIES")
                    }

                    items(report.categoryMetrics, key = { it.category }) { metric ->
                        MetricRow(metric)
                    }

                    item {
                        SectionDivider(label = "DETAILED RESULTS")
                    }

                    items(report.results, key = { it.caseId }) { result ->
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
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metric.category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${metric.passedCases}/${metric.totalCases} passed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(metric.passRate * 100).toInt()}%",
                color = if (metric.passRate >= 0.8f) NexoraTheme.customColors.priorityLow else MaterialTheme.colorScheme.error,
                style = NumericStyle.copy(fontSize = 18.sp)
            )
        }
    }
}

@Composable
fun ResultItem(result: AiEvaluationResult) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (result.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (result.passed) NexoraTheme.customColors.priorityLow else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = result.caseId,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${result.latencies.processingTimeMs}ms",
                style = NumericStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            )
        }
        if (result.actualMessage.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = result.actualMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}
