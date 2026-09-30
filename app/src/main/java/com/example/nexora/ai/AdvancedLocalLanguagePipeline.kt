package com.example.nexora.ai

/**
 * Advanced offline natural language processing pipeline.
 * Handles normalization, intent detection, and entity extraction via local heuristics.
 */
class AdvancedLocalLanguagePipeline {

    /**
     * Processes a user message into a structured language result.
     */
    fun process(
        message: String,
        context: AiContext,
        convContext: AiConversationContext = AiConversationContext()
    ): AiLanguageResult {
        // 1. Text Normalization
        val normalized = normalize(message)
        if (normalized.isBlank()) return AiLanguageResult(AiDecisionType.NO_ACTION, AiConfidence.LOW)

        // 2. Check for Confirmation/Cancellation if there's a pending action
        if (convContext.pendingAction != null) {
            val isConfirm = isConfirmation(normalized)
            val isCancel = isCancellation(normalized)
            
            if (isConfirm) {
                return AiLanguageResult(
                    intent = mapActionToDecision(convContext.pendingAction.type),
                    confidence = AiConfidence.HIGH,
                    isConfirmation = true
                )
            } else if (isCancel) {
                return AiLanguageResult(
                    intent = AiDecisionType.CANCEL,
                    confidence = AiConfidence.HIGH,
                    isCancellation = true
                )
            }
        }

        // 3. Intent Detection
        val intentResult = detectIntent(normalized)
        
        // 4. Handle Conversational Clarification
        if (convContext.activeClarification != null && !isNewIntent(normalized)) {
            return handleClarificationFollowUp(normalized, convContext, context)
        }

        // 5. Entity & Parameter Extraction
        val entities = extractEntities(normalized, intentResult.first)
        
        // 6. Contextual Resolution (it, the first one, etc.)
        val resolvedEntities = resolveContextualReferences(entities, convContext, context, normalized)

        // 7. Ambiguity and Safety Check: If an action target is completely missing and not in context, ask clarification!
        if ((intentResult.first == AiDecisionType.DELETE_TASK || intentResult.first == AiDecisionType.DELETE_GOAL || intentResult.first == AiDecisionType.COMPLETE_TASK) &&
            resolvedEntities["taskId"] == null && resolvedEntities["goalId"] == null &&
            (resolvedEntities["title"]?.toString().isNullOrBlank() || resolvedEntities["title"] == "It" || resolvedEntities["title"] == "That")
        ) {
            val actionName = if (intentResult.first == AiDecisionType.COMPLETE_TASK) "complete" else "delete"
            val question = "Which task or goal would you like to $actionName?"
            return AiLanguageResult(
                intent = AiDecisionType.CLARIFY,
                confidence = AiConfidence.LOW,
                clarificationNeeded = AiClarification(
                    question = question,
                    intent = intentResult.first,
                    missingField = "title",
                    originalQuery = message
                ),
                textResponse = question
            )
        }

        return AiLanguageResult(
            intent = intentResult.first,
            confidence = intentResult.second,
            entities = resolvedEntities
        )
    }

    private fun isConfirmation(text: String): Boolean {
        return text.contains(Regex("(?i)\\byes\\b|\\bconfirm\\b|\\bdo it\\b|\\bdo that\\b|\\bcontinue\\b|\\bproceed\\b|\\bokay\\b|\\bok\\b|\\bgo ahead\\b|\\bdelete them\\b|\\bdelete everything\\b"))
    }

