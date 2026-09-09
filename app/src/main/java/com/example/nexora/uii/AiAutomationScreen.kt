package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
        containerColor = NexoraBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NexoraPrimaryText)
                }
                Text(
                    text = "Automations",
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoraPrimaryText,
                    modifier = Modifier.weight(1f)
                )
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
                    color = NexoraMutedText,
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(NexoraShapes.small)
                    .background(NexoraSoftGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NexoraPrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoraPrimaryText
                )
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraMutedText,
                    lineHeight = 16.sp
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NexoraPrimaryGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = NexoraBorder,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
