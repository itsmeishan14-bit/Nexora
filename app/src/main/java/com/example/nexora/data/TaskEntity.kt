package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val category: String,

    val duration: String,

    val goalTitle: String? = null,

    // Store priority as plain text in SQLite.
    val priority: String = "MEDIUM",

    val completed: Boolean = false
)