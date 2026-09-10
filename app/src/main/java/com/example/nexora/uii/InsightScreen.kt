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
    val viewModel: NexoraAiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember(engine) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NexoraAiViewModel(engine = engine) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = NexoraBackgroundLight,
        topBar = {
            Surface(
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Gray90)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Observations",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Green10
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // PRODUCTIVITY PATTERNS
            if (uiState.memory.items.isNotEmpty()) {
                item {
                    Text("Productivity Patterns", style = MaterialTheme.typography.titleMedium, color = Green10)
                }
                items(uiState.memory.items) { memory ->
                    PatternCard(memory)
                }
            }

            // PROACTIVE OBSERVATIONS
            if (uiState.proactiveInsights.isNotEmpty()) {
                item {
                    Text("AI Observations", style = MaterialTheme.typography.titleMedium, color = Green10)
                }
                items(uiState.proactiveInsights) { rec ->
                    ObservationCard(rec)
                }
            }
            
            if (uiState.memory.items.isEmpty() && uiState.proactiveInsights.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text("Everything looks steady.", style = MaterialTheme.typography.bodyLarge, color = Green40)
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternCard(memory: AiMemoryItem) {
    NexoraCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(Icons.Rounded.AutoAwesome, Green95, Green60, size = 24)
                Spacer(Modifier.width(12.dp))
                Text(text = memory.title, style = MaterialTheme.typography.titleSmall, color = Green10)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Green40,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ObservationCard(rec: AiRecommendation) {
    AiSurface {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(Icons.Rounded.Visibility, Clay90, Clay40, size = 24)
                Spacer(Modifier.width(12.dp))
                Text(text = rec.title, style = MaterialTheme.typography.titleSmall, color = Green10)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = rec.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Green40,
                lineHeight = 20.sp
            )
        }
    }
}
