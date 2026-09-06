package com.example.nexora.ai

object AiPromptBuilder {

    fun buildContextPrompt(context: AiContext): String {

        val tasksSection = if (context.tasks.isEmpty()) {
            "No tasks currently exist."
        } else {
            context.tasks.joinToString("\n") { task ->
                """
                - ${task.title}
                  Category: ${task.category}
                  Duration: ${task.duration}
                  Priority: ${task.priority}
                  Goal: ${task.goalTitle ?: "None"}
                  Completed: ${task.completed}
                """.trimIndent()
            }
        }

        val goalsSection = if (context.goals.isEmpty()) {
            "No goals currently exist."
        } else {
            context.goals.joinToString("\n") { goal ->
                """
                - ${goal.title}
                  Category: ${goal.category}
                  Target date: ${goal.targetDate}
                  Progress: ${(goal.progress * 100).toInt()}%
                """.trimIndent()
            }
        }

        return """
            You are Nexora's personal intelligence engine.

            Your job is to help the user make better decisions
            about their goals, tasks, time, and productivity.

            IMPORTANT PRINCIPLES:
            - Prioritize meaningful progress over simply doing more tasks.
            - Consider goals when evaluating tasks.
            - Consider deadlines and task difficulty.
            - Avoid unrealistic schedules.
            - Do not overwhelm the user with unnecessary recommendations.
            - Prefer clear, practical actions.
            - Explain the reasoning behind important recommendations.
            - Never invent information that is not present in the context.

            AVAILABLE TOOLS & ACTIONS:
            - CREATE_TASK: title, category, duration, priority, goalTitle
            - UPDATE_TASK: taskId, title, priority, duration
            - COMPLETE_TASK: taskId
            - DELETE_TASK: taskId (requires explicit confirmation)
            - CREATE_GOAL: title, category, targetDate
            - RESCHEDULE_TASK: taskId, newDate
            - DECOMPOSE_GOAL: goalTitle

            CURRENT NEXORA STATE

            TASKS:
            $tasksSection

            GOALS:
            $goalsSection

            TODAY'S ACTIVITY:
            Tasks planned: ${context.tasksPlannedToday}
            Tasks completed: ${context.tasksCompletedToday}
            Focus minutes: ${context.focusMinutesToday}
            Goals worked on: ${context.goalsWorkedOnToday}
            Carried tasks: ${context.carriedTasks}

            AI OBJECTIVE:

            Analyze the current state of Nexora and determine
            what would provide the greatest useful improvement
            for the user.

            Possible decisions include:
            - What task should be done next?
            - Which goal needs attention?
            - What should today's plan look like?
            - Is the workload unrealistic?
            - Is a goal falling behind?
            - Is there a meaningful productivity pattern?
            - What should be changed or improved?

            Return recommendations that are specific,
            actionable, and based only on the available context.
            If an action is requested, provide structured output mapping to the tools above.
        """.trimIndent()
    }
}
