package com.korenskoy.yandexmusic

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.widget.CoverWidgetReceiver
import com.korenskoy.yandexmusic.widget.NowPlayingWidgetReceiver
import com.korenskoy.yandexmusic.widget.PlayerWidgetReceiver
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * For the check from the computer only (-e pin line|player|cover): asks the launcher to put a widget on the home screen,
 * as a person does from the list of widgets; the computer then confirms the launcher's question and looks.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPinInstrumentedTest {

    @Test
    fun pinFromTheComputer() {
        val which = InstrumentationRegistry.getArguments().getString("pin")
        assumeTrue(which != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = when (which) {
            "player" -> PlayerWidgetReceiver::class.java
            "cover" -> CoverWidgetReceiver::class.java
            else -> NowPlayingWidgetReceiver::class.java
        }
        val manager = context.getSystemService(AppWidgetManager::class.java)
        assumeTrue(manager.isRequestPinAppWidgetSupported)
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    }
}
