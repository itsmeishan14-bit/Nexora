package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

class AiPlanner {
    fun decomposeGoal(
        context: AiContext,
        goalTitle: String,
        goalDescription: String = ""
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

        steps.add(
            AiGoalStep(
                title = "Define the first milestone",
                description = "Clarify what meaningful progress toward \"$cleanTitle\" looks like.",
                priority = AiPriority.HIGH,
                estimatedDuration = "20 min",
                order = 1
            )
        )

        steps.add(
            AiGoalStep(
                title = "Build the foundation",
                description = "Learn or complete the fundamental work required for \"$cleanTitle\".",
                priority = AiPriority.HIGH,
                estimatedDuration = "60 min",
                order = 2
            )
        )

        steps.add(
            AiGoalStep(
                title = "Complete a practical step",
                description = "Turn the goal into a concrete piece of work you can finish.",
                priority = AiPriority.MEDIUM,
                estimatedDuration = "60 min",
                order = 3
            )
        )

        steps.add(
            AiGoalStep(
                title = "Review your progress",
                description = "Evaluate what is complete and identify the next useful step.",
                priority = AiPriority.MEDIUM,
                estimatedDuration = "20 min",
                order = 4
            )
        )

        return AiGoalDecomposition(
            goalTitle = cleanTitle,
            summary =
                if (goalDescription.isBlank()) {
                    "Nexora has broken your goal into a sequence of practical steps."
                } else {
                    "Nexora used your goal and description to create a practical starting sequence."
                },
            steps = steps
        )
    }

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
                date = java.time.LocalDate.now().toString(),
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
                totalMinutes += if (duration > 0) duration else 30 // Default 30 if unknown
            }
            
            // Limit to 6 tasks to prevent overwhelming
            if (selectedPlannedTasks.size >= 6) return@forEach
        }

        val summary = if (selectedPlannedTasks.size >= 4) {
            "You have a productive day ahead. Focus on these ${selectedPlannedTasks.size} tasks to make meaningful progress toward your goals."
        } else {
            "Today's plan is focused and achievable. Completing these tasks will build great momentum."
        }

        return NexoraDailyPlan(
            date = java.time.LocalDate.now().toString(),
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

        // Priority
        score += when (task.priority) {
            TaskPriority.URGENT -> {
                reasons.add("Urgent priority")
                150
            }
            TaskPriority.HIGH -> {
                reasons.add("High priority")
                80
            }
            TaskPriority.MEDIUM -> 40
            TaskPriority.LOW -> 10
        }

        // Goal importance
        val linkedGoal = context.activeGoals.find { it.title == task.goalTitle }
        if (linkedGoal != null) {
            score += 40
            reasons.add("Goal: ${linkedGoal.title}")

            if (linkedGoal.progress < 0.3f) {
                score += 20
                reasons.add("Goal needs attention")
            }
            
            if (linkedGoal.targetDate.isNotBlank()) {
                score += 15
            }
        }

        // Today's progress - if we already completed many tasks of a goal, maybe focus on another?
        // Or if we worked on a goal today, keep going?
        // For now, simple scoring.

        // Duration
        val duration = extractDurationMinutes(task.duration)
        if (duration in 1..45) {
            score += 10
        }

        val primaryReason = reasons.firstOrNull() ?: "Next best step"
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

        // 1. Completion Accuracy / Workload Pattern
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

        // 2. Carry-over tendency
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

        // 3. Focus Trend
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
    // CHAT
    // ================================================================

    fun chat(
        context: AiContext,
        userMessage: String
    ): String {

        val input = userMessage.lowercase().trim()

        return when {

            input.contains("plan") -> {
                val plan = createDailyPlan(context)
                if (plan.tasks.isEmpty()) {
                    plan.summary
                } else {
                    val taskList = plan.tasks.joinToString("\n") { 
                        "${it.recommendedOrder}. ${it.task.title} (${it.task.duration})" 
                    }
                    "${plan.summary}\n\n$taskList"
                }
            }

            input.contains("next") || 
            input.contains("work on") || 
            input.contains("priority") -> {
                val next = analyze(context).find { it.type == AiRecommendationType.NEXT_TASK }
                next?.message ?: "I don't see any urgent tasks right now."
            }

            input.contains("goal") -> {
                val goalAnalysis = analyzeGoals(context).firstOrNull()
                goalAnalysis?.message ?: "You haven't set any active goals yet."
            }

            input.contains("overload") || 
            input.contains("many tasks") || 
            input.contains("behind") -> {
                if (context.incompleteTasks.size >= 8) {
                    "You're currently carrying ${context.incompleteTasks.size} tasks. Focus on finishing one high-priority item rather than starting new ones."
                } else {
                    "Your workload looks manageable with ${context.incompleteTasks.size} tasks."
                }
            }

            input.contains("productivity") || 
            input.contains("progress") ||
            input.contains("pattern") ||
            input.contains("consistent") -> {
                if (context.memory.patterns.isEmpty()) {
                    "I don't have enough history to detect specific patterns yet. Keep using Nexora and I'll analyze your consistency over time."
                } else {
                    val patternList = context.memory.patterns.joinToString("\n") { "- ${it.title}: ${it.description}" }
                    "Here is what I've learned about your productivity recently:\n\n$patternList"
                }
            }

            input.contains("break down") || 
            input.contains("decompose") -> {
                "To break down a goal, please use the Goal Decomposer tool or tell me the specific goal title."
            }

            else -> {
                "I'm Nexora, your productivity assistant. I can help you plan your day, prioritize tasks, or review your goals. Try asking 'What should I work on next?'"
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