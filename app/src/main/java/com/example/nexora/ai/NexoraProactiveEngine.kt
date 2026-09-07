package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority
import java.util.UUID

/**
 * Engine for detecting proactive signals and identifying opportunities/risks.
 */
class NexoraProactiveEngine {

    /**
     * Evaluates the current context and returns a list of detected proactive signals.
     */
    fun detectSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        
        signals.addAll(detectWorkloadSignals(context))
        signals.addAll(detectGoalSignals(context))
        signals.addAll(detectTaskSignals(context))
        signals.addAll(detectProductivitySignals(context))
        
        // Return top 3 most important signals to avoid overwhelming the user
        return signals.distinctBy { it.fingerprint }
            .sortedByDescending { it.severity }
            .take(3)
    }

    private fun detectWorkloadSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val profile = context.adaptiveProfile
        val plannedToday = context.tasksPlannedToday
        val capacity = profile.preferredDailyWorkload

        if (plannedToday > capacity * 1.5) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.OVERLOAD,
                title = "High Workload Warning",
                message = "You've planned $plannedToday tasks, but you usually complete around $capacity tasks per day.",
                severity = AiPriority.HIGH,
                confidence = mapAdaptiveConfidence(profile.confidence),
                evidence = "Historical capacity: $capacity, Planned today: $plannedToday",
                fingerprint = "workload_overload_${context.tasksPlannedToday}",
                suggestedAction = AiAction(
                    type = AiActionType.RESCHEDULE_TASK,
                    title = "Review Today's Plan",
                    description = "Should we move some low-priority tasks to tomorrow?",
                    reason = "Overloaded workload detected."
                )
            ))
        } else if (plannedToday > 0 && plannedToday <= capacity) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.WORKLOAD_BALANCED,
                title = "Balanced Workload",
                message = "Your current plan fits your typical daily capacity.",
                severity = AiPriority.LOW,
                confidence = mapAdaptiveConfidence(profile.confidence),
                evidence = "Planned: $plannedToday, Capacity: $capacity",
                fingerprint = "workload_balanced"
            ))
        }

        return signals
    }

    private fun detectGoalSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        
        context.activeGoals.forEach { goal ->
            val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
            val incompleteLinked = linkedTasks.filter { !it.completed }
            
            if (incompleteLinked.isEmpty() && goal.progress < 1.0f) {
                 signals.add(AiProactiveSignal(
                    type = ProactiveSignalType.MISSING_NEXT_ACTION,
                    title = "Goal Neglected",
                    message = "\"${goal.title}\" is active but has no tasks planned.",
                    severity = AiPriority.MEDIUM,
                    confidence = AiConfidence.HIGH,
                    evidence = "Goal progress: ${(goal.progress * 100).toInt()}%, Linked tasks: 0",
                    relatedGoalId = goal.id,
                    fingerprint = "goal_missing_action_${goal.id}",
                    suggestedAction = AiAction(
                        type = AiActionType.DECOMPOSE_GOAL,
                        title = "Plan Next Steps",
                        description = "Break down \"${goal.title}\" into new tasks?",
                        goalId = goal.id,
                        reason = "No active tasks for this goal."
                    )
                ))
            }

            // Detection of goal at risk from personal context
            val health = context.personalContext.goalHealth.find { it.goalId == goal.id }
            if (health?.state == GoalHealthState.AT_RISK) {
                signals.add(AiProactiveSignal(
                    type = ProactiveSignalType.NEGLECTED_GOAL,
                    title = "Goal Neglected",
                    message = "\"${goal.title}\" has not received much attention recently.",
                    severity = AiPriority.HIGH,
                    confidence = AiConfidence.HIGH,
                    evidence = health.evidence,
                    relatedGoalId = goal.id,
                    fingerprint = "goal_at_risk_${goal.id}"
                ))
            }
        }
        
        return signals
    }

    private fun detectTaskSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        
        val urgentPending = context.incompleteTasks.find { it.priority == TaskPriority.URGENT }
        if (urgentPending != null) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.HIGH_PRIORITY_CONFLICT,
                title = "Urgent Task Pending",
                message = "\"${urgentPending.title}\" requires immediate attention.",
                severity = AiPriority.CRITICAL,
                confidence = AiConfidence.HIGH,
                evidence = "Priority: URGENT",
                relatedTaskId = urgentPending.id,
                fingerprint = "task_urgent_${urgentPending.id}"
            ))
        }

        if (context.carriedTasks > 3) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.REPEATED_CARRY_FORWARD,
                title = "Persistent Carry-over",
                message = "Several tasks are being carried forward repeatedly. They might be too large.",
                severity = AiPriority.MEDIUM,
                confidence = AiConfidence.MEDIUM,
                evidence = "${context.carriedTasks} tasks carried forward.",
                fingerprint = "tasks_carry_over_high"
            ))
        }

        return signals
    }

    private fun detectProductivitySignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val personalContext = context.personalContext

        if (personalContext.productivityTrend == ProductivityTrend.DECLINING) {
             signals.add(AiProactiveSignal(
                type = ProactiveSignalType.PRODUCTIVITY_DROP,
                title = "Productivity Trend",
                message = "Nexora noticed a slight decline in your recent task completion rate.",
                severity = AiPriority.MEDIUM,
                confidence = personalContext.confidence,
                evidence = "Declining completion rate trend detected.",
                fingerprint = "productivity_declining"
            ))
        } else if (personalContext.productivityTrend == ProductivityTrend.IMPROVING) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.PRODUCTIVITY_IMPROVEMENT,
                title = "Positive Momentum",
                message = "You're exceeding your typical completion rate. Keep it up!",
                severity = AiPriority.LOW,
                confidence = personalContext.confidence,
                evidence = "Improving completion rate trend detected.",
                fingerprint = "productivity_improving"
            ))
        }

        return signals
    }

    private fun mapAdaptiveConfidence(confidence: AdaptiveConfidence): AiConfidence {
        return when (confidence) {
            AdaptiveConfidence.HIGH -> AiConfidence.HIGH
            AdaptiveConfidence.MODERATE -> AiConfidence.MEDIUM
            AdaptiveConfidence.LOW -> AiConfidence.LOW
            AdaptiveConfidence.UNKNOWN -> AiConfidence.LOW
        }
    }
}
