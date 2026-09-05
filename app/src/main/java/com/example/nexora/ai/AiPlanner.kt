package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

class AiPlanner {

    fun analyze(context: AiContext): List<AiRecommendation> {

        val recommendations = mutableListOf<AiRecommendation>()

        // ------------------------------------------------------------
        // NEXT BEST TASK
        // ------------------------------------------------------------

        val nextTask: PremiumTask? = chooseNextTask(context)

        if (nextTask != null) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.NEXT_TASK,
                    title = "Start with this",
                    message = buildNextTaskMessage(
                        task = nextTask,
                        context = context
                    ),
                    priority = taskPriorityToAiPriority(
                        nextTask.priority
                    ),
                    relatedTaskId = nextTask.id,
                    actionLabel = "Start task"
                )
            )
        }

        // ------------------------------------------------------------
        // GOAL ATTENTION
        // ------------------------------------------------------------

        val goal = context.activeGoals
            .minByOrNull { goal ->
                goal.progress
            }

        if (goal != null) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Move this goal forward",
                    message =
                        "\"${goal.title}\" is currently at " +
                                "${(goal.progress * 100).toInt()}%. " +
                                "Consider completing a task connected to this goal today.",
                    priority = AiPriority.MEDIUM,
                    relatedGoalId = goal.id,
                    actionLabel = "Work on goal"
                )
            )
        }

        // ------------------------------------------------------------
        // OVERLOAD WARNING
        // ------------------------------------------------------------

        if (context.incompleteTasks.size >= 8) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.WARNING,
                    title = "Your workload is getting heavy",
                    message =
                        "You currently have ${context.incompleteTasks.size} " +
                                "unfinished tasks. Focus on the highest-impact " +
                                "work instead of trying to complete everything.",
                    priority = AiPriority.HIGH,
                    actionLabel = "Prioritize tasks"
                )
            )
        }

        // ------------------------------------------------------------
        // PRODUCTIVITY INSIGHT
        // ------------------------------------------------------------

        if (context.tasksPlannedToday > 0) {

            val completionRate =
                context.tasksCompletedToday.toFloat() /
                        context.tasksPlannedToday.toFloat()

            when {

                completionRate >= 0.8f -> {

                    recommendations.add(
                        AiRecommendation(
                            type =
                                AiRecommendationType.PRODUCTIVITY_INSIGHT,
                            title = "Strong progress",
                            message =
                                "You've completed " +
                                        "${context.tasksCompletedToday} of " +
                                        "${context.tasksPlannedToday} planned tasks today. " +
                                        "Protect this momentum by choosing your next task carefully.",
                            priority = AiPriority.LOW
                        )
                    )
                }

                completionRate <= 0.3f &&
                        context.tasksPlannedToday >= 3 -> {

                    recommendations.add(
                        AiRecommendation(
                            type =
                                AiRecommendationType.PRODUCTIVITY_INSIGHT,
                            title = "Reduce the pressure",
                            message =
                                "Your completion rate is currently low. " +
                                        "Rather than adding more work, focus on " +
                                        "one meaningful task and build momentum.",
                            priority = AiPriority.MEDIUM
                        )
                    )
                }
            }
        }

        return recommendations
    }

    // ================================================================
    // DAILY PLAN
    // ================================================================

    fun createDailyPlan(
        context: AiContext
    ): List<AiRecommendation> {

        val incompleteTasks =
            context.incompleteTasks
                .sortedByDescending { task ->
                    taskScore(
                        task = task,
                        context = context
                    )
                }

        if (incompleteTasks.isEmpty()) {

            return listOf(
                AiRecommendation(
                    type = AiRecommendationType.DAILY_PLAN,
                    title = "You're clear for today",
                    message =
                        "There are no unfinished tasks in Nexora right now. " +
                                "Use the time to review your goals or plan your next meaningful step.",
                    priority = AiPriority.LOW
                )
            )
        }

        val selectedTasks =
            incompleteTasks.take(5)

        val planText =
            buildString {

                append("Today's focus:\n\n")

                selectedTasks.forEachIndexed { index, task ->

                    append("${index + 1}. ${task.title}")

                    if (task.duration.isNotBlank()) {
                        append(" • ${task.duration}")
                    }

                    if (task.goalTitle != null) {
                        append(" • Goal: ${task.goalTitle}")
                    }

                    append("\n")
                }

                append(
                    "\nNexora recommends focusing on these tasks " +
                            "in this order rather than trying to complete " +
                            "everything at once."
                )
            }

        return listOf(
            AiRecommendation(
                type = AiRecommendationType.DAILY_PLAN,
                title = "Your focused plan",
                message = planText,
                priority = AiPriority.HIGH,
                actionLabel = "Begin with #1"
            )
        )
    }

    // ================================================================
    // GOAL ANALYSIS
    // ================================================================

    fun analyzeGoals(
        context: AiContext
    ): List<AiRecommendation> {

        if (context.activeGoals.isEmpty()) {

            return listOf(
                AiRecommendation(
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "No active goals yet",
                    message =
                        "Create a goal and connect tasks to it so Nexora " +
                                "can help guide your progress.",
                    priority = AiPriority.LOW,
                    actionLabel = "Create a goal"
                )
            )
        }

        val lowestProgressGoal =
            context.activeGoals.minByOrNull { goal ->
                goal.progress
            }

        if (lowestProgressGoal == null) {
            return emptyList()
        }

        return listOf(
            AiRecommendation(
                type = AiRecommendationType.GOAL_ACTION,
                title = "Goal needs attention",
                message =
                    "\"${lowestProgressGoal.title}\" has " +
                            "${(lowestProgressGoal.progress * 100).toInt()}% progress. " +
                            "Choose one concrete task that moves this goal forward.",
                priority = AiPriority.MEDIUM,
                relatedGoalId = lowestProgressGoal.id,
                actionLabel = "Take the next step"
            )
        )
    }

    // ================================================================
    // PRODUCTIVITY ANALYSIS
    // ================================================================

    fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation> {

        if (context.tasksPlannedToday == 0) {

            return listOf(
                AiRecommendation(
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "Nothing planned yet",
                    message =
                        "Add a few meaningful tasks and Nexora can start " +
                                "learning how your daily workload behaves.",
                    priority = AiPriority.LOW,
                    actionLabel = "Plan your day"
                )
            )
        }

        val completionRate =
            context.tasksCompletedToday.toFloat() /
                    context.tasksPlannedToday.toFloat()

        return when {

            completionRate >= 0.8f -> {

                listOf(
                    AiRecommendation(
                        type =
                            AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "You're building momentum",
                        message =
                            "You've completed ${context.tasksCompletedToday} " +
                                    "of ${context.tasksPlannedToday} planned tasks today. " +
                                    "Keep your next step focused.",
                        priority = AiPriority.LOW
                    )
                )
            }

            completionRate >= 0.5f -> {

                listOf(
                    AiRecommendation(
                        type =
                            AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Solid progress",
                        message =
                            "You've completed ${context.tasksCompletedToday} " +
                                    "of ${context.tasksPlannedToday} planned tasks. " +
                                    "Finish the most important remaining task before adding more.",
                        priority = AiPriority.MEDIUM
                    )
                )
            }

            else -> {

                listOf(
                    AiRecommendation(
                        type =
                            AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Focus before adding more",
                        message =
                            "Your current completion rate is " +
                                    "${(completionRate * 100).toInt()}%. " +
                                    "Nexora recommends reducing context switching " +
                                    "and finishing one important task.",
                        priority = AiPriority.MEDIUM
                    )
                )
            }
        }
    }

    // ================================================================
    // TASK SELECTION
    // ================================================================

    private fun chooseNextTask(
        context: AiContext
    ): PremiumTask? {

        return context.incompleteTasks
            .maxByOrNull { task ->
                taskScore(
                    task = task,
                    context = context
                )
            }
    }

    // ================================================================
    // TASK SCORING
    // ================================================================

    private fun taskScore(
        task: PremiumTask,
        context: AiContext
    ): Int {

        var score = 0

        score += when (task.priority) {

            TaskPriority.URGENT -> 100

            TaskPriority.HIGH -> 70

            TaskPriority.MEDIUM -> 40

            TaskPriority.LOW -> 20
        }

        if (
            task.goalTitle != null &&
            context.activeGoals.any { goal ->
                goal.title == task.goalTitle
            }
        ) {
            score += 30
        }

        val durationMinutes =
            extractDurationMinutes(task.duration)

        if (
            context.incompleteTasks.size >= 6 &&
            durationMinutes in 1..30
        ) {
            score += 15
        }

        if (task.completed) {
            score -= 1000
        }

        return score
    }

    // ================================================================
    // DURATION PARSER
    // ================================================================

    private fun extractDurationMinutes(
        duration: String
    ): Int {

        val value =
            duration
                .lowercase()
                .trim()

        val number =
            Regex("\\d+")
                .find(value)
                ?.value
                ?.toIntOrNull()
                ?: return 0

        return when {

            value.contains("hour") ||
                    value.contains("hr") -> number * 60

            else -> number
        }
    }

    // ================================================================
    // AI PRIORITY
    // ================================================================

    private fun taskPriorityToAiPriority(
        priority: TaskPriority
    ): AiPriority {

        return when (priority) {

            TaskPriority.URGENT ->
                AiPriority.CRITICAL

            TaskPriority.HIGH ->
                AiPriority.HIGH

            TaskPriority.MEDIUM ->
                AiPriority.MEDIUM

            TaskPriority.LOW ->
                AiPriority.LOW
        }
    }

    // ================================================================
    // NEXT TASK MESSAGE
    // ================================================================

    private fun buildNextTaskMessage(
        task: PremiumTask,
        context: AiContext
    ): String {

        return when {

            task.priority == TaskPriority.URGENT ->
                "\"${task.title}\" should be your first priority because " +
                        "you marked it as urgent."

            task.priority == TaskPriority.HIGH &&
                    task.goalTitle != null ->
                "\"${task.title}\" is high priority and contributes to " +
                        "your goal \"${task.goalTitle}\"."

            task.goalTitle != null ->
                "\"${task.title}\" is connected to \"${task.goalTitle}\". " +
                        "Completing it will create meaningful progress toward that goal."

            else ->
                "\"${task.title}\" is currently the strongest next step " +
                        "based on your task priorities."
        }
    }
}