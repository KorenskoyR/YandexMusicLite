package com.korenskoy.yandexmusic.widget

import android.app.Application
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NowPlayingWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val playing = WidgetNow(
        title = "Группа крови", artist = "Кино", isPlaying = true, isLiked = true, isRadio = false, canReact = true,
        progress = 0.4f, positionMs = 82_000, durationMs = 285_000, source = "МОЯ ВОЛНА", cover = null, glow = null
    )
    private val nothing = WidgetNow(null, null, false, false, false, false, null, 0, 0, null, null, null)

    @Test
    fun theLineShowsTheTrackAndTheControls() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(320.dp, 64.dp))
        provideComposable { LineWidget(playing) }
        onNode(hasText("Группа крови")).assertExists()
        onNode(hasText("Кино")).assertExists()
        onNode(hasContentDescription("Предыдущий")).assertExists()
        onNode(hasContentDescription("Воспроизведение или пауза")).assertExists()
        onNode(hasContentDescription("Следующий")).assertExists()
    }

    @Test
    fun theWideLineShowsTheHeartToo() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(430.dp, 64.dp))
        provideComposable { LineWidget(playing) }
        onNode(hasText("Группа крови")).assertExists()
        onNode(hasContentDescription("Нравится")).assertExists()
    }

    @Test
    fun withNothingPlayingTheWaveIsATapAway() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { LineWidget(nothing) }
        onNode(hasText("Моя волна")).assertExists()
        onNode(hasContentDescription("Слушать Мою волну")).assertExists()
    }

    @Test
    fun thePlayerShowsEverything() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(320.dp, 180.dp))
        provideComposable { PlayerWidgetContent(playing) }
        onNode(hasText("МОЯ ВОЛНА")).assertExists()
        onNode(hasText("Группа крови")).assertExists()
        onNode(hasText("1:22")).assertExists()
        onNode(hasText("−3:23")).assertExists()
        onNode(hasContentDescription("Не нравится")).assertExists()
        onNode(hasContentDescription("Нравится")).assertExists()
    }

    @Test
    fun thePlayerWithNothingPlayingOffersTheWaveAndThePages() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(320.dp, 180.dp))
        provideComposable { PlayerWidgetContent(nothing) }
        onNode(hasContentDescription("Слушать Мою волну")).assertExists()
        onNode(hasText("Мне нравится")).assertExists()
        onNode(hasText("Радио")).assertExists()
        onNode(hasText("В авто")).assertExists()
    }

    @Test
    fun theCoverShowsTheTrackAndTheControls() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(180.dp, 180.dp))
        provideComposable { CoverWidgetContent(playing) }
        onNode(hasText("Группа крови")).assertExists()
        onNode(hasContentDescription("Воспроизведение или пауза")).assertExists()
    }

    @Test
    fun aStationHasNoProgress() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(320.dp, 180.dp))
        provideComposable { PlayerWidgetContent(playing.copy(isRadio = true, progress = null, source = "РАДИО", artist = "В эфире")) }
        onNode(hasText("РАДИО")).assertExists()
        onNode(hasText("1:22")).assertDoesNotExist()
    }
}
