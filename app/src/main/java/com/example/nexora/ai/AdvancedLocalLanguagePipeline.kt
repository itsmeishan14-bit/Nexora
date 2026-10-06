package com.example.nexora.ai

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Advanced offline natural language processing pipeline.
 * Formulates the canonical structured request interpretation for Nexora.
 * Understands structural grammar (questions, directives, advice, explanations, predictions, temporal queries)
 * and resolves entities deterministically.
 */
class AdvancedLocalLanguagePipeline {

    /**
     * Processes a user message into a canonical structured language result.
     */
    fun process(
        message: String,
        context: AiContext,
        convContext: AiConversationContext = AiConversationContext(),
        referenceDate: LocalDate = LocalDate.now()
    ): AiLanguageResult {
        // Reset expired conversation context
        val effectiveConvContext = if (convContext.isExpired()) AiConversationContext() else convContext

        // 1. Text Normalization
        val normalized = normalize(message)
        if (normalized.isBlank()) return AiLanguageResult(intent = AiDecisionType.NO_ACTION, confidence = AiConfidence.LOW)

        // 2. Check for Confirmation / Cancellation if there's a pending action
        if (effectiveConvContext.pendingAction != null) {
            val isConfirm = isConfirmation(normalized)
            val isCancel = isCancellation(normalized)

            if (isConfirm) {
                val action = effectiveConvContext.pendingAction
                return AiLanguageResult(
                    intent = mapActionToDecision(action.type),
                    confidence = AiConfidence.HIGH,
                    isConfirmation = true,
                    requiresMutation = true,
                    requestedAction = action.type,
                    targetTaskId = action.taskId,
                    targetGoalId = action.goalId
                )
            } else if (isCancel) {
                return AiLanguageResult(
                    intent = AiDecisionType.CANCEL,
                    confidence = AiConfidence.HIGH,
                    isCancellation = true
                )
            }
        }

        // 3. Handle Active Clarification Follow-ups
        if (effectiveConvContext.activeClarification != null && !isNewDirective(normalized)) {
            return handleClarificationFollowUp(normalized, effectiveConvContext, context)
        }

        // 4. Structural Semantic Classification
        val temporalRange = resolveTemporalRange(normalized, referenceDate)
        val structuralResult = classifyIntent(normalized, message, context, effectiveConvContext, temporalRange)

        // 5. Entity & Parameter Extraction
        val entities = extractEntities(normalized, structuralResult.intent, message).toMutableMap()
        temporalRange?.let {
            entities["temporalScope"] = it.scope.name
            entities["startDate"] = it.startDate.toString()
            entities["endDate"] = it.endDate.toString()
        }

        // 6. Contextual Reference Resolution ("it", "the previous task", "that goal")
        val resolvedEntities = resolveContextualReferences(entities, effectiveConvContext, context, normalized).toMutableMap()

        // 7. Entity Grounding & Ambiguity Verification
        var finalIntent = structuralResult.intent
        var finalConfidence = structuralResult.confidence
        var requiresClarification = structuralResult.requiresClarification
        var clarificationNeeded: AiClarification? = null
        var targetTaskId: Long? = resolvedEntities["taskId"] as? Long
        var targetGoalId: Long? = resolvedEntities["goalId"] as? Long
        var targetTaskTitle: String? = null
        var targetGoalTitle: String? = null

        val originalIntent = structuralResult.intent
        val originalRequestedAction = when (originalIntent) {
            AiDecisionType.CREATE_TASK -> AiActionType.CREATE_TASK
            AiDecisionType.COMPLETE_TASK -> AiActionType.COMPLETE_TASK
            AiDecisionType.DELETE_TASK -> AiActionType.DELETE_TASK
            AiDecisionType.UPDATE_TASK -> AiActionType.UPDATE_TASK
            AiDecisionType.CREATE_GOAL -> AiActionType.CREATE_GOAL
            AiDecisionType.DELETE_GOAL -> AiActionType.DELETE_GOAL
            AiDecisionType.UPDATE_GOAL -> AiActionType.UPDATE_GOAL
            AiDecisionType.DECOMPOSE_GOAL -> AiActionType.DECOMPOSE_GOAL
            AiDecisionType.DELETE_ALL_TASKS -> AiActionType.DELETE_ALL_TASKS
            AiDecisionType.COMPLETE_ALL_TASKS -> AiActionType.COMPLETE_ALL_TASKS
            AiDecisionType.CREATE_AUTOMATION -> AiActionType.CREATE_AUTOMATION
            AiDecisionType.TOGGLE_AUTOMATION -> AiActionType.TOGGLE_AUTOMATION
            AiDecisionType.DELETE_AUTOMATION -> AiActionType.DELETE_AUTOMATION
            AiDecisionType.UPDATE_AUTOMATION -> AiActionType.UPDATE_AUTOMATION
            else -> null
        }

        val taskActionVerb = when (originalIntent) {
            AiDecisionType.COMPLETE_TASK -> "complete"
            AiDecisionType.DELETE_TASK -> "delete"
            AiDecisionType.UPDATE_TASK -> "update"
            else -> "process"
        }

        val queryTitle = resolvedEntities["title"]?.toString() ?: ""
        val ambiguousTitles = listOf("It", "That", "This", "Task", "My task", "The task", "This task", "That task", "Something")

        // Task Action Entity Verification
        if (originalIntent in listOf(AiDecisionType.COMPLETE_TASK, AiDecisionType.DELETE_TASK, AiDecisionType.UPDATE_TASK)) {
            if (targetTaskId != null) {
                val found = context.tasks.find { it.id == targetTaskId }
                targetTaskTitle = found?.title
            } else if (queryTitle.isNotBlank() && queryTitle !in ambiguousTitles) {
                val match = AiEntityResolver.resolveTask(queryTitle, context.tasks, effectiveConvContext)
                when (match) {
                    is ResolutionResult.Success -> {
                        targetTaskId = match.entity.id
                        targetTaskTitle = match.entity.title
                        resolvedEntities["taskId"] = match.entity.id
                        finalConfidence = AiConfidence.HIGH
                    }
                    is ResolutionResult.Ambiguous -> {
                        finalIntent = AiDecisionType.AMBIGUOUS
                        finalConfidence = AiConfidence.LOW
                        requiresClarification = true
                        clarificationNeeded = AiClarification(
                            question = "I found multiple matching tasks (${match.candidates.joinToString { it.title }}). Which one would you like to $taskActionVerb?",
                            intent = originalIntent,
                            missingField = "taskId",
                            candidates = match.candidates.map { it.id },
                            originalQuery = message
                        )
                    }
                    is ResolutionResult.NotFound -> {
                        // Named task not found in active list
                        targetTaskId = null
                        targetTaskTitle = null
                        resolvedEntities.remove("taskId")
                        finalConfidence = AiConfidence.MEDIUM
                    }
                }
            } else if (context.tasks.size == 1 && (queryTitle.isNotBlank() && queryTitle in ambiguousTitles)) {
                val single = context.tasks.first()
                targetTaskId = single.id
                targetTaskTitle = single.title
                resolvedEntities["taskId"] = single.id
                finalConfidence = AiConfidence.HIGH
            } else if (context.tasks.size > 1 && (queryTitle.isBlank() || queryTitle in ambiguousTitles)) {
                finalIntent = AiDecisionType.AMBIGUOUS
                finalConfidence = AiConfidence.LOW
                requiresClarification = true
                clarificationNeeded = AiClarification(
                    question = "I found multiple tasks (${context.tasks.joinToString { it.title }}). Which one would you like to $taskActionVerb?",
                    intent = originalIntent,
                    missingField = "taskId",
                    candidates = context.tasks.map { it.id },
                    originalQuery = message
                )
            } else {
                // Missing target or unknown entity
                finalIntent = AiDecisionType.CLARIFY
                finalConfidence = AiConfidence.LOW
                requiresClarification = true
                clarificationNeeded = AiClarification(
                    question = "Which task would you like to $taskActionVerb?",
                    intent = originalIntent,
                    missingField = "title",
                    originalQuery = message
                )
            }
        }

        // Goal Action Entity Verification
        val goalActionVerb = when (originalIntent) {
            AiDecisionType.DELETE_GOAL -> "delete"
            AiDecisionType.UPDATE_GOAL -> "update"
            AiDecisionType.DECOMPOSE_GOAL -> "decompose"
            else -> "process"
        }

        if (originalIntent in listOf(AiDecisionType.DELETE_GOAL, AiDecisionType.DECOMPOSE_GOAL, AiDecisionType.UPDATE_GOAL)) {
            val ambiguousGoalTitles = listOf("It", "That", "This", "Goal", "My goal", "The goal", "This goal", "That goal")
            if (targetGoalId != null) {
                val found = context.goals.find { it.id == targetGoalId }
                targetGoalTitle = found?.title
            } else if (queryTitle.isNotBlank() && queryTitle !in ambiguousGoalTitles) {
                val match = AiEntityResolver.resolveGoal(queryTitle, context.goals, effectiveConvContext)
                when (match) {
                    is ResolutionResult.Success -> {
                        targetGoalId = match.entity.id
                        targetGoalTitle = match.entity.title
                        resolvedEntities["goalId"] = match.entity.id
                        finalConfidence = AiConfidence.HIGH
                    }
                    is ResolutionResult.Ambiguous -> {
                        finalIntent = AiDecisionType.AMBIGUOUS
                        finalConfidence = AiConfidence.LOW
                        requiresClarification = true
                        clarificationNeeded = AiClarification(
                            question = "I found multiple matching goals (${match.candidates.joinToString { it.title }}). Which one would you like to $goalActionVerb?",
                            intent = originalIntent,
                            missingField = "goalId",
                            candidates = match.candidates.map { it.id },
                            originalQuery = message
                        )
                    }
                    is ResolutionResult.NotFound -> {
                        targetGoalId = null
                        targetGoalTitle = null
                        resolvedEntities.remove("goalId")
                        if (originalIntent == AiDecisionType.DECOMPOSE_GOAL) {
                            resolvedEntities["title"] = queryTitle
                            finalConfidence = AiConfidence.HIGH
                        } else {
                            finalConfidence = AiConfidence.MEDIUM
                        }
                    }
                }
            } else if (context.goals.size > 1 && (queryTitle.isBlank() || queryTitle in ambiguousGoalTitles)) {
                finalIntent = AiDecisionType.AMBIGUOUS
                finalConfidence = AiConfidence.LOW
                requiresClarification = true
                clarificationNeeded = AiClarification(
                    question = "I found multiple goals (${context.goals.joinToString { it.title }}). Which one would you like to $goalActionVerb?",
                    intent = originalIntent,
                    missingField = "goalId",
                    candidates = context.goals.map { it.id },
                    originalQuery = message
                )
            } else if (context.goals.size == 1 && (queryTitle.isNotBlank() && queryTitle in ambiguousGoalTitles)) {
                val single = context.goals.first()
                targetGoalId = single.id
                targetGoalTitle = single.title
                resolvedEntities["goalId"] = single.id
                finalConfidence = AiConfidence.HIGH
            } else {
                finalIntent = AiDecisionType.CLARIFY
                finalConfidence = AiConfidence.LOW
                requiresClarification = true
                clarificationNeeded = AiClarification(
                    question = "Which goal would you like to $goalActionVerb?",
                    intent = originalIntent,
                    missingField = "title",
                    originalQuery = message
                )
            }
        }

        val hasValidTarget = when (originalIntent) {
            AiDecisionType.COMPLETE_TASK, AiDecisionType.DELETE_TASK, AiDecisionType.UPDATE_TASK -> targetTaskId != null
            AiDecisionType.DELETE_GOAL, AiDecisionType.UPDATE_GOAL -> targetGoalId != null
            AiDecisionType.DECOMPOSE_GOAL -> targetGoalId != null || (queryTitle.isNotBlank() && queryTitle !in ambiguousTitles)
            else -> true
        }

        val isTargetMissing = queryTitle.isBlank() || queryTitle in ambiguousTitles
        if ((originalIntent in listOf(AiDecisionType.UPDATE_TASK, AiDecisionType.UPDATE_GOAL, AiDecisionType.DELETE_TASK, AiDecisionType.DELETE_GOAL, AiDecisionType.COMPLETE_TASK)) &&
            isTargetMissing && !hasValidTarget && finalIntent != AiDecisionType.AMBIGUOUS && !requiresClarification) {
            finalIntent = AiDecisionType.CLARIFY
            requiresClarification = true
            clarificationNeeded = AiClarification(
                question = if (originalIntent in listOf(AiDecisionType.UPDATE_TASK, AiDecisionType.DELETE_TASK, AiDecisionType.COMPLETE_TASK)) {
                    "Which task would you like to $taskActionVerb?"
                } else {
                    "Which goal would you like to $goalActionVerb?"
                },
                intent = originalIntent,
                missingField = "title",
                originalQuery = message
            )
        }

        val hasUpdateFields = when (originalIntent) {
            AiDecisionType.UPDATE_TASK -> resolvedEntities.containsKey("newTitle") || resolvedEntities.containsKey("priority") || resolvedEntities.containsKey("duration") || resolvedEntities.containsKey("category")
            AiDecisionType.UPDATE_GOAL -> resolvedEntities.containsKey("newTitle") || resolvedEntities.containsKey("category") || resolvedEntities.containsKey("targetDate")
            else -> true
        }

        if ((originalIntent == AiDecisionType.UPDATE_TASK || originalIntent == AiDecisionType.UPDATE_GOAL) && !hasUpdateFields && !requiresClarification) {
            requiresClarification = true
            finalIntent = AiDecisionType.CLARIFY
            clarificationNeeded = AiClarification(
                question = if (originalIntent == AiDecisionType.UPDATE_TASK) {
                    val targetName = targetTaskTitle ?: queryTitle.takeIf { it.isNotBlank() } ?: "the task"
                    "What would you like to update about \"$targetName\"? You can specify a new title, priority, duration, or category."
                } else {
                    val targetName = targetGoalTitle ?: queryTitle.takeIf { it.isNotBlank() } ?: "the goal"
                    "What would you like to update about \"$targetName\"? You can specify a new title or category."
                },
                intent = originalIntent,
                missingField = "update_fields",
                originalQuery = message
            )
        }

        val effectiveRequiresMutation = structuralResult.requiresMutation &&
                !requiresClarification &&
                hasValidTarget &&
                hasUpdateFields &&
                finalIntent != AiDecisionType.AMBIGUOUS &&
                finalIntent != AiDecisionType.CLARIFY

        return AiLanguageResult(
            intent = finalIntent,
            confidence = finalConfidence,
            entities = resolvedEntities,
            textResponse = clarificationNeeded?.question,
            clarificationNeeded = clarificationNeeded,
            temporalRange = temporalRange,
            requiresMultiStepReasoning = structuralResult.requiresMultiStepReasoning,
            requiresMutation = effectiveRequiresMutation,
            requiresClarification = requiresClarification,
            targetTaskId = targetTaskId,
            targetGoalId = targetGoalId,
            targetTaskTitle = targetTaskTitle,
            targetGoalTitle = targetGoalTitle,
            requestedAction = originalRequestedAction
        )
    }

