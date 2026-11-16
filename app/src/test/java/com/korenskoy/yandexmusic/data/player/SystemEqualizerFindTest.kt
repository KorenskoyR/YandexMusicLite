package com.korenskoy.yandexmusic.data.player

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "The app does not see the system equalizer": a head unit's equalizer is an app of its own that does not answer
 * Android's standard request. It is found among the launcher's apps by its name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SystemEqualizerFindTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun namesOfEqualizersAreTold() {
        val yes = listOf(
            "EQ" to "com.syu.eq", "Эквалайзер" to "com.spd.audio", "Equalizer" to "com.android.musicfx",
            "Звуковые эффекты" to "com.miui.misound", "Dolby Atmos" to "com.dolby.daxappui", "DSP" to "com.tw.dsp",
            "Wavelet" to "com.pittvandewitt.wavelet", "Audio" to "com.fyt.eq"
        )
        for ((label, pkg) in yes) assertTrue("$label ($pkg)", SystemEqualizer.EqualizerNames.looksLikeOne(label, pkg))
        val no = listOf(
            "Яндекс Музыка" to "ru.yandex.music", "Request" to "com.example.request", "Sequence" to "com.example.seq",
            "Навигатор" to "ru.yandex.yandexnavi", "Настройки" to "com.android.settings", "Radio" to "com.syu.radio"
        )
        for ((label, pkg) in no) assertFalse("$label ($pkg)", SystemEqualizer.EqualizerNames.looksLikeOne(label, pkg))
    }

    @Test
    fun aClearNameWinsOverAGuessByPackage() {
        assertTrue(SystemEqualizer.EqualizerNames.strength("Эквалайзер", "x.y") > SystemEqualizer.EqualizerNames.strength("Audio", "com.fyt.eq"))
    }

    private fun installLauncherApp(pkg: String, label: String) {
        val pm = shadowOf(app.packageManager)
        val component = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        pm.getInternalMutablePackageInfo(pkg).applicationInfo!!.nonLocalizedLabel = label
        pm.getInternalMutablePackageInfo(pkg).activities?.forEach { it.nonLocalizedLabel = label }
    }

    @Test
    fun aHeadUnitsOwnEqualizerAppIsFoundWithoutTheStandardPanel() {
        installLauncherApp("com.android.navi", "Навигатор")
        installLauncherApp("com.syu.eq", "EQ")
        val found = SystemEqualizer(app, audioSessionId = 77).find()
        assertEquals(SystemEqualizer.Target.App("com.syu.eq", "EQ"), found)
    }

    @Test
    fun withNoEqualizerAnywhereNothingIsOffered() {
        installLauncherApp("com.android.navi", "Навигатор")
        assertNull(SystemEqualizer(app, audioSessionId = 77).find())
    }
}
