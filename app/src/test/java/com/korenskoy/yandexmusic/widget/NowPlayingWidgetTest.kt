package com.korenskoy.yandexmusic.widget

import android.app.Application
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NowPlayingWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun showsTheTrackAndTheControls() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { NowPlayingContent("Группа крови", "Кино", isPlaying = true, cover = null) }

        onNode(hasText("Группа крови")).assertExists()
        onNode(hasText("Кино")).assertExists()
        onNode(hasContentDescription("Предыдущий")).assertExists()
        onNode(hasContentDescription("Воспроизведение или пауза")).assertExists()
        onNode(hasContentDescription("Следующий")).assertExists()
    }

    @Test
    fun nothingPlayingInvitesToOpenTheApp() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { NowPlayingContent(null, null, isPlaying = false, cover = null) }

        onNode(hasText("Ничего не играет")).assertExists()
        onNode(hasText("Нажмите, чтобы открыть приложение")).assertExists()
    }
}
