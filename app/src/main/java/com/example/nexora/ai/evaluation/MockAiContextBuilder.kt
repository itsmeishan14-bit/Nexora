package com.example.nexora.ai.evaluation

import com.example.nexora.ai.AiContext
import com.example.nexora.ai.AiContextBuilder
import com.example.nexora.ai.AiRequest

/**
 * A context builder that returns a fixed context for evaluation purposes.
 */
class MockAiContextBuilder : AiContextBuilder(null) {
    var fixedContext: AiContext? = null

    override suspend fun build(request: AiRequest?): AiContext {
        return fixedContext ?: super.build(request)
    }
}
