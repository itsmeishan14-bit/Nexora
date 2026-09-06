package com.example.nexora.ai

import kotlinx.coroutines.delay

/**
 * Nexora AI Agent capable of multi-step reasoning and tool use.
 */
class NexoraAiAgent(
    private val toolRegistry: AiToolRegistry,
    private val decisionGate: AiDecisionGate? = null,
    private val maxSteps: Int = 5
) {

    /**
     * Executes a user request using an iterative planning and execution loop.
     */
    suspend fun execute(request: AiRequest, context: AiContext, relevantMemory: List<AiMemoryItem> = emptyList()): AiResponse {
        var currentContext = context
        var stepsTaken = 0
        val plan = mutableListOf<AiAgentStep>()
        
        // Initial reasoning - "What do I need to do?"
        var currentReasoning = reason(request, currentContext, plan, relevantMemory)
        
        while (stepsTaken < maxSteps && !isGoalComplete(currentReasoning)) {
            val nextStep = currentReasoning.nextStep ?: break
            plan.add(nextStep)
            
            // 1. Tool Selection & Execution
            val tool = toolRegistry.getTool(nextStep.toolName)
            if (tool == null) {
                return AiResponse(
                    AiResponseType.WARNING,
                    "Tool Error",
                    "I don't know how to use the tool: ${nextStep.toolName}"
                )
            }

            // Decision Gate Check for non-safe tools
            if (tool.riskLevel != ToolRiskLevel.SAFE && decisionGate != null) {
                val action = AiAction(
                    type = mapToolToType(tool.name),
                    title = nextStep.reason,
                    description = nextStep.reason,
                    parameters = nextStep.parameters,
                    requiresConfirmation = nextStep.requiresConfirmation
                )
                val gateResult = decisionGate.evaluateAction(action, AiConfidence.MEDIUM, 0.5f)
                if (!gateResult.success) {
                    return AiResponse(
                        AiResponseType.CLARIFICATION_NEEDED,
                        "Safety Check",
                        gateResult.message
                    )
                }
            }
            
            val stepResult = tool.execute(nextStep.parameters)
            val updatedStep = nextStep.copy(
                status = if (stepResult.success) StepStatus.COMPLETED else StepStatus.FAILED,
                result = stepResult
            )
            plan[plan.lastIndex] = updatedStep
            
            // 2. Observation & Verification
            if (stepResult.success && tool.riskLevel != ToolRiskLevel.SAFE) {
                // If it was a write operation, we could re-read data here to verify
                // For now, we trust the ToolResult success flag but the Agent could do more.
            }
            
            stepsTaken++
            
            // 3. Re-reasoning
            currentReasoning = reason(request, currentContext, plan, relevantMemory)
        }

        return finalizeResponse(currentReasoning, plan)
    }

    private fun reason(request: AiRequest, context: AiContext, plan: List<AiAgentStep>, relevantMemory: List<AiMemoryItem>): AgentReasoning {
        val lastStep = plan.lastOrNull()
        
        // Simple deterministic reasoning logic for the agent
        // In a real LLM implementation, this would be a prompt to the model.
        
        return when {
            // Case: Request about memory/history
            request.userMessage?.lowercase()?.contains("remember") == true || 
            request.userMessage?.lowercase()?.contains("history") == true ||
            request.userMessage?.lowercase()?.contains("know") == true -> {
                handleMemoryReasoning(request, context, plan, relevantMemory)
            }

            // Case: Task completion request
            request.userMessage?.lowercase()?.contains("complete") == true -> {
                handleCompleteTaskReasoning(request, context, plan)
            }
            
            // Case: Task creation request
            request.userMessage?.lowercase()?.contains("create") == true || 
            request.userMessage?.lowercase()?.contains("add") == true -> {
                handleCreateTaskReasoning(request, context, plan)
            }

            // Default fallback: No clear multi-step intent recognized for agent loop
            else -> AgentReasoning(isComplete = true, finalMessage = "I'm not sure how to break this into steps yet.")
        }
    }

    private fun handleMemoryReasoning(request: AiRequest, context: AiContext, plan: List<AiAgentStep>, relevantMemory: List<AiMemoryItem>): AgentReasoning {
        val personal = context.personalContext
        
        val report = buildString {
            append("Here is an overview of your current productivity status and history:\n\n")
            
            append("CURRENT CONTEXT:\n")
            append("- Workload: ${personal.workload.state} (${personal.workload.taskCount} tasks left)\n")
            append("- Day Progress: ${personal.dayState}\n")
            append("- Trend: ${personal.productivityTrend}\n")
            
            val atRisk = personal.goalHealth.filter { it.state == GoalHealthState.AT_RISK }
            if (atRisk.isNotEmpty()) {
                append("- Goal Risks: Found ${atRisk.size} goals needing immediate attention.\n")
            }
            
            if (relevantMemory.isNotEmpty()) {
                append("\nHISTORICAL PATTERNS:\n")
                relevantMemory.forEach { append("- ${it.title}: ${it.content}\n") }
            }
        }

        return AgentReasoning(isComplete = true, finalMessage = report)
    }

    private fun handleCompleteTaskReasoning(request: AiRequest, context: AiContext, plan: List<AiAgentStep>): AgentReasoning {
        val lastStep = plan.lastOrNull()
        
        if (lastStep == null) {
            // Step 1: Find the task
            val taskQuery = request.userMessage?.replace(Regex("(?i)complete|mark|as|done"), "")?.trim() ?: ""
            return AgentReasoning(
                nextStep = AiAgentStep("1", "findTask", mapOf("query" to taskQuery), "I need to find the task first.")
            )
        }
        
        if (lastStep.toolName == "findTask") {
            if (lastStep.result?.success == true) {
                val task = lastStep.result.data as? com.example.nexora.uii.PremiumTask
                if (task != null) {
                    return AgentReasoning(
                        nextStep = AiAgentStep("2", "completeTask", mapOf("taskId" to task.id), "Now I will mark the task as complete.")
                    )
                }
            } else {
                return AgentReasoning(isComplete = true, finalMessage = lastStep.result?.message ?: "Task not found.")
            }
        }
        
        if (lastStep.toolName == "completeTask") {
            return AgentReasoning(isComplete = true, finalMessage = lastStep.result?.message ?: "Task completed.")
        }

        return AgentReasoning(isComplete = true)
    }

    private fun handleCreateTaskReasoning(request: AiRequest, context: AiContext, plan: List<AiAgentStep>): AgentReasoning {
        val lastStep = plan.lastOrNull()
        
        if (lastStep == null) {
            val title = request.parameters["title"]?.toString() ?: request.userMessage?.replace(Regex("(?i)create|add|task|a"), "")?.trim() ?: ""
            return AgentReasoning(
                nextStep = AiAgentStep("1", "createTask", mapOf("title" to title), "Creating the requested task.")
            )
        }
        
        if (lastStep.toolName == "createTask") {
            return AgentReasoning(isComplete = true, finalMessage = lastStep.result?.message ?: "Task created.")
        }

        return AgentReasoning(isComplete = true)
    }

    private fun mapToolToType(toolName: String): AiActionType {
        return when (toolName) {
            "createTask" -> AiActionType.CREATE_TASK
            "completeTask" -> AiActionType.COMPLETE_TASK
            "updateTask" -> AiActionType.UPDATE_TASK
            "deleteTask" -> AiActionType.DELETE_TASK
            "createGoal" -> AiActionType.CREATE_GOAL
            "updateGoal" -> AiActionType.UPDATE_GOAL
            "deleteGoal" -> AiActionType.DELETE_GOAL
            else -> AiActionType.SHOW_INSIGHT
        }
    }

    private fun isGoalComplete(reasoning: AgentReasoning): Boolean = reasoning.isComplete

    private fun finalizeResponse(reasoning: AgentReasoning, plan: List<AiAgentStep>): AiResponse {
        val lastResult = plan.lastOrNull()?.result
        return AiResponse(
            responseType = if (lastResult?.success == true) AiResponseType.INFORMATION else AiResponseType.WARNING,
            title = "Agent Task Result",
            message = reasoning.finalMessage ?: lastResult?.message ?: "Agent execution finished.",
            proposedActions = emptyList() // The agent already executed actions
        )
    }

    private data class AgentReasoning(
        val nextStep: AiAgentStep? = null,
        val isComplete: Boolean = false,
        val finalMessage: String? = null
    )
}
