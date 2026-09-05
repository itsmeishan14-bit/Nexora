package com.example.nexora.uii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val NexoraBackground = Color(0xFFF7F8F4)
private val NexoraInk = Color(0xFF17231C)
private val NexoraGreen = Color(0xFF78A982)
private val NexoraSoftGreen = Color(0xFFE4EFE5)
private val NexoraMuted = Color(0xFF747B75)
private val NexoraBorder = Color(0xFFE1E5E1)

@Composable
fun DailyProgressCalendar(
    progressHistory: List<DailyProgress>
) {

    // --------------------------------------------------------
    // CALENDAR STATE
    // --------------------------------------------------------

    var displayedMonth by remember {
        mutableStateOf(YearMonth.now())
    }

    var selectedDate by remember {
        mutableStateOf(LocalDate.now())
    }

    val progressByDate = progressHistory.associateBy {
        it.date
    }

    // --------------------------------------------------------
    // GLOBAL STATS
    // --------------------------------------------------------

    val currentStreak = calculateCurrentStreak(
        progressHistory = progressHistory
    )

    val longestStreak = calculateLongestStreak(
        progressHistory = progressHistory
    )

    val averageCompletion = calculateAverageCompletion(
        progressHistory = progressHistory
    )

    val perfectDays = progressHistory.count {
        it.isPerfectDay
    }

    // --------------------------------------------------------
    // DISPLAYED MONTH DATA
    // --------------------------------------------------------

    val monthProgress = progressHistory.filter {
        YearMonth.from(it.date) == displayedMonth
    }

    val monthCompletedTasks = monthProgress.sumOf {
        it.tasksCompleted
    }

    val monthPlannedTasks = monthProgress.sumOf {
        it.tasksPlanned
    }

    val monthCompletion = if (monthPlannedTasks == 0) {
        0f
    } else {
        monthCompletedTasks.toFloat() /
                monthPlannedTasks.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoraBackground),
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
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexoraSoftGreen),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Daily Progress",
                    tint = NexoraGreen
                )
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column {

                Text(
                    text = "Daily Progress",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )

                Text(
                    text = "Your consistency over time",
                    fontSize = 14.sp,
                    color = NexoraMuted
                )
            }
        }

        // ====================================================
        // STATS
        // ====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            ProgressStatCard(
                modifier = Modifier.weight(1f),
                icon = "🔥",
                value = "$currentStreak",
                label = "Current streak"
            )

            ProgressStatCard(
                modifier = Modifier.weight(1f),
                icon = "🏆",
                value = "$longestStreak",
                label = "Longest streak"
            )

            ProgressStatCard(
                modifier = Modifier.weight(1f),
                icon = "⭐",
                value = "$perfectDays",
                label = "Perfect days"
            )
        }

        // ====================================================
        // CALENDAR
        // ====================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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

                // --------------------------------------------
                // MONTH NAVIGATION
                // --------------------------------------------

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    IconButton(
                        onClick = {

                            displayedMonth =
                                displayedMonth.minusMonths(1)

                            selectedDate =
                                displayedMonth.atDay(1)
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.ArrowBackIosNew,
                            contentDescription =
                                "Previous month",
                            tint = NexoraInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                displayedMonth.month.name
                                    .lowercase()
                                    .replaceFirstChar {
                                        it.uppercase()
                                    },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexoraInk
                        )

                        Text(
                            text = displayedMonth.year.toString(),
                            fontSize = 13.sp,
                            color = NexoraMuted
                        )
                    }

                    IconButton(
                        onClick = {

                            displayedMonth =
                                displayedMonth.plusMonths(1)

                            selectedDate =
                                displayedMonth.atDay(1)
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.ArrowForwardIos,
                            contentDescription =
                                "Next month",
                            tint = NexoraInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                // --------------------------------------------
                // TODAY BUTTON
                // --------------------------------------------

                if (displayedMonth != YearMonth.now()) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                NexoraSoftGreen
                            )
                            .clickable {

                                displayedMonth =
                                    YearMonth.now()

                                selectedDate =
                                    LocalDate.now()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text = "Return to today",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NexoraInk
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )
                }

                // --------------------------------------------
                // WEEK HEADER
                // --------------------------------------------

                CalendarWeekHeader()

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                // --------------------------------------------
                // CALENDAR GRID
                // --------------------------------------------

                CalendarGrid(
                    month = displayedMonth,
                    progressByDate = progressByDate,
                    selectedDate = selectedDate,
                    onDateSelected = {
                        selectedDate = it
                    }
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                CalendarLegend()
            }
        }

        // ====================================================
        // MONTHLY OVERVIEW
        // ====================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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

                Text(
                    text =
                        "${displayedMonth.month.name
                            .lowercase()
                            .replaceFirstChar {
                                it.uppercase()
                            }} Overview",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexoraInk
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                OverviewRow(
                    label = "Monthly completion",
                    value =
                        "${(monthCompletion * 100).toInt()}%"
                )

                OverviewRow(
                    label = "Tasks completed",
                    value = "$monthCompletedTasks"
                )

                OverviewRow(
                    label = "Tasks planned",
                    value = "$monthPlannedTasks"
                )

                OverviewRow(
                    label = "Average completion",
                    value =
                        "${(averageCompletion * 100).toInt()}%"
                )
            }
        }

        // ====================================================
        // SELECTED DAY
        // ====================================================

        val selectedProgress =
            progressByDate[selectedDate]

        SelectedDayCard(
            date = selectedDate,
            progress = selectedProgress
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )
    }
}

