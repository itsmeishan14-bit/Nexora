package com.example.nexora.uii

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddGoalScreen(
    onBack: () -> Unit,
    onSave: (String, String, String, Float) -> Unit,
    existingGoal: NexoraGoal? = null
) {
    var goalName by remember { mutableStateOf(existingGoal?.title ?: "") }
    var category by remember { mutableStateOf(existingGoal?.category ?: "") }
    var targetDate by remember { mutableStateOf(existingGoal?.targetDate ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val isEditing = existingGoal != null

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
                        if (goalName.isNotBlank()) {
                            onSave(
                                goalName.trim(),
                                category.trim().ifBlank { "Personal" },
                                targetDate.trim().ifBlank { "No date" },
                                existingGoal?.progress ?: 0f
                            )
                        }
                    },
                    enabled = goalName.isNotBlank()
                ) {
                    Text(
                        if (isEditing) "Save" else "Create",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (goalName.isNotBlank()) NexoraPrimaryGreen else NexoraMutedText
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
                text = if (isEditing) "Edit Goal" else "New Goal",
                style = MaterialTheme.typography.headlineLarge,
                color = NexoraPrimaryText
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // TITLE INPUT
            TextField(
                value = goalName,
                onValueChange = { goalName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What's your objective?", style = MaterialTheme.typography.headlineSmall, color = NexoraMutedText) },
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

            // OPTIONS
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // CATEGORY
                InputFieldLabel("Category")
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Career, Growth") },
                    shape = NexoraShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexoraPrimaryGreen,
                        unfocusedBorderColor = NexoraBorder
                    )
                )

                // TARGET DATE
                InputFieldLabel("Target Date")
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    shape = NexoraShapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexoraBorder),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            targetDate.ifBlank { "Set a deadline" },
                            color = if (targetDate.isBlank()) NexoraMutedText else NexoraPrimaryText
                        )
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NexoraPrimaryGreen)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            targetDate = formatter.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = NexoraPrimaryGreen, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
