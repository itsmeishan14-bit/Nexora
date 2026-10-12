package com.example.nexora.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility to verify device internet connectivity.
 * Supports testing override for deterministic unit and integration tests.
 */
object NetworkUtils {

    @Volatile
    private var networkOverrideForTesting: Boolean? = null

    /**
     * Override network availability state for unit tests.
     * Pass null to clear override and use real system checks.
     */
    fun setNetworkAvailableForTesting(available: Boolean?) {
        networkOverrideForTesting = available
    }

    /**
     * Checks if the device has an active internet connection.
     * Returns true if network capabilities include NET_CAPABILITY_INTERNET.
     * If context is null and no override is set, defaults to true (optimistic check).
     */
    fun isNetworkAvailable(context: Context?): Boolean {
        networkOverrideForTesting?.let { return it }
        if (context == null) return true

        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
