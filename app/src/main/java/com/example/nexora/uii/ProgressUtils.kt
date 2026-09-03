package com.example.nexora.uii

fun calculateGoalProgress(
    goal: NexoraGoal,
    tasks: List<PremiumTask>
): Float {

    val goalTasks = tasks.filter { task ->
        task.goalTitle == goal.title
    }

    if (goalTasks.isEmpty()) {
        return 0f
    }

    val completedTasks = goalTasks.count { task ->
        task.completed
    }

    return completedTasks.toFloat() / goalTasks.size.toFloat()
}