package com.example.nexora.uii

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexora.ui.theme.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ============================================================
// ADAPTIVE ACTIVITY INTENSITY RAMP
// ============================================================

enum class DailyActivityLevel {
    NONE, LOW, MEDIUM, HIGH
}

/**
 * Calculates activity intensity level from real DailyProgress data.
 */
fun calculateActivityLevel(progress: DailyProgress?): DailyActivityLevel {
    if (progress == null) return DailyActivityLevel.NONE
    if (progress.tasksCompleted == 0 && progress.focusMinutes == 0 && progress.goalsWorkedOn == 0) {
        return DailyActivityLevel.NONE
    }

    val tasks = progress.tasksCompleted
    val focus = progress.focusMinutes
    val goals = progress.goalsWorkedOn
    val rate = progress.completionRate

    return when {
        tasks >= 4 || focus >= 60 || (tasks >= 2 && rate >= 0.8f) -> DailyActivityLevel.HIGH
        tasks >= 2 || focus >= 25 || rate >= 0.5f || goals >= 2 -> DailyActivityLevel.MEDIUM
        tasks >= 1 || focus > 0 || goals > 0 -> DailyActivityLevel.LOW
        else -> DailyActivityLevel.NONE
    }
}

/**
 * Generates calendar weeks (approx. last 6–8 months = 32 weeks) aligned to Monday–Sunday.
 */
fun getContributionData(
    weeksCount: Int = 32,
    today: LocalDate = LocalDate.now()
): List<List<LocalDate?>> {
    val currentWeekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startMonday = currentWeekMonday.minusWeeks((weeksCount - 1).toLong())

    val weeks = mutableListOf<List<LocalDate?>>()
    for (w in 0 until weeksCount) {
        val weekMonday = startMonday.plusWeeks(w.toLong())
        val week = (0..6).map { dayIndex ->
            val date = weekMonday.plusDays(dayIndex.toLong())
            if (date.isAfter(today)) null else date
        }
        weeks.add(week)
    }
    return weeks
}

/**
 * Backwards compatible overload for getContributionData taking history list.
 */
fun getContributionData(
    history: List<DailyProgress>,
    weeksCount: Int = 32,
    today: LocalDate = LocalDate.now()
): List<List<LocalDate?>> = getContributionData(weeksCount, today)

/**
 * Generates dynamic month labels based on the actual dates in each week column.
 */
fun getMonthLabels(weeks: List<List<LocalDate?>>): Map<Int, String> {
    val labels = mutableMapOf<Int, String>()
    var lastLabelWeek = -10

    val monthStartWeeks = mutableListOf<Pair<Int, String>>()
    weeks.forEachIndexed { weekIndex, week ->
        val firstDayOfMonth = week.filterNotNull().firstOrNull { it.dayOfMonth == 1 }
        if (firstDayOfMonth != null) {
            val monthName = firstDayOfMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            monthStartWeeks.add(weekIndex to monthName)
        }
    }

    val firstMonthStartWeek = monthStartWeeks.firstOrNull()?.first ?: weeks.size
    if (firstMonthStartWeek >= 3) {
        val firstDate = weeks.firstOrNull()?.filterNotNull()?.firstOrNull()
        if (firstDate != null) {
            val monthName = firstDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            labels[0] = monthName
            lastLabelWeek = 0
        }
    }

    for ((weekIndex, monthName) in monthStartWeeks) {
        if (weekIndex - lastLabelWeek >= 3 && weekIndex < weeks.size - 1) {
            labels[weekIndex] = monthName
            lastLabelWeek = weekIndex
        }
    }

    return labels
}

