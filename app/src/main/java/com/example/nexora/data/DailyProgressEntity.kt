package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey
    val date: String,

    val tasksPlanned: Int,
    val tasksCompleted: Int,
    val focusMinutes: Int = 0,
    val goalsWorkedOn: Int = 0,
    val carriedTasks: Int = 0
)