// ============================================================
// STAT CARD
// ============================================================

@Composable
private fun ProgressStatCard(
    modifier: Modifier,
    icon: String,
    value: String,
    label: String
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            NexoraBorder
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = icon,
                fontSize = 20.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NexoraInk
            )

            Text(
                text = label,
                fontSize = 10.sp,
                color = NexoraMuted
            )
        }
    }
}

// ============================================================
// WEEK HEADER
// ============================================================

@Composable
private fun CalendarWeekHeader() {

    val days = listOf(
        "M",
        "T",
        "W",
        "T",
        "F",
        "S",
        "S"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        days.forEach { day ->

            Box(
                modifier = Modifier.width(34.dp),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexoraMuted
                )
            }
        }
    }
}

// ============================================================
// CALENDAR GRID
// ============================================================

@Composable
private fun CalendarGrid(
    month: YearMonth,
    progressByDate: Map<LocalDate, DailyProgress>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {

    val firstDay = month.atDay(1)

    val firstDayOffset = when (firstDay.dayOfWeek) {

        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }

    val totalDays = month.lengthOfMonth()

    val totalCells =
        firstDayOffset + totalDays

    val rows =
        (totalCells + 6) / 7

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        for (row in 0 until rows) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                for (column in 0..6) {

                    val cellIndex =
                        row * 7 + column

                    if (
                        cellIndex < firstDayOffset ||
                        cellIndex >=
                        firstDayOffset + totalDays
                    ) {

                        Box(
                            modifier = Modifier.size(34.dp)
                        )

                    } else {

                        val day =
                            cellIndex -
                                    firstDayOffset +
                                    1

                        val date =
                            month.atDay(day)

                        val progress =
                            progressByDate[date]

                        CalendarDay(
                            date = date,
                            progress = progress,
                            selected =
                                date == selectedDate,
                            onClick = {
                                onDateSelected(date)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// CALENDAR DAY
// ============================================================

@Composable
private fun CalendarDay(
    date: LocalDate,
    progress: DailyProgress?,
    selected: Boolean,
    onClick: () -> Unit
) {

    val completion =
        progress?.completionRate ?: 0f

    val background = when {

        completion >= 1f ->
            NexoraGreen

        completion >= 0.75f ->
            Color(0xFFB8D6BD)

        completion >= 0.50f ->
            Color(0xFFCFE3D2)

        completion >= 0.25f ->
            Color(0xFFE1EEE3)

        completion > 0f ->
            Color(0xFFEEF4EF)

        else ->
            Color(0xFFF1F3F0)
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(
                RoundedCornerShape(9.dp)
            )
            .background(background)
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 11.sp,
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
            color =
                if (completion >= 1f) {
                    Color.White
                } else {
                    NexoraInk
                }
        )
    }
}

// ============================================================
// LEGEND
// ============================================================

@Composable
private fun CalendarLegend() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.End,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = "Less",
            fontSize = 10.sp,
            color = NexoraMuted
        )

        Spacer(
            modifier = Modifier.width(5.dp)
        )

        LegendSquare(
            Color(0xFFF1F3F0)
        )

        LegendSquare(
            Color(0xFFE1EEE3)
        )

        LegendSquare(
            Color(0xFFCFE3D2)
        )

        LegendSquare(
            Color(0xFFB8D6BD)
        )

        LegendSquare(
            NexoraGreen
        )

        Spacer(
            modifier = Modifier.width(5.dp)
        )

        Text(
            text = "More",
            fontSize = 10.sp,
            color = NexoraMuted
        )
    }
}

@Composable
private fun LegendSquare(
    color: Color
) {

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(13.dp)
            .clip(
                RoundedCornerShape(4.dp)
            )
            .background(color)
    )
}

// ============================================================
// SELECTED DAY
// ============================================================

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    progress: DailyProgress?
) {

    val completion =
        progress?.completionRate ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = NexoraInk
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = formatDate(date),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            if (progress == null) {

                Text(
                    text =
                        "No activity recorded for this day yet.",
                    fontSize = 14.sp,
                    color =
                        Color(0xFFB8C1BA)
                )

            } else {

                Text(
                    text =
                        "${progress.tasksCompleted} " +
                                "of " +
                                "${progress.tasksPlanned} " +
                                "tasks completed",
                    fontSize = 15.sp,
                    color = Color.White
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text =
                        "${(completion * 100).toInt()}% " +
                                "daily progress",
                    fontSize = 13.sp,
                    color =
                        Color(0xFFB8C1BA)
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                if (progress.isPerfectDay) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Star,
                            contentDescription =
                                "Perfect day",
                            tint = Color.White,
                            modifier =
                                Modifier.size(17.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text(
                            text = "Perfect day",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// OVERVIEW ROW
// ============================================================

@Composable
private fun OverviewRow(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            fontSize = 14.sp,
            color = NexoraMuted
        )

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = NexoraInk
        )
    }
}

// ============================================================
// DATE FORMAT
// ============================================================

private fun formatDate(
    date: LocalDate
): String {

    val month =
        date.month.name
            .lowercase()
            .replaceFirstChar {
                it.uppercase()
            }

    return "$month ${date.dayOfMonth}, ${date.year}"
}