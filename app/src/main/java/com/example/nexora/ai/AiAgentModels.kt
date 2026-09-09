package com.example.nexora.ai

/**
 * Represents a tool that the AI Agent can use.
 */
interface AiTool {
    val name: String
    val description: String
    val riskLevel: ToolRiskLevel
    
    suspend fun execute(parameters: Map<String, Any>): ToolResult
}

enum class ToolRiskLevel {
    SAFE,        // Read-only
    LOW_RISK,    // Create/Update
    HIGH_RISK,   // Major changes
    DESTRUCTIVE  // Delete
}

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val message: String,
    val error: String? = null
)

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
