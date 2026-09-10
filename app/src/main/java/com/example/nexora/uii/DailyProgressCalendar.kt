package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ui.theme.*
import java.time.LocalDate

@Composable
fun DailyProgressCalendar(progressHistory: List<DailyProgress>) {
    val weeks = remember(progressHistory) { getContributionData(progressHistory) }
    val progressMap = remember(progressHistory) { progressHistory.associateBy { it.date } }
    val scrollState = rememberScrollState()

    // Ensure we start at the most recent days (end of scroll)
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    NexoraCard {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Consistency",
                style = MaterialTheme.typography.titleMedium,
                color = NexoraPrimaryTextLight
            )
            
            Spacer(Modifier.height(20.dp))

            // THE SCROLLABLE HEATMAP GRID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            ContributionSquare(date, progressMap[date])
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // LEGEND
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = Green40)
                Spacer(Modifier.width(8.dp))
                listOf(Gray95, Green90, Green80, Green60, Green40).forEach { color ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("More", style = MaterialTheme.typography.labelSmall, color = Green40)
            }
        }
    }
}

@Composable
private fun ContributionSquare(date: LocalDate?, progress: DailyProgress?) {
    val rate = progress?.completionRate ?: 0f
    
    val color = when {
        date == null -> Color.Transparent
        date.isAfter(LocalDate.now()) -> Color.Transparent
        rate >= 1.0f -> Green40
        rate >= 0.7f -> Green60
        rate >= 0.4f -> Green80
        rate > 0f -> Green95
        else -> Gray95
    }

    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

/**
 * Generates data for the GitHub-style heatmap.
 * Returns a list of 15 weeks, each containing 7 days.
 */
private fun getContributionData(history: List<DailyProgress>): List<List<LocalDate?>> {
    val today = LocalDate.now()
    val endDate = today
    // Show approx 15 weeks
    val startDate = today.minusWeeks(14).minusDays(today.dayOfWeek.value.toLong() - 1)
    
    val weeks = mutableListOf<List<LocalDate?>>()
    var current = startDate
    
    repeat(15) {
        val week = mutableListOf<LocalDate?>()
        repeat(7) {
            if (current.isAfter(endDate)) {
                week.add(null)
            } else {
                week.add(current)
            }
            current = current.plusDays(1)
        }
        weeks.add(week)
    }
    return weeks
}
