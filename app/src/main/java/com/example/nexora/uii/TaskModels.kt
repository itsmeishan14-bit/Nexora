package com.example.nexora.uii

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

data class PremiumTask(
    val id: Long = 0L,
    val title: String,
    val category: String,
    val duration: String,
    val goalTitle: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val completed: Boolean = false
)

data class NexoraGoal(
    val id: Long = 0L,
    val title: String,
    val category: String,
    val targetDate: String,
    val progress: Float
)
