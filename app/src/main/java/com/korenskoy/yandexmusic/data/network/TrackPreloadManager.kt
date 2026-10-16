package com.korenskoy.yandexmusic.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class TrackPreloadManager @Inject constructor(
    private val api: YandexMusicApi,
    private val downloadManager: TrackDownloadManager,
    private val okHttpClient: OkHttpClient
) {
    private data class CachedTrackUrl(val url: String, val fetchedAtMs: Long)

    companion object {
        const val URL_TTL_MS = 45 * 60 * 1000L // 45 минут
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolvedUrls = ConcurrentHashMap<String, CachedTrackUrl>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()

    fun getResolvedUrl(trackId: String): String? {
        val cached = resolvedUrls[trackId] ?: return null
        if (System.currentTimeMillis() - cached.fetchedAtMs > URL_TTL_MS) {
            resolvedUrls.remove(trackId)
            return null
        }
        return cached.url
    }

    fun evictUrl(trackId: String) {
        resolvedUrls.remove(trackId)
        inFlightRequests.remove(trackId)?.cancel()
    }

    fun preloadTracks(trackIds: List<String>) {
        scope.launch {
            trackIds.map { trackId ->
                async {
                    fetchOrAwait(trackId)
                }
            }.awaitAll()
        }
    }

    suspend fun preloadSingleTrackPriority(trackId: String): String? {
        return fetchOrAwait(trackId)
    }

    suspend fun getOrFetchUrl(trackId: String): String? {
        val cached = getResolvedUrl(trackId)
        if (cached != null) return cached
        return fetchOrAwait(trackId)
    }

    private suspend fun fetchOrAwait(trackId: String): String? {
        if (trackId.startsWith("radio_")) return null
        if (downloadManager.isTrackDownloaded(trackId)) return null
        getResolvedUrl(trackId)?.let { return it }

        val deferred = inFlightRequests.computeIfAbsent(trackId) {
            scope.async(Dispatchers.IO) {
                try {
                    val downloadInfoResponse = api.getTrackDownloadInfo(trackId)
                    val infoList = downloadInfoResponse.result ?: emptyList()
                    val preferredInfo = infoList.filter { !it.preview && it.codec.equals("mp3", ignoreCase = true) }
                        .maxByOrNull { it.bitrateInKbps }
                        ?: infoList.filter { !it.preview }.maxByOrNull { it.bitrateInKbps }
                        ?: infoList.firstOrNull()
                    val infoUrl = preferredInfo?.downloadInfoUrl
                    
                    if (infoUrl != null) {
                        val mp3Url = resolveXmlToMp3Url(infoUrl)
                        if (mp3Url != null) {
                            resolvedUrls[trackId] = CachedTrackUrl(mp3Url, System.currentTimeMillis())
                            Log.d("TrackPreloadManager", "Successfully resolved URL for track $trackId")
                            return@async mp3Url
                        }
                    }
                    null
                } catch (e: Exception) {
                    Log.e("TrackPreloadManager", "Error resolving URL for track $trackId", e)
                    null
                } finally {
                    inFlightRequests.remove(trackId)
                }
            }
        }
        return try {
            deferred.await()
        } catch (e: Exception) {
            inFlightRequests.remove(trackId)
            null
        }
    }

    fun clearCache() {
        resolvedUrls.clear()
        inFlightRequests.values.forEach { it.cancel() }
        inFlightRequests.clear()
    }
    
    private suspend fun resolveXmlToMp3Url(infoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(infoUrl).build()
            val xmlData = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val xmlResponse = response.body?.string() ?: return@use null
                DownloadInfoParser.parseXml(xmlResponse)
            }
            if (xmlData != null) {
                return@withContext Md5Helper.generateMp3Url(
                    xmlData.host,
                    xmlData.path,
                    xmlData.ts,
                    xmlData.s
                )
            }
            null
        } catch (e: Exception) {
            Log.e("TrackPreloadManager", "Error parsing XML for url: $infoUrl", e)
            null
        }
    }
}
