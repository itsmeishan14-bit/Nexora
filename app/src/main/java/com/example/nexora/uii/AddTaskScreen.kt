package com.example.nexora.uii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ui.theme.*

@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    goals: List<NexoraGoal>,
    selectedGoal: NexoraGoal? = null,
    onSave: (String, String, String, String?, TaskPriority) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var chosenGoal by remember { mutableStateOf(selectedGoal) }
    var chosenPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var goalMenuExpanded by remember { mutableStateOf(false) }

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Green40)
                    }
                    Text(
                        text = "New Task",
                        style = MaterialTheme.typography.titleSmall,
                        color = Green10
                    )
                    TextButton(
                        onClick = {
                            if (taskName.isNotBlank()) {
                                onSave(
                                    taskName.trim(),
                                    category.ifBlank { "Personal" },
                                    duration.ifBlank { "30 min" },
                                    chosenGoal?.title,
                                    chosenPriority
                                )
                            }
                        },
                        enabled = taskName.isNotBlank()
                    ) {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (taskName.isNotBlank()) Green60 else NexoraBorder,
                            fontWeight = FontWeight.SemiBold
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // TITLE INPUT — large, immersive
            TextField(
                value = taskName,
                onValueChange = { taskName = it },
                placeholder = {
                    Text(
                        "What needs to be done?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NexoraBorder
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Green10),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Green60,
                    unfocusedIndicatorColor = NexoraBorder
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // OPTIONS
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // CATEGORY
                PremiumFormField(label = "Category") {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Work, Academic, Personal…", style = MaterialTheme.typography.bodyMedium) },
                        shape = NexoraShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Green60,
                            unfocusedBorderColor = NexoraBorder
                        )
                    )
                }

                // DURATION & PRIORITY
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        PremiumFormField(label = "Duration") {
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { duration = it },
                                placeholder = { Text("30 min", style = MaterialTheme.typography.bodyMedium) },
                                shape = NexoraShapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Green60,
                                    unfocusedBorderColor = NexoraBorder
                                )
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PremiumFormField(label = "Priority") {
                            Box {
                                Surface(
                                    onClick = { priorityMenuExpanded = true },
                                    shape = NexoraShapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NexoraBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            chosenPriority.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Green10
                                        )
                                        Icon(Icons.Rounded.ArrowDropDown, null, tint = Green40)
                                    }
                                }
                                DropdownMenu(
                                    expanded = priorityMenuExpanded,
                                    onDismissRequest = { priorityMenuExpanded = false }
                                ) {
                                    TaskPriority.entries.forEach { p ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    p.name.lowercase().replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            onClick = { chosenPriority = p; priorityMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // LINKED GOAL
                PremiumFormField(label = "Contributes to Goal") {
                    Box {
                        Surface(
                            onClick = { if (goals.isNotEmpty()) goalMenuExpanded = true },
                            shape = NexoraShapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexoraBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chosenGoal?.title ?: "None selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (chosenGoal == null) NexoraMutedTextLight else Green10
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null, tint = Green40)
                            }
                        }
                        DropdownMenu(
                            expanded = goalMenuExpanded,
                            onDismissRequest = { goalMenuExpanded = false }
                        ) {
                            goals.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.title, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { chosenGoal = g; goalMenuExpanded = false }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear selection",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NexoraError
                                    )
                                },
                                onClick = { chosenGoal = null; goalMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            // INTELLIGENCE HINT
            Surface(
                shape = NexoraShapes.medium,
                color = Green95,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Green80)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = Green60,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                     Text(
                        text = if (chosenGoal != null) "Contributing to this goal." else "Adding to your personal workload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Green20,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PremiumFormField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NexoraMutedTextLight
        )
        content()
    }
}
