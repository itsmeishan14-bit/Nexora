package com.example.nexora.util

/**
 * Nexora Privacy and Security Policy.
 * 
 * Defines the core principles for data handling in the application.
 */
object PrivacyPolicy {
    
    /**
     * Nexora is local-first. All productivity data must be stored on-device
     * by default.
     */
    const val LOCAL_FIRST = true
    
    /**
     * AI Processing should happen locally whenever possible.
     */
    const val LOCAL_AI_PROCESSING = true
    
    /**
     * Sensitive data minimization. AI operations should only receive
     * the subset of data required for the specific task.
     */
    const val DATA_MINIMIZATION_ENABLED = true
    
    /**
     * User data deletion rules. Deleting an entity must remove all
     * related AI metadata and memories.
     */
    const val CASCADE_DELETION_REQUIRED = true
    
    /**
     * Logging rules. No sensitive user data (titles, content) in production logs.
     */
    const val PRODUCTION_LOGGING_SENSITIVE_DATA = false
}
