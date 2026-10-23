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
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class TrackPreloadManager @Inject constructor(
    private val api: YandexMusicApi,
    private val downloadManager: TrackDownloadManager,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: com.korenskoy.yandexmusic.domain.repository.SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: com.korenskoy.yandexmusic.domain.repository.AuthRepository
) {
    /** [capKbps]: the bitrate limit the link was chosen under (null — best quality). */
    private data class CachedTrackUrl(val url: String, val fetchedAtMs: Long, val capKbps: Int?)

    /**
     * A playable link and what it is. [bestMp3] is the best MP3 offered: what a file downloaded before 1.6
     * holds, as those always took the best MP3. [encrawKey] is the AES-CTR decryption key for lossless streams.
     */
    data class ResolvedStream(
        val url: String,
        val quality: StreamQuality,
        val bestMp3: StreamQuality? = null,
        val encrawKey: String? = null
    )

    companion object {
        const val URL_TTL_MS = 45 * 60 * 1000L // 45 минут
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolvedUrls = ConcurrentHashMap<String, CachedTrackUrl>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()

    data class StreamQuality(val codec: String, val bitrateKbps: Int) {
        val isLossless: Boolean get() = codec.startsWith("flac", ignoreCase = true)
    }
    private val qualities = ConcurrentHashMap<String, StreamQuality>()

    fun qualityOf(trackId: String): StreamQuality? = qualities[trackId]

    /** Every variant the server offered for a track ("AAC 192 · MP3 192 · …"), for the player's quality details. */
    private val offered = ConcurrentHashMap<String, String>()
    fun offeredOf(trackId: String): String? = offered[trackId]

    /** Diagnostic failure reason for Lossless if fallback occurred. */
    private val lastLosslessError = ConcurrentHashMap<String, String>()

    /** True when the track's current link was chosen under the economy cap. */
    fun isCapped(trackId: String): Boolean = resolvedUrls[trackId]?.capKbps != null

    fun getResolvedUrl(trackId: String): String? {
        val cached = resolvedUrls[trackId] ?: return null
        if (System.currentTimeMillis() - cached.fetchedAtMs > URL_TTL_MS) {
            resolvedUrls.remove(trackId)
            return null
        }
        return cached.url
    }

    /** Economy on mobile data: the bitrate cap for streaming now, or null for the best quality. */
    private suspend fun playbackCapKbps(): Int? =
        if (networkMonitor.isMetered() && settingsRepository.economyOnMobileData.first())
            com.korenskoy.yandexmusic.domain.repository.SettingsRepository.ECONOMY_BITRATE_KBPS
        else null

    /**
     * The best stream for keeping offline, ignoring any copy already downloaded and the economy cap.
     * Null when the track has no playable stream or the network failed.
     */
    suspend fun resolveForDownload(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            resolveBest(trackId, capKbps = null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TrackPreloadManager", "Error resolving download for $trackId", e)
            null
        }
    }

    /** Tries the offered variants from the best down until one gives a link. */
    private suspend fun resolveBest(trackId: String, capKbps: Int?): ResolvedStream? {
        if (capKbps == null) {
            val losslessStream = tryResolveLossless(trackId)
            if (losslessStream != null) return losslessStream
        }

        val offeredList = api.getTrackDownloadInfo(trackId).result.orEmpty()
        val baseDesc = StreamVariants.describe(offeredList)
        val losslessErr = lastLosslessError[trackId]
        val desc = if (!losslessErr.isNullOrBlank()) {
            val userFriendly = when {
                losslessErr.contains("превью", ignoreCase = true) -> "нужен Яндекс Плюс (превью)"
                losslessErr.contains("not-allowed", ignoreCase = true) || losslessErr.contains("no-rights", ignoreCase = true) -> {
                    if (authRepository.getActiveAccount()?.hasPlus != true) {
                        "нужен Яндекс Плюс"
                    } else {
                        "нет FLAC для этого трека"
                    }
                }
                losslessErr.contains("bad-signature", ignoreCase = true) -> "ошибка подписи"
                losslessErr.contains("403") -> {
                    if (authRepository.getActiveAccount()?.hasPlus != true) {
                        "нужен Яндекс Плюс (403)"
                    } else {
                        "доступ ограничен (403)"
                    }
                }
                else -> losslessErr
            }
            "$baseDesc (Lossless: $userFriendly)"
        } else {
            baseDesc
        }
        offered[trackId] = desc
        Log.i("TrackPreloadManager", "Track $trackId offered: $desc")
        val bestMp3 = offeredList.filter { !it.preview && it.codec.equals("mp3", ignoreCase = true) }
            .maxByOrNull { it.bitrateInKbps }?.let { StreamQuality(it.codec, it.bitrateInKbps) }
        for (variant in StreamVariants.ranked(offeredList, capKbps)) {
            val url = resolveXmlToStreamUrl(variant.downloadInfoUrl, variant.codec) ?: continue
            return ResolvedStream(url, StreamQuality(variant.codec, variant.bitrateInKbps), bestMp3)
        }
        return null
    }

    private fun extractServerError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        if (body.contains("not-allowed", ignoreCase = true)) return "not-allowed"
        if (body.contains("bad-signature", ignoreCase = true)) return "bad-signature"
        if (body.contains("no-rights", ignoreCase = true)) return "no-rights"
        val messageRegex = """"message"\s*:\s*"([^"]+)"""".toRegex()
        val match = messageRegex.find(body)
        if (match != null) {
            val msg = match.groupValues[1]
            if (msg.isNotBlank()) return msg
        }
        val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
        val nameMatch = nameRegex.find(body)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1]
            if (name.isNotBlank()) return name
        }
        return null
    }

    private suspend fun tryResolveLossless(trackId: String): ResolvedStream? {
        val numericId = LosslessSigner.parseNumericTrackId(trackId) ?: run {
            lastLosslessError[trackId] = "некорректный ID"
            return null
        }
        val idString = numericId.toString()
        var finalError: String? = null

        for (strategy in LosslessSigner.STRATEGIES) {
            try {
                val sign = LosslessSigner.signFileInfo(
                    numericTrackId = numericId,
                    quality = "lossless",
                    codecs = strategy.codecs,
                    transport = strategy.transport,
                    key = strategy.key
                )
                val response = api.getFileInfo(
                    clientHeader = strategy.clientHeader,
                    userAgent = strategy.userAgent,
                    trackId = idString,
                    quality = "lossless",
                    codecs = strategy.codecs,
                    transports = strategy.transport,
                    ts = sign.ts,
                    sign = sign.sign
                )
                val downloadInfo = response.activeDownloadInfo
                if (downloadInfo != null && downloadInfo.streamUrls.isNotEmpty()) {
                    if (downloadInfo.isPreview) {
                        finalError = "требуется Яндекс Плюс (сервер выдал превью)"
                        Log.w("TrackPreloadManager", "get-file-info (${strategy.name}) returned preview for $trackId")
                        continue
                    }

                    val directUrl = downloadInfo.streamUrls.first()
                    val encrawKey = downloadInfo.key
                    val playableUrl = if (!encrawKey.isNullOrBlank() && strategy.transport == "encraw") {
                        "$directUrl#encraw_key=$encrawKey"
                    } else directUrl

                    val codec = downloadInfo.codec?.lowercase() ?: "mp3"
                    val defaultBitrate = if (codec.startsWith("flac")) 1411 else if (codec == "aac") 256 else 320
                    val quality = StreamQuality(codec, downloadInfo.bitrate ?: defaultBitrate)

                    val offeredList = try {
                        api.getTrackDownloadInfo(trackId).result.orEmpty()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val bestMp3 = offeredList.filter { !it.preview && it.codec.equals("mp3", ignoreCase = true) }
                        .maxByOrNull { it.bitrateInKbps }?.let { StreamQuality(it.codec, it.bitrateInKbps) }
                    val label = if (quality.isLossless) "FLAC Lossless" else "${quality.codec.uppercase()} ${quality.bitrateKbps}"
                    val desc = label + if (offeredList.isNotEmpty()) " · " + StreamVariants.describe(offeredList) else ""
                    offered[trackId] = desc
                    lastLosslessError.remove(trackId)
                    Log.i("TrackPreloadManager", "Track $trackId resolved via get-file-info (${strategy.name}): $desc")
                    return ResolvedStream(playableUrl, quality, bestMp3, encrawKey)
                } else {
                    val err = response.failureReason ?: "нет аудиопотока"
                    finalError = err
                    Log.w("TrackPreloadManager", "get-file-info (${strategy.name}) returned no stream for $trackId: $err")
                }
            } catch (e: Exception) {
                val httpErrorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                val parsed = extractServerError(httpErrorBody)
                finalError = parsed ?: e.message ?: e.javaClass.simpleName
                Log.w("TrackPreloadManager", "get-file-info (${strategy.name}) failed for $trackId: $finalError")
            }
        }

        lastLosslessError[trackId] = finalError ?: "не удалось получить FLAC"
        return null
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

    suspend fun getOrFetchUrl(trackId: String): String? = fetchOrAwait(trackId)

    private suspend fun fetchOrAwait(trackId: String): String? {
        if (trackId.startsWith("radio_")) return null
        if (downloadManager.isTrackDownloaded(trackId)) return null
        val cap = playbackCapKbps()
        // A link chosen under another cap (Wi-Fi ↔ mobile data) is chosen again.
        // If cap is null (best quality) but previously cached quality was not lossless, re-resolve.
        resolvedUrls[trackId]?.takeIf {
            it.capKbps == cap && (cap != null || qualities[trackId]?.isLossless == true)
        }?.let { getResolvedUrl(trackId)?.let { url -> return url } }

        val deferred = inFlightRequests.computeIfAbsent(trackId) {
            scope.async(Dispatchers.IO) {
                try {
                    val stream = resolveBest(trackId, cap) ?: return@async null
                    resolvedUrls[trackId] = CachedTrackUrl(stream.url, System.currentTimeMillis(), cap)
                    qualities[trackId] = stream.quality
                    stream.url
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
    
    private suspend fun resolveXmlToStreamUrl(infoUrl: String, codec: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(infoUrl).build()
            val xmlData = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val xmlResponse = response.body.string().ifEmpty { return@use null }
                DownloadInfoParser.parseXml(xmlResponse)
            }
            if (xmlData != null) {
                return@withContext Md5Helper.generateStreamUrl(
                    xmlData.host,
                    xmlData.path,
                    xmlData.ts,
                    xmlData.s,
                    codec
                )
            }
            null
        } catch (e: Exception) {
            Log.e("TrackPreloadManager", "Error parsing XML for url: $infoUrl", e)
            null
        }
    }
}
