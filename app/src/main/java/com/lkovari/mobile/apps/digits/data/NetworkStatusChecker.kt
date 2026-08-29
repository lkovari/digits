package com.lkovari.mobile.apps.digits.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface NetworkStatusChecker {
    fun isOnline(): Boolean
}

class AndroidNetworkStatusChecker(
    private val context: Context
) : NetworkStatusChecker {
    override fun isOnline(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }
}

enum class SyncIssue {
    NONE,
    NO_INTERNET,
    DATABASE_UNAVAILABLE
}

object SyncIssueMessages {
    fun message(issue: SyncIssue): String? {
        return when (issue) {
            SyncIssue.NONE -> null
            SyncIssue.NO_INTERNET ->
                "No internet connection. Playing an offline puzzle. Progress stays on this device until you reconnect."
            SyncIssue.DATABASE_UNAVAILABLE ->
                "Puzzle database is unreachable. Playing a local puzzle. Tap Retry when the connection is back."
        }
    }

    fun classify(isOnline: Boolean, error: Throwable?): SyncIssue {
        if (error == null) {
            return SyncIssue.NONE
        }
        if (!isOnline) {
            return SyncIssue.NO_INTERNET
        }
        val text = (error.message ?: error.toString()).lowercase()
        return if (
            text.contains("unable to resolve host") ||
            text.contains("network") ||
            text.contains("timeout") ||
            text.contains("unreachable") ||
            text.contains("offline") ||
            text.contains("connection")
        ) {
            SyncIssue.NO_INTERNET
        } else {
            SyncIssue.DATABASE_UNAVAILABLE
        }
    }
}
