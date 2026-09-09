package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nexora.ui.theme.*

@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    goals: List<NexoraGoal>,
    selectedGoal: NexoraGoal? = null,
    onSave: (
        String,
        String,
        String,
        String?,
        TaskPriority
    ) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }

    var chosenGoal by remember { mutableStateOf(selectedGoal) }
    var goalMenuExpanded by remember { mutableStateOf(false) }

    var chosenPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var priorityMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NexoraBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = NexoraPrimaryText)
                }
                TextButton(
                    onClick = {
                        if (taskName.isNotBlank()) {
                            onSave(
                                taskName.trim(),
                                category.trim().ifBlank { "Personal" },
                                duration.trim().ifBlank { "30 min" },
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
                        color = if (taskName.isNotBlank()) NexoraPrimaryGreen else NexoraMutedText
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "New Task",
                style = MaterialTheme.typography.headlineLarge,
                color = NexoraPrimaryText
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // TITLE INPUT
            TextField(
                value = taskName,
                onValueChange = { taskName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What needs to be done?", style = MaterialTheme.typography.headlineSmall, color = NexoraMutedText) },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = NexoraPrimaryGreen,
                    unfocusedIndicatorColor = NexoraBorder
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(40.dp))

            // OPTIONS GRID
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // CATEGORY
                InputFieldLabel("Category")
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Work, Health") },
                    shape = NexoraShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexoraPrimaryGreen,
                        unfocusedBorderColor = NexoraBorder
                    )
                )

                // DURATION & PRIORITY
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        InputFieldLabel("Duration")
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            placeholder = { Text("30 min") },
                            shape = NexoraShapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NexoraPrimaryGreen,
                                unfocusedBorderColor = NexoraBorder
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        InputFieldLabel("Priority")
                        Box {
                            OutlinedCard(
                                onClick = { priorityMenuExpanded = true },
                                shape = NexoraShapes.medium,
                                border = BorderStroke(1.dp, NexoraBorder),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chosenPriority.name.lowercase().capitalize())
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = priorityMenuExpanded, onDismissRequest = { priorityMenuExpanded = false }) {
                                TaskPriority.values().forEach { priority ->
                                    DropdownMenuItem(
                                        text = { Text(priority.name.lowercase().capitalize()) },
                                        onClick = {
                                            chosenPriority = priority
                                            priorityMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // GOAL SELECTION
                InputFieldLabel("Goal")
                Box {
                    OutlinedCard(
                        onClick = { if (goals.isNotEmpty()) goalMenuExpanded = true },
                        shape = NexoraShapes.medium,
                        border = BorderStroke(1.dp, NexoraBorder),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                chosenGoal?.title ?: "Select a goal",
                                color = if (chosenGoal == null) NexoraMutedText else NexoraPrimaryText
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = goalMenuExpanded, onDismissRequest = { goalMenuExpanded = false }) {
                        goals.forEach { goal ->
                            DropdownMenuItem(
                                text = { Text(goal.title) },
                                onClick = {
                                    chosenGoal = goal
                                    goalMenuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("No goal", color = NexoraError) },
                            onClick = {
                                chosenGoal = null
                                goalMenuExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun InputFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = NexoraMutedText,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
