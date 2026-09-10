package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
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
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Green10)
                }
                TextButton(
                    onClick = {
                        if (goalName.isNotBlank()) {
                            onSave(
                                goalName.trim(),
                                category.ifBlank { "Personal" },
                                targetDate.ifBlank { "No date" },
                                existingGoal?.progress ?: 0f
                            )
                        }
                    },
                    enabled = goalName.isNotBlank()
                ) {
                    Text(
                        if (isEditing) "Save" else "Create",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (goalName.isNotBlank()) Green60 else Green80
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
                text = if (isEditing) "Edit Goal" else "New Goal",
                style = MaterialTheme.typography.headlineLarge,
                color = Green10
            )

            // MAIN TITLE INPUT
            TextField(
                value = goalName,
                onValueChange = { goalName = it },
                placeholder = { Text("What's your objective?", style = MaterialTheme.typography.headlineSmall, color = Green80) },
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
                        placeholder = { Text("Career, Growth, Health...") },
                        shape = NexoraShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Green60,
                            unfocusedBorderColor = Gray90
                        )
                    )
                }

                // TARGET DATE
                FormInputField(label = "Target Date") {
                    Surface(
                        onClick = { showDatePicker = true },
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
                                text = targetDate.ifBlank { "Set a deadline" },
                                color = if (targetDate.isBlank()) Green40 else Green10
                            )
                            Icon(Icons.Default.CalendarMonth, null, tint = Green60)
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
                        text = "A clear goal helps Nexora prioritize relevant tasks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green20
                    )
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
                    Text("Select", color = Green60, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
