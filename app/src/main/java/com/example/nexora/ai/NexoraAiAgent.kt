package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.delay

/**
 * Nexora AI Agent capable of multi-step reasoning and workflow execution.
 */
class NexoraAiAgent(
    private val toolRegistry: AiToolRegistry,
    private val decisionGate: AiDecisionGate? = null,
    private val contextBuilder: AiContextBuilder? = null,
    private val maxSteps: Int = 8
) {
    private val pipeline = AdvancedLocalLanguagePipeline()

    /**
     * Executes a user request using a controlled iterative loop.
     */
    suspend fun execute(
        request: AiRequest, 
        initialContext: AiContext, 
        relevantMemory: List<AiMemoryItem> = emptyList()
    ): AiResponse {
        var currentContext = initialContext
        var stepsTaken = 0
        
        // Initialize Workflow
        var workflow = AgentWorkflow(
            objective = request.userMessage ?: "Process Request",
            status = WorkflowStatus.PLANNING
        )

        val executedActions = mutableListOf<String>()

        while (stepsTaken < maxSteps && !isWorkflowFinished(workflow.status)) {
            
            // 1. OBSERVE & REFRESH (if not first step)
            if (stepsTaken > 0 && contextBuilder != null) {
                currentContext = contextBuilder.build()
            }

            // 2. UNDERSTAND & PLAN
            val reasoning = reason(request, currentContext, workflow, relevantMemory)
            
            // Update workflow status from reasoning
            workflow = workflow.copy(status = reasoning.status)
            
            if (workflow.status == WorkflowStatus.NO_ACTION || workflow.status == WorkflowStatus.COMPLETED) {
                workflow = workflow.copy(status = workflow.status, completionReason = reasoning.finalMessage)
                break
            }

            if (workflow.status == WorkflowStatus.FAILED) {
                workflow = workflow.copy(status = WorkflowStatus.FAILED, failureReason = reasoning.finalMessage)
                break
            }

            val nextStep = reasoning.nextStep ?: break
            
            // 3. VALIDATE & CONFIRM
            if (nextStep.requiresConfirmation) {
                // If it requires confirmation, we stop and ask the user
                return buildConfirmationResponse(workflow, nextStep, reasoning.finalMessage)
            }

            // 4. EXECUTE
            val tool = toolRegistry.getTool(nextStep.toolName)
            if (tool == null) {
                workflow = workflow.copy(status = WorkflowStatus.FAILED, failureReason = "Tool not found: ${nextStep.toolName}")
                break
            }

            // Security Gate
            if (tool.riskLevel != ToolRiskLevel.SAFE && decisionGate != null) {
                val action = mapStepToAction(nextStep)
                val gateResult = decisionGate.evaluateAction(action, nextStep.confidence, 0.5f)
                if (!gateResult.success) {
                    return AiResponse(
                        AiResponseType.CLARIFICATION_NEEDED,
                        "Safety Check",
                        gateResult.message,
                        workflow = workflow
                    )
                }
            }

            val stepResult = tool.execute(nextStep.parameters)
            
            // 5. VERIFY & UPDATE
            val updatedStep = nextStep.copy(
                status = if (stepResult.success) StepStatus.COMPLETED else StepStatus.FAILED,
                result = stepResult
            )
            
            workflow = workflow.copy(
                steps = workflow.steps + updatedStep,
                currentStepIndex = stepsTaken,
                updatedAt = System.currentTimeMillis()
            )

            if (!stepResult.success) {
                workflow = workflow.copy(status = WorkflowStatus.FAILED, failureReason = stepResult.message)
                break
            }

            executedActions.add(updatedStep.description)
            stepsTaken++
            
            // Small delay for realism/processing in complex workflows
            if (stepsTaken < maxSteps) delay(50L)
        }

        if (stepsTaken >= maxSteps && !isWorkflowFinished(workflow.status)) {
            workflow = workflow.copy(status = WorkflowStatus.FAILED, failureReason = "Workflow reached maximum step limit ($maxSteps).")
        }

        return finalizeResponse(workflow, executedActions)
    }

    private fun reason(
        request: AiRequest, 
        context: AiContext, 
        workflow: AgentWorkflow, 
        relevantMemory: List<AiMemoryItem>
    ): AgentReasoning {
        val msg = request.userMessage ?: ""
        
        // Use the pipeline to extract structured intent/entities
        val langResult = pipeline.process(msg, context)
        
        return when {
            // Case: Clean up / Organize
            msg.lowercase().contains("clean") || msg.lowercase().contains("organize") || msg.lowercase().contains("overload") -> {
                handleCleanupWorkflow(request, context, workflow)
            }

            // Case: Help with Goal (e.g. "Help me finish my Java goal")
            langResult.intent == AiDecisionType.CREATE_GOAL || 
            langResult.intent == AiDecisionType.UPDATE_GOAL ||
            langResult.intent == AiDecisionType.DECOMPOSE_GOAL ||
            msg.lowercase().contains("goal") -> {
                handleGoalExecutionReasoning(request, context, workflow, langResult)
            }

            // Case: Memory/History
            msg.lowercase().contains("remember") || msg.lowercase().contains("history") || msg.lowercase().contains("know") -> {
                handleMemoryReasoning(request, context, workflow, relevantMemory)
            }

            // Case: Task completion
            langResult.intent == AiDecisionType.COMPLETE_TASK -> {
                handleCompleteTaskReasoning(request, context, workflow, langResult)
            }
            
            // Case: Task creation
            langResult.intent == AiDecisionType.CREATE_TASK -> {
                handleCreateTaskReasoning(request, context, workflow, langResult)
            }

            // Case: Clean up / Organize
            msg.contains("clean") || msg.contains("organize") || msg.contains("overload") -> {
                handleCleanupWorkflow(request, context, workflow)
            }

            // Default fallback
            else -> AgentReasoning(status = WorkflowStatus.NO_ACTION, finalMessage = "I'm not sure how to break this into steps yet.")
        }
    }

    private fun handleCleanupWorkflow(
        request: AiRequest, 
        context: AiContext, 
        workflow: AgentWorkflow
    ): AgentReasoning {
        val lowPriorityIncomplete = context.incompleteTasks.filter { it.priority == com.example.nexora.uii.TaskPriority.LOW }

        if (lowPriorityIncomplete.isEmpty()) {
            return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = "Your task list looks clean! No low-priority items found to organize.")
        }

        val summary = "I found ${lowPriorityIncomplete.size} low-priority tasks. Should I reschedule them to tomorrow?"
        return AgentReasoning(
            nextStep = AgentWorkflowStep(
                workflowId = workflow.id,
                order = 0,
                description = "Postpone tasks",
                toolName = "rescheduleTask",
                parameters = mapOf("taskId" to lowPriorityIncomplete.first().id),
                requiresConfirmation = true,
                reason = summary
            ),
            finalMessage = summary
        )
    }

    private fun handleGoalExecutionReasoning(
        request: AiRequest, 
        context: AiContext, 
        workflow: AgentWorkflow,
        langResult: AiLanguageResult
    ): AgentReasoning {
        val lastStep = workflow.steps.lastOrNull()
        val goalQuery = langResult.entities["title"]?.toString() ?: request.userMessage ?: ""

        // 1. Identify Goal
        if (workflow.steps.isEmpty()) {
            return AgentReasoning(
                nextStep = AgentWorkflowStep(
                    workflowId = workflow.id,
                    order = 0,
                    description = "Finding the goal",
                    toolName = "findGoal",
                    parameters = mapOf("query" to goalQuery),
                    confidence = AiConfidence.HIGH
                )
            )
        }

        // 2. Analyze Goal & Propose Steps
        if (lastStep?.toolName == "findGoal") {
            if (lastStep.result?.success == true) {
                val goal = lastStep.result.data as? com.example.nexora.uii.NexoraGoal
                if (goal != null) {
                    return AgentReasoning(
                        nextStep = AgentWorkflowStep(
                            workflowId = workflow.id,
                            order = 1,
                            description = "Decomposing goal into tasks",
                            toolName = "decomposeGoal",
                            parameters = mapOf("title" to goal.title, "category" to goal.category),
                            confidence = AiConfidence.HIGH
                        )
                    )
                }
            } else {
                return AgentReasoning(status = WorkflowStatus.FAILED, finalMessage = lastStep.result?.message ?: "Goal not found.")
            }
        }

        // 3. Propose Task Creation (Requires Confirmation)
        if (lastStep?.toolName == "decomposeGoal") {
            if (lastStep.result?.success == true) {
                val decomposition = lastStep.result.data as? AiGoalDecomposition
                if (decomposition != null && decomposition.steps.isNotEmpty()) {
                    
                    // Filter out existing tasks to ensure IDEMPOTENCY
                    val missingSteps = decomposition.steps.filter { step ->
                        context.tasks.none { it.title.lowercase().trim() == step.title.lowercase().trim() }
                    }

                    if (missingSteps.isEmpty()) {
                        return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = "All steps for \"${decomposition.goalTitle}\" are already in your task list.")
                    }

                    val summary = "I can break down \"${decomposition.goalTitle}\" into ${missingSteps.size} new tasks. Should I add them?"
                    return AgentReasoning(
                        nextStep = AgentWorkflowStep(
                            workflowId = workflow.id,
                            order = 2,
                            description = "Create missing sub-tasks",
                            toolName = "createTask",
                            parameters = mapOf("title" to missingSteps.first().title, "goalTitle" to decomposition.goalTitle),
                            requiresConfirmation = true,
                            reason = summary
                        ),
                        finalMessage = summary
                    )
                }
            }
        }

        return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = "Goal execution steps finished.")
    }

    private fun handleMemoryReasoning(request: AiRequest, context: AiContext, workflow: AgentWorkflow, relevantMemory: List<AiMemoryItem>): AgentReasoning {
        if (relevantMemory.isEmpty()) {
            return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = "I don't have enough history to provide specific insights yet.")
        }
        val memorySummary = relevantMemory.joinToString("\n") { "- ${it.title}: ${it.content}" }
        return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = "Here is what I remember:\n\n$memorySummary")
    }

    private fun handleCompleteTaskReasoning(
        request: AiRequest, 
        context: AiContext, 
        workflow: AgentWorkflow,
        langResult: AiLanguageResult
    ): AgentReasoning {
        val lastStep = workflow.steps.lastOrNull()
        val taskQuery = langResult.entities["title"]?.toString() ?: ""
        
        if (lastStep == null) {
            return AgentReasoning(
                nextStep = AgentWorkflowStep(
                    workflowId = workflow.id,
                    order = 0,
                    description = "Finding the task",
                    toolName = "findTask",
                    parameters = mapOf("query" to taskQuery)
                )
            )
        }
        
        if (lastStep.toolName == "findTask") {
            if (lastStep.result?.success == true) {
                val task = lastStep.result.data as? com.example.nexora.uii.PremiumTask
                if (task != null) {
                    if (task.completed) return AgentReasoning(status = WorkflowStatus.NO_ACTION, finalMessage = "Task \"${task.title}\" is already completed.")
                    return AgentReasoning(
                        nextStep = AgentWorkflowStep(
                            workflowId = workflow.id,
                            order = 1,
                            description = "Completing task",
                            toolName = "completeTask",
                            parameters = mapOf("taskId" to task.id)
                        )
                    )
                }
            } else {
                return AgentReasoning(status = WorkflowStatus.FAILED, finalMessage = lastStep.result?.message ?: "Task not found.")
            }
        }
        
        if (lastStep.toolName == "completeTask") {
            return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = lastStep.result?.message ?: "Task completed.")
        }

        return AgentReasoning(status = WorkflowStatus.COMPLETED)
    }

    private fun handleCreateTaskReasoning(
        request: AiRequest, 
        context: AiContext, 
        workflow: AgentWorkflow,
        langResult: AiLanguageResult
    ): AgentReasoning {
        val lastStep = workflow.steps.lastOrNull()
        val title = langResult.entities["title"]?.toString() ?: ""
        
        if (lastStep == null) {
            if (title.isBlank()) return AgentReasoning(status = WorkflowStatus.FAILED, finalMessage = "I'm not sure what task you want me to create.")

            // IDEMPOTENCY CHECK
            val normalizedTitle = title.lowercase().trim()
            val existing = context.tasks.find { it.title.lowercase().trim() == normalizedTitle }
            if (existing != null) {
                return AgentReasoning(status = WorkflowStatus.NO_ACTION, finalMessage = "A task with that title already exists: \"${existing.title}\"")
            }

            return AgentReasoning(
                nextStep = AgentWorkflowStep(
                    workflowId = workflow.id,
                    order = 0,
                    description = "Creating the task",
                    toolName = "createTask",
                    parameters = mapOf("title" to title)
                )
            )
        }
        
        if (lastStep.toolName == "createTask") {
            return AgentReasoning(status = WorkflowStatus.COMPLETED, finalMessage = lastStep.result?.message ?: "Task created.")
        }

        return AgentReasoning(status = WorkflowStatus.COMPLETED)
    }

    private fun isWorkflowFinished(status: WorkflowStatus): Boolean {
        return status == WorkflowStatus.COMPLETED || 
               status == WorkflowStatus.FAILED || 
               status == WorkflowStatus.CANCELLED ||
               status == WorkflowStatus.NO_ACTION
    }

    private fun buildConfirmationResponse(workflow: AgentWorkflow, step: AgentWorkflowStep, message: String?): AiResponse {
        return AiResponse(
            responseType = AiResponseType.ACTION_PROPOSAL,
            title = "Confirmation Required",
            message = message ?: "I need your approval to proceed with: ${step.description}",
            proposedActions = listOf(mapStepToAction(step)),
            confidence = step.confidence,
            workflow = workflow.copy(status = WorkflowStatus.WAITING_FOR_CONFIRMATION)
        )
    }

    private fun finalizeResponse(workflow: AgentWorkflow, executedActions: List<String>): AiResponse {
        val status = workflow.status
        val responseType = when (status) {
            WorkflowStatus.COMPLETED -> AiResponseType.INFORMATION
            WorkflowStatus.NO_ACTION -> AiResponseType.NO_ACTION
            WorkflowStatus.FAILED -> AiResponseType.WARNING
            WorkflowStatus.WAITING_FOR_CONFIRMATION -> AiResponseType.ACTION_PROPOSAL
            else -> AiResponseType.INFORMATION
        }

        val message = buildString {
            if (executedActions.isNotEmpty()) {
                append("Actions taken: ${executedActions.joinToString(", ")}.\n\n")
            }
            append(workflow.completionReason ?: workflow.failureReason ?: "Agent execution finished.")
        }

        return AiResponse(
            responseType = responseType,
            title = if (status == WorkflowStatus.FAILED) "Agent Failure" else "Agent Workflow",
            message = message,
            workflow = workflow
        )
    }

    private fun mapStepToAction(step: AgentWorkflowStep): AiAction {
        return AiAction(
            type = mapToolToType(step.toolName),
            title = step.description,
            description = step.description,
            parameters = step.parameters,
            reason = step.reason,
            requiresConfirmation = step.requiresConfirmation
        )
    }

    private fun mapToolToType(toolName: String): AiActionType {
        return when (toolName) {
            "createTask" -> AiActionType.CREATE_TASK
            "completeTask" -> AiActionType.COMPLETE_TASK
            "updateTask" -> AiActionType.UPDATE_TASK
            "deleteTask" -> AiActionType.DELETE_TASK
            "rescheduleTask" -> AiActionType.RESCHEDULE_TASK
            "createGoal" -> AiActionType.CREATE_GOAL
            "updateGoal" -> AiActionType.UPDATE_GOAL
            "deleteGoal" -> AiActionType.DELETE_GOAL
            "decomposeGoal" -> AiActionType.DECOMPOSE_GOAL
            "findGoal" -> AiActionType.OPEN_GOAL
            else -> AiActionType.SHOW_INSIGHT
        }
    }

    private data class AgentReasoning(
        val nextStep: AgentWorkflowStep? = null,
        val status: WorkflowStatus = WorkflowStatus.EXECUTING,
        val finalMessage: String? = null
    )
}