    private data class StructuralClassification(
        val intent: AiDecisionType,
        val confidence: AiConfidence,
        val requiresMultiStepReasoning: Boolean = false,
        val requiresMutation: Boolean = false,
        val requiresClarification: Boolean = false
    )

    private fun classifyIntent(
        lower: String,
        rawMessage: String,
        context: AiContext,
        convContext: AiConversationContext,
        temporalRange: TemporalRange?
    ): StructuralClassification {

        // ─────────────────────────────────────────────────────────────
        // 1. STRUCTURAL CLASS: EXPLANATION / DEFINITION / CONCEPT
        // Queries asking for conceptual explanations must NEVER mutate.
        val isExplanation = temporalRange == null && !lower.contains("planned") && !lower.contains("scheduled") && (
            lower.matches(Regex("(?i)^\\s*(explain|can you explain|could you explain|what does .+ mean|why is .+ useful|why is .+ important|role of|what is the role of|meaning of|how does .+ work|how do i delete|how to delete|tell me how to delete|can you explain .+ deletion|what is task deletion|what is goal deletion)\\b.*")) ||
            lower.contains(Regex("(?i)\\b(explain goal decomposition|what is goal decomposition|role of goal decomposition|why is goal decomposition|meaning of goal decomposition|what does decompose mean)\\b")) ||
            lower.contains(Regex("(?i)\\b(explain task prioritization|what is task prioritization|role of task prioritization)\\b")) ||
            lower.contains(Regex("(?i)\\b(explain time blocking|what is time blocking|how does time blocking work|explain carry forward|what is carry forward)\\b")) ||
            lower.contains("can you explain task deletion") ||
            lower.contains("can you explain goal deletion") ||
            lower.contains("what is task deletion") ||
            lower.contains("how do i delete a task") ||
            lower.contains("how do i delete a goal") ||
            lower.contains("could you tell me how to delete") ||
            lower.matches(Regex("(?i)^\\s*what is\\s+(the\\s+)?(concept|role|purpose|definition|meaning)\\b.*"))
        )

        if (isExplanation) {
            return StructuralClassification(
                intent = AiDecisionType.EXPLANATION,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = false,
                requiresMutation = false
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 2. STRUCTURAL CLASS: ADVICE / OPINION / EVALUATION
        // "Should I delete this task?", "Why should I prioritize this task?", "Do you think I should complete this?"
        // These ask for counsel, NOT execution of an action.
        // ─────────────────────────────────────────────────────────────
        val isAdvisoryQuestion = lower.startsWith("should i") ||
            lower.startsWith("do you think i should") ||
            lower.startsWith("why should i") ||
            lower.startsWith("would you recommend") ||
            lower.startsWith("is it a good idea to") ||
            lower.startsWith("can you tell me which task") ||
            lower.startsWith("which task should i") ||
            lower.contains("which task should i delete") ||
            lower.contains("should i delete") ||
            lower.contains("should i decompose") ||
            lower.contains("why should i prioritize") ||
            lower.contains("do you think i should complete")

        if (isAdvisoryQuestion) {
            return StructuralClassification(
                intent = AiDecisionType.SHOW_INSIGHT,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = false,
                requiresMutation = false
            )
        }

        // Historical inquiries ("Did I complete my Java task yesterday?", "Did I finish...")
        val isHistoricalInquiry = lower.startsWith("did i") || lower.startsWith("have i") ||
            lower.contains("did i complete") || lower.contains("did i finish") || lower.contains("did i accomplish")
        if (isHistoricalInquiry) {
            return StructuralClassification(
                intent = AiDecisionType.SHOW_INSIGHT,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = false,
                requiresMutation = false
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 3. STRUCTURAL CLASS: CONVERSATION & SOCIAL
        // ─────────────────────────────────────────────────────────────
        if (lower.contains(Regex("(?i)\\b(hello|hi|hey|greetings|good morning|good afternoon|good evening)\\b"))) {
            return StructuralClassification(AiDecisionType.GREETING, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(thanks|thank you|appreciated|awesome|cool|great|perfect)\\b"))) {
            return StructuralClassification(AiDecisionType.THANKS, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(bye|goodbye|see you|see ya|goodnight)\\b"))) {
            return StructuralClassification(AiDecisionType.GOODBYE, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(what can you do|who are you|how are you|tell me about yourself|what are your capabilities|features|what do you do)\\b"))) {
            return StructuralClassification(AiDecisionType.GENERAL_CONVERSATION, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(cancel|never mind|stop|forget it)\\b"))) {
            return StructuralClassification(AiDecisionType.CANCEL, AiConfidence.HIGH)
        }

        // ─────────────────────────────────────────────────────────────
        // 4. STRUCTURAL CLASS: MULTI-STEP REASONING / AGENT
        // Complex workflows requiring multiple tool actions & planning.
        // ─────────────────────────────────────────────────────────────
        val isMultiStepWorkflow = (lower.contains("organize") && (lower.contains("workload") || lower.contains("tasks") || lower.contains("create") || lower.contains("day"))) ||
            (lower.contains("clean") && (lower.contains("tasks") || lower.contains("up") || lower.contains("workload"))) ||
            ((lower.contains("decompose") || lower.contains("break down")) && (lower.contains("and create") || lower.contains("into tasks and add") || lower.contains("create the tasks"))) ||
            (lower.contains("help") && (lower.contains("goal") || lower.contains("with my") || lower.contains("on my")))

        if (isMultiStepWorkflow) {
            return StructuralClassification(
                intent = if (lower.contains("goal")) AiDecisionType.DECOMPOSE_GOAL else AiDecisionType.DAILY_PLAN,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = true,
                requiresMutation = true
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 5. STRUCTURAL CLASS: BULK ACTIONS (Requires high protection)
        // ─────────────────────────────────────────────────────────────
        if (lower.contains(Regex("(?i)\\b(delete|remove|clear)\\b")) &&
            (lower.contains(Regex("(?i)\\b(all|every|everything)\\b")) || lower == "delete all" || lower.startsWith("delete all") || lower.contains("all tasks"))
        ) {
            return StructuralClassification(
                intent = AiDecisionType.DELETE_ALL_TASKS,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = false,
                requiresMutation = true
            )
        }
        if (lower.contains(Regex("(?i)\\b(complete|finish|done|checked off|mark)\\b")) &&
            (lower.contains(Regex("(?i)\\b(all|every|everything)\\b")) || lower == "complete all" || lower.startsWith("complete all") || lower.contains("all tasks"))
        ) {
            return StructuralClassification(
                intent = AiDecisionType.COMPLETE_ALL_TASKS,
                confidence = AiConfidence.HIGH,
                requiresMultiStepReasoning = false,
                requiresMutation = true
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 6. STRUCTURAL CLASS: AUTOMATION DIRECTIVES
        // ─────────────────────────────────────────────────────────────
        val isAutomationKeyword = lower.contains("automation") || lower.contains("rule") ||
            lower.contains("assistant") || lower.contains("workload manager") ||
            lower.contains("goal progress guard") || lower.contains("conflict detector") ||
            lower.contains("morning plan")

        if (isAutomationKeyword) {
            return when {
                lower.contains(Regex("(?i)\\b(why did|explain|reason for)\\b")) ->
                    StructuralClassification(AiDecisionType.EXPLAIN_AUTOMATION, AiConfidence.HIGH)
                lower.contains(Regex("(?i)\\b(turn off|disable|enable|turn on|toggle)\\b")) ->
                    StructuralClassification(AiDecisionType.TOGGLE_AUTOMATION, AiConfidence.HIGH, requiresMutation = true)
                lower.contains(Regex("(?i)\\b(delete|remove)\\b")) ->
                    StructuralClassification(AiDecisionType.DELETE_AUTOMATION, AiConfidence.HIGH, requiresMutation = true)
                lower.contains(Regex("(?i)\\b(show|list|view|active)\\b")) ->
                    StructuralClassification(AiDecisionType.LIST_AUTOMATIONS, AiConfidence.HIGH)
                lower.contains(Regex("(?i)\\b(create|add|new)\\b")) ->
                    StructuralClassification(AiDecisionType.CREATE_AUTOMATION, AiConfidence.HIGH, requiresMutation = true)
                else ->
                    StructuralClassification(AiDecisionType.LIST_AUTOMATIONS, AiConfidence.MEDIUM)
            }
        }
        if (lower.contains(Regex("(?i)\\b(every morning|when i finish|if i carry|when my workload|when a goal)\\b"))) {
            return StructuralClassification(AiDecisionType.CREATE_AUTOMATION, AiConfidence.HIGH, requiresMutation = true)
        }

        // ─────────────────────────────────────────────────────────────
        // 7. STRUCTURAL CLASS: PREDICTIONS
        // ─────────────────────────────────────────────────────────────
        if (lower.contains(Regex("(?i)\\b(will i finish|when will i finish|estimated completion|goal finish|finish my goal|am i on track to finish)\\b"))) {
            return StructuralClassification(AiDecisionType.PREDICT_GOAL, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(postpone|delay risk|at risk|task at risk|most likely to postpone|delay)\\b")) && lower.contains("task")) {
            return StructuralClassification(AiDecisionType.PREDICT_TASK_RISK, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(taking on too much|schedule realistic|workload risk|overload risk|too much today|unrealistic)\\b"))) {
            return StructuralClassification(AiDecisionType.PREDICT_WORKLOAD, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(productivity trend|completion pace|my pace|accurate|accuracy|calibration|prediction quality)\\b")) ||
            (lower.contains("how productive") && temporalRange == null && !lower.contains("was i"))
        ) {
            return StructuralClassification(AiDecisionType.PREDICT_PRODUCTIVITY, AiConfidence.HIGH)
        }

        // ─────────────────────────────────────────────────────────────
        // 8. STRUCTURAL CLASS: PLANNING & RECOMMENDATIONS
        // ─────────────────────────────────────────────────────────────
        if (lower.contains(Regex("(?i)\\bplan\\b")) && lower.contains(Regex("(?i)\\b(day|today|schedule)\\b"))) {
            return StructuralClassification(AiDecisionType.DAILY_PLAN, AiConfidence.HIGH)
        }
        if (lower.contains(Regex("(?i)\\b(next task|what should i do next|what should i work on|what to work on|what's next|what should i focus on|do next|do now|what to do next|which task)\\b")) ||
            (lower.contains(Regex("(?i)\\bhighest priority|most important|top priority|urgent\\b")) && !lower.contains("create") && !lower.contains("add") && !lower.contains("complete") && !lower.contains("delete"))
        ) {
            return StructuralClassification(AiDecisionType.START_TASK, AiConfidence.HIGH)
        }

        // ─────────────────────────────────────────────────────────────
        // 9. STRUCTURAL CLASS: DIRECTIVE ACTIONS (Goal Decomposition & Mutations)
        // ─────────────────────────────────────────────────────────────
        // Goal Decomposition Directive (Higher priority than generic "list")
        if ((lower.contains("break down") || lower.contains("decompose") || (lower.contains("plan") && lower.contains("goal"))) && 
            (lower.contains("goal") || lower.contains("into tasks") || lower.contains("into steps") || lower.contains("sub-tasks") || lower.startsWith("decompose") || lower.startsWith("break down") || lower.startsWith("plan goal") || lower.startsWith("plan my goal"))) {
            return StructuralClassification(
                intent = AiDecisionType.DECOMPOSE_GOAL,
                confidence = AiConfidence.HIGH,
                requiresMutation = true
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 10. STRUCTURAL CLASS: DIRECTIVE ACTIONS (Single operations)
        // Explicit commands (create, complete, delete, update) take precedence
        // over temporal information queries.
        // ─────────────────────────────────────────────────────────────

        // Task Creation Directive
        if (lower.contains(Regex("(?i)\\b(create|add|new|remind me to)\\b")) && lower.contains(Regex("(?i)\\b(task|todo)\\b"))) {
            return StructuralClassification(
                intent = AiDecisionType.CREATE_TASK,
                confidence = AiConfidence.HIGH,
                requiresMutation = true
            )
        }

        // Goal Creation Directive
        if (lower.contains(Regex("(?i)\\b(create|add|new)\\b")) && lower.contains(Regex("(?i)\\b(goal|objective)\\b"))) {
            return StructuralClassification(
                intent = AiDecisionType.CREATE_GOAL,
                confidence = AiConfidence.HIGH,
                requiresMutation = true
            )
        }

        // Complete Task Directive
        val isCompleteDirective = lower.startsWith("complete") || lower.startsWith("finish") ||
            lower.startsWith("mark") || lower.contains("as complete") || lower.contains("as done") ||
            lower.contains(Regex("(?i)\\b(complete|finish|done|checked off)\\b"))
        if (isCompleteDirective) {
            return StructuralClassification(
                intent = AiDecisionType.COMPLETE_TASK,
                confidence = AiConfidence.HIGH,
                requiresMutation = true
            )
        }

        // Delete Task or Goal Directive
        if (lower.contains(Regex("(?i)\\b(delete|remove|destroy|trash|clear)\\b"))) {
            val intent = if (lower.contains("goal")) AiDecisionType.DELETE_GOAL else AiDecisionType.DELETE_TASK
            return StructuralClassification(
                intent = intent,
                confidence = AiConfidence.HIGH,
                requiresMutation = true
            )
        }

        // Update Goal Directive
        if (lower.contains(Regex("(?i)\\b(change|update|edit|rename|modify|set)\\b")) && lower.contains("goal")) {
            return StructuralClassification(
                intent = AiDecisionType.UPDATE_GOAL,
                confidence = AiConfidence.MEDIUM,
                requiresMutation = true
            )
        }

        // Update Task Directive
        if (lower.contains(Regex("(?i)\\b(change|update|edit|priority|rename|modify|set)\\b")) && (lower.contains("task") || lower.contains("it"))) {
            return StructuralClassification(
                intent = AiDecisionType.UPDATE_TASK,
                confidence = AiConfidence.MEDIUM,
                requiresMutation = true
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 11. STRUCTURAL CLASS: TEMPORAL QUERIES & INSIGHTS
        // ─────────────────────────────────────────────────────────────
        if (temporalRange != null) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.HIGH)
        }

        if (lower.contains(Regex("(?i)\\b(progress|stats|history|falling behind|behind|why am i|how am i doing|why did)\\b"))) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.HIGH)
        }

        if (lower.contains(Regex("(?i)\\b(show|list|view|display)\\b")) && (lower.contains("goal") || lower.contains("task"))) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.HIGH)
        }

        if (lower.contains("goal") && (lower.contains("how is") || lower.contains("on track") || lower.contains("status") || lower.contains("need"))) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.HIGH)
        }

        if (lower.contains("how is it") || lower.contains("how are they") || lower.contains("how it is") || lower.contains("how is that") || lower.contains("how's it") ||
            (lower.contains("doing") && (lower.contains("it") || lower.contains("they") || lower.contains("goal") || lower.contains("task")))
        ) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.HIGH)
        }

        if (lower.contains(Regex("(?i)\\b(remember|memory|recall)\\b"))) {
            return StructuralClassification(AiDecisionType.SHOW_INSIGHT, AiConfidence.MEDIUM)
        }

        return StructuralClassification(AiDecisionType.NO_ACTION, AiConfidence.LOW)
    }

    private fun resolveTemporalRange(lower: String, today: LocalDate = LocalDate.now()): TemporalRange? {
        return when {
            lower.contains("yesterday") -> {
                val date = today.minusDays(1)
                TemporalRange(TemporalScope.YESTERDAY, date, date, "Yesterday ($date)")
            }
            lower.contains("carrying from yesterday") || lower.contains("carried from yesterday") || lower.contains("carrying from") -> {
                val date = today.minusDays(1)
                TemporalRange(TemporalScope.YESTERDAY, date, date, "Carried from Yesterday ($date)")
            }
            lower.contains("today") -> {
                TemporalRange(TemporalScope.TODAY, today, today, "Today ($today)")
            }
            lower.contains("tomorrow") -> {
                val date = today.plusDays(1)
                TemporalRange(TemporalScope.TOMORROW, date, date, "Tomorrow ($date)")
            }
            lower.contains("this week") -> {
                val start = today.with(DayOfWeek.MONDAY)
                TemporalRange(TemporalScope.THIS_WEEK, start, today, "This Week ($start to $today)")
            }
            lower.contains("last week") -> {
                val end = today.with(DayOfWeek.MONDAY).minusDays(1)
                val start = end.minusDays(6)
                TemporalRange(TemporalScope.LAST_WEEK, start, end, "Last Week ($start to $end)")
            }
            lower.contains("next week") -> {
                val start = today.with(DayOfWeek.MONDAY).plusWeeks(1)
                val end = start.plusDays(6)
                TemporalRange(TemporalScope.NEXT_WEEK, start, end, "Next Week ($start to $end)")
            }
            lower.contains("this month") -> {
                val start = today.withDayOfMonth(1)
                TemporalRange(TemporalScope.THIS_MONTH, start, today, "This Month ($start to $today)")
            }
            lower.contains("recently") || lower.contains("recent") -> {
                val start = today.minusDays(7)
                TemporalRange(TemporalScope.RECENTLY, start, today, "Recently ($start to $today)")
            }
            else -> null
        }
    }

    data class RenameExtraction(
        val target: String?,
        val newTitle: String?
    )

    private fun extractRename(text: String, entityKeyword: String): RenameExtraction? {
        val trimmed = text.trim()
        val isRenameIntent = trimmed.contains(Regex("(?i)\\b(rename|title|name)\\b")) ||
            (trimmed.contains(Regex("(?i)\\bchange\\b")) && (trimmed.contains(Regex("(?i)\\bto\\b")) || trimmed.contains(Regex("(?i)\\b$entityKeyword\\b"))))
        if (!isRenameIntent) return null

        // 1. Complete rename: rename [entityKeyword] <target> to <newTitle>
        val completePattern = Regex(
            "(?i)^\\s*(?:rename|change(?:\\s+the)?\\s+(?:title|name)\\s+of|change)\\s+" +
            "(?:$entityKeyword\\s+)?" +
            "(?:\"([^\"]+)\"|'([^']+)'|(.+?))\\s+to\\s+" +
            "(?:\"([^\"]+)\"|'([^']+)'|(.+?))\\s*[.!?]?\\s*$"
        )
        val completeMatch = completePattern.find(trimmed)
        if (completeMatch != null) {
            val rawTarget = completeMatch.groups[1]?.value ?: completeMatch.groups[2]?.value ?: completeMatch.groups[3]?.value ?: ""
            val rawNewTitle = completeMatch.groups[4]?.value ?: completeMatch.groups[5]?.value ?: completeMatch.groups[6]?.value ?: ""
            val target = rawTarget.trim().trim('"', '\'')
            val newTitle = rawNewTitle.trim().trimEnd('.', '!', '?', ';', ',').trim('"', '\'')
            if (target.equals(entityKeyword, ignoreCase = true) || target.equals("the $entityKeyword", ignoreCase = true)) {
                return RenameExtraction(target = null, newTitle = newTitle)
            }
            if (target.isNotBlank() && newTitle.isNotBlank()) {
                return RenameExtraction(target, newTitle)
            }
        }

        // 2. Missing target: rename [entityKeyword] to <newTitle>
        val missingTargetPattern = Regex(
            "(?i)^\\s*(?:rename|change(?:\\s+the)?\\s+(?:title|name)\\s+of|change)\\s+" +
            "(?:$entityKeyword\\s+)?to\\s+" +
            "(?:\"([^\"]+)\"|'([^']+)'|(.+?))\\s*[.!?]?\\s*$"
        )
        val missingTargetMatch = missingTargetPattern.find(trimmed)
        if (missingTargetMatch != null) {
            val rawNewTitle = missingTargetMatch.groups[1]?.value ?: missingTargetMatch.groups[2]?.value ?: missingTargetMatch.groups[3]?.value ?: ""
            val newTitle = rawNewTitle.trim().trimEnd('.', '!', '?', ';', ',').trim('"', '\'')
            if (newTitle.isNotBlank()) {
                return RenameExtraction(target = null, newTitle = newTitle)
            }
        }

        // 3. Missing new title: rename [entityKeyword] <target> [to]
        val missingNewTitlePattern = Regex(
            "(?i)^\\s*(?:rename|change(?:\\s+the)?\\s+(?:title|name)\\s+of|change)\\s+" +
            "(?:$entityKeyword\\s+)?" +
            "(?:\"([^\"]+)\"|'([^']+)'|(.+?))(?:\\s+to)?\\s*[.!?]?\\s*$"
        )
        val missingNewTitleMatch = missingNewTitlePattern.find(trimmed)
        if (missingNewTitleMatch != null) {
            val rawTarget = missingNewTitleMatch.groups[1]?.value ?: missingNewTitleMatch.groups[2]?.value ?: missingNewTitleMatch.groups[3]?.value ?: ""
            val target = rawTarget.trim().trim('"', '\'')
            if (target.isNotBlank() && !target.equals(entityKeyword, ignoreCase = true) && !target.equals("the $entityKeyword", ignoreCase = true)) {
                return RenameExtraction(target = target, newTitle = null)
            }
        }

        // 4. Both missing: e.g. "rename task", "rename goal", "change the title"
        return RenameExtraction(target = null, newTitle = null)
    }

    private fun extractEntities(text: String, intent: AiDecisionType, rawText: String = text): Map<String, Any> {
        val entities = mutableMapOf<String, Any>()

        // Extract Duration
        val durationMatch = Regex("(\\d+)\\s*(minute|min|hour|hr)s?").find(text)
        if (durationMatch != null) {
            val value = durationMatch.groupValues[1]
            val unit = durationMatch.groupValues[2]
            entities["duration"] = if (unit.startsWith("h")) "$value hours" else "$value minutes"
        }

        // Extract Priority
        when {
            text.contains(Regex("(?i)\\b(urgent|critical|immediately)\\b|\\bpriority\\s*(?:to|is|=)?\\s*urgent\\b")) -> entities["priority"] = "URGENT"
            text.contains(Regex("(?i)\\b(high\\s+priority|important)\\b|\\bpriority\\s*(?:to|is|=)?\\s*high\\b")) -> entities["priority"] = "HIGH"
            text.contains(Regex("(?i)\\b(low\\s+priority|not\\s+important)\\b|\\bpriority\\s*(?:to|is|=)?\\s*low\\b")) -> entities["priority"] = "LOW"
            text.contains(Regex("(?i)\\bmedium\\s+priority\\b|\\bpriority\\s*(?:to|is|=)?\\s*medium\\b")) -> entities["priority"] = "MEDIUM"
        }

        // Extract Target Entity Title
        val rawTitle = when (intent) {
            AiDecisionType.EXPLANATION -> {
                text.replace(Regex("(?i)^\\s*(what is the role of|what is|what are|explain to me|can you explain|could you explain|explain|what does|mean|why is|useful|important|role of|meaning of)\\s*"), "")
                    .replace(Regex("(?i)\\b(the|a|an|concept of)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.CREATE_TASK -> {
                val explicitMatch = Regex("(?i)(?:called|named|title)\\s+[\"']?([^\"']+)[\"']?").find(text)
                if (explicitMatch != null) {
                    explicitMatch.groupValues[1].trim()
                } else {
                    var t = text
                    t = t.replace(Regex("(?i)^\\s*(create|add|new|remind me to)\\s+(a|an)?\\s*(high|urgent|low|medium)?\\s*(priority)?\\s*(task|todo)?\\s*(called|named|to|for)?\\s*"), "")
                    t = t.replace(Regex("(?i)\\s+(priority|high|urgent|low|medium)\\b"), "")
                        .replace(Regex("(?i)\\s+for\\s+\\d+\\s*(minute|min|hour|hr)s?\\b.*"), "")
                        .replace(Regex("\\b\\d+\\s*(minute|min|hour|hr)s?\\b"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    t
                }
            }
            AiDecisionType.CREATE_GOAL -> {
                text.replace(Regex("(?i)\\b(create|add|new|a|an|goal|objective|to|called|for|with)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.UPDATE_GOAL -> {
                val rename = extractRename(rawText, "goal") ?: extractRename(text, "goal")
                if (rename != null) {
                    if (rename.newTitle != null) {
                        entities["newTitle"] = rename.newTitle
                    }
                    if (rename.target != null) {
                        rename.target
                    } else ""
                } else {
                    var t = text
                    val catMatch = Regex("(?i)\\bcategory\\s+(?:to\\s+|is\\s+)?(?:[\"']([^\"']+)[\"']|([a-zA-Z0-9_-]+))").find(t)
                    if (catMatch != null) {
                        val cat = (catMatch.groups[1]?.value ?: catMatch.groups[2]?.value)?.trim()?.trimEnd('.', '!', '?', ';', ',') ?: ""
                        if (cat.isNotBlank()) {
                            entities["category"] = cat.replaceFirstChar { it.uppercase() }
                            t = t.removeRange(catMatch.range)
                        }
                    }
                    t = t.replace(Regex("(?i)^\\s*(?:update|change|edit|rename|modify|set)\\s+(?:the\\s+)?(?:goal|objective)\\s*"), " ")
                        .replace(Regex("(?i)\\b(goal|objective|the|my|a|an|category)\\b"), " ")
                        .trimEnd('.', '!', '?', ';', ',')
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .trim('"', '\'')
                    t
                }
            }
            AiDecisionType.UPDATE_TASK -> {
                val rename = extractRename(rawText, "task") ?: extractRename(text, "task")
                if (rename != null) {
                    if (rename.newTitle != null) {
                        entities["newTitle"] = rename.newTitle
                    }
                    if (rename.target != null) {
                        rename.target
                    } else ""
                } else {
                    var t = text
                    if (entities.containsKey("priority")) {
                        t = t.replace(Regex("(?i)\\s*(?:priority\\s*(?:to|is|=)?|to\\s*(?:priority)?)\\s*(?:urgent|critical|immediately|high|important|medium|low|not important)\\b|\\b(urgent|critical|immediately|high|important|medium|low)\\s+priority\\b"), " ")
                            .replace(Regex("(?i)\\b(urgent|critical|immediately|high|important|medium|low)\\b"), " ")
                    }
                    if (entities.containsKey("duration")) {
                        t = t.replace(Regex("(?i)\\s*(?:duration\\s*(?:to|of)?|for|to)?\\s*\\d+\\s*(minute|min|hour|hr)s?\\b"), " ")
                    }
                    t = t.replace(Regex("(?i)^\\s*(?:set|change|update|edit|rename|modify)\\s+(?:the\\s+)?(?:task|todo)\\s*"), " ")
                        .replace(Regex("(?i)\\b(task|todo|the|my|a|an|priority|duration|to|is|set|change|update|edit|rename|modify)\\b"), " ")
                        .trimEnd('.', '!', '?', ';', ',')
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .trim('"', '\'')
                    t
                }
            }
            AiDecisionType.COMPLETE_TASK, AiDecisionType.DELETE_TASK -> {
                text.replace(Regex("(?i)\\b(complete|finish|done|checked off|mark|as|delete|remove|destroy|the|my|a|an|task)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DELETE_GOAL -> {
                text.replace(Regex("(?i)\\b(delete|remove|destroy|trash|clear|the|my|a|an|goal|objective)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DECOMPOSE_GOAL -> {
                text.replace(Regex("(?i)\\b(break down|decompose|steps|the|my|a|an|goal|objective|into tasks|into steps|plan|how to|create a plan for|make a plan for)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DELETE_ALL_TASKS, AiDecisionType.COMPLETE_ALL_TASKS -> ""
            else -> {
                if (text.contains("goal")) {
                    text.replace(Regex("(?i)\\b(show|how is|is|on track|status|why is|falling behind|my|the|goal|doing|need|should i|decompose|will i finish)\\b"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                } else ""
            }
        }

        val cleanTitle = if (rawTitle.isNotBlank()) {
            if (rawTitle.firstOrNull()?.isLowerCase() == true) rawTitle.replaceFirstChar { it.uppercase() }.trim()
            else rawTitle.trim()
        } else ""

        if (cleanTitle.isNotBlank()) {
            entities["title"] = cleanTitle
            entities["query"] = cleanTitle
        }

        return entities
    }

    private fun resolveContextualReferences(
        entities: Map<String, Any>,
        convContext: AiConversationContext,
        context: AiContext,
        normalizedMessage: String
    ): Map<String, Any> {
        val newEntities = entities.toMutableMap()
        if (convContext.isExpired()) return newEntities

        val title = entities["title"]?.toString() ?: ""
        val isIt = title.isBlank() || title == "It" || title == "That" || title == "This" ||
            normalizedMessage.matches(Regex("(?i)^\\s*(delete|complete|update|finish)\\s+(it|that|this|that task|this task)\\s*$")) ||
            normalizedMessage.contains(Regex("\\b(it|that task|the previous task)\\b"))

        val hasTaskRef = title.contains("that task", ignoreCase = true) ||
            title.contains("the task", ignoreCase = true) ||
            title.contains("previous task", ignoreCase = true)

        val hasGoalRef = title.contains("that goal", ignoreCase = true) ||
            title.contains("the goal", ignoreCase = true) ||
            title.contains("previous goal", ignoreCase = true)

        if ((isIt || hasTaskRef) && convContext.lastTaskId != null) {
            newEntities["taskId"] = convContext.lastTaskId
        }
        if ((isIt || hasGoalRef) && convContext.lastGoalId != null) {
            newEntities["goalId"] = convContext.lastGoalId
        }

        // Handle "the first one"
        val query = entities["query"]?.toString() ?: ""
        if (query.contains(Regex("first one|first task|top one")) && convContext.candidateIds.isNotEmpty()) {
            newEntities["taskId"] = convContext.candidateIds.first()
        }

        return newEntities
    }

    private fun isConfirmation(text: String): Boolean {
        return text.contains(Regex("(?i)\\byes\\b|\\bconfirm\\b|\\bdo it\\b|\\bdo that\\b|\\bcontinue\\b|\\bproceed\\b|\\bokay\\b|\\bok\\b|\\bgo ahead\\b|\\bdelete them\\b|\\bdelete everything\\b"))
    }

    private fun isCancellation(text: String): Boolean {
        return text.contains(Regex("(?i)\\bno\\b|\\bcancel\\b|\\bstop\\b|\\bnever mind\\b|\\bforget it\\b|\\bdon't\\b"))
    }

    private fun isNewDirective(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("create") || lower.startsWith("add") || lower.startsWith("delete all") ||
            lower.startsWith("plan my day") || lower.startsWith("show my") || lower.startsWith("what is")
    }

    private fun handleClarificationFollowUp(
        text: String,
        convContext: AiConversationContext,
        context: AiContext
    ): AiLanguageResult {
        val clarification = convContext.activeClarification ?: return AiLanguageResult(AiDecisionType.NO_ACTION, AiConfidence.LOW)
        val entities = clarification.partialEntities.toMutableMap()

        when (clarification.missingField) {
            "title" -> {
                entities["title"] = text
                val match = AiEntityResolver.resolveTask(text, context.tasks, convContext)
                if (match is ResolutionResult.Success) {
                    entities["taskId"] = match.entity.id
                    return AiLanguageResult(
                        intent = clarification.intent,
                        confidence = AiConfidence.HIGH,
                        entities = entities,
                        targetTaskId = match.entity.id,
                        targetTaskTitle = match.entity.title,
                        requiresMutation = true
                    )
                }
            }
            "taskId" -> {
                val tasks = context.tasks.filter { it.id in clarification.candidates }
                val match = AiEntityResolver.resolveTask(text, tasks)
                if (match is ResolutionResult.Success) {
                    entities["taskId"] = match.entity.id
                    return AiLanguageResult(
                        intent = clarification.intent,
                        confidence = AiConfidence.HIGH,
                        entities = entities,
                        targetTaskId = match.entity.id,
                        targetTaskTitle = match.entity.title,
                        requiresMutation = true
                    )
                } else {
                    return AiLanguageResult(
                        intent = AiDecisionType.CLARIFY,
                        confidence = AiConfidence.LOW,
                        clarificationNeeded = clarification.copy(question = "I'm sorry, I still couldn't identify which task you meant. Which one was it?"),
                        requiresClarification = true
                    )
                }
            }
            "goalId" -> {
                val goals = context.goals.filter { it.id in clarification.candidates }
                val match = AiEntityResolver.resolveGoal(text, goals)
                if (match is ResolutionResult.Success) {
                    entities["goalId"] = match.entity.id
                    return AiLanguageResult(
                        intent = clarification.intent,
                        confidence = AiConfidence.HIGH,
                        entities = entities,
                        targetGoalId = match.entity.id,
                        targetGoalTitle = match.entity.title,
                        requiresMutation = true
                    )
                }
            }
        }

        return AiLanguageResult(
            intent = clarification.intent,
            confidence = AiConfidence.HIGH,
            entities = entities,
            requiresMutation = true
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[?!.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun mapActionToDecision(type: AiActionType): AiDecisionType {
        return when (type) {
            AiActionType.CREATE_TASK -> AiDecisionType.CREATE_TASK
            AiActionType.COMPLETE_TASK -> AiDecisionType.COMPLETE_TASK
            AiActionType.UPDATE_TASK -> AiDecisionType.UPDATE_TASK
            AiActionType.DELETE_TASK -> AiDecisionType.DELETE_TASK
            AiActionType.RESCHEDULE_TASK -> AiDecisionType.RESCHEDULE_TASK
            AiActionType.CREATE_GOAL -> AiDecisionType.CREATE_GOAL
            AiActionType.UPDATE_GOAL -> AiDecisionType.UPDATE_GOAL
            AiActionType.DELETE_GOAL -> AiDecisionType.DELETE_GOAL
            AiActionType.DECOMPOSE_GOAL -> AiDecisionType.DECOMPOSE_GOAL
            AiActionType.SHOW_INSIGHT -> AiDecisionType.SHOW_INSIGHT
            AiActionType.OPEN_TASK -> AiDecisionType.START_TASK
            AiActionType.OPEN_GOAL -> AiDecisionType.UPDATE_GOAL
            AiActionType.DELETE_ALL_TASKS -> AiDecisionType.DELETE_ALL_TASKS
            AiActionType.COMPLETE_ALL_TASKS -> AiDecisionType.COMPLETE_ALL_TASKS
            AiActionType.CREATE_AUTOMATION -> AiDecisionType.CREATE_AUTOMATION
            AiActionType.TOGGLE_AUTOMATION -> AiDecisionType.TOGGLE_AUTOMATION
            AiActionType.DELETE_AUTOMATION -> AiDecisionType.DELETE_AUTOMATION
            AiActionType.UPDATE_AUTOMATION -> AiDecisionType.UPDATE_AUTOMATION
        }
    }
}
