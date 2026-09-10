package com.example.nexora.uii

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
    Scaffold(
        containerColor = NexoraBackgroundLight,
        topBar = {
            Surface(
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Gray90)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Green10)
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
                Text(
                    text = "Automations help Nexora assist you without being asked. All logic remains entirely on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green40,
                    lineHeight = 22.sp
                )
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
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(
                icon = Icons.Rounded.AutoAwesome,
                containerColor = Green95,
                contentColor = Green60,
                size = 40
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Green10
                )
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = Green40,
                    lineHeight = 16.sp
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Green60,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Gray90,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
