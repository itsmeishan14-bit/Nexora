package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

object AiPlanner {

    fun generateRecommendations(
        context: AiContext
    ): List<AiRecommendation> {

        val recommendations = mutableListOf<AiRecommendation>()

        addNextTaskRecommendation(
            context = context,
            recommendations = recommendations
        )

        addGoalRecommendations(
            context = context,
            recommendations = recommendations
        )

        addWorkloadRecommendation(
            context = context,
            recommendations = recommendations
        )

        addProductivityRecommendation(
            context = context,
            recommendations = recommendations
        )

        return recommendations
    }

    private fun addNextTaskRecommendation(
        context: AiContext,
        recommendations: MutableList<AiRecommendation>
    ) {
        val nextTask = chooseNextTask(context) ?: return

        recommendations.add(
            AiRecommendation(
                type = AiRecommendationType.NEXT_TASK,
                title = "Recommended next task",
                message = buildNextTaskMessage(nextTask),
                priority = when (nextTask.priority) {
                    TaskPriority.URGENT -> AiPriority.CRITICAL
                    TaskPriority.HIGH -> AiPriority.HIGH
                    TaskPriority.MEDIUM -> AiPriority.MEDIUM
                    TaskPriority.LOW -> AiPriority.LOW
                },
                relatedTaskId = nextTask.id,
                actionLabel = "Start task"
            )
        )
    }

    private fun chooseNextTask(
        context: AiContext
    ): PremiumTask? {

        val incompleteTasks = context.incompleteTasks

        if (incompleteTasks.isEmpty()) {
            return null
        }

        return incompleteTasks.maxWithOrNull(
            compareBy<PremiumTask> {

                when (it.priority) {
                    TaskPriority.URGENT -> 4
                    TaskPriority.HIGH -> 3
                    TaskPriority.MEDIUM -> 2
                    TaskPriority.LOW -> 1
                }

            }.thenBy {

                // Tasks connected to an active goal
                // receive additional importance.
                if (
                    it.goalTitle != null &&
                    context.activeGoals.any { goal ->
                        goal.title.equals(
                            it.goalTitle,
                            ignoreCase = true
                        )
                    }
                ) {
                    1
                } else {
                    0
                }

            }.thenByDescending {

                // Prefer tasks that appear earlier
                // in the current task collection.
                -it.id
            }
        )
    }

    private fun buildNextTaskMessage(
        task: PremiumTask
    ): String {

        return if (task.goalTitle != null) {
            "Work on \"${task.title}\" next because it contributes to your goal \"${task.goalTitle}\"."
        } else {
            "Work on \"${task.title}\" next based on its current priority."
        }
    }

    private fun addGoalRecommendations(
        context: AiContext,
        recommendations: MutableList<AiRecommendation>
    ) {

        val neglectedGoal = context.activeGoals
            .filter { it.progress < 1f }
            .minByOrNull { it.progress }

        if (neglectedGoal != null) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Goal needs attention",
                    message = "\"${neglectedGoal.title}\" is currently at ${(neglectedGoal.progress * 100).toInt()}% progress. Consider completing a task connected to this goal.",
                    priority = AiPriority.MEDIUM,
                    relatedGoalId = neglectedGoal.id,
                    actionLabel = "View goal"
                )
            )
        }
    }

    private fun addWorkloadRecommendation(
        context: AiContext,
        recommendations: MutableList<AiRecommendation>
    ) {

        val incompleteCount = context.incompleteTasks.size

        if (incompleteCount >= 8) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.WARNING,
                    title = "Heavy workload",
                    message = "You currently have $incompleteCount incomplete tasks. Consider focusing on the most important ones instead of trying to complete everything.",
                    priority = AiPriority.HIGH
                )
            )
        }
    }

    private fun addProductivityRecommendation(
        context: AiContext,
        recommendations: MutableList<AiRecommendation>
    ) {

        if (context.tasksPlannedToday <= 0) {
            return
        }

        val completionRate =
            context.tasksCompletedToday.toFloat() /
                    context.tasksPlannedToday.toFloat()

        if (completionRate >= 0.8f) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "Strong progress today",
                    message = "You've completed ${(completionRate * 100).toInt()}% of today's planned tasks. Keep your focus on meaningful progress.",
                    priority = AiPriority.LOW
                )
            )

        } else if (
            context.tasksPlannedToday >= 3 &&
            completionRate <= 0.3f
        ) {

            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "Your plan may need adjustment",
                    message = "You've completed ${(completionRate * 100).toInt()}% of today's planned tasks. Consider reducing the remaining workload and focusing on one important task.",
                    priority = AiPriority.MEDIUM
                )
            )
        }
    }
}
