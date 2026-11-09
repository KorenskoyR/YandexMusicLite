package com.korenskoy.yandexmusic

import okio.Path.Companion.toOkioPath
import coil3.request.crossfade
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.ImageLoader
import android.app.Application
import androidx.work.Configuration
import com.korenskoy.yandexmusic.widget.NowPlayingWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YandexMusicApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    /**
     * WorkManager starts on demand from this configuration (its start-up initializer is removed in the
     * manifest), so it is ready whenever it is first used — whatever order the process starts in.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @Inject
    lateinit var widgetUpdater: NowPlayingWidgetUpdater

    @Inject
    lateinit var bluetoothAutoplay: com.korenskoy.yandexmusic.data.player.BluetoothAutoplay

    /**
     * Covers: a quarter of the app's memory for decoded images and 300 MB on disk, so covers seen once come
     * back without the network. The loader keeps its own HTTP client: the app's one adds the account token
     * to every request, and image hosts (radio logos come from third-party sites) must never get it.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("covers").toOkioPath())
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .crossfade(COVER_CROSSFADE_MS)
            .build()

    override fun onCreate() {
        // Before anything else, so a crash during start-up is recorded too
        com.korenskoy.yandexmusic.data.crash.CrashLog.install(this)
        super.onCreate()
        widgetUpdater.start()
        bluetoothAutoplay.start()
    }
}

/** Covers fade in over this long when they arrive (a cover from memory shows at once). */
private const val COVER_CROSSFADE_MS = 150
