package com.example.nexora.ai

import kotlinx.coroutines.delay

/**
 * Nexora AI Agent capable of multi-step reasoning and tool use.
 */
class NexoraAiAgent(
    private val toolRegistry: AiToolRegistry,
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
        if (relevantMemory.isEmpty()) {
            return AgentReasoning(isComplete = true, finalMessage = "I don't have enough history to provide specific insights on that yet.")
        }

        val memorySummary = relevantMemory.joinToString("\n") { "- ${it.title}: ${it.content}" }
        return AgentReasoning(isComplete = true, finalMessage = "Here is what I remember about your productivity:\n\n$memorySummary")
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
