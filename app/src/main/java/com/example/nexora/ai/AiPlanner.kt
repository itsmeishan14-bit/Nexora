package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class AiPlanner {

    /**
     * Decomposes a goal into actionable steps based on category and title.
     * Works entirely offline using deterministic templates.
     */
    fun decomposeGoal(
        goalTitle: String,
        goalDescription: String = "",
        category: String = "Personal"
    ): AiGoalDecomposition {

        val cleanTitle = goalTitle.trim()

        if (cleanTitle.isBlank()) {
            return AiGoalDecomposition(
                goalTitle = "",
                summary = "Give Nexora a goal so it can break it into meaningful steps.",
                steps = emptyList()
            )
        }

        val steps = mutableListOf<AiGoalStep>()
        
        // Strategy selection based on category
        val normalizedCategory = category.lowercase()
        when {
            normalizedCategory.contains("learn") || normalizedCategory.contains("study") -> {
                steps.add(AiGoalStep("Gather resources", "Identify books, courses, or documentation for $cleanTitle.", AiPriority.HIGH, "45 min", 1))
                steps.add(AiGoalStep("Foundational concepts", "Master the core principles of $cleanTitle.", AiPriority.HIGH, "90 min", 2))
                steps.add(AiGoalStep("Practical application", "Build a small project or complete exercises.", AiPriority.MEDIUM, "120 min", 3))
                steps.add(AiGoalStep("Review & internalize", "Test your knowledge and identify gaps.", AiPriority.MEDIUM, "60 min", 4))
            }
            normalizedCategory.contains("soft") || normalizedCategory.contains("dev") || normalizedCategory.contains("code") || normalizedCategory.contains("project") -> {
                steps.add(AiGoalStep("Architecture & design", "Outline the structure and components for $cleanTitle.", AiPriority.HIGH, "60 min", 1))
                steps.add(AiGoalStep("Environment setup", "Configure tools and dependencies.", AiPriority.MEDIUM, "30 min", 2))
                steps.add(AiGoalStep("Core implementation", "Develop the primary functionality.", AiPriority.HIGH, "180 min", 3))
                steps.add(AiGoalStep("Testing & refinement", "Fix bugs and optimize the solution.", AiPriority.MEDIUM, "90 min", 4))
            }
            normalizedCategory.contains("fit") || normalizedCategory.contains("health") -> {
                steps.add(AiGoalStep("Initial assessment", "Record your starting point and specific targets.", AiPriority.HIGH, "20 min", 1))
                steps.add(AiGoalStep("Consistent routine", "Schedule and complete your first week of activity.", AiPriority.HIGH, "45 min", 2))
                steps.add(AiGoalStep("Nutritional alignment", "Adjust habits to support $cleanTitle.", AiPriority.MEDIUM, "30 min", 3))
                steps.add(AiGoalStep("Progress evaluation", "Measure results and adjust intensity.", AiPriority.MEDIUM, "15 min", 4))
            }
            normalizedCategory.contains("work") || normalizedCategory.contains("career") -> {
                steps.add(AiGoalStep("Stakeholder alignment", "Clarify expectations and deliverables for $cleanTitle.", AiPriority.HIGH, "45 min", 1))
                steps.add(AiGoalStep("Execution phase", "Produce the primary output.", AiPriority.HIGH, "120 min", 2))
                steps.add(AiGoalStep("Feedback loop", "Present work and gather input.", AiPriority.MEDIUM, "60 min", 3))
                steps.add(AiGoalStep("Final delivery", "Address feedback and complete the work.", AiPriority.HIGH, "60 min", 4))
            }
            else -> {
                steps.add(AiGoalStep("Initial planning", "Break $cleanTitle into smaller chunks.", AiPriority.HIGH, "30 min", 1))
                steps.add(AiGoalStep("Execution phase", "Focus on the most important sub-task.", AiPriority.HIGH, "90 min", 2))
                steps.add(AiGoalStep("Milestone check", "Verify if you're on track.", AiPriority.MEDIUM, "20 min", 3))
                steps.add(AiGoalStep("Next steps", "Define what follows after this.", AiPriority.MEDIUM, "30 min", 4))
            }
        }

        val summary = if (goalDescription.isNotBlank()) {
            "Nexora used your description and $category category to create a specialized sequence for \"$cleanTitle\"."
        } else {
            "Nexora analyzed your $category goal and generated a sequence of actionable steps."
        }

        return AiGoalDecomposition(
            goalTitle = cleanTitle,
            summary = summary,
            steps = steps,
            confidence = if (category == "Personal") AiConfidence.LOW else AiConfidence.HIGH
        )
    }

    /**
     * Legacy signature for compatibility.
     */
    fun decomposeGoal(goalTitle: String, goalDescription: String = ""): AiGoalDecomposition {
        return decomposeGoal(goalTitle, goalDescription, "Personal")
    }

    fun analyze(context: AiContext): List<AiRecommendation> {
        val basic = getBasicRecommendations(context)
        return basic.sortedByDescending { it.priority }
    }

    private fun getBasicRecommendations(context: AiContext): List<AiRecommendation> {
        val recommendations = mutableListOf<AiRecommendation>()
        
        // NEXT BEST TASK
        val incompleteTasks = context.incompleteTasks
        if (incompleteTasks.isEmpty()) return recommendations

        val scoredTasks = incompleteTasks.map { task ->
            task to improvedTaskScore(task, context)
        }.sortedByDescending { it.second.first }

        val best = scoredTasks.firstOrNull()
        if (best != null) {
            val (task, scoreResult) = best
            val factors = scoreResult.second
            
            recommendations.add(
                AiRecommendation(
                    id = "planner_next_task_${task.id}",
                    type = AiRecommendationType.NEXT_TASK,
                    title = "Priority: ${task.title}",
                    message = buildNextTaskMessage(task, factors),
                    priority = taskPriorityToAiPriority(task.priority),
                    relatedTaskId = task.id,
                    actionLabel = "Start task",
                    evidence = factors,
                    confidence = if (factors.size >= 3) AiConfidence.HIGH else AiConfidence.MEDIUM
                )
            )
        }
        
        return recommendations
    }



    // ================================================================
    // DAILY PLAN
    // ================================================================

    fun createDailyPlan(
        context: AiContext
    ): NexoraDailyPlan {
        val profile = context.adaptiveProfile
        val personal = context.personalContext

        val incompleteTasks = context.incompleteTasks
        if (incompleteTasks.isEmpty()) {
            return NexoraDailyPlan(
                date = LocalDate.now().toString(),
                summary = "All caught up! You have no unfinished tasks. It's a great time to review your long-term goals."
            )
        }

        val scoredTasks = incompleteTasks.map { task ->
            task to improvedTaskScore(task, context)
        }.sortedByDescending { it.second.first }

        // Determine realistic capacity
        val recentEvaluations = context.recentEvaluations
        val tooAmbitiousCount = recentEvaluations.count { it.outcome == AiOutcomeType.PLAN_TOO_LARGE }
        
        var baseCapacity = if (profile.sampleCount >= 3) profile.preferredDailyWorkload else 5
        if (tooAmbitiousCount >= 2) baseCapacity = Math.max(2, baseCapacity - 1)
        
        // Adjust for current workload
        if (personal.workload.state == WorkloadState.VERY_HIGH) baseCapacity = Math.max(2, baseCapacity - 2)
        else if (personal.workload.state == WorkloadState.HIGH) baseCapacity = Math.max(3, baseCapacity - 1)

        val maxMinutes = 480 // 8 hours max focus estimate
        var currentMinutes = 0
        val selectedTasks = mutableListOf<PlannedTask>()

        for ((task, scoreResult) in scoredTasks) {
            val duration = extractDurationMinutes(task.duration).let { if (it <= 0) 30 else it }
            
            // Criteria: Fit within task count capacity OR always take top 2 high-priority items
            val isHighPriority = task.priority == TaskPriority.URGENT || task.priority == TaskPriority.HIGH
            val fitsCapacity = selectedTasks.size < baseCapacity
            val fitsTime = currentMinutes + duration <= maxMinutes

            if ((fitsCapacity || (isHighPriority && selectedTasks.size < 4)) && fitsTime) {
                selectedTasks.add(
                    PlannedTask(
                        task = task,
                        reason = scoreResult.second.firstOrNull()?.evidence ?: "Consistent progress",
                        recommendedOrder = selectedTasks.size + 1
                    )
                )
                currentMinutes += duration
            }
            
            if (selectedTasks.size >= baseCapacity + 2) break
        }

        val summary = buildString {
            if (selectedTasks.size <= 3) {
                append("Your plan is highly focused. ")
            } else if (selectedTasks.size >= 7) {
                append("You have a demanding day ahead. ")
            } else {
                append("Today's plan is balanced and achievable. ")
            }
            
            append("Nexora prioritized ${selectedTasks.size} tasks based on your priority settings, goal deadlines, and typical capacity.")
            
            if (tooAmbitiousCount >= 2) {
                append(" I've slightly reduced the plan size to match your recent completion patterns.")
            }
            
            val urgentCount = selectedTasks.count { it.task.priority == TaskPriority.URGENT }
            if (urgentCount > 0) {
                append(" $urgentCount urgent tasks are prioritized first.")
            }
        }

        return NexoraDailyPlan(
            date = LocalDate.now().toString(),
            tasks = selectedTasks,
            totalDurationMinutes = currentMinutes,
            summary = summary
        )
    }

    private fun improvedTaskScore(
        task: PremiumTask,
        context: AiContext
    ): Pair<Int, List<ReasoningFactor>> {
        var score = 0
        val factors = mutableListOf<ReasoningFactor>()
        val profile = context.adaptiveProfile

        // 1. PRIORITY (Base Score)
        when (task.priority) {
            TaskPriority.URGENT -> {
                score += 150
                factors.add(ReasoningFactor("Priority", ReasoningImpact.CRITICAL, "Marked as URGENT priority."))
            }
            TaskPriority.HIGH -> {
                score += 80
                factors.add(ReasoningFactor("Priority", ReasoningImpact.POSITIVE, "High priority task."))
            }
            TaskPriority.MEDIUM -> score += 40
            TaskPriority.LOW -> {
                score += 10
                factors.add(ReasoningFactor("Priority", ReasoningImpact.NEUTRAL, "Low priority task."))
            }
        }

        // 2. GOAL RELATIONSHIP
        val linkedGoal = context.activeGoals.find { it.title == task.goalTitle }
        if (linkedGoal != null) {
            score += 50
            factors.add(ReasoningFactor("Goal Alignment", ReasoningImpact.POSITIVE, "Directly supports your goal: \"${linkedGoal.title}\"."))

            // Progress-based fine-tuning
            if (linkedGoal.progress < 0.2f) {
                score += 30
                factors.add(ReasoningFactor("Goal Momentum", ReasoningImpact.POSITIVE, "This goal needs early momentum to succeed."))
            } else if (linkedGoal.progress > 0.85f) {
                score += 25
                factors.add(ReasoningFactor("Goal Completion", ReasoningImpact.POSITIVE, "You are near the finish line for this goal."))
            }
            
            // Goal Deadline / Target Date
            val daysToDeadline = parseTargetDateDaysRemaining(linkedGoal.targetDate)
            if (daysToDeadline != null) {
                if (daysToDeadline <= 3) {
                    score += 70
                    factors.add(ReasoningFactor("Deadline", ReasoningImpact.CRITICAL, "The goal target date is in $daysToDeadline days."))
                } else if (daysToDeadline <= 7) {
                    score += 30
                    factors.add(ReasoningFactor("Deadline", ReasoningImpact.POSITIVE, "Goal target date is approaching (under a week)."))
                }
            }
            
            // Goal Health
            val health = context.personalContext.goalHealth.find { it.goalId == linkedGoal.id }
            if (health?.state == GoalHealthState.AT_RISK) {
                score += 60
                factors.add(ReasoningFactor("Goal Health", ReasoningImpact.CRITICAL, "This goal is stagnating and needs activity."))
            }

            val stagnationMemory = context.memory.items.find { it.category == AiMemoryCategory.GOAL_PATTERN && it.relatedGoalId == linkedGoal.id && it.title == "Goal Stagnation" }
            if (stagnationMemory != null) {
                score += 30
                factors.add(ReasoningFactor("Behavior", ReasoningImpact.CRITICAL, "Progress has been inconsistent lately; this task helps rebuild momentum."))
            }
        }

        // 3. DURATION & WORKLOAD FIT
        val duration = extractDurationMinutes(task.duration)
        if (duration > 0) {
            if (profile.preferredTaskSize == "Small" && duration <= 30) {
                score += 20
                factors.add(ReasoningFactor("Duration Fit", ReasoningImpact.POSITIVE, "Matches your preference for smaller tasks."))
            } else if (profile.preferredTaskSize == "Large" && duration >= 60) {
                score += 20
                factors.add(ReasoningFactor("Focus Fit", ReasoningImpact.POSITIVE, "Matches your pattern for deep work sessions."))
            }
            
            // Quick Win detection
            if (duration <= 20 && (context.incompleteTasks.size > 6 || context.personalContext.workload.state == WorkloadState.VERY_HIGH)) {
                score += 25
                factors.add(ReasoningFactor("Quick Win", ReasoningImpact.POSITIVE, "Small enough to finish quickly despite heavy workload."))
            }
        }

        // 4. HISTORICAL REINFORCEMENT
        if (profile.sampleCount >= 5 && profile.completionRate > 0.7f) {
            score += 15
            factors.add(ReasoningFactor("History", ReasoningImpact.NEUTRAL, "Reinforced by your high completion pattern."))
        }

        context.memory.items.forEach { item ->
            if (item.category == AiMemoryCategory.TASK_SIZE_PATTERN) {
                if (item.title == "Quick Win Preference" && duration in 1..30) {
                    score += 20
                    factors.add(ReasoningFactor("Behavior", ReasoningImpact.POSITIVE, "Matches your historical 'Quick Win' success pattern."))
                } else if (item.title == "Deep Work Pattern" && duration >= 60) {
                    score += 20
                    factors.add(ReasoningFactor("Behavior", ReasoningImpact.POSITIVE, "Matches your historical 'Deep Work' success pattern."))
                }
            }
        }
        
        // 5. CARRIED WORK
        val memory = context.memory.items.find { it.category == AiMemoryCategory.TASK_PATTERN && it.relatedTaskId == task.id }
        if (memory != null && memory.content.contains("carried", ignoreCase = true)) {
            score += 20
            factors.add(ReasoningFactor("Persistence", ReasoningImpact.POSITIVE, "This task has been carried forward; finishing it now clears focus."))
        }

        return Pair(score, factors)
    }

    private fun parseTargetDateDaysRemaining(targetDate: String): Long? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
            val date = LocalDate.parse(targetDate, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, date)
        } catch (e: Exception) {
            null
        }
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
                    id = "planner_no_goals",
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Define your Objectives",
                    message = "Create a goal and link tasks to it. This allows Nexora to guide you with structured progress and priority scoring.",
                    priority = AiPriority.LOW,
                    confidence = AiConfidence.LOW,
                    actionLabel = "Create Goal"
                )
            )
        }

        val recommendations = mutableListOf<AiRecommendation>()
        
        // Identify most critical goal
        val personal = context.personalContext
        val criticalGoalHealth = personal.goalHealth.find { it.state == GoalHealthState.AT_RISK }
        val criticalGoal = criticalGoalHealth?.let { health -> context.activeGoals.find { it.id == health.goalId } }
            ?: context.activeGoals.minByOrNull { it.progress }

        if (criticalGoal != null) {
            val taskCount = context.tasks.count { it.goalTitle == criticalGoal.title && !it.completed }
            val message = when {
                taskCount == 0 -> "\"${criticalGoal.title}\" has no actionable sub-tasks. Add some concrete steps to start making progress."
                criticalGoal.progress < 0.1f -> "\"${criticalGoal.title}\" is waiting to start. Completing just one task will build initial momentum."
                else -> "Focusing on \"${criticalGoal.title}\" today will maximize your meaningful progress."
            }

            recommendations.add(
                AiRecommendation(
                    id = "planner_goal_focus_${criticalGoal.id}",
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Goal Progress",
                    message = message,
                    priority = if (criticalGoalHealth?.state == GoalHealthState.AT_RISK) AiPriority.HIGH else AiPriority.MEDIUM,
                    confidence = AiConfidence.HIGH,
                    relatedGoalId = criticalGoal.id,
                    evidence = listOf(
                        ReasoningFactor("Progress", ReasoningImpact.NEUTRAL, "Current progress: ${(criticalGoal.progress * 100).toInt()}%."),
                        ReasoningFactor("Attention", ReasoningImpact.POSITIVE, "Highest value focus area identified.")
                    ),
                    actionLabel = if (taskCount == 0) "Add Tasks" else "View Goal"
                )
            )
        }

        return recommendations
    }

    // ================================================================
    // PRODUCTIVITY ANALYSIS
    // ================================================================

    fun analyzeProductivity(
        context: AiContext
    ): List<AiRecommendation> {
        val profile = context.adaptiveProfile

        if (context.tasksPlannedToday == 0) {
            return listOf(
                AiRecommendation(
                    id = "planner_no_tasks_today",
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "Set Today's Agenda",
                    message = "Nexora hasn't detected any tasks in your daily plan. Adding focus items helps refine your capacity patterns.",
                    priority = AiPriority.LOW,
                    actionLabel = "Plan Day"
                )
            )
        }

        val completionRate = if (context.tasksPlannedToday > 0) 
            context.tasksCompletedToday.toFloat() / context.tasksPlannedToday.toFloat() else 0f
            
        val factors = listOf(
            ReasoningFactor("Planned", ReasoningImpact.NEUTRAL, "${context.tasksPlannedToday} tasks in agenda."),
            ReasoningFactor("Completed", ReasoningImpact.POSITIVE, "${context.tasksCompletedToday} tasks finished.")
        )

        return when {
            completionRate >= 0.85f -> {
                listOf(
                    AiRecommendation(
                        id = "planner_high_momentum",
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Exceptional Focus",
                        message = "You've completed ${context.tasksCompletedToday} tasks today. Your Standard of Excellence is very high right now.",
                        evidence = factors,
                        priority = AiPriority.LOW,
                        confidence = AiConfidence.HIGH
                    )
                )
            }
            completionRate >= 0.5f -> {
                listOf(
                    AiRecommendation(
                        id = "planner_on_track",
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Solid Daily Progress",
                        message = "You're halfway through your planned tasks. Maintain this pace to finish strong.",
                        evidence = factors,
                        priority = AiPriority.MEDIUM,
                        confidence = AiConfidence.HIGH
                    )
                )
            }
            else -> {
                val workloadMessage = if (profile.sampleCount >= 3 && completionRate < profile.completionRate * 0.7f) {
                    "Your pace is currently below your typical standard. Consider focusing on one small 'Quick Win' to rebuild momentum."
                } else {
                    "You've completed ${(completionRate * 100).toInt()}% of your plan. Focus on your top urgent item next."
                }
                
                listOf(
                    AiRecommendation(
                        id = "planner_pace_alert",
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Maintain Momentum",
                        message = workloadMessage,
                        evidence = factors,
                        priority = AiPriority.MEDIUM,
                        confidence = mapAdaptiveConfidence(profile.confidence)
                    )
                )
            }
        }
    }

    // ================================================================
    // MEMORY & PATTERNS
    // ================================================================

    fun detectPatterns(
        history: List<com.example.nexora.data.DailyProgressEntity>
    ): AiMemory {

        if (history.size < 3) {
            return AiMemory(
                legacyPatterns = listOf(
                    AiProductivityPattern(
                        type = AiPatternType.INSUFFICIENT_DATA,
                        title = "Learning Phase",
                        description = "Nexora is observing your workflow. Your unique productivity patterns will appear once more history is recorded.",
                        confidence = 1.0f
                    )
                ),
                analyzedDays = history.size
            )
        }

        val patterns = mutableListOf<AiProductivityPattern>()

        val avgCompletion = history.map { it.tasksCompleted.toFloat() / Math.max(1, it.tasksPlanned) }.average().toFloat()
        
        if (avgCompletion > 0.8f) {
            patterns.add(AiProductivityPattern(
                type = AiPatternType.WORKLOAD_CONSISTENCY,
                title = "High Execution Pattern",
                description = "You consistently complete over 80% of your planned work. This indicates strong planning accuracy.",
                confidence = 0.9f
            ))
        }

        val overloadedDays = history.count { it.tasksPlanned > 7 && it.tasksCompleted < it.tasksPlanned / 2 }
        if (overloadedDays >= 2) {
            patterns.add(AiProductivityPattern(
                type = AiPatternType.COMPLETION_ACCURACY,
                title = "Over-planning Trend",
                description = "Nexora noticed some days have too many tasks, reducing total completion. Aim for 3-5 high-value items.",
                confidence = 0.8f
            ))
        }

        return AiMemory(
            legacyPatterns = patterns,
            analyzedDays = history.size
        )
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private fun extractDurationMinutes(duration: String): Int {
        val value = duration.lowercase().trim()
        val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: return 0
        return if (value.contains("hour") || value.contains("hr")) number * 60 else number
    }

    private fun taskPriorityToAiPriority(priority: TaskPriority): AiPriority {
        return when (priority) {
            TaskPriority.URGENT -> AiPriority.CRITICAL
            TaskPriority.HIGH -> AiPriority.HIGH
            TaskPriority.MEDIUM -> AiPriority.MEDIUM
            TaskPriority.LOW -> AiPriority.LOW
        }
    }

    private fun buildNextTaskMessage(task: PremiumTask, factors: List<ReasoningFactor>): String {
        val primaryFactor = factors.firstOrNull()?.evidence ?: "it matches your current focus needs"
        return "Nexora identifies \"${task.title}\" as your best next step because $primaryFactor."
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
