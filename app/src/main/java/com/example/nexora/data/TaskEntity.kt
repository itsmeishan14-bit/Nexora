package com.example.nexora.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.example.nexora.uii.TaskPriority

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val duration: String,
    val goalTitle: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val completed: Boolean = false
)