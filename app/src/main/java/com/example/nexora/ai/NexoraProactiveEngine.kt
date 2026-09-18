package com.example.nexora.ai

import com.example.nexora.uii.TaskPriority
import java.util.UUID

/**
 * Engine for detecting proactive signals and identifying opportunities/risks.
 * Reasons over current state, history, and learned patterns to intervene helpfully.
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
        signals.addAll(detectPlanningSignals(context))
        
        // 1. Safety & Confidence Gate
        val validatedSignals = signals.filter { it.confidence >= AiConfidence.MEDIUM }

        // 2. Ranking & Deduplication
        return validatedSignals.distinctBy { it.fingerprint }
            .sortedWith(compareByDescending<AiProactiveSignal> { it.severity }
                .thenByDescending { it.confidence }
                .thenByDescending { it.detectedAt })
            .take(3)
    }

    private fun detectWorkloadSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val profile = context.adaptiveProfile
        val plannedToday = context.tasksPlannedToday
        val capacity = profile.preferredDailyWorkload

        // High Workload Risk
        if (capacity > 0 && plannedToday > capacity * 1.5) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.WORKLOAD_RISK,
                title = "High Workload Risk",
                message = "You have $plannedToday tasks planned, but your typical capacity is $capacity. You may be over-committing.",
                severity = if (plannedToday > capacity * 2) AiPriority.CRITICAL else AiPriority.HIGH,
                confidence = mapAdaptiveConfidence(profile.confidence),
                evidence = "Planned: $plannedToday, Typical Capacity: $capacity",
                fingerprint = "workload_overload_${plannedToday}",
                suggestedAction = AiAction(
                    type = AiActionType.RESCHEDULE_TASK,
                    title = "Review Today's Plan",
                    description = "Should we move some low-priority tasks to tomorrow?",
                    reason = "Overloaded workload detected."
                )
            ))
        }

        // Low Completion Risk
        if (plannedToday >= 5 && context.tasksCompletedToday < plannedToday * 0.2) {
            val progress = (context.tasksCompletedToday.toFloat() / plannedToday * 100).toInt()
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.LOW_COMPLETION_RATE,
                title = "Completion Pace",
                message = "Only $progress% of today's plan is finished. Consider focusing on one urgent task.",
                severity = AiPriority.MEDIUM,
                confidence = AiConfidence.HIGH,
                evidence = "Completed: ${context.tasksCompletedToday}, Planned: $plannedToday",
                fingerprint = "low_completion_pace"
            ))
        }

        return signals
    }

    private fun detectGoalSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val personalContext = context.personalContext

        context.activeGoals.forEach { goal ->
            val linkedTasks = context.tasks.filter { it.goalTitle == goal.title }
            val incompleteLinked = linkedTasks.filter { !it.completed }
            
            // Missing Next Action Opportunity
            if (incompleteLinked.isEmpty() && goal.progress < 1.0f) {
                 signals.add(AiProactiveSignal(
                    type = ProactiveSignalType.MISSING_NEXT_ACTION,
                    title = "Goal Stagnating",
                    message = "\"${goal.title}\" is active but has no tasks planned. Progress has paused.",
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

            // Goal Neglect Detection (based on health assessment)
            val health = personalContext.goalHealth.find { it.goalId == goal.id }
            if (health?.state == GoalHealthState.AT_RISK) {
                signals.add(AiProactiveSignal(
                    type = ProactiveSignalType.GOAL_NEGLECT,
                    title = "Goal at Risk",
                    message = "\"${goal.title}\" has not received enough attention recently. Progress is declining.",
                    severity = AiPriority.HIGH,
                    confidence = AiConfidence.HIGH,
                    evidence = health.evidence,
                    relatedGoalId = goal.id,
                    fingerprint = "goal_neglect_${goal.id}"
                ))
            }

            // Goal Progress Opportunity
            if (goal.progress > 0.8f && goal.progress < 1.0f && incompleteLinked.isNotEmpty()) {
                signals.add(AiProactiveSignal(
                    type = ProactiveSignalType.GOAL_PROGRESS_OPPORTUNITY,
                    title = "Near Completion",
                    message = "\"${goal.title}\" is ${(goal.progress * 100).toInt()}% complete. A final push could finish it today!",
                    severity = AiPriority.LOW,
                    confidence = AiConfidence.HIGH,
                    evidence = "Current progress: ${goal.progress}",
                    relatedGoalId = goal.id,
                    fingerprint = "goal_momentum_${goal.id}"
                ))
            }
        }
        
        return signals
    }

    private fun detectTaskSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        
        // Priority Task Risk
        val urgentPending = context.incompleteTasks.find { it.priority == TaskPriority.URGENT }
        if (urgentPending != null) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.HIGH_PRIORITY_CONFLICT,
                title = "Urgent Priority Pending",
                message = "\"${urgentPending.title}\" requires immediate attention. It is your most critical item.",
                severity = AiPriority.CRITICAL,
                confidence = AiConfidence.HIGH,
                evidence = "Priority: URGENT",
                relatedTaskId = urgentPending.id,
                fingerprint = "task_urgent_${urgentPending.id}",
                suggestedAction = AiAction(
                    type = AiActionType.OPEN_TASK,
                    title = "Start Task",
                    description = "Focus on \"${urgentPending.title}\" now?",
                    taskId = urgentPending.id
                )
            ))
        }

        // Carry-Forward Pattern
        if (context.carriedTasks > 3) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.CARRY_FORWARD_PATTERN,
                title = "Persistent Carry-over",
                message = "Several tasks are being carried forward repeatedly. They might be too large or complex.",
                severity = AiPriority.MEDIUM,
                confidence = AiConfidence.MEDIUM,
                evidence = "${context.carriedTasks} tasks carried forward today.",
                fingerprint = "tasks_carry_over_high",
                suggestedAction = AiAction(
                    type = AiActionType.RESCHEDULE_TASK,
                    title = "Review Carry-over",
                    description = "Should we break these down or reschedule them?",
                    reason = "High carry-over count detected."
                )
            ))
        }

        // Task Too Large Risk
        val longTask = context.incompleteTasks.find { extractDurationMinutes(it.duration) > 120 }
        if (longTask != null && context.adaptiveProfile.preferredTaskSize == "Small") {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.TASK_TOO_LARGE,
                title = "Complex Task Intervention",
                message = "\"${longTask.title}\" is very long. Breaking it into 30-minute chunks may help completion.",
                severity = AiPriority.MEDIUM,
                confidence = mapAdaptiveConfidence(context.adaptiveProfile.confidence),
                evidence = "Duration: ${longTask.duration}, Preference: Small tasks.",
                relatedTaskId = longTask.id,
                fingerprint = "task_too_large_${longTask.id}",
                suggestedAction = AiAction(
                    type = AiActionType.DECOMPOSE_GOAL, // Reusing decomposition logic for large tasks
                    title = "Break Down Task",
                    description = "Split \"${longTask.title}\" into smaller sub-tasks?",
                    taskId = longTask.id
                )
            ))
        }

        return signals
    }

    private fun detectProductivitySignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val personalContext = context.personalContext

        // Productivity Drop
        if (personalContext.productivityTrend == ProductivityTrend.DECLINING) {
             signals.add(AiProactiveSignal(
                type = ProactiveSignalType.PRODUCTIVITY_DROP,
                title = "Productivity Trend",
                message = "Nexora noticed a decline in your task completion rate. Focus on one small 'Quick Win' to resume momentum.",
                severity = AiPriority.HIGH,
                confidence = personalContext.confidence,
                evidence = "Historical trend is declining.",
                fingerprint = "productivity_declining"
            ))
        } 
        
        // Productivity Improvement
        else if (personalContext.productivityTrend == ProductivityTrend.IMPROVING) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.PRODUCTIVITY_IMPROVEMENT,
                title = "Positive Momentum",
                message = "You're exceeding your typical completion rate. This is an ideal time to tackle a high-priority goal.",
                severity = AiPriority.LOW,
                confidence = personalContext.confidence,
                evidence = "Historical trend is improving.",
                fingerprint = "productivity_improving"
            ))
        }

        return signals
    }

    private fun detectPlanningSignals(context: AiContext): List<AiProactiveSignal> {
        val signals = mutableListOf<AiProactiveSignal>()
        val evaluations = context.recentEvaluations
        
        // Plan Mismatch Detection
        val tooLargeCount = evaluations.count { it.outcome == AiOutcomeType.PLAN_TOO_LARGE }
        if (tooLargeCount >= 3) {
            signals.add(AiProactiveSignal(
                type = ProactiveSignalType.PLAN_MISMATCH,
                title = "Planning Strategy",
                message = "Recent daily plans have been consistently too large. Nexora suggests a more focused, realistic agenda.",
                severity = AiPriority.MEDIUM,
                confidence = AiConfidence.HIGH,
                evidence = "Identified 'Plan Too Large' pattern in recent evaluations.",
                fingerprint = "plan_overload_trend"
            ))
        }

        return signals
    }

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: return 0
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
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
