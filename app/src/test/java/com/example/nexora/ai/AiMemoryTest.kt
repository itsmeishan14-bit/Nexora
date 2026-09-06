package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiMemoryTest {

    private lateinit var repository: FakeNexoraRepository
    private lateinit var memoryRetriever: AiMemoryRetriever

    @Before
    fun setUp() {
        repository = FakeNexoraRepository()
        memoryRetriever = AiMemoryRetriever(repository)
    }

    @Test
    fun testMemoryRelevanceScoring() = runBlocking {
        val taskId = 100L
        val memory = AiMemoryItem(
            category = AiMemoryCategory.TASK_PATTERN,
            title = "Task Pattern",
            content = "Repeated carry over",
            relatedTaskId = taskId,
            importance = AiMemoryImportance.HIGH,
            confidence = AiMemoryConfidence.HIGH
        )
        
        repository.memories = listOf(memory)
        
        val request = AiRequest(AiRequestType.NEXT_TASK, taskId = taskId)
        val retrieved = memoryRetriever.retrieveRelevantMemory(request)
        
        assertEquals(1, retrieved.size)
        assertEquals(memory.id, retrieved[0].id)
    }

    @Test
    fun testMemoryRetrievalLimit() = runBlocking {
        val memories = (1..10).map { 
            AiMemoryItem(
                category = AiMemoryCategory.PRODUCTIVITY_PATTERN,
                title = "Pattern $it",
                content = "Content $it",
                importance = AiMemoryImportance.MEDIUM
            )
        }
        
        repository.memories = memories
        
        val request = AiRequest(AiRequestType.PRODUCTIVITY_ANALYSIS)
        val retrieved = memoryRetriever.retrieveRelevantMemory(request)
        
        assertEquals(5, retrieved.size)
    }

    private class FakeNexoraRepository : NexoraRepository(null) {
        var memories = emptyList<AiMemoryItem>()

        override suspend fun getAllMemory(): List<AiMemoryItem> = memories
    }
}
