package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFFF7F8F4)
private val Ink = Color(0xFF17231C)
private val Muted = Color(0xFF747B75)
private val Green = Color(0xFF78A982)
private val SoftGreen = Color(0xFFE4EFE5)
private val Border = Color(0xFFE1E5E1)

data class NexoraGoal(
    val id: Long = 0,
    val title: String,
    val category: String,
    val targetDate: String,
    val progress: Float
)

@Composable
fun GoalScreen(
    goals: SnapshotStateList<NexoraGoal>,
    onAddGoal: () -> Unit,
    onEditGoal: (NexoraGoal) -> Unit,
    onOpenGoal: (NexoraGoal) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 22.dp,
                top = 26.dp,
                end = 22.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ============================================================
            // HEADER
            // ============================================================

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "Goals",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Turn intentions into progress.",
                            fontSize = 15.sp,
                            color = Muted
                        )
                    }

                    IconButton(
                        onClick = onAddGoal
                    ) {

                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add goal",
                            tint = Ink
                        )
                    }
                }
            }

            // ============================================================
            // ACTIVE GOALS CARD
            // ============================================================

            item {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Ink
                    )
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFF344039)
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Green,
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        Spacer(
                            modifier = Modifier.size(15.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = "ACTIVE GOALS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                                color = Color(0xFFB8C1BA)
                            )

                            Spacer(
                                modifier = Modifier.height(5.dp)
                            )

                            Text(
                                text = "${goals.size} goals in progress",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ============================================================
            // SECTION TITLE
            // ============================================================

            item {

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "Your goals",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            // ============================================================
            // GOAL LIST
            // ============================================================

            if (goals.isEmpty()) {

                item {
                    EmptyGoalsCard()
                }

            } else {

                itemsIndexed(
                    items = goals,

                    // IMPORTANT:
                    // Use the Room-generated goal ID.
                    // Never use title/category/date as the key.
                    key = { index, goal ->

                        if (goal.id != 0L) {
                            "goal_${goal.id}"
                        } else {
                            "temporary_goal_$index"
                        }
                    }

                ) { _, goal ->

                    GoalCard(
                        goal = goal,
                        onOpen = {
                            onOpenGoal(goal)
                        }
                    )
                }
            }
        }

        // ================================================================
        // ADD BUTTON
        // ================================================================

        FloatingActionButton(
            onClick = onAddGoal,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp),
            containerColor = Ink,
            contentColor = Color.White
        ) {

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add goal"
            )
        }
    }
}

// ========================================================================
// GOAL CARD
// ========================================================================

@Composable
private fun GoalCard(
    goal: NexoraGoal,
    onOpen: () -> Unit
) {

    val percentage =
        (goal.progress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onOpen()
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.size(14.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = goal.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "${goal.category} • ${goal.targetDate}",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }

                Text(
                    text = "$percentage%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = Green,
                trackColor = Border
            )

            Spacer(
                modifier = Modifier.height(13.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = if (percentage == 100) {
                        "Goal completed"
                    } else {
                        "Tap to view goal"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Muted,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFB0B6B1),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// ========================================================================
// EMPTY GOALS CARD
// ========================================================================

@Composable
private fun EmptyGoalsCard() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(SoftGreen),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "No goals yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = "Create a goal and start making progress.",
                fontSize = 13.sp,
                color = Muted
            )
        }
    }
}