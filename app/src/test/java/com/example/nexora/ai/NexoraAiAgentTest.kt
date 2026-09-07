package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraAiAgentTest {

    private lateinit var agent: NexoraAiAgent
    private lateinit var toolRegistry: FakeToolRegistry

    @Before
    fun setup() {
        toolRegistry = FakeToolRegistry()
        agent = NexoraAiAgent(toolRegistry)
    }

    @Test
    fun `test agent handles complete task request in two steps`() = runBlocking {
        val task = PremiumTask(id = 42, title = "Study Java", category = "Work", duration = "1 hr")
        toolRegistry.tasks.add(task)
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Complete Study Java")
        val context = AiContext(tasks = listOf(task))
        
        val response = agent.execute(request, context)
        
        assertEquals(AiResponseType.INFORMATION, response.responseType)
        assertTrue(response.message.contains("completed", ignoreCase = true))
        
        // Verify steps
        assertEquals(2, toolRegistry.executionLog.size)
        assertEquals("findTask", toolRegistry.executionLog[0])
        assertEquals("completeTask", toolRegistry.executionLog[1])
    }

    @Test
    fun `test agent goal execution workflow stops for confirmation`() = runBlocking {
        val goal = NexoraGoal(id = 1, title = "Java Mastery", category = "Learning", targetDate = "", progress = 0.1f)
        toolRegistry.goals.add(goal)
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Help me finish my Java Mastery goal")
        val context = AiContext(goals = listOf(goal))
        
        val response = agent.execute(request, context)
        
        // Should stop at step 3 (createTask) because it requires confirmation
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(response.message.contains("Should I add them", ignoreCase = true))
        
        assertEquals(2, toolRegistry.executionLog.size)
        assertEquals("findGoal", toolRegistry.executionLog[0])
        assertEquals("decomposeGoal", toolRegistry.executionLog[1])
        
        assertNotNull(response.proposedActions.find { it.type == AiActionType.CREATE_TASK })
    }

    @Test
    fun `test agent prevents duplicate task creation (idempotency)`() = runBlocking {
        val existingTask = PremiumTask(title = "Study Java", category = "Personal", duration = "30 min")
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Create task Study Java")
        val context = AiContext(tasks = listOf(existingTask))
        
        val response = agent.execute(request, context)
        
        assertEquals(AiResponseType.NO_ACTION, response.responseType)
        assertTrue(response.message.contains("already exists", ignoreCase = true))
        assertEquals(0, toolRegistry.executionLog.size)
    }

    @Test
    fun `test agent reaches step limit and fails safely`() = runBlocking {
        // Create an agent with very small step limit
        val smallAgent = NexoraAiAgent(toolRegistry, maxSteps = 1)
        val goal = NexoraGoal(id = 1, title = "Java Mastery", category = "Learning", targetDate = "", progress = 0.1f)
        toolRegistry.goals.add(goal)
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Help me finish my Java Mastery goal")
        val context = AiContext(goals = listOf(goal))
        
        val response = smallAgent.execute(request, context)
        
        assertEquals(AiResponseType.WARNING, response.responseType)
        assertTrue(response.message.contains("maximum step limit", ignoreCase = true))
    }

    @Test
    fun `test agent cleanup workflow identifies low priority tasks`() = runBlocking {
        val task = PremiumTask(id = 100, title = "Old Task", category = "Misc", duration = "10m", priority = com.example.nexora.uii.TaskPriority.LOW)
        toolRegistry.tasks.add(task)
        val context = AiContext(tasks = listOf(task))
        
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Clean up my tasks")
        val response = agent.execute(request, context)
        
        // Should stopped for confirmation to reschedule
        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(response.message.contains("low-priority tasks", ignoreCase = true))
    }

    private class FakeToolRegistry : AiToolRegistry(null, null) {
        val executionLog = mutableListOf<String>()
        val tasks = mutableListOf<PremiumTask>()
        val goals = mutableListOf<NexoraGoal>()

        override fun getTool(name: String): AiTool? {
            return when (name) {
                "findTask" -> object : AiTool {
                    override val name = "findTask"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.SAFE
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        val query = parameters["query"]?.toString() ?: ""
                        // Robust fake matching
                        val match = tasks.find { task ->
                            val normalizedTitle = task.title.lowercase().trim()
                            val normalizedQuery = query.lowercase().trim()
                            normalizedQuery.contains(normalizedTitle) || normalizedTitle.contains(normalizedQuery)
                        }
                        return if (match != null) ToolResult(true, match, "Found") else ToolResult(false, message = "Task not found for: $query")
                    }
                }
                "findGoal" -> object : AiTool {
                    override val name = "findGoal"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.SAFE
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        val query = parameters["query"]?.toString() ?: ""
                        // Robust fake matching: check if any part of the query is in the goal title or vice versa
                        val match = goals.find { goal ->
                            val normalizedTitle = goal.title.lowercase().trim()
                            val normalizedQuery = query.lowercase().trim()
                            normalizedQuery.contains(normalizedTitle) || normalizedTitle.contains(normalizedQuery)
                        }
                        return if (match != null) ToolResult(true, match, "Found") else ToolResult(false, message = "Goal not found for: $query")
                    }
                }
                "decomposeGoal" -> object : AiTool {
                    override val name = "decomposeGoal"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.SAFE
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        val title = parameters["title"]?.toString() ?: "Goal"
                        val decomposition = AiGoalDecomposition(
                            goalTitle = title,
                            summary = "Steps",
                            steps = listOf(AiGoalStep("Subtask 1", "desc", AiPriority.MEDIUM, "30m", 1))
                        )
                        return ToolResult(true, decomposition, "Decomposed")
                    }
                }
                "completeTask" -> object : AiTool {
                    override val name = "completeTask"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.LOW_RISK
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        return ToolResult(true, message = "Task completed")
                    }
                }
                "createTask" -> object : AiTool {
                    override val name = "createTask"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.LOW_RISK
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        return ToolResult(true, message = "Task created")
                    }
                }
                "listTasks" -> object : AiTool {
                    override val name = "listTasks"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.SAFE
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        return ToolResult(true, tasks, "Listed")
                    }
                }
                "rescheduleTask" -> object : AiTool {
                    override val name = "rescheduleTask"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.LOW_RISK
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        return ToolResult(true, message = "Rescheduled")
                    }
                }
                else -> null
            }
        }
    }
}
