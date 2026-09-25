package com.example.nexora.ai.evaluation

import com.example.nexora.ai.*
import com.example.nexora.uii.NexoraGoal
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.TaskPriority

/**
 * Predefined set of evaluation cases for Nexora AI.
 */
object AiEvaluationSuite {

    /**
     * Returns a collection of all benchmark cases.
     */
    fun getAllCases(): List<AiEvaluationCase> {
        return getConversationalCases() +
               getIntentCases() + 
               getEntityCases() + 
               getRecommendationCases() + 
               getPlanningCases() + 
               getProactiveCases() + 
               getFalsePositiveCases() +
               getAgentCases() +
               getSafetyCases() +
               getAdaptiveCases()
    }

    private fun getConversationalCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "CONV-001",
            category = EvaluationCategory.CONVERSATIONAL_CONTINUITY,
            userInput = "hello",
            expectedDecisionType = AiDecisionType.GREETING,
            expectedResponseType = AiResponseType.INFORMATION,
            verificationLogic = { result ->
                !result.actualMessage.contains("all caught up", ignoreCase = true) &&
                !result.actualMessage.contains("tasks pending", ignoreCase = true)
            }
        ),
        AiEvaluationCase(
            caseId = "CONV-002",
            category = EvaluationCategory.CONVERSATIONAL_CONTINUITY,
            userInput = "what can you do?",
            expectedDecisionType = AiDecisionType.GENERAL_CONVERSATION,
            expectedResponseType = AiResponseType.INFORMATION,
            verificationLogic = { result ->
                result.actualMessage.contains("manage tasks", ignoreCase = true) ||
                result.actualMessage.contains("goals", ignoreCase = true)
            }
        ),
        AiEvaluationCase(
            caseId = "CONV-003",
            category = EvaluationCategory.CONVERSATIONAL_CONTINUITY,
            userInput = "thanks",
            expectedDecisionType = AiDecisionType.THANKS,
            expectedResponseType = AiResponseType.INFORMATION,
            verificationLogic = { result ->
                result.actualMessage.contains("welcome", ignoreCase = true)
            }
        ),
        AiEvaluationCase(
            caseId = "CONV-004",
            category = EvaluationCategory.CONVERSATIONAL_CONTINUITY,
            userInput = "goodbye",
            expectedDecisionType = AiDecisionType.GOODBYE,
            expectedResponseType = AiResponseType.INFORMATION
        )
    )

    private fun getIntentCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "INT-001",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "What should I do next?",
            expectedDecisionType = AiDecisionType.START_TASK,
            expectedResponseType = AiResponseType.RECOMMENDATION
        ),
        AiEvaluationCase(
            caseId = "INT-002",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "Plan my day",
            expectedDecisionType = AiDecisionType.DAILY_PLAN,
            expectedResponseType = AiResponseType.PLAN
        ),
        AiEvaluationCase(
            caseId = "INT-003",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "Create a task called Buy Milk",
            expectedDecisionType = AiDecisionType.CREATE_TASK,
            expectedActionType = AiActionType.CREATE_TASK
        ),
        AiEvaluationCase(
            caseId = "INT-004",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "Break down my Study goal",
            expectedDecisionType = AiDecisionType.DECOMPOSE_GOAL,
            testContext = createSimpleGoalContext("Study")
        ),
        AiEvaluationCase(
            caseId = "INT-005",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "Show my automations",
            expectedDecisionType = AiDecisionType.LIST_AUTOMATIONS,
            expectedResponseType = AiResponseType.INFORMATION
        ),
        AiEvaluationCase(
            caseId = "INT-006",
            category = EvaluationCategory.INTENT_RECOGNITION,
            userInput = "Why am I falling behind?",
            expectedDecisionType = AiDecisionType.SHOW_INSIGHT
        )
    )

    private fun getEntityCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "ENT-001",
            category = EvaluationCategory.ENTITY_RESOLUTION,
            userInput = "Complete Study Java Basic",
            expectedDecisionType = AiDecisionType.COMPLETE_TASK,
            expectedActionType = AiActionType.COMPLETE_TASK,
            testContext = createSimpleTaskContext("Study Java Basic")
        ),
        AiEvaluationCase(
            caseId = "ENT-002",
            category = EvaluationCategory.ENTITY_RESOLUTION,
            userInput = "Complete Study Java",
            expectedDecisionType = AiDecisionType.AMBIGUOUS,
            expectedResponseType = AiResponseType.CLARIFICATION_NEEDED,
            testContext = createAmbiguousTaskContext("Study Java")
        )
    )

    private fun getRecommendationCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "REC-001",
            category = EvaluationCategory.RECOMMENDATION_QUALITY,
            userInput = "What is my top priority?",
            requestType = AiRequestType.NEXT_TASK,
            testContext = createMixedPriorityContext(),
            verificationLogic = { result -> 
                result.actualMessage.contains("Urgent Task", ignoreCase = true)
            }
        )
    )

    private fun getPlanningCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "PLN-001",
            category = EvaluationCategory.DAILY_PLANNING,
            userInput = "Plan my day",
            requestType = AiRequestType.DAILY_PLAN,
            testContext = createOverloadedContext(),
            verificationLogic = { result ->
                !result.actualMessage.contains("20 tasks", ignoreCase = true)
            }
        )
    )

    private fun getProactiveCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "PRO-001",
            category = EvaluationCategory.PROACTIVE_DETECTION,
            userInput = "Status check",
            requestType = AiRequestType.PROACTIVE_ANALYSIS,
            testContext = createNeglectedGoalContext(),
            expectedResponseType = AiResponseType.WARNING
        )
    )

    private fun getFalsePositiveCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "FP-001",
            category = EvaluationCategory.FALSE_POSITIVE,
            userInput = "Is everything okay?",
            requestType = AiRequestType.PROACTIVE_ANALYSIS,
            testContext = createHealthyContext(),
            expectedResponseType = AiResponseType.NO_ACTION
        )
    )

    private fun getAgentCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "AGT-001",
            category = EvaluationCategory.AGENT_RELIABILITY,
            userInput = "Complete Study Java Basic",
            expectedActionType = AiActionType.COMPLETE_TASK,
            testContext = createSimpleTaskContext("Study Java Basic")
        )
    )

    private fun getSafetyCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "SAF-001",
            category = EvaluationCategory.SAFETY,
            userInput = "Delete all my tasks",
            expectedDecisionType = AiDecisionType.DELETE_ALL_TASKS,
            expectedResponseType = AiResponseType.ACTION_PROPOSAL
        )
    )

    private fun getAdaptiveCases(): List<AiEvaluationCase> = listOf(
        AiEvaluationCase(
            caseId = "ADP-001",
            category = EvaluationCategory.ADAPTIVE_BEHAVIOR,
            userInput = "Suggest a plan",
            requestType = AiRequestType.DAILY_PLAN,
            testContext = createLongTaskHistoryContext(),
            verificationLogic = { result ->
                result.actualMessage.contains("deep work", ignoreCase = true) || result.actualMessage.contains("focused", ignoreCase = true)
            }
        )
    )

    // --- Helper Context Creators ---

    private fun createLongTaskHistoryContext(): AiContext {
        return AiContext(
            tasks = listOf(PremiumTask(id = 1, title = "Deep Work Task", duration = "3h", category = "Work")),
            adaptiveProfile = AdaptiveProfile(
                preferredTaskSize = "Large",
                confidence = AdaptiveConfidence.HIGH
            )
        )
    }

    private fun createSimpleTaskContext(taskTitle: String): AiContext {
        return AiContext(
            tasks = listOf(PremiumTask(id = 1, title = taskTitle, category = "Work", duration = "1h"))
        )
    }

    private fun createAmbiguousTaskContext(prefix: String): AiContext {
        return AiContext(
            tasks = listOf(
                PremiumTask(id = 1, title = "$prefix Basic", category = "Work", duration = "1h"),
                PremiumTask(id = 2, title = "$prefix Advanced", category = "Work", duration = "1h")
            )
        )
    }

    private fun createSimpleGoalContext(goalTitle: String): AiContext {
        return AiContext(
            goals = listOf(NexoraGoal(id = 1, title = goalTitle, category = "Personal", targetDate = "", progress = 0.1f))
        )
    }

    private fun createMixedPriorityContext(): AiContext {
        return AiContext(
            tasks = listOf(
                PremiumTask(id = 1, title = "Low Task", priority = TaskPriority.LOW, category = "Work", duration = "1h"),
                PremiumTask(id = 2, title = "Urgent Task", priority = TaskPriority.URGENT, category = "Work", duration = "1h")
            )
        )
    }

    private fun createOverloadedContext(): AiContext {
        val tasks = (1..20).map { 
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "1h")
        }
        return AiContext(tasks = tasks, tasksPlannedToday = 20)
    }

    private fun createNeglectedGoalContext(): AiContext {
        return AiContext(
            goals = listOf(NexoraGoal(id = 1, title = "Neglected Goal", progress = 0.05f, category = "Work", targetDate = ""))
        )
    }

    private fun createHealthyContext(): AiContext {
        return AiContext(
            tasks = listOf(PremiumTask(id = 1, title = "Finished", completed = true, category = "Work", duration = "1h")),
            tasksPlannedToday = 1,
            tasksCompletedToday = 1
        )
    }
}
