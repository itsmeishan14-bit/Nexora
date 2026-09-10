package com.example.nexora.uii

import java.time.LocalDate

/**
 * Stores Nexora's daily productivity information.
 */
data class DailyProgress(
    val date: LocalDate,
    val tasksPlanned: Int,
    val tasksCompleted: Int,
    val focusMinutes: Int = 0,
    val goalsWorkedOn: Int = 0,
    val carriedTasks: Int = 0
) {
    val completionRate: Float
        get() = if (tasksPlanned == 0) 0f else tasksCompleted.toFloat() / tasksPlanned.toFloat()

    val isPerfectDay: Boolean
        get() = tasksPlanned > 0 && tasksCompleted == tasksPlanned
}

/**
 * Calculates the current consistency streak.
 */
fun calculateCurrentStreak(
    progressHistory: List<DailyProgress>,
    today: LocalDate = LocalDate.now()
): Int {
    val progressByDate = progressHistory.associateBy { it.date }
    var date = today
    var streak = 0

    // Only count if today has at least one task or is a perfect day
    // Actually,GitHub style usually counts days with any activity.
    while (true) {
        val progress = progressByDate[date]
        if (progress == null || progress.tasksCompleted == 0) break
        streak++
        date = date.minusDays(1)
    }
    return streak
}

/**
 * Finds the longest consistency streak.
 */
fun calculateLongestStreak(
    progressHistory: List<DailyProgress>
): Int {
    if (progressHistory.isEmpty()) return 0

    val sortedDates = progressHistory
        .filter { it.tasksCompleted > 0 }
        .map { it.date }
        .distinct()
        .sorted()

    if (sortedDates.isEmpty()) return 0

    var longestStreak = 1
    var currentStreak = 1

    for (index in 1 until sortedDates.size) {
        val previousDate = sortedDates[index - 1]
        val currentDate = sortedDates[index]
        if (currentDate == previousDate.plusDays(1)) {
            currentStreak++
            if (currentStreak > longestStreak) longestStreak = currentStreak
        } else {
            currentStreak = 1
        }
    }
    return longestStreak
}

/**
 * Calculates the average completion rate.
 */
fun calculateAverageCompletion(
    progressHistory: List<DailyProgress>
): Float {
    if (progressHistory.isEmpty()) return 0f
    return progressHistory.map { it.completionRate }.average().toFloat()
}
