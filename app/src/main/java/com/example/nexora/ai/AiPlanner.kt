package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority
import com.example.nexora.uii.NexoraGoal
import java.time.LocalDate

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
            "Nexora analyzed your $category goal and generated a sequence of actionable steps to ensure steady progress."
        }

        return AiGoalDecomposition(
            goalTitle = cleanTitle,
            summary = summary,
            steps = steps
        )
    }

    /**
     * Legacy signature for compatibility.
     */
    fun decomposeGoal(goalTitle: String, goalDescription: String = ""): AiGoalDecomposition {
        return decomposeGoal(goalTitle, goalDescription, "Personal")
    }

    fun analyze(context: AiContext): List<AiRecommendation> {
        val proactive = getProactiveInsights(context)
        val basic = getBasicRecommendations(context)
        
        return (proactive + basic).distinctBy { it.title + it.type }
            .sortedByDescending { it.priority }
    }

    private fun getBasicRecommendations(context: AiContext): List<AiRecommendation> {
        val recommendations = mutableListOf<AiRecommendation>()
        
        // NEXT BEST TASK
        val nextTask: PremiumTask? = chooseNextTask(context)
        if (nextTask != null) {
            val scoreResult = improvedTaskScore(nextTask, context)
            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.NEXT_TASK,
                    title = "Start with this",
                    message = buildNextTaskMessage(nextTask, context, scoreResult.second),
                    priority = taskPriorityToAiPriority(nextTask.priority),
                    relatedTaskId = nextTask.id,
                    actionLabel = "Start task",
                    evidence = "Nexora scored this task as ${scoreResult.first} based on priority and goal alignment."
                )
            )
        }
        
        return recommendations
    }

    fun getProactiveInsights(context: AiContext): List<AiRecommendation> {
        val insights = mutableListOf<AiRecommendation>()

        // 1. WORKLOAD INTELLIGENCE
        val plannedToday = context.tasksPlannedToday
        val avgCompleted = if (context.memory.analyzedDays > 0) {
            // Heuristic capacity based on patterns
            val accuracyPattern = context.memory.patterns.find { it.type == AiPatternType.COMPLETION_ACCURACY }
            if (accuracyPattern != null) 3 else 5
        } else 5

        if (plannedToday > avgCompleted * 1.5) {
            insights.add(
                AiRecommendation(
                    type = AiRecommendationType.WARNING,
                    title = "Heavy Workload Detected",
                    message = "You've planned $plannedToday tasks, which is significantly more than your typical completion rate.",
                    evidence = "Planned: $plannedToday, Typical capacity: $avgCompleted. Reducing workload prevents burnout.",
                    priority = AiPriority.HIGH,
                    confidence = AiConfidence.HIGH,
                    actionLabel = "Review Today's Plan"
                )
            )
        }

        // 2. GOAL INTELLIGENCE
        val neglectedGoal = context.activeGoals.find { goal ->
            goal.progress < 0.5f && context.tasks.none { it.goalTitle == goal.title && !it.completed }
        }

        if (neglectedGoal != null) {
            insights.add(
                AiRecommendation(
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Goal Neglected",
                    message = "\"${neglectedGoal.title}\" is falling behind and has no tasks planned today.",
                    evidence = "Progress: ${(neglectedGoal.progress * 100).toInt()}%. Even a small task can restart momentum.",
                    priority = AiPriority.MEDIUM,
                    confidence = AiConfidence.MEDIUM,
                    relatedGoalId = neglectedGoal.id,
                    actionLabel = "Work on Goal"
                )
            )
        }

        // 3. TASK INTELLIGENCE
        val urgentIgnored = context.incompleteTasks.find { 
            it.priority == TaskPriority.URGENT 
        }
        
        if (urgentIgnored != null) {
            insights.add(
                AiRecommendation(
                    type = AiRecommendationType.WARNING,
                    title = "Urgent Task Pending",
                    message = "\"${urgentIgnored.title}\" is marked as urgent but remains incomplete.",
                    evidence = "Priority: URGENT. High-priority items should be addressed early in the day.",
                    priority = AiPriority.CRITICAL,
                    confidence = AiConfidence.HIGH,
                    relatedTaskId = urgentIgnored.id,
                    actionLabel = "View Task"
                )
            )
        }

        // 4. CARRY-OVER INTELLIGENCE
        if (context.carriedTasks > 3) {
            insights.add(
                AiRecommendation(
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "High Task Carry-over",
                    message = "You've carried over ${context.carriedTasks} tasks from previous days. This often leads to cumulative overload.",
                    evidence = "History shows ${context.carriedTasks} unfinished tasks from previous sessions.",
                    priority = AiPriority.MEDIUM,
                    confidence = AiConfidence.HIGH,
                    actionLabel = "Review Workload"
                )
            )
        }

        return insights.sortedByDescending { it.priority }
    }

    // ================================================================
    // DAILY PLAN
    // ================================================================

    fun createDailyPlan(
        context: AiContext
    ): NexoraDailyPlan {

        val incompleteTasks =
            context.incompleteTasks
                .map { task ->
                    val scored = improvedTaskScore(task, context)
                    task to scored
                }
                .sortedByDescending { it.second.first }

        if (incompleteTasks.isEmpty()) {
            return NexoraDailyPlan(
                date = LocalDate.now().toString(),
                summary = "There are no unfinished tasks in Nexora right now. Use the time to review your goals or plan your next meaningful step."
            )
        }

        // Realistic workload limit: 300 minutes (5 hours)
        var totalMinutes = 0
        val maxMinutes = 300
        val selectedPlannedTasks = mutableListOf<PlannedTask>()

        incompleteTasks.forEach { (task, scoreResult) ->
            val duration = extractDurationMinutes(task.duration)
            val reason = scoreResult.second

            // Always take top 2 regardless of duration, then fit others
            if (selectedPlannedTasks.size < 2 || (totalMinutes + duration <= maxMinutes)) {
                selectedPlannedTasks.add(
                    PlannedTask(
                        task = task,
                        reason = reason,
                        recommendedOrder = selectedPlannedTasks.size + 1
                    )
                )
                totalMinutes += if (duration > 0) duration else 30 
            }
            
            if (selectedPlannedTasks.size >= 6) return@forEach
        }

        val summary = if (selectedPlannedTasks.size >= 4) {
            "You have a productive day ahead. Focus on these ${selectedPlannedTasks.size} tasks to make meaningful progress toward your goals."
        } else {
            "Today's plan is focused and achievable. Completing these tasks will build great momentum."
        }

        return NexoraDailyPlan(
            date = LocalDate.now().toString(),
            tasks = selectedPlannedTasks,
            totalDurationMinutes = totalMinutes,
            summary = summary
        )
    }

    private fun improvedTaskScore(
        task: PremiumTask,
        context: AiContext
    ): Pair<Int, String> {
        var score = 0
        val reasons = mutableListOf<String>()

        // 1. Priority Base
        when (task.priority) {
            TaskPriority.URGENT -> {
                score += 150
                reasons.add("Urgent priority")
            }
            TaskPriority.HIGH -> {
                score += 80
                reasons.add("High priority")
            }
            TaskPriority.MEDIUM -> score += 40
            TaskPriority.LOW -> score += 10
        }

        // 2. Goal Alignment
        val linkedGoal = context.activeGoals.find { it.title == task.goalTitle }
        if (linkedGoal != null) {
            score += 50
            reasons.add("Linked to active goal '${linkedGoal.title}'")

            // Progress-based boost
            if (linkedGoal.progress < 0.3f) {
                score += 30
                reasons.add("Goal needs early momentum")
            } else if (linkedGoal.progress > 0.8f) {
                score += 20
                reasons.add("Goal is near completion")
            }
        }

        // 3. Efficiency boost for small tasks when list is long
        val duration = extractDurationMinutes(task.duration)
        if (context.incompleteTasks.size > 5 && duration in 1..30) {
            score += 15
            reasons.add("Quick win to reduce list size")
        }

        val primaryReason = reasons.lastOrNull() ?: "Consistent progress step"
        return Pair(score, primaryReason)
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
                    message = "Create a goal and connect tasks to it so Nexora can help guide your progress.",
                    priority = AiPriority.LOW,
                    actionLabel = "Create a goal"
                )
            )
        }

        val recommendations = mutableListOf<AiRecommendation>()
        
        val lowestProgressGoal = context.activeGoals.minByOrNull { it.progress }
        if (lowestProgressGoal != null) {
            recommendations.add(
                AiRecommendation(
                    type = AiRecommendationType.GOAL_ACTION,
                    title = "Goal needs attention",
                    message = "\"${lowestProgressGoal.title}\" has only ${(lowestProgressGoal.progress * 100).toInt()}% progress. Choose one concrete task that moves this goal forward.",
                    priority = AiPriority.MEDIUM,
                    relatedGoalId = lowestProgressGoal.id,
                    evidence = "This is your active goal with the lowest completion percentage.",
                    actionLabel = "Take the next step"
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

        if (context.tasksPlannedToday == 0) {
            return listOf(
                AiRecommendation(
                    type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                    title = "Nothing planned yet",
                    message = "Add a few meaningful tasks and Nexora can start learning how your daily workload behaves.",
                    priority = AiPriority.LOW,
                    actionLabel = "Plan your day"
                )
            )
        }

        val completionRate = context.tasksCompletedToday.toFloat() / context.tasksPlannedToday.toFloat()
        val evidence = "Completed: ${context.tasksCompletedToday}, Planned: ${context.tasksPlannedToday}."

        return when {
            completionRate >= 0.8f -> {
                listOf(
                    AiRecommendation(
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "You're building momentum",
                        message = "You've completed ${context.tasksCompletedToday} tasks today. You're maintaining a high standard of focus.",
                        evidence = evidence,
                        priority = AiPriority.LOW
                    )
                )
            }
            completionRate >= 0.5f -> {
                listOf(
                    AiRecommendation(
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Solid progress",
                        message = "You're halfway through your plan. Finish the most important remaining task before adding more.",
                        evidence = evidence,
                        priority = AiPriority.MEDIUM
                    )
                )
            }
            else -> {
                listOf(
                    AiRecommendation(
                        type = AiRecommendationType.PRODUCTIVITY_INSIGHT,
                        title = "Focus before adding more",
                        message = "Your current completion rate is lower than usual. Try focusing on one task until completion.",
                        evidence = evidence,
                        priority = AiPriority.MEDIUM
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
                patterns = listOf(
                    AiProductivityPattern(
                        type = AiPatternType.INSUFFICIENT_DATA,
                        title = "Building intelligence",
                        description = "Keep using Nexora. Your productivity patterns will appear as more history builds.",
                        confidence = 1.0f
                    )
                ),
                analyzedDays = history.size
            )
        }

        val patterns = mutableListOf<AiProductivityPattern>()

        val avgPlanned = history.map { it.tasksPlanned }.average()
        val avgCompleted = history.map { it.tasksCompleted }.average()
        
        if (avgPlanned > avgCompleted * 1.5 && avgPlanned > 3) {
            patterns.add(
                AiProductivityPattern(
                    type = AiPatternType.COMPLETION_ACCURACY,
                    title = "Plan vs Reality",
                    description = "You often plan more tasks than you complete. Try creating smaller, more focused daily plans.",
                    confidence = 0.8f,
                    severity = AiPriority.MEDIUM,
                    recommendation = "Limit your next plan to ${avgCompleted.toInt() + 1} priority tasks."
                )
            )
        }

        val carryOverDays = history.count { it.carriedTasks > 0 }
        if (carryOverDays >= history.size * 0.7) {
            patterns.add(
                AiProductivityPattern(
                    type = AiPatternType.WORKLOAD_CONSISTENCY,
                    title = "Task Carry-over",
                    description = "Tasks are carried forward on most days. This might indicate that your initial task estimates are too low or workload is too high.",
                    confidence = 0.9f,
                    severity = AiPriority.HIGH
                )
            )
        }

        val firstHalfFocus = history.takeLast(history.size / 2).map { it.focusMinutes }.average()
        val secondHalfFocus = history.take(history.size / 2).map { it.focusMinutes }.average()
        
        if (secondHalfFocus > firstHalfFocus * 1.2) {
            patterns.add(
                AiProductivityPattern(
                    type = AiPatternType.FOCUS_TREND,
                    title = "Rising Focus",
                    description = "Your daily focus time has been increasing. You're building strong deep work habits.",
                    confidence = 0.7f,
                    severity = AiPriority.LOW
                )
            )
        } else if (secondHalfFocus < firstHalfFocus * 0.8 && firstHalfFocus > 60) {
             patterns.add(
                AiProductivityPattern(
                    type = AiPatternType.FOCUS_TREND,
                    title = "Focus Dip",
                    description = "Your focus time has decreased recently. Consider scheduling a distraction-free block tomorrow.",
                    confidence = 0.7f,
                    severity = AiPriority.MEDIUM
                )
            )
        }

        return AiMemory(
            patterns = patterns,
            analyzedDays = history.size
        )
    }

    // ================================================================
    // TASK SELECTION & MESSAGES
    // ================================================================

    private fun chooseNextTask(context: AiContext): PremiumTask? {
        return context.incompleteTasks.maxByOrNull { taskScore(it, context) }
    }

    private fun taskScore(task: PremiumTask, context: AiContext): Int {
        var score = 0
        score += when (task.priority) {
            TaskPriority.URGENT -> 100
            TaskPriority.HIGH -> 70
            TaskPriority.MEDIUM -> 40
            TaskPriority.LOW -> 20
        }
        if (task.goalTitle != null && context.activeGoals.any { it.title == task.goalTitle }) {
            score += 30
        }
        val durationMinutes = extractDurationMinutes(task.duration)
        if (context.incompleteTasks.size >= 6 && durationMinutes in 1..30) {
            score += 15
        }
        if (task.completed) score -= 1000
        return score
    }

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

    private fun buildNextTaskMessage(task: PremiumTask, context: AiContext, reason: String): String {
        return "Nexora recommends starting \"${task.title}\". $reason."
    }
}
