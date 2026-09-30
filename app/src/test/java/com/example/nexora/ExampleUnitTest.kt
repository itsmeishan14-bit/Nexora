package com.example.nexora

import com.example.nexora.uii.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun testCalendarGridStructureAndDayOfWeekAlignment() {
        val testToday = LocalDate.of(2026, 9, 30) // Wednesday
        val weeks = getContributionData(weeksCount = 32, today = testToday)

        assertEquals(32, weeks.size)

        for (week in weeks) {
            assertEquals(7, week.size)
        }

        // Test day of week alignment: Row 0 = Mon, Row 1 = Tue, ..., Row 6 = Sun
        for (weekIndex in 0 until weeks.size - 1) {
            val week = weeks[weekIndex]
            assertEquals(DayOfWeek.MONDAY, week[0]?.dayOfWeek)
            assertEquals(DayOfWeek.TUESDAY, week[1]?.dayOfWeek)
            assertEquals(DayOfWeek.WEDNESDAY, week[2]?.dayOfWeek)
            assertEquals(DayOfWeek.THURSDAY, week[3]?.dayOfWeek)
            assertEquals(DayOfWeek.FRIDAY, week[4]?.dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, week[5]?.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, week[6]?.dayOfWeek)
        }

        // In the final week, Wed is today, Thu..Sun must be null (future)
        val lastWeek = weeks.last()
        assertEquals(DayOfWeek.MONDAY, lastWeek[0]?.dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, lastWeek[1]?.dayOfWeek)
        assertEquals(testToday, lastWeek[2]) // Wednesday = today
        assertNull(lastWeek[3]) // Thursday is after today
        assertNull(lastWeek[4]) // Friday is after today
        assertNull(lastWeek[5]) // Saturday is after today
        assertNull(lastWeek[6]) // Sunday is after today
    }

    @Test
    fun testCalendarContinuousDateProgression() {
        val testToday = LocalDate.of(2026, 9, 30)
        val weeks = getContributionData(weeksCount = 32, today = testToday)

        val nonNullDates = weeks.flatten().filterNotNull()
        for (i in 1 until nonNullDates.size) {
            val prev = nonNullDates[i - 1]
            val curr = nonNullDates[i]
            assertEquals("Dates must be consecutive: $prev -> $curr", prev.plusDays(1), curr)
        }
        assertEquals(testToday, nonNullDates.last())
    }

    @Test
    fun testDynamicMonthLabels() {
        val testToday = LocalDate.of(2026, 9, 30)
        val weeks = getContributionData(weeksCount = 32, today = testToday)
        val monthLabels = getMonthLabels(weeks)

        assertFalse("Month labels must not be empty", monthLabels.isEmpty())

        // Ensure every month label corresponds to a real month that starts or appears in that week
        for ((weekIndex, label) in monthLabels) {
            val weekDates = weeks[weekIndex].filterNotNull()
            assertTrue("Week $weekIndex must contain dates", weekDates.isNotEmpty())
            val matchingDate = weekDates.any {
                it.month.name.startsWith(label.uppercase()) || label.equals(it.month.name.take(3), ignoreCase = true)
            }
            assertTrue("Label $label must correspond to dates in week $weekIndex", matchingDate)
        }

        // Verify spacing between labels is at least 3 weeks to prevent visual collision
        val sortedWeekIndices = monthLabels.keys.sorted()
        for (i in 1 until sortedWeekIndices.size) {
            val prevWeek = sortedWeekIndices[i - 1]
            val currWeek = sortedWeekIndices[i]
            assertTrue(
                "Month labels at $prevWeek and $currWeek must be at least 3 weeks apart",
                currWeek - prevWeek >= 3
            )
        }
    }

    @Test
    fun testActivityLevelCalculation() {
        val testDate = LocalDate.of(2026, 9, 30)

        // Null progress -> NONE
        assertEquals(DailyActivityLevel.NONE, calculateActivityLevel(null))

        // Planned tasks but 0 completed & 0 focus -> NONE
        val emptyDay = DailyProgress(date = testDate, tasksPlanned = 4, tasksCompleted = 0)
        assertEquals(DailyActivityLevel.NONE, calculateActivityLevel(emptyDay))

        // Low activity: 1 completed task
        val lowDay = DailyProgress(date = testDate, tasksPlanned = 5, tasksCompleted = 1)
        assertEquals(DailyActivityLevel.LOW, calculateActivityLevel(lowDay))

        // Medium activity: 2 completed tasks with 50% rate
        val mediumDay = DailyProgress(date = testDate, tasksPlanned = 4, tasksCompleted = 2)
        assertEquals(DailyActivityLevel.MEDIUM, calculateActivityLevel(mediumDay))

        // Medium activity: 30 focus minutes
        val focusMediumDay = DailyProgress(date = testDate, tasksPlanned = 0, tasksCompleted = 0, focusMinutes = 30)
        assertEquals(DailyActivityLevel.MEDIUM, calculateActivityLevel(focusMediumDay))

        // High activity: 4+ completed tasks
        val highDay = DailyProgress(date = testDate, tasksPlanned = 5, tasksCompleted = 4)
        assertEquals(DailyActivityLevel.HIGH, calculateActivityLevel(highDay))

        // High activity: 2 tasks completed with >= 80% completion rate (e.g. 2 / 2)
        val perfectDay = DailyProgress(date = testDate, tasksPlanned = 2, tasksCompleted = 2)
        assertEquals(DailyActivityLevel.HIGH, calculateActivityLevel(perfectDay))

        // High activity: 60+ focus minutes
        val highFocusDay = DailyProgress(date = testDate, tasksPlanned = 1, tasksCompleted = 1, focusMinutes = 60)
        assertEquals(DailyActivityLevel.HIGH, calculateActivityLevel(highFocusDay))
    }

    @Test
    fun testStreakCalculationsPreserved() {
        val today = LocalDate.of(2026, 9, 30)
        val history = listOf(
            DailyProgress(date = today, tasksPlanned = 3, tasksCompleted = 3),
            DailyProgress(date = today.minusDays(1), tasksPlanned = 2, tasksCompleted = 2),
            DailyProgress(date = today.minusDays(2), tasksPlanned = 1, tasksCompleted = 1),
            DailyProgress(date = today.minusDays(3), tasksPlanned = 2, tasksCompleted = 0) // Break
        )

        val currentStreak = calculateCurrentStreak(history, today = today)
        assertEquals(3, currentStreak)

        val longestStreak = calculateLongestStreak(history)
        assertEquals(3, longestStreak)
    }
}