@Composable
fun DailyProgressCalendar(
    progressHistory: List<DailyProgress>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val weeks = remember(today) { getContributionData(weeksCount = 32, today = today) }
    val monthLabels = remember(weeks) { getMonthLabels(weeks) }
    val progressMap = remember(progressHistory) { progressHistory.associateBy { it.date } }

    val scrollState = rememberScrollState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val squareSize = 13.dp
    val squareSpacing = 3.dp
    val headerHeight = 16.dp

    // Scroll to the latest dates once layout is measured
    LaunchedEffect(weeks) {
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
            .let { max ->
                scrollState.scrollTo(max)
            }
    }

    NexoraCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Consistency",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            // Calendar Heatmap Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Weekday Labels on the left
                Column(
                    modifier = Modifier.width(26.dp),
                    verticalArrangement = Arrangement.spacedBy(squareSpacing)
                ) {
                    Spacer(Modifier.height(headerHeight))
                    WeekdayLabel("Mon", squareSize)
                    Spacer(Modifier.height(squareSize)) // Tue
                    WeekdayLabel("Wed", squareSize)
                    Spacer(Modifier.height(squareSize)) // Thu
                    WeekdayLabel("Fri", squareSize)
                    Spacer(Modifier.height(squareSize)) // Sat
                    Spacer(Modifier.height(squareSize)) // Sun
                }

                Spacer(Modifier.width(4.dp))

                // Scrollable heatmap grid with dynamic month labels
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(squareSpacing)
                ) {
                    weeks.forEachIndexed { weekIndex, week ->
                        Column(
                            modifier = Modifier.width(squareSize),
                            verticalArrangement = Arrangement.spacedBy(squareSpacing)
                        ) {
                            // Month label across top
                            Box(
                                modifier = Modifier
                                    .height(headerHeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                val monthLabel = monthLabels[weekIndex]
                                if (monthLabel != null) {
                                    Text(
                                        text = monthLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.wrapContentSize(
                                            align = Alignment.BottomStart,
                                            unbounded = true
                                        )
                                    )
                                }
                            }

                            // 7 day squares for this week (Mon = 0 ... Sun = 6)
                            week.forEach { date ->
                                ContributionSquare(
                                    date = date,
                                    progress = date?.let { progressMap[it] },
                                    today = today,
                                    squareSize = squareSize,
                                    onSquareClick = { clickedDate ->
                                        selectedDate = clickedDate
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend: Less [none] [low] [med] [high] More
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val legendColors = getHeatmapColors(isDark)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(6.dp))
                LegendSquare(bgColor = legendColors.noneBg, borderColor = legendColors.noneBorder)
                Spacer(Modifier.width(3.dp))
                LegendSquare(bgColor = legendColors.lowBg, borderColor = legendColors.lowBorder)
                Spacer(Modifier.width(3.dp))
                LegendSquare(bgColor = legendColors.medBg, borderColor = legendColors.medBorder)
                Spacer(Modifier.width(3.dp))
                LegendSquare(bgColor = legendColors.highBg, borderColor = legendColors.highBorder)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // Day Details Dialog
    if (selectedDate != null) {
        val date = selectedDate!!
        val progress = progressMap[date]
        val isToday = date == today
        val hasData = progress != null && (
            progress.tasksPlanned > 0 ||
            progress.tasksCompleted > 0 ||
            progress.focusMinutes > 0 ||
            progress.goalsWorkedOn > 0 ||
            progress.carriedTasks > 0
        )

        AlertDialog(
            onDismissRequest = { selectedDate = null },
            confirmButton = {
                TextButton(onClick = { selectedDate = null }) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            title = {
                Column {
                    if (isToday) {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                if (!hasData) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "No activity recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContributionDetailRow(
                            label = "Tasks completed",
                            value = "${progress!!.tasksCompleted} / ${progress.tasksPlanned}"
                        )
                        ContributionDetailRow(
                            label = "Focus minutes",
                            value = "${progress.focusMinutes} min"
                        )
                        ContributionDetailRow(
                            label = "Goals worked on",
                            value = "${progress.goalsWorkedOn}"
                        )
                        ContributionDetailRow(
                            label = "Carried tasks",
                            value = "${progress.carriedTasks}"
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NexoraShapes.large,
            tonalElevation = 2.dp
        )
    }
}

private data class HeatmapPalette(
    val noneBg: Color,
    val noneBorder: Color,
    val lowBg: Color,
    val lowBorder: Color,
    val medBg: Color,
    val medBorder: Color,
    val highBg: Color,
    val highBorder: Color
)

private fun getHeatmapColors(isDark: Boolean): HeatmapPalette {
    return if (isDark) {
        HeatmapPalette(
            noneBg = Color(0xFF1E2420),
            noneBorder = Color(0xFF27302A),
            lowBg = Color(0xFF1B402E),
            lowBorder = Color(0xFF23543D),
            medBg = Color(0xFF2E7554),
            medBorder = Color(0xFF388E67),
            highBg = Color(0xFF4ADE80),
            highBorder = Color(0xFF6EE7A0)
        )
    } else {
        HeatmapPalette(
            noneBg = Color(0xFFF0F3EF),
            noneBorder = Color(0xFFE4E9E2),
            lowBg = Color(0xFFCDE8D5),
            lowBorder = Color(0xFFB8DCBF),
            medBg = Color(0xFF427B63),
            medBorder = Color(0xFF366B55),
            highBg = Color(0xFF1B4D3E),
            highBorder = Color(0xFF133E2B)
        )
    }
}

@Composable
private fun WeekdayLabel(text: String, height: Dp) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ContributionSquare(
    date: LocalDate?,
    progress: DailyProgress?,
    today: LocalDate,
    squareSize: Dp,
    onSquareClick: (LocalDate) -> Unit
) {
    if (date == null) {
        Box(modifier = Modifier.size(squareSize))
        return
    }

    val isToday = date == today
    val isFuture = date.isAfter(today)

    if (isFuture) {
        Box(modifier = Modifier.size(squareSize))
        return
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = remember(isDark) { getHeatmapColors(isDark) }
    val activityLevel = remember(progress) { calculateActivityLevel(progress) }

    val (bgColor, borderColor) = when (activityLevel) {
        DailyActivityLevel.NONE -> palette.noneBg to palette.noneBorder
        DailyActivityLevel.LOW -> palette.lowBg to palette.lowBorder
        DailyActivityLevel.MEDIUM -> palette.medBg to palette.medBorder
        DailyActivityLevel.HIGH -> palette.highBg to palette.highBorder
    }

    val finalBorderWidth = if (isToday) 1.5.dp else 0.5.dp
    val finalBorderColor = if (isToday) MaterialTheme.colorScheme.primary else borderColor

    Box(
        modifier = Modifier
            .size(squareSize)
            .background(bgColor, RoundedCornerShape(3.dp))
            .border(
                width = finalBorderWidth,
                color = finalBorderColor,
                shape = RoundedCornerShape(3.dp)
            )
            .clip(RoundedCornerShape(3.dp))
            .clickable {
                onSquareClick(date)
            }
    )
}

@Composable
private fun LegendSquare(bgColor: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(bgColor, RoundedCornerShape(2.5.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(2.5.dp))
    )
}

@Composable
private fun ContributionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

