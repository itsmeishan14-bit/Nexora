package com.example.nexora.uii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.AiAutomationRule
import com.example.nexora.ui.theme.*

@Composable
fun AiAutomationScreen(
    rules: List<AiAutomationRule>,
    onBack: () -> Unit,
    onToggleRule: (AiAutomationRule) -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, NexoraBorder),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Green10
                        )
                    }
                    Text(
                        text = "Automations",
                        style = MaterialTheme.typography.titleLarge,
                        color = Green10,
                        modifier = Modifier.weight(1f)
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nexora Intelligence Rules",
                        style = MaterialTheme.typography.titleMedium,
                        color = Green10
                    )
                    Text(
                        text = "All automation logic runs entirely on your device. Nothing is sent to the cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraMutedTextLight,
                        lineHeight = 18.sp
                    )
                }
            }

            item {
                SectionDivider(label = "RULES")
            }

            items(rules) { rule ->
                AutomationRuleCard(
                    rule = rule,
                    onToggle = { onToggleRule(rule) }
                )
            }
        }
    }
}

@Composable
fun AutomationRuleCard(
    rule: AiAutomationRule,
    onToggle: () -> Unit
) {
    NexoraCard {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(
                icon = Icons.Rounded.AutoAwesome,
                containerColor = Green95,
                contentColor = Green60,
                size = 36
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Green10
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraMutedTextLight,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Green60,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = NexoraBorder,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
