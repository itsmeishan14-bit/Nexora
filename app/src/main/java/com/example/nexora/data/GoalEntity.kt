package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val targetDate: String,
    val progress: Float = 0f
)