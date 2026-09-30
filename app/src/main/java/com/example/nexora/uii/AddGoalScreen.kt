package com.example.nexora.uii

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    var progress by remember { mutableFloatStateOf(existingGoal?.progress ?: 0f) }
    var showDatePicker by remember { mutableStateOf(false) }

    val isEditing = existingGoal != null

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
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Green40)
                    }
                    Text(
                        text = if (isEditing) "Edit Goal" else "New Goal",
                        style = MaterialTheme.typography.titleSmall,
                        color = Green10
                    )
                    TextButton(
                        onClick = {
                            if (goalName.isNotBlank()) {
                                onSave(
                                    goalName.trim(),
                                    category.ifBlank { "Personal" },
                                    targetDate.ifBlank { "No date" },
                                    progress
                                )
                            }
                        },
                        enabled = goalName.isNotBlank()
                    ) {
                        Text(
                            if (isEditing) "Save" else "Create",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (goalName.isNotBlank()) Green60 else NexoraBorder,
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
            // TITLE INPUT — immersive, large
            TextField(
                value = goalName,
                onValueChange = { goalName = it },
                placeholder = {
                    Text(
                        "What's your objective?",
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

            // FORM FIELDS
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // CATEGORY
                GoalFormField(label = "Category") {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Career, Growth, Health…", style = MaterialTheme.typography.bodyMedium) },
                        shape = NexoraShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Green60,
                            unfocusedBorderColor = NexoraBorder
                        )
                    )
                }

                // TARGET DATE
                GoalFormField(label = "Target Date") {
                    Surface(
                        onClick = { showDatePicker = true },
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
                                text = targetDate.ifBlank { "Set a deadline" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (targetDate.isBlank()) NexoraMutedTextLight else Green10
                            )
                            Icon(Icons.Default.CalendarMonth, null, tint = Green60, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // PROGRESS
                GoalFormField(label = "Starting progress  ${(progress * 100).toInt()}%") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Slider(
                            value = progress,
                            onValueChange = { progress = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Green60,
                                activeTrackColor = Green60,
                                inactiveTrackColor = NexoraBorder
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { pct ->
                                val isSelected = kotlin.math.abs(progress - pct) < 0.05f
                                Surface(
                                    onClick = { progress = pct },
                                    shape = CircleShape,
                                    color = if (isSelected) Green60 else Green95,
                                    contentColor = if (isSelected) Color.White else Green40
                                ) {
                                    Text(
                                        text = "${(pct * 100).toInt()}%",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
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
                        text = "A clear goal helps Nexora prioritize relevant tasks for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Green20,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // DATE PICKER DIALOG
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
private fun GoalFormField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NexoraMutedTextLight
        )
        content()
    }
}
