package com.korenskoy.yandexmusic.data.network

import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.OkHttpClient
import android.util.Log
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Unexpected error", e)
            false
        }
    }

    val isConnectedFlow: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(true)
            }

            // The default network: moving from Wi-Fi to mobile data only replaces it, and this comes when
            // there is no network left at all
            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(false)
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)

            // Initial state
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                trySend(hasInternet)
            } else {
                trySend(false)
            }
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Unexpected error", e)
            trySend(true) // Fallback to true if we can't check
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Unexpected error", e)
            }
        }
    }.distinctUntilChanged()

    /** Its own small client: no account token, no cache; the connections of the app's client are not waited for. */
    private val probeClient by lazy { OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build() }

    /**
     * Does Yandex's music server answer at all? The way the music itself goes: every address of the name (a
     * network that hands out an IPv6 address with no IPv6 route behind it is common on boxes and head units),
     * the system's proxy and DNS. A bare socket to the first address said "no server" on such networks forever.
     */
    suspend fun isYandexReachable(timeoutMs: Int = 1500): Boolean = withContext(Dispatchers.IO) {
        answers(probeClient, PROBE_URL, timeoutMs)
    }

    companion object {
        private const val PROBE_URL = "https://api.music.yandex.net/"

        /** Any HTTP answer, even an error page, means the server is there. */
        internal fun answers(client: OkHttpClient, url: String, timeoutMs: Int): Boolean = try {
            client.newBuilder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
                .newCall(Request.Builder().url(url).head().build())
                .execute()
                .use { true }
        } catch (e: IOException) {
            // Unreachable, timed out or refused: all the same here
            Log.d("NetworkMonitor", "No answer from $url: ${e.message}")
            false
        }
    }
}
