package com.example.nexora.ai

import com.example.nexora.uii.PremiumTask
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
        
        // Match the title better
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Complete Study Java")
        val context = AiContext(tasks = listOf(task))
        
        val response = agent.execute(request, context)
        
        assertEquals("Expected INFORMATION response type. Actual: ${response.responseType}, message: ${response.message}", 
            AiResponseType.INFORMATION, response.responseType)
        assertTrue(response.message.contains("completed", ignoreCase = true))
        
        // Verify steps
        assertEquals(2, toolRegistry.executionLog.size)
        assertEquals("findTask", toolRegistry.executionLog[0])
        assertEquals("completeTask", toolRegistry.executionLog[1])
    }

    @Test
    fun `test agent handles create task request in one step`() = runBlocking {
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Create a task to build Nexora Agent")
        val context = AiContext()
        
        val response = agent.execute(request, context)
        
        assertEquals(AiResponseType.INFORMATION, response.responseType)
        assertTrue(response.message.contains("created", ignoreCase = true))
        assertEquals("createTask", toolRegistry.executionLog.first())
    }

    @Test
    fun `test agent stops when task not found`() = runBlocking {
        val request = AiRequest(AiRequestType.CHAT, userMessage = "Complete nonexistent")
        val context = AiContext()
        
        val response = agent.execute(request, context)
        
        // Should return WARNING because the loop ended with a failed step (findTask)
        assertEquals(AiResponseType.WARNING, response.responseType)
        assertTrue(response.message.contains("not found", ignoreCase = true))
        assertEquals(1, toolRegistry.executionLog.size)
        assertEquals("findTask", toolRegistry.executionLog.first())
    }

    private class FakeToolRegistry : AiToolRegistry(null, null) {
        val executionLog = mutableListOf<String>()
        val tasks = mutableListOf<PremiumTask>()

        override fun getTool(name: String): AiTool? {
            return when (name) {
                "findTask" -> object : AiTool {
                    override val name = "findTask"
                    override val description = ""
                    override val riskLevel = ToolRiskLevel.SAFE
                    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
                        executionLog.add(name)
                        val query = parameters["query"]?.toString() ?: ""
                        val match = tasks.find { it.title.contains(query, ignoreCase = true) }
                        return if (match != null) ToolResult(true, match, "Found") else ToolResult(false, message = "Task not found")
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
                else -> null
            }
        }
    }
}
