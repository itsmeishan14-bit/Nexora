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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun AddGoalScreen(
    onBack: () -> Unit,
    onSave: (String, String, String, Float) -> Unit,
    existingGoal: NexoraGoal? = null
) {

    var goalName by remember {
        mutableStateOf(existingGoal?.title ?: "")
    }

    var category by remember {
        mutableStateOf(existingGoal?.category ?: "")
    }

    var targetDate by remember {
        mutableStateOf(existingGoal?.targetDate ?: "")
    }

    val isEditing = existingGoal != null

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F8F4)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = if (isEditing) "Edit goal" else "New goal",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17231C)
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = if (isEditing)
                            "Refine your goal details."
                        else
                            "What do you want to achieve?",
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

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Goal name",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = goalName,
                onValueChange = {
                    goalName = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. Master Java")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Category",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = category,
                onValueChange = {
                    category = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. Academic")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Target date",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303630)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = targetDate,
                onValueChange = {
                    targetDate = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("e.g. September 30")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFE4EFE5)
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = if (isEditing)
                            "Keep moving forward"
                        else
                            "Start with clarity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17231C)
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = if (isEditing)
                            "Small improvements keep your goals moving in the right direction."
                        else
                            "A clear goal gives Nexora something meaningful to help you work toward.",
                        fontSize = 13.sp,
                        color = Color(0xFF747B75),
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {

                    if (goalName.isNotBlank()) {

                        onSave(
                            goalName.trim(),

                            if (category.isBlank())
                                "Personal"
                            else
                                category.trim(),

                            if (targetDate.isBlank())
                                "No date"
                            else
                                targetDate.trim(),

                            existingGoal?.progress ?: 0f
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

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isEditing)
                        "Save changes"
                    else
                        "Create goal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}