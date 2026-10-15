package com.korenskoy.yandexmusic.di

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

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerRepository(
        @ApplicationContext context: Context,
        downloadManager: com.korenskoy.yandexmusic.data.network.TrackDownloadManager,
        preloadManager: com.korenskoy.yandexmusic.data.network.TrackPreloadManager,
        wakeLockHelper: com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
    ): com.korenskoy.yandexmusic.domain.repository.PlayerRepository {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Prioritize standard Google software decoders (c2.android.* / OMX.google.*)
        // for audio to prevent speed acceleration / chipmunk effect caused by buggy
        // vendor hardware audio decoders (common on Android car head units).
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

        // ExoPlayer with Software Audio Decoder priority, AudioAttributes, and safe WakeLock fallback.
        val player = try {
            ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        } catch (e: Exception) {
            ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }

        return com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl(context, player, downloadManager, preloadManager, wakeLockHelper)
    }

}


