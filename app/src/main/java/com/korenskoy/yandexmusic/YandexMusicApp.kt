package com.korenskoy.yandexmusic

import android.app.Application
import androidx.work.Configuration
import com.korenskoy.yandexmusic.widget.NowPlayingWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YandexMusicApp : Application(), Configuration.Provider {

    /**
     * WorkManager starts on demand from this configuration (its start-up initializer is removed in the
     * manifest), so it is ready whenever it is first used — whatever order the process starts in.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @Inject
    lateinit var widgetUpdater: NowPlayingWidgetUpdater

    override fun onCreate() {
        // Before anything else, so a crash during start-up is recorded too
        com.korenskoy.yandexmusic.data.crash.CrashLog.install(this)
        super.onCreate()
        widgetUpdater.start()
    }
}
