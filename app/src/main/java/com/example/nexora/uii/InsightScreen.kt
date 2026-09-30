package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.nexora.ai.*
import com.example.nexora.ui.theme.*

@Composable
fun InsightScreen(
    engine: NexoraAiEngine
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: NexoraAiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember(engine) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NexoraAiViewModel(engine, context.applicationContext) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, NexoraBorder),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Observations",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Green10
                    )
                    Text(
                        text = "Patterns Nexora has learned about you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Green40
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // PRODUCTIVITY PATTERNS
            if (uiState.memory.items.isNotEmpty()) {
                item {
                    SectionDivider(label = "PRODUCTIVITY PATTERNS")
                }
                items(uiState.memory.items) { memory ->
                    InsightPatternCard(memory)
                }
            }

            // PROACTIVE OBSERVATIONS
            if (uiState.proactiveInsights.isNotEmpty()) {
                item {
                    SectionDivider(label = "AI OBSERVATIONS")
                }
                items(uiState.proactiveInsights) { rec ->
                    InsightObservationCard(rec)
                }
            }

            // EMPTY STATE
            if (uiState.memory.items.isEmpty() && uiState.proactiveInsights.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Everything looks steady.",
                                style = MaterialTheme.typography.titleMedium,
                                color = Green40
                            )
                            Text(
                                text = "Patterns will appear as you use Nexora.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoraMutedTextLight,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightPatternCard(memory: AiMemoryItem) {
    NexoraCard {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBox(Icons.Rounded.AutoAwesome, Green95, Green60, size = 24)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = memory.title, style = MaterialTheme.typography.titleSmall, color = Green10)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green40,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun InsightObservationCard(rec: AiRecommendation) {
    AiSurface {
        Row(verticalAlignment = Alignment.Top) {
            IconBox(Icons.Rounded.Visibility, Clay90, Clay40, size = 24)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = rec.title, style = MaterialTheme.typography.titleSmall, color = Green10)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rec.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green40,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
