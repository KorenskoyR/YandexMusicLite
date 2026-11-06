package com.korenskoy.yandexmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.korenskoy.yandexmusic.data.player.SoundMode
import com.korenskoy.yandexmusic.data.player.deviceHasFlacDecoder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The real codec list, on whatever Android this runs: the question must get an answer, not a crash. */
@RunWith(AndroidJUnit4::class)
class SoundModeDeviceInstrumentedTest {

    @Test
    fun theDeviceAnswersWhetherItHasAFlacDecoder() = runBlocking {
        val decoder = deviceHasFlacDecoder()
        Log.i("SoundModeDevice", "FLAC decoder on this device: $decoder")

        assertEquals(!decoder, SoundMode(flowOf(null), {}, ::deviceHasFlacDecoder).isCompatible())
    }
}
