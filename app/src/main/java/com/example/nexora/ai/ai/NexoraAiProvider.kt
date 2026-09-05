package com.example.nexora.ai

import android.content.Context
import com.example.nexora.data.NexoraDatabase
import com.example.nexora.data.NexoraRepository

object NexoraAiProvider {

    fun createEngine(context: Context): NexoraAiEngine {

        val database = NexoraDatabase.getDatabase(context)

        val repository = NexoraRepository(database)

        val contextBuilder = AiContextBuilder(repository)

        val aiService = LocalNexoraAiService()

        return NexoraAiEngine(
            contextBuilder = contextBuilder,
            aiService = aiService
        )
    }
}