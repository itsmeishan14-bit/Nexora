package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ai.AiRecommendation
import com.example.nexora.ai.AiRecommendationType
import com.example.nexora.ai.NexoraAiEngine
import com.example.nexora.ai.NexoraAiViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider

private val NexoraBackground = Color(0xFFF7F8F4)
private val NexoraInk = Color(0xFF17231C)
private val NexoraGreen = Color(0xFF78A982)
private val NexoraSoftGreen = Color(0xFFE4EFE5)
private val NexoraMuted = Color(0xFF747B75)
private val NexoraBorder = Color(0xFFE1E5E1)

@Composable
fun AiScreen(
    engine: NexoraAiEngine
) {

    val viewModel: NexoraAiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel>
                    create(modelClass: Class<T>): T {

                return NexoraAiViewModel(
                    engine = engine
                ) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoraBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ====================================================
        // HEADER
        // ====================================================

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(NexoraSoftGreen),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Nexora AI",
                    tint = NexoraGreen
                )
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column {

                Text(
                    text = "Nexora Intelligence",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )

                Text(
                    text = "Understand. Prioritize. Improve.",
                    fontSize = 14.sp,
                    color = NexoraMuted
                )
            }
        }

        // ====================================================
        // INTRO
        // ====================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = NexoraInk
            )
        ) {

            Column(
                modifier = Modifier.padding(22.dp)
            ) {

                Text(
                    text = "Your personal intelligence layer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Nexora analyzes your tasks, goals and progress to help you decide what deserves your attention.",
                    fontSize = 14.sp,
                    color = Color(0xFFB8C1BA),
                    lineHeight = 21.sp
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Button(
                        onClick = {
                            viewModel.analyze()
                        },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexoraGreen,
                            contentColor = NexoraInk
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(7.dp)
                        )

                        Text(
                            text = "Analyze",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.createDailyPlan()
                        },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = NexoraInk
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(7.dp)
                        )

                        Text(
                            text = "Daily plan",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ====================================================
        // LOADING
        // ====================================================

        if (uiState.isLoading) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    NexoraBorder
                )
            ) {

                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NexoraGreen,
                        strokeWidth = 3.dp
                    )

                    Spacer(
                        modifier = Modifier.width(14.dp)
                    )

                    Text(
                        text = "Nexora is analyzing your data...",
                        fontSize = 14.sp,
                        color = NexoraMuted
                    )
                }
            }
        }

        // ====================================================
        // ERROR
        // ====================================================

        uiState.error?.let { error ->

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF4F2)
                ),
                border = BorderStroke(
                    1.dp,
                    Color(0xFFE8D3CF)
                )
            ) {

                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF9A5B50)
                    )

                    Spacer(
                        modifier = Modifier.width(12.dp)
                    )

                    Text(
                        text = error,
                        fontSize = 14.sp,
                        color = NexoraInk
                    )
                }
            }
        }

        // ====================================================
        // AI RESULTS
        // ====================================================

        if (
            !uiState.isLoading &&
            uiState.recommendations.isNotEmpty()
        ) {

            Text(
                text = "Nexora's thinking",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = NexoraInk
            )

            uiState.recommendations.forEach { recommendation ->

                AiRecommendationCard(
                    recommendation = recommendation
                )
            }
        }

        // ====================================================
        // EMPTY STATE
        // ====================================================

        if (
            !uiState.isLoading &&
            uiState.error == null &&
            uiState.recommendations.isEmpty()
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    NexoraBorder
                )
            ) {

                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(30.dp)
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text = "Ready when you are",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInk
                    )

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = "Add some tasks or goals, then ask Nexora to analyze your current system.",
                        fontSize = 14.sp,
                        color = NexoraMuted,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// RECOMMENDATION CARD
// ============================================================

@Composable
private fun AiRecommendationCard(
    recommendation: AiRecommendation
) {

    val icon = when (recommendation.type) {

        AiRecommendationType.NEXT_TASK ->
            Icons.Default.TaskAlt

        AiRecommendationType.GOAL_ACTION ->
            Icons.Default.Lightbulb

        AiRecommendationType.WARNING ->
            Icons.Default.Warning

        else ->
            Icons.Default.AutoAwesome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            NexoraBorder
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoraSoftGreen),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexoraGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Text(
                    text = recommendation.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = recommendation.message,
                fontSize = 14.sp,
                color = NexoraMuted,
                lineHeight = 21.sp
            )

            recommendation.actionLabel?.let { action ->

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    text = action,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexoraGreen
                )
            }
        }
    }
}