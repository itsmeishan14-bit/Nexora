package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyProgressCalendar(
    progressHistory: List<DailyProgress>
) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val progressByDate = remember(progressHistory) { progressHistory.associateBy { it.date } }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoraPrimaryText
                )
                Text(
                    text = displayedMonth.year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraMutedText
                )
            }
            Row {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        // CALENDAR GRID
        NexoraCard {
            Column(modifier = Modifier.padding(16.dp)) {
                CalendarWeekHeader()
                Spacer(modifier = Modifier.height(12.dp))
                CalendarGrid(
                    month = displayedMonth,
                    progressByDate = progressByDate,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it }
                )
            }
        }

        // SELECTED DAY DETAIL
        val selectedProgress = progressByDate[selectedDate]
        SelectedDaySummary(date = selectedDate, progress = selectedProgress)
    }
}

@Composable
private fun CalendarWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = NexoraMutedText,
                modifier = Modifier.width(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    progressByDate: Map<LocalDate, DailyProgress>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    val firstDayOffset = (firstDay.dayOfWeek.value - 1) % 7
    val totalDays = month.lengthOfMonth()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var currentDay = 1
        for (row in 0..5) {
            if (currentDay > totalDays) break
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (col in 0..6) {
                    val dayIndex = row * 7 + col
                    if (dayIndex < firstDayOffset || currentDay > totalDays) {
                        Spacer(modifier = Modifier.size(32.dp))
                    } else {
                        val date = month.atDay(currentDay)
                        CalendarDayCell(
                            date = date,
                            progress = progressByDate[date],
                            isSelected = date == selectedDate,
                            onClick = { onDateSelected(date) }
                        )
                        currentDay++
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    progress: DailyProgress?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val completion = progress?.completionRate ?: 0f
    val bgColor = when {
        isSelected -> NexoraPrimaryText
        completion >= 1f -> NexoraPrimaryGreen
        completion > 0f -> NexoraSoftGreen
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected || completion >= 1f -> Color.White
        else -> NexoraPrimaryText
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SelectedDaySummary(date: LocalDate, progress: DailyProgress?) {
    NexoraCard(containerColor = NexoraSoftGreen, border = null) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraPrimaryGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (progress != null) "${progress.tasksCompleted} tasks completed" else "No activity recorded",
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraPrimaryText
                )
            }
            if (progress?.isPerfectDay == true) {
                Icon(Icons.Default.Star, contentDescription = null, tint = NexoraPrimaryGreen)
            }
        }
    }
}
