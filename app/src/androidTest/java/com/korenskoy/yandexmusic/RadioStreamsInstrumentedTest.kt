package com.korenskoy.yandexmusic

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.di.PlayerModule
import com.korenskoy.yandexmusic.domain.model.RadioStation
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every station of the list plays on this Android, silently: its stream opens through the app's own network rules
 * (plain http only where allowed, the bundled Let's Encrypt roots), decodes and runs on for a few seconds. A station
 * on HLS plays its best variant, not the thin 32 kbit/s one. 1.14.6 found two stations answering 404 and two more
 * refused for an expired certificate: this is the check that would have shown it.
 */
@RunWith(AndroidJUnit4::class)
class RadioStreamsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Null when [station] played; otherwise what went wrong. */
    private fun problemOf(station: RadioStation): String? {
        val player = onMain {
            ExoPlayer.Builder(context)
                // The app's own choice of variant
                .setTrackSelector(PlayerModule.bestVariantSelector(context))
                .build()
                .apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(station.streamUrl))
                    prepare()
                    play()
                }
        }
        try {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < TIMEOUT_MS) {
                val (error, position, state) = onMain { Triple(player.playerError, player.currentPosition, player.playbackState) }
                if (error != null) return "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
                if (state == Player.STATE_READY && position >= PLAYED_MS) {
                    // An HLS station: of its variants, the best one plays
                    val variants = onMain {
                        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).bitrate to group.isTrackSelected(it) } }
                    }
                    if (variants.size > 1) {
                        val best = variants.maxOf { it.first }
                        val playing = variants.firstOrNull { it.second }?.first
                        if (playing != best) return "HLS plays $playing bit/s of $best"
                    }
                    return null
                }
                Thread.sleep(100)
            }
            return "did not play ${PLAYED_MS / 1000} s within ${TIMEOUT_MS / 1000} s"
        } finally {
            onMain { player.release() }
        }
    }

    @Test
    fun everyStationPlays() {
        val stations = RadioRepository(context).getBuiltinStations()
        // A slow server gets one more try before it counts
        val problems = stations.mapNotNull { station ->
            (problemOf(station)?.let { problemOf(station) })?.let { "${station.name} (${station.streamUrl}) — $it" }
        }
        assertTrue("on Android ${android.os.Build.VERSION.RELEASE}:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private companion object {
        const val PLAYED_MS = 3_000L
        const val TIMEOUT_MS = 20_000L
    }
}
