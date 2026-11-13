package com.korenskoy.yandexmusic.di

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import com.korenskoy.yandexmusic.data.player.LinkRenewal
import android.net.Uri
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.korenskoy.yandexmusic.data.download.CardAwareDataSourceFactory
import com.korenskoy.yandexmusic.data.download.CardStorage
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.player.EncrawDataSourceFactory
import com.korenskoy.yandexmusic.data.player.PlaybackBuffering
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
import com.korenskoy.yandexmusic.data.player.SystemEqualizer
import com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /**
     * A station on HLS (Like FM, Авторадио) offers 128, 64 and 32 kbit/s: always the best, never the thin 32.
     * A new one for each player: a selector belongs to one player.
     */
    internal fun bestVariantSelector(context: Context) = DefaultTrackSelector(
        context,
        DefaultTrackSelector.Parameters.Builder(context).setForceHighestSupportedBitrate(true).build()
    )

    private fun createConfiguredExoPlayer(context: Context, audioSessionId: Int, card: CardStorage, renewal: Renewal): ExoPlayer =
        createConfiguredExoPlayer(context, card, renewal).apply { if (audioSessionId > 0) setAudioSessionId(audioSessionId) }

    /** How a link that ran out mid-track is renewed, and who is told. */
    private class Renewal(val renew: (String) -> Uri?, val onRenewed: (String) -> Unit)

    private fun createConfiguredExoPlayer(context: Context, card: CardStorage, renewal: Renewal): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val audioSoftwareCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType, requiresSecureDecoder, requiresTunnelingDecoder
            )
            if (mimeType.startsWith("audio/")) {
                decoders.sortedByDescending {
                    it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")
                }
            } else {
                decoders
            }
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(audioSoftwareCodecSelector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val encrawDataSourceFactory = EncrawDataSourceFactory(context)
        // Encrypted tracks on the SD card are read through their own source; everything else as before
        val cardAware = CardAwareDataSourceFactory(encrawDataSourceFactory, card)
        // A link that ran out while the connection was broken is renewed, the download goes on from the same byte
        val mediaSourceFactory = DefaultMediaSourceFactory(LinkRenewal(cardAware, renewal.renew, renewal.onRenewed))
            // A broken download is retried while the music plays on from what is ahead
            .setLoadErrorHandlingPolicy(PlaybackBuffering.PatientLoadErrors())

        return try {
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(bestVariantSelector(context))
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(PlaybackBuffering.loadControl())
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
                .setHandleAudioBecomingNoisy(true)
                // The processor and the Wi-Fi stay awake while the music plays: a sleeping Wi-Fi with the screen
                // off fills the buffer in jerks, and the music runs dry
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        } catch (e: Exception) {
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(bestVariantSelector(context))
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(PlaybackBuffering.loadControl())
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
    }

    /**
     * A fresh link for a track of the service whose link ran out: the same way as for its start. Not for a station,
     * the user's own file or a FLAC stream (those keep the usual recovery).
     */
    private fun renewedLink(preloadManager: TrackPreloadManager, trackId: String): Uri? {
        if (trackId.startsWith("radio_") || trackId.startsWith("local_")) return null
        if (preloadManager.qualityOf(trackId)?.isLossless == true) return null
        return runBlocking {
            withTimeoutOrNull(RENEW_TIMEOUT_MS) {
                preloadManager.evictUrl(trackId)
                preloadManager.getOrFetchUrl(trackId)
            }
        }?.toUri()
    }

    private const val RENEW_TIMEOUT_MS = 15_000L

    @Provides
    @Singleton
    fun providePlayerRepository(
        @ApplicationContext context: Context,
        downloadManager: TrackDownloadManager,
        preloadManager: TrackPreloadManager,
        wakeLockHelper: PlaybackWakeLockHelper,
        equalizer: SystemEqualizer,
        card: CardStorage,
        journal: PlaybackJournal
    ): PlayerRepository {
        val renewal = Renewal(
            renew = { trackId -> renewedLink(preloadManager, trackId) },
            onRenewed = { journal.record(PlaybackJournal.Kind.LINK_RENEWED) }
        )
        val playerA = createConfiguredExoPlayer(context, equalizer.audioSessionId, card, renewal)
        val playerB = createConfiguredExoPlayer(context, equalizer.audioSessionId, card, renewal)
        equalizer.announce()
        return PlayerRepositoryImpl(
            playerA, playerB, downloadManager, preloadManager, wakeLockHelper, journal,
            readArtwork = { uri -> context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() } }
        )
    }

}


