package com.korenskoy.yandexmusic

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The full-screen player on a real renderer: the timeline seeks, the time follows the position, the
 * "Далее" plate opens the queue sheet, a track can be removed from it, and Back closes the sheet before
 * the player. Screenshots land in /data/local/tmp for a look at the real rendering.
 */
@RunWith(AndroidJUnit4::class)
class FullPlayerInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val queue = listOf(track("1", "Первая песня", "Группа"), track("2", "Вторая песня", "Кино"), track("3", "Третья песня", "Сплин"))

    /** A real screen capture (the shell may write to /data/local/tmp, the app may not). */
    private fun save(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/$name.png")
            .use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
    }

    @Test
    fun timelineQueueAndBack() {
        val position = mutableLongStateOf(82_000L)
        var sought = -1L
        var removed: String? = null
        var collapsed = 0
        compose.setContent {
            YandexMusicTheme {
                FullScreenPlayer(
                    track = queue[0], isPlaying = false, isLiked = true, progress = { position.longValue }, queue = queue,
                    onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {},
                    onSeekTo = { sought = it }, onTrackSelect = {}, onRemoveFromQueue = { removed = it },
                    onCollapse = { collapsed++ }, onCloseAndClear = {}, onDislikeClick = {}, sourceLabel = "Моя волна"
                )
            }
        }

        // Elapsed and remaining time, and they follow the position
        compose.onNodeWithText("1:22").assertExists()
        compose.onNodeWithText("−2:13").assertExists()
        position.longValue = 125_000L
        compose.waitForIdle()
        compose.onNodeWithText("2:05").assertExists()
        compose.onNodeWithText("−1:30").assertExists()
        save("full_player_real")

        // The timeline is adjustable for accessibility services and seeks the player
        compose.onNodeWithContentDescription("Позиция в треке").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(107_500L, sought)

        // "Далее" shows the next track and opens the queue
        compose.onNodeWithText("ДАЛЕЕ").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Очередь").assertExists()
        compose.onNodeWithText("Моя волна · 3 трека").assertExists()
        save("player_queue_real")

        // Only the tracks after the current one can be removed
        val removeButtons = compose.onAllNodesWithContentDescription("Удалить из очереди").fetchSemanticsNodes()
        assertEquals(2, removeButtons.size)
        compose.onAllNodesWithContentDescription("Удалить из очереди")[0].performClick()
        assertEquals("2", removed)

        // Back closes the sheet first, the player stays open
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Очередь").assertDoesNotExist()
        assertEquals(0, collapsed)
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, collapsed)
    }
}
