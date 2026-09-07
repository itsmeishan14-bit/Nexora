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

        // 2. Intent Detection
        val intentResult = detectIntent(normalized)
        
        // 3. Handle Conversational Clarification
        if (convContext.activeClarification != null && !isNewIntent(normalized)) {
            return handleClarificationFollowUp(normalized, convContext, context)
        }

        // 4. Entity & Parameter Extraction
        val entities = extractEntities(normalized, intentResult.first)
        
        // 5. Contextual Resolution (it, the first one, etc.)
        val resolvedEntities = resolveContextualReferences(entities, convContext, context, normalized)

        return AiLanguageResult(
            intent = intentResult.first,
            confidence = intentResult.second,
            entities = resolvedEntities
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[?!.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\bcan you\\b|\\bcould you\\b|\\bplease\\b|\\bhelp me\\b|\\bwant to\\b|\\bneed to\\b"), "")
            .trim()
    }

    private fun detectIntent(text: String): Pair<AiDecisionType, AiConfidence> {
        return when {
            // Task Actions
            text.contains(Regex("(?i)create|add|new|remind")) && text.contains("task") -> AiDecisionType.CREATE_TASK to AiConfidence.HIGH
            text.contains(Regex("(?i)complete|finish|done|checked off|mark")) -> AiDecisionType.COMPLETE_TASK to AiConfidence.HIGH
            text.contains(Regex("(?i)delete|remove|destroy")) -> AiDecisionType.DELETE_TASK to AiConfidence.HIGH
            text.contains(Regex("(?i)change|update|edit|priority|rename")) -> AiDecisionType.UPDATE_TASK to AiConfidence.MEDIUM
            
            // Planning
            text.contains(Regex("(?i)plan")) && text.contains(Regex("(?i)day|today")) -> AiDecisionType.DAILY_PLAN to AiConfidence.HIGH
            text.contains(Regex("(?i)next|focus|do next|do now")) -> AiDecisionType.START_TASK to AiConfidence.HIGH
            text.contains(Regex("(?i)break down|decompose|steps")) -> AiDecisionType.DECOMPOSE_GOAL to AiConfidence.MEDIUM
            
            // Goal Actions
            text.contains(Regex("(?i)create|add|new")) && text.contains("goal") -> AiDecisionType.CREATE_GOAL to AiConfidence.MEDIUM
            
            // Analysis & Info
            text.contains(Regex("(?i)show|list|view")) && text.contains("task") -> AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
            text.contains(Regex("(?i)show|list|view")) && text.contains("goal") -> AiDecisionType.SHOW_INSIGHT to AiConfidence.HIGH
            text.contains(Regex("(?i)productivity|pattern|consistency|how am i doing")) -> AiDecisionType.SHOW_INSIGHT to AiConfidence.MEDIUM
            text.contains(Regex("(?i)clean|organize|overload")) -> AiDecisionType.SHOW_INSIGHT to AiConfidence.MEDIUM
            
            else -> AiDecisionType.NO_ACTION to AiConfidence.LOW
        }
    }

    private fun extractEntities(text: String, intent: AiDecisionType): Map<String, Any> {
        val entities = mutableMapOf<String, Any>()
        
        // Extract Duration
        val durationMatch = Regex("(\\d+)\\s*(minute|min|hour|hr)").find(text)
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

        // Extract Task/Goal Title (crude heuristic)
        // We remove intent-related words to isolate the entity title
        val title = when (intent) {
            AiDecisionType.CREATE_TASK -> {
                text.replace(Regex("(?i)\\b(create|add|new|remind)\\b|\\b(task|todo)\\b|\\b(to|called|a)\\b"), "").trim()
            }
            AiDecisionType.CREATE_GOAL -> {
                text.replace(Regex("(?i)\\b(create|add|new)\\b|\\b(goal|objective)\\b|\\b(to|called|a)\\b"), "").trim()
            }
            AiDecisionType.COMPLETE_TASK, AiDecisionType.DELETE_TASK, AiDecisionType.UPDATE_TASK -> {
                text.replace(Regex("(?i)\\b(complete|finish|done|checked off|mark|delete|remove|destroy|change|update|edit|priority|rename)\\b|\\b(the|task|it)\\b"), "").trim()
            }
            AiDecisionType.DECOMPOSE_GOAL -> {
                text.replace(Regex("(?i)\\b(break down|decompose|steps)\\b|\\b(the|goal|objective)\\b"), "").trim()
            }
            else -> ""
        }
        
        if (title.isNotBlank()) {
            entities["title"] = title
            entities["query"] = title
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
        val isIt = title.isBlank() || title == "it" || normalizedMessage.contains(Regex("\\bit\\b"))
        val hasTaskRef = title.contains("that task") || title.contains("the task")
        val hasGoalRef = title.contains("that goal") || title.contains("the goal")

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
                // Try to resolve within candidates
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
