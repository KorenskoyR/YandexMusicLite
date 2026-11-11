package com.korenskoy.yandexmusic.di

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

    private fun createConfiguredExoPlayer(context: Context, audioSessionId: Int, card: CardStorage): ExoPlayer =
        createConfiguredExoPlayer(context, card).apply { if (audioSessionId > 0) setAudioSessionId(audioSessionId) }

    private fun createConfiguredExoPlayer(context: Context, card: CardStorage): ExoPlayer {
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
        val mediaSourceFactory = DefaultMediaSourceFactory(cardAware)
            // A broken download is retried while the music plays on from what is ahead
            .setLoadErrorHandlingPolicy(PlaybackBuffering.PatientLoadErrors())

        return try {
            ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(PlaybackBuffering.loadControl())
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        } catch (e: Exception) {
            ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(PlaybackBuffering.loadControl())
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
    }

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
        val playerA = createConfiguredExoPlayer(context, equalizer.audioSessionId, card)
        val playerB = createConfiguredExoPlayer(context, equalizer.audioSessionId, card)
        equalizer.announce()
        return PlayerRepositoryImpl(
            playerA, playerB, downloadManager, preloadManager, wakeLockHelper, journal,
            readArtwork = { uri -> context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() } }
        )
    }

}


