package com.example.nexora.ai

import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask

object NexoraAiCore {

    fun analyze(
        tasks: List<PremiumTask>,
        goals: List<NexoraGoal>,
        tasksCompletedToday: Int = 0,
        tasksPlannedToday: Int = 0,
        focusMinutesToday: Int = 0,
        goalsWorkedOnToday: Int = 0,
        carriedTasks: Int = 0
    ): List<AiRecommendation> {

        val context = AiContext(
            tasks = tasks,
            goals = goals,
            tasksCompletedToday = tasksCompletedToday,
            tasksPlannedToday = tasksPlannedToday,
            focusMinutesToday = focusMinutesToday,
            goalsWorkedOnToday = goalsWorkedOnToday,
            carriedTasks = carriedTasks
        )

        return AiPlanner.generateRecommendations(context)
    }

    fun buildAiPrompt(
        tasks: List<PremiumTask>,
        goals: List<NexoraGoal>,
        tasksCompletedToday: Int = 0,
        tasksPlannedToday: Int = 0,
        focusMinutesToday: Int = 0,
        goalsWorkedOnToday: Int = 0,
        carriedTasks: Int = 0
    ): String {

        val context = AiContext(
            tasks = tasks,
            goals = goals,
            tasksCompletedToday = tasksCompletedToday,
            tasksPlannedToday = tasksPlannedToday,
            focusMinutesToday = focusMinutesToday,
            goalsWorkedOnToday = goalsWorkedOnToday,
            carriedTasks = carriedTasks
        )

        return AiPromptBuilder.buildContextPrompt(context)
    }
}