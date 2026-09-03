package com.example.nexora.uii

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    var chosenGoal by remember {
        mutableStateOf(selectedGoal)
    }

    var goalMenuExpanded by remember {
        mutableStateOf(false)
    }

    var chosenPriority by remember {
        mutableStateOf(TaskPriority.MEDIUM)
    }

    var priorityMenuExpanded by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F8F4)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "New task",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17231C)
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = if (selectedGoal != null) {
                            "Add a task to ${selectedGoal.title}"
                        } else {
                            "What needs to get done?"
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF747B75)
                    )
                }

                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF17231C)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            // TASK NAME
            Text(
                text = "Task name",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = taskName,
                onValueChange = { taskName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. Complete Java assignment")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // CATEGORY
            Text(
                text = "Category",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. Academic")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // DURATION
            Text(
                text = "Duration",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. 30 minutes")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // PRIORITY
            Text(
                text = "Priority",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Column {

                OutlinedButton(
                    onClick = {
                        priorityMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {

                    Text(
                        text = when (chosenPriority) {
                            TaskPriority.URGENT -> "Urgent"
                            TaskPriority.HIGH -> "High"
                            TaskPriority.MEDIUM -> "Medium"
                            TaskPriority.LOW -> "Low"
                        },
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF17231C)
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select priority",
                        tint = Color(0xFF17231C)
                    )
                }

                DropdownMenu(
                    expanded = priorityMenuExpanded,
                    onDismissRequest = {
                        priorityMenuExpanded = false
                    }
                ) {

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Urgent",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            chosenPriority = TaskPriority.URGENT
                            priorityMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "High",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            chosenPriority = TaskPriority.HIGH
                            priorityMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Medium")
                        },
                        onClick = {
                            chosenPriority = TaskPriority.MEDIUM
                            priorityMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Low")
                        },
                        onClick = {
                            chosenPriority = TaskPriority.LOW
                            priorityMenuExpanded = false
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // GOAL
            Text(
                text = "Goal",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Column {

                OutlinedButton(
                    onClick = {
                        if (goals.isNotEmpty()) {
                            goalMenuExpanded = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {

                    Text(
                        text = chosenGoal?.title
                            ?: if (goals.isEmpty()) {
                                "No goals available"
                            } else {
                                "Select a goal"
                            },
                        modifier = Modifier.weight(1f),
                        color = if (chosenGoal == null) {
                            Color(0xFF747B75)
                        } else {
                            Color(0xFF17231C)
                        }
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select goal",
                        tint = Color(0xFF17231C)
                    )
                }

                DropdownMenu(
                    expanded = goalMenuExpanded,
                    onDismissRequest = {
                        goalMenuExpanded = false
                    }
                ) {

                    goals.forEach { goal ->

                        DropdownMenuItem(
                            text = {

                                Column {

                                    Text(
                                        text = goal.title,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        text = goal.category,
                                        fontSize = 12.sp,
                                        color = Color(0xFF747B75)
                                    )
                                }
                            },
                            onClick = {

                                chosenGoal = goal
                                goalMenuExpanded = false
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No goal",
                                color = Color(0xFF747B75)
                            )
                        },
                        onClick = {

                            chosenGoal = null
                            goalMenuExpanded = false
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            // LINKED GOAL INFO
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFE4EFE5)
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = if (chosenGoal != null) {
                            "Linked to ${chosenGoal!!.title}"
                        } else {
                            "Make it actionable"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17231C)
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = if (chosenGoal != null) {
                            "This task will contribute to the progress of this goal."
                        } else {
                            "Linking a task to a goal helps Nexora understand what your work is contributing toward."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF747B75),
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            // SAVE
            Button(
                onClick = {

                    if (taskName.isNotBlank()) {

                        onSave(
                            taskName.trim(),
                            if (category.isBlank()) {
                                "Personal"
                            } else {
                                category.trim()
                            },
                            if (duration.isBlank()) {
                                "No duration"
                            } else {
                                duration.trim()
                            },
                            chosenGoal?.title,
                            chosenPriority
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF17231C),
                    contentColor = Color.White
                )
            ) {

                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Save task",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }
    }
}