    private fun isCancellation(text: String): Boolean {
        return text.contains(Regex("(?i)\\bno\\b|\\bcancel\\b|\\bstop\\b|\\bnever mind\\b|\\bforget it\\b|\\bdon't\\b"))
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
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[?!.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun detectIntent(text: String): Pair<AiDecisionType, AiConfidence> {
        val lower = text.lowercase().trim()

        // 1. BROAD INTENT: EXPLANATION / CONCEPTUAL DEFINITION
        // Educational queries must NEVER collide with action directives
        val isExplanation = lower.matches(Regex("(?i)^\\s*(what is|what are|explain|can you explain|what does .+ mean|why is .+ useful|why is .+ important|role of|what is the role of)\\b.*")) ||
            lower.contains(Regex("(?i)\\b(explain goal decomposition|what is goal decomposition|role of goal decomposition|why is goal decomposition|meaning of goal decomposition)\\b")) ||
            lower.contains(Regex("(?i)\\b(explain task prioritization|what is task prioritization|role of task prioritization)\\b")) ||
            lower.contains(Regex("(?i)\\b(explain time blocking|what is time blocking|explain carry forward|what is carry forward)\\b"))

        if (isExplanation) {
            return AiDecisionType.EXPLANATION to AiConfidence.HIGH
        }

        // 2. BROAD INTENT: CONVERSATION
        if (lower.contains(Regex("(?i)\\b(hello|hi|hey|greetings|good morning|good afternoon|good evening)\\b"))) {
            return AiDecisionType.GREETING to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(thanks|thank you|appreciated|awesome|cool|great|perfect)\\b"))) {
            return AiDecisionType.THANKS to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(bye|goodbye|see you|see ya|goodnight)\\b"))) {
            return AiDecisionType.GOODBYE to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(what can you do|who are you|how are you|tell me about yourself|what are your capabilities|features|what do you do)\\b"))) {
            return AiDecisionType.GENERAL_CONVERSATION to AiConfidence.HIGH
        }

        // 3. BROAD INTENT: CANCEL
        if (lower.contains(Regex("(?i)\\bcancel\\b|\\bnever mind\\b|\\bdon't\\b|\\bstop\\b"))) {
            return AiDecisionType.CANCEL to AiConfidence.HIGH
        }

        // 4. BROAD INTENT: AUTOMATIONS
        val isAutomationKeyword = lower.contains("automation") || lower.contains("rule") ||
            lower.contains("assistant") || lower.contains("workload manager") ||
            lower.contains("goal progress guard") || lower.contains("conflict detector") ||
            lower.contains("morning plan")
        if (isAutomationKeyword) {
            return when {
                lower.contains(Regex("(?i)\\b(why did|explain|reason for)\\b")) -> AiDecisionType.EXPLAIN_AUTOMATION to AiConfidence.HIGH
                lower.contains(Regex("(?i)\\b(turn off|disable|enable|turn on|toggle)\\b")) -> AiDecisionType.TOGGLE_AUTOMATION to AiConfidence.HIGH
                lower.contains(Regex("(?i)\\b(delete|remove)\\b")) -> AiDecisionType.DELETE_AUTOMATION to AiConfidence.HIGH
                lower.contains(Regex("(?i)\\b(show|list|view|active)\\b")) -> AiDecisionType.LIST_AUTOMATIONS to AiConfidence.HIGH
                lower.contains(Regex("(?i)\\b(create|add|new)\\b")) -> AiDecisionType.CREATE_AUTOMATION to AiConfidence.HIGH
                else -> AiDecisionType.LIST_AUTOMATIONS to AiConfidence.MEDIUM
            }
        }
        if (lower.contains(Regex("(?i)\\b(every morning|when i finish|if i carry|when my workload|when a goal)\\b"))) {
            return AiDecisionType.CREATE_AUTOMATION to AiConfidence.HIGH
        }

        // 5. BROAD INTENT: PREDICTION
        if (lower.contains(Regex("(?i)\\b(will i finish|when will i finish|estimated completion|goal finish|finish my goal)\\b"))) {
            return AiDecisionType.PREDICT_GOAL to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(postpone|delay risk|at risk|task at risk|most likely to postpone|delay)\\b")) && lower.contains("task")) {
            return AiDecisionType.PREDICT_TASK_RISK to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(taking on too much|schedule realistic|workload risk|overload risk|too much today|unrealistic)\\b"))) {
            return AiDecisionType.PREDICT_WORKLOAD to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(productivity trend|completion pace|my pace|accurate|accuracy|calibration|prediction quality)\\b")) ||
            (lower.contains("how productive") && !lower.contains("this week") && !lower.contains("last week") && !lower.contains("yesterday") && !lower.contains("was i"))
        ) {
            return AiDecisionType.PREDICT_PRODUCTIVITY to AiConfidence.HIGH
        }

        // 6. BROAD INTENT: PLANNING
        if (lower.contains(Regex("(?i)\\bplan\\b")) && lower.contains(Regex("(?i)\\b(day|today|schedule)\\b"))) {
            return AiDecisionType.DAILY_PLAN to AiConfidence.HIGH
        }

        // 7. BROAD INTENT: RECOMMENDATION
        if (lower.contains(Regex("(?i)\\b(next task|what should i do next|what to work on|what's next|what should i focus on|do next|do now|what to do next|which task)\\b")) ||
            lower.contains(Regex("(?i)\\bhighest priority|most important|urgent\\b")) && !lower.contains("create") && !lower.contains("add")
        ) {
            return AiDecisionType.START_TASK to AiConfidence.HIGH
        }

        // 8. BROAD INTENT: BULK ACTIONS (Checked before single actions)
        if (lower.contains(Regex("(?i)\\b(delete|remove|clear)\\b")) && 
            (lower.contains(Regex("(?i)\\b(all|every|everything)\\b")) || lower == "delete all" || lower.startsWith("delete all") || lower.contains("all tasks"))
        ) {
            return AiDecisionType.DELETE_ALL_TASKS to AiConfidence.HIGH
        }
        if (lower.contains(Regex("(?i)\\b(complete|finish|done|checked off|mark)\\b")) && 
            (lower.contains(Regex("(?i)\\b(all|every|everything)\\b")) || lower == "complete all" || lower.startsWith("complete all") || lower.contains("all tasks"))
        ) {
            return AiDecisionType.COMPLETE_ALL_TASKS to AiConfidence.HIGH
        }

        // 9. BROAD INTENT: ACTIONS
        // Goal Decomposition: Directive on a goal
        val isDecomposeDirective = (lower.contains("break down") || lower.contains("decompose")) && 
            (lower.contains("goal") || lower.contains("into tasks") || lower.contains("into steps")) &&
            !lower.startsWith("should i") && !lower.contains("should i decompose")
        if (isDecomposeDirective) {
            return AiDecisionType.DECOMPOSE_GOAL to AiConfidence.HIGH
        }

        // Task Creation
        if (lower.contains(Regex("(?i)\\b(create|add|new|remind me to)\\b")) && lower.contains(Regex("(?i)\\b(task|todo)\\b"))) {
            return AiDecisionType.CREATE_TASK to AiConfidence.HIGH
        }

        // Goal Creation
        if (lower.contains(Regex("(?i)\\b(create|add|new)\\b")) && lower.contains(Regex("(?i)\\b(goal|objective)\\b"))) {
            return AiDecisionType.CREATE_GOAL to AiConfidence.HIGH
        }

        // Complete Task
        val isCompleteDirective = lower.startsWith("complete ") || lower.startsWith("finish ") || 
            lower.startsWith("mark ") || lower.contains("as complete") || lower.contains("as done") ||
            lower.contains(Regex("(?i)\\b(complete|finish|done|checked off)\\b"))
        if (isCompleteDirective) {
            return AiDecisionType.COMPLETE_TASK to AiConfidence.HIGH
        }

        // Delete Task or Goal
        if (lower.contains(Regex("(?i)\\b(delete|remove|destroy|trash|clear)\\b"))) {
            return if (lower.contains("goal")) {
                AiDecisionType.DELETE_GOAL to AiConfidence.HIGH
            } else {
                AiDecisionType.DELETE_TASK to AiConfidence.HIGH
            }
        }

        // Update Task
        if (lower.contains(Regex("(?i)\\b(change|update|edit|priority|rename|modify|set)\\b")) && (lower.contains("task") || lower.contains("it"))) {
            return AiDecisionType.UPDATE_TASK to AiConfidence.MEDIUM
        }

        // 10. BROAD INTENT: ANALYSIS / INFORMATION / TEMPORAL INSIGHT
        if (lower.contains(Regex("(?i)\\b(yesterday|accomplish|carrying|this week|last week|progress|stats|history|falling behind|behind|why am i|how am i doing)\\b"))) {
            return AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
        }

        if (lower.contains(Regex("(?i)\\b(show|list|view|display)\\b"))) {
            return AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
        }

        if (lower.contains("goal") && (lower.contains("how is") || lower.contains("on track") || lower.contains("status") || lower.contains("falling behind") || lower.contains("need") || lower.contains("should i"))) {
            return AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
        }

        if (lower.contains(Regex("(?i)\\b(remember|memory|recall)\\b"))) {
            return AiDecisionType.SHOW_INSIGHT to AiConfidence.MEDIUM
        }

        if (lower.contains(Regex("(?i)why|reason")) && (lower.contains("behind") || lower.contains("delay") || lower.contains("stagnat"))) {
            return AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
        }

        return AiDecisionType.NO_ACTION to AiConfidence.LOW
    }

    private fun extractEntities(text: String, intent: AiDecisionType): Map<String, Any> {
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
            text.contains(Regex("urgent|critical|immediately")) -> entities["priority"] = "URGENT"
            text.contains(Regex("high priority|important")) -> entities["priority"] = "HIGH"
            text.contains(Regex("low priority|not important")) -> entities["priority"] = "LOW"
            text.contains("medium priority") -> entities["priority"] = "MEDIUM"
        }

        // Extract Task/Goal/Concept Title
        val rawTitle = when (intent) {
            AiDecisionType.EXPLANATION -> {
                text.replace(Regex("(?i)^\\s*(what is the role of|what is|what are|explain to me|can you explain|explain|what does|mean|why is|useful|important|role of)\\s*"), "")
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
            AiDecisionType.COMPLETE_TASK, AiDecisionType.DELETE_TASK, AiDecisionType.UPDATE_TASK -> {
                text.replace(Regex("(?i)\\b(complete|finish|done|checked off|mark|as|delete|remove|destroy|change|update|edit|priority|rename|the|my|a|an|task|it)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DELETE_GOAL -> {
                text.replace(Regex("(?i)\\b(delete|remove|destroy|trash|clear|the|my|a|an|goal|objective|it)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DECOMPOSE_GOAL -> {
                text.replace(Regex("(?i)\\b(break down|decompose|steps|the|my|a|an|goal|objective|into tasks|into steps)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            AiDecisionType.DELETE_ALL_TASKS, AiDecisionType.COMPLETE_ALL_TASKS -> ""
            else -> {
                if (text.contains("goal")) {
                    text.replace(Regex("(?i)\\b(show|how is|is|on track|status|why is|falling behind|my|the|goal|doing|need|should i|decompose)\\b"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                } else ""
            }
        }
        
        val cleanTitle = if (rawTitle.isNotBlank()) rawTitle.replaceFirstChar { it.uppercase() }.trim() else ""

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

        // Handle "it", "that goal", "that task"
        val isIt = title.isBlank() || title == "It" || normalizedMessage.contains(Regex("\\bit\\b"))
        val hasTaskRef = title.contains("that task", ignoreCase = true) || title.contains("the task", ignoreCase = true)
        val hasGoalRef = title.contains("that goal", ignoreCase = true) || title.contains("the goal", ignoreCase = true)

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

    private fun isNewIntent(text: String): Boolean {
        val (intent, confidence) = detectIntent(text)
        return intent != AiDecisionType.NO_ACTION && confidence == AiConfidence.HIGH
    }

    private fun handleClarificationFollowUp(
        text: String,
        convContext: AiConversationContext,
        context: AiContext
    ): AiLanguageResult {
        val clarification = convContext.activeClarification ?: return AiLanguageResult(AiDecisionType.NO_ACTION, AiConfidence.LOW)
        
        val entities = clarification.partialEntities.toMutableMap()
        
        when (clarification.missingField) {
            "title" -> entities["title"] = text
            "taskId" -> {
                val tasks = context.tasks.filter { it.id in clarification.candidates }
                val match = AiEntityResolver.resolveTask(text, tasks)
                if (match is ResolutionResult.Success) {
                    entities["taskId"] = match.entity.id
                } else {
                    return AiLanguageResult(
                        intent = AiDecisionType.CLARIFY,
                        confidence = AiConfidence.HIGH,
                        clarificationNeeded = clarification.copy(question = "I'm sorry, I still couldn't identify which task you meant. Which one was it?")
                    )
                }
            }
        }
        
        return AiLanguageResult(
            intent = clarification.intent,
            confidence = AiConfidence.HIGH,
            entities = entities
        )
    }
}
