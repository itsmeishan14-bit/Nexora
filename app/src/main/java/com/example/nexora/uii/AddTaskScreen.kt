package com.example.nexora.uii

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

    Scaffold(
        containerColor = NexoraBackgroundLight,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Green10)
                }
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
                        color = if (taskName.isNotBlank()) Green60 else Green80
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "New Task",
                style = MaterialTheme.typography.headlineLarge,
                color = Green10
            )

            // MAIN TITLE INPUT
            TextField(
                value = taskName,
                onValueChange = { taskName = it },
                placeholder = { Text("What needs to be done?", style = MaterialTheme.typography.headlineSmall, color = Green80) },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Green60,
                    unfocusedIndicatorColor = Gray90
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // OPTIONS
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // CATEGORY
                FormInputField(label = "Category") {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Work, Academic, Personal...") },
                        shape = NexoraShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Green60,
                            unfocusedBorderColor = Gray90
                        )
                    )
                }

                // DURATION & PRIORITY
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormInputField(label = "Duration") {
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { duration = it },
                                placeholder = { Text("30 min") },
                                shape = NexoraShapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Green60,
                                    unfocusedBorderColor = Gray90
                                )
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormInputField(label = "Priority") {
                            Box {
                                Surface(
                                    onClick = { priorityMenuExpanded = true },
                                    shape = NexoraShapes.medium,
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Gray90),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(chosenPriority.name.lowercase().replaceFirstChar { it.uppercase() })
                                        Icon(Icons.Rounded.ArrowDropDown, null)
                                    }
                                }
                                DropdownMenu(expanded = priorityMenuExpanded, onDismissRequest = { priorityMenuExpanded = false }) {
                                    TaskPriority.entries.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                            onClick = { chosenPriority = p; priorityMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // GOAL
                FormInputField(label = "Contributes to Goal") {
                    Box {
                        Surface(
                            onClick = { if (goals.isNotEmpty()) goalMenuExpanded = true },
                            shape = NexoraShapes.medium,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gray90),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chosenGoal?.title ?: "No Goal selected",
                                    color = if (chosenGoal == null) Green40 else Green10
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(expanded = goalMenuExpanded, onDismissRequest = { goalMenuExpanded = false }) {
                            goals.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.title) },
                                    onClick = { chosenGoal = g; goalMenuExpanded = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Clear Selection", color = NexoraError) },
                                onClick = { chosenGoal = null; goalMenuExpanded = false }
                            )
                        }
                    }
                }
            }
            
            // INTELLIGENCE PREVIEW
            NexoraCard(containerColor = Green95) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.Rounded.AutoAwesome, Green60, Color.White, 24)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (chosenGoal != null) "Contributing to goal progress." else "Adding to your personal workload.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green20
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FormInputField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Green40)
        content()
    }
}
