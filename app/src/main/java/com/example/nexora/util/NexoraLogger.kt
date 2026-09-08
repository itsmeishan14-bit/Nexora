package com.example.nexora.util

import android.util.Log

/**
 * Privacy-conscious logger for Nexora.
 * 
 * Ensures that sensitive information is only logged in non-production environments
 * (currently controlled by a manual flag, but ideally by BuildConfig.DEBUG).
 */
object NexoraLogger {
    private const val TAG = "NexoraAI"
    
    var isDebug: Boolean = true
    var useLog: Boolean = true // Flag to enable/disable android.util.Log

    fun d(tag: String = TAG, message: String) {
        if (isDebug) {
            if (useLog) {
                try {
                    Log.d(tag, message)
                } catch (e: Exception) {
                    println("DEBUG: [$tag] $message")
                }
            } else {
                println("DEBUG: [$tag] $message")
            }
        }
    }

    fun i(tag: String = TAG, message: String) {
        if (useLog) {
            try {
                Log.i(tag, message)
            } catch (e: Exception) {
                println("INFO: [$tag] $message")
            }
        } else {
            println("INFO: [$tag] $message")
        }
    }

    fun w(tag: String = TAG, message: String) {
        if (useLog) {
            try {
                Log.w(tag, message)
            } catch (e: Exception) {
                println("WARN: [$tag] $message")
            }
        } else {
            println("WARN: [$tag] $message")
        }
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (useLog) {
            try {
                if (throwable != null) {
                    Log.e(tag, message, throwable)
                } else {
                    Log.e(tag, message)
                }
            } catch (e: Exception) {
                println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
            }
        } else {
            println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
        }
    }

    /**
     * Logs a message that might contain sensitive data only in DEBUG mode.
     */
    fun sensitive(message: String) {
        if (isDebug) {
            d("NexoraSensitive", message)
        }
    }
}
