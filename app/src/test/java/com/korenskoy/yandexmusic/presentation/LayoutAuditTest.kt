package com.korenskoy.yandexmusic.presentation

import androidx.compose.foundation.layout.Column
import com.korenskoy.yandexmusic.data.download.StorageStatus
import com.korenskoy.yandexmusic.data.download.Drive
import com.korenskoy.yandexmusic.data.auth.DeviceSignIn
import com.korenskoy.yandexmusic.presentation.auth.CodeFallbackStrip
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInScreen
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInState
import com.korenskoy.yandexmusic.presentation.main.QuickNote
import com.korenskoy.yandexmusic.presentation.main.QuickMark
import com.korenskoy.yandexmusic.presentation.main.QuickAdd
import com.korenskoy.yandexmusic.presentation.car.CarPlaylists
import com.korenskoy.yandexmusic.domain.model.CarLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.waveContentBottom
import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.car.CarRadio
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
import com.korenskoy.yandexmusic.presentation.main.components.WaveTuneSheet
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/**
 * The layout of every main screen on the screens the app meets: phones, standing and flat head units, the
 * interface scale at 80% and 120%. On each, every button is wholly on the screen (or reachable by scrolling),
 * not covered by another, at least 40 dp to hit, and no text is cut.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class LayoutAuditTest(private val screen: String, private val qualifiers: String, private val scale: Float) {

    /** The screen's size and density, set before the activity of the compose rule starts. */
    @get:Rule(order = 0)
    val size = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                RuntimeEnvironment.setQualifiers(qualifiers)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private fun track(id: String, title: String, artist: String = "Исполнитель") =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000, lyricsAvailable = true)

    private val tracks = listOf(
        track("1", "Первая песня", "Группа"),
        track("2", "Очень длинное название трека, которое не помещается в одну строку", "Исполнитель с длинным именем"),
        track("3", "Третья песня"),
        track("4", "Четвёртая", "Кино"),
        track("5", "Пятая песня", "Сплин"),
        track("6", "Шестая", "Земфира")
    )
    private val playlists = listOf(
        UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 42, revision = 7),
        UserPlaylistDto(kind = JsonPrimitive(1004), title = "Спокойное", trackCount = 1, revision = 2)
    )
    private val words = (1..6).joinToString("\n\n") { verse -> (1..4).joinToString("\n") { "Строка $it куплета $verse, довольно длинная" } }
    private val lyrics = LyricsUi(known = { LyricsAnswer.Text(words) }, load = { LyricsAnswer.Text(words) })

    /** Renders [content] as the app does: on the living background, at the interface scale. */
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density * scale, system.fontScale)) {
                YandexMusicTheme {
                    CompositionLocalProvider(
                        LocalArtworkTint provides tint,
                        LocalLyrics provides lyrics,
                        LocalCarClock provides { FIXED_TIME },
                        LocalLinkState provides LinkState.GOOD
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HiFiBackdrop(tint = tint)
                            content()
                        }
                    }
                }
            }
        }
        repeat(25) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    // --- The screens ---

    private fun wave(playing: Boolean) = show {
        HomeScreen(
            waveTrack = tracks[0], isPlaying = playing, isLoading = false, isLiked = true, progress = { 70_000L },
            shelf = waveShelfOf(true, tracks.drop(1), emptyList()), shimmer = false,
            onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = if (playing) null else ({}), onShelfClick = {}
        )
    }

    private fun player(lyricsOpen: Boolean) = show {
        FullScreenPlayer(
            track = tracks[1], isPlaying = true, isLiked = false, progress = { 61_000L }, queue = tracks,
            onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
            onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
            sourceLabel = "Моя волна", onTrackWave = {}, offline = com.korenskoy.yandexmusic.data.download.TrackOfflineState.NONE,
            initialLyricsOpen = lyricsOpen, coverOnRight = true
        )
    }

    private fun car(
        source: PlaylistSource = PlaylistSource.VIBE,
        tune: Boolean = false,
        queue: Boolean = false,
        lyricsOpen: Boolean = false,
        shuffled: Boolean = false,
        onRadio: Boolean = false,
        stationsOpen: Boolean = false,
        playlistsOpen: Boolean = false,
        layout: CarLayout = CarLayout.AUTO
    ) = show {
        val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
        val radio = CarRadio(stations, setOf(stations[0].id, stations[3].id, stations[9].id, stations[14].id), onAir = stations[0].id, onPlay = {}, onToggleFavourite = {})
        val station = TrackDetailsDto(
            id = JsonPrimitive("radio_${stations[0].id}"), title = stations[0].name,
            artists = listOf(ArtistDto(name = "Прямой эфир • ${stations[0].frequency}")), durationMs = 0, isRadio = true
        )
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        CarScreen(
            currentTrack = if (onRadio) station else tracks[1], isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
            onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = landscape, progress = { 82_000L }, durationMs = 215_000,
            upcoming = tracks.drop(2), source = source, initialTuneOpen = tune, initialQueueOpen = queue, initialLyricsOpen = lyricsOpen,
            shuffled = shuffled, onOpenSettings = {}, onVoiceSearch = {}, waveTuning = WaveTuning(),
            radio = radio, initialStationsOpen = stationsOpen, isLiked = onRadio,
            playlists = carPlaylists(tracks[1].idString), initialPlaylistsOpen = playlistsOpen, layout = layout
        )
    }

    /** Five playlists, each row in another state, and the note at the bottom: the fullest the panel gets. */
    private fun carPlaylists(trackId: String): CarPlaylists {
        val names = listOf("Рок", "Русское и очень длинное название для проверки", "Спокойное", "Для пробежки", "В дорогу")
        val own = names.mapIndexed { i, name -> UserPlaylistDto(kind = JsonPrimitive(1003 + i), title = name, trackCount = 10 * i + 1, revision = 1) }
        val marks = mapOf("1003" to QuickMark.ADDED, "1004" to QuickMark.ALREADY, "1005" to QuickMark.WAITING, "1006" to QuickMark.ADDING)
        return CarPlaylists(own, QuickAdd(trackId, marks, QuickNote("Добавится в «Спокойное», когда появится связь", 0)), onOpen = {}, onAdd = { _, _ -> }, onCreate = { _, _ -> })
    }

    private fun waiting() = PhoneSignInState.Waiting(
        DeviceSignIn.Code("d", "LH2EL4HK", "https://ya.ru/device", 5, 300), expiresAt = android.os.SystemClock.elapsedRealtime() + 300_000L
    )

    private val drives = listOf(
        Drive("/storage/1A2B-3C4D/Android/data/app/files", "USB-накопитель", usb = true, freeBytes = 28_400_000_000L),
        Drive("/storage/5F1E-22A0/Android/data/app/files", "USB-накопитель (2)", usb = true, freeBytes = 7_100_000_000L)
    )

    @Test
    fun audit() {
        when (screen) {
            "wave_paused" -> wave(playing = false)
            "wave_playing" -> wave(playing = true)
            "wave_tune" -> show { WaveTuneSheet(WaveTuning(), onChange = {}, onDismiss = {}) }
            // The playing wave with the mini-player shown over it ("Всегда"), laid out as MainScreen does standing
            "wave_mini" -> show {
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        waveTrack = tracks[0], isPlaying = true, isLoading = false, isLiked = true, progress = { 70_000L },
                        shelf = waveShelfOf(true, tracks.drop(1), emptyList()), shimmer = false,
                        onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = null, onShelfClick = {},
                        contentBottom = waveContentBottom(waveActive = true, isLandscape = landscape, miniShown = true,
                            contentBottom = if (landscape) DockClearance else 168.dp)
                    )
                    if (!landscape) {
                        androidx.compose.foundation.layout.Column(
                            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth(),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            MiniPlayer(track = tracks[1], isPlaying = true, progress = { 61_000L }, onPlayPauseClick = {}, onNextClick = {}, onClick = {}, onDismiss = {}, onPrevClick = {})
                            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                            FloatingGlassBottomBar(currentRoute = "home", onNavigate = {})
                        }
                    }
                }
            }
            "player" -> player(lyricsOpen = false)
            "player_lyrics" -> player(lyricsOpen = true)
            "track_menu" -> show {
                TrackContextMenu(
                    track = tracks[1], inPlaylist = playlists[0], playlists = playlists, isLiked = true, startWithPlaylists = false,
                    onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {}, onCreatePlaylist = {}, onRemoveFromPlaylist = {},
                    onOpenArtist = {}, onOpenAlbum = {}, onTrackWave = {}, offline = com.korenskoy.yandexmusic.data.download.TrackOfflineState.NONE
                )
            }
            "mini_player" -> show {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                    MiniPlayer(
                        track = tracks[1], isPlaying = true, progress = { 61_000L }, onPlayPauseClick = {}, onNextClick = {},
                        onClick = {}, onDismiss = {}, onPrevClick = {}
                    )
                }
            }
            "settings_interface" -> show {
                SettingsSheet(state = SettingsUiState(), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.INTERFACE)
            }
            "settings_playback" -> show {
                SettingsSheet(state = SettingsUiState(crossfadeSeconds = 3, reserveMb = 100), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
            }
            "library" -> show {
                LibraryScreen(
                    tracks = tracks, isLoading = false, currentTrackId = "1", isPlaying = true, likedTrackIds = setOf("1", "2"), downloadedTrackIds = setOf("2"),
                    downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                    onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {}, playlists = playlists
                )
            }
            "search" -> show {
                SearchScreen(
                    query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, trendTracks = tracks,
                    isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                    onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино")
                )
            }
            "radio" -> {
                val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
                show {
                    RadioScreen(
                        stations = stations, allStations = stations, selectedCategory = RadioCategory.ALL,
                        onCategorySelect = {}, currentStationId = stations.first().id, isPlaying = true,
                        favoriteStationIds = setOf(stations[1].id), onStationClick = {}, onToggleFavorite = {}, onPlayPause = {}
                    )
                }
            }
            "car" -> car()
            "car_tune" -> car(tune = true)
            "car_queue" -> car(queue = true)
            "car_lyrics" -> car(lyricsOpen = true)
            "car_liked" -> car(source = PlaylistSource.LIBRARY, shuffled = true)
            "car_radio" -> car(source = PlaylistSource.RADIO, onRadio = true)
            "car_stations" -> car(source = PlaylistSource.RADIO, onRadio = true, stationsOpen = true)
            "car_playlists" -> car(playlistsOpen = true)
            "car_three" -> car(layout = CarLayout.THREE)
            "auth_strip" -> show {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth())
                    CodeFallbackStrip(reset = true, onSignInByCode = {})
                }
            }
            "auth_code" -> show { PhoneSignInScreen(waiting(), onNewCode = {}, onBackToPage = {}) }
            "settings_drives" -> show {
                val storage = StorageStatus(
                    cardAvailable = true, onCard = true, cardFreeBytes = 28_400_000_000L, pickerAvailable = false,
                    drives = drives, cardPath = drives[0].path, cardIsUsb = true
                )
                SettingsSheet(state = SettingsUiState(storage = storage), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
            }
            "settings_about" -> show {
                SettingsSheet(state = SettingsUiState(), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.ABOUT)
            }
            "settings_support" -> show {
                SettingsSheet(state = SettingsUiState(), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.SUPPORT)
            }
            "settings_no_drive" -> show {
                SettingsSheet(state = SettingsUiState(storage = StorageStatus(pickerAvailable = false)), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
            }
            else -> error(screen)
        }
        keepPictures()
        val problems = LayoutAudit.of(compose.onAllNodes(SemanticsMatcher("all") { true }).fetchSemanticsNodes(), windowOf(), scale)
        if (problems.isNotEmpty()) fail("$screen at $qualifiers ×$scale:\n" + problems.joinToString("\n") { "  • $it" })
    }

    /** With build/audit/.on present, the screen (dialogs too) is saved there to look at (not a reference). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun keepPictures() {
        val dir = java.io.File("build/audit")
        if (!java.io.File(dir, ".on").exists()) return
        val name = "${screen}_${qualifiers.substringBefore("-port").substringBefore("-land")}_x$scale"
        com.github.takahirom.roborazzi.captureScreenRoboImage(
            "build/audit/$name.png",
            com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record)
        )
    }

    private fun windowOf(): Rect {
        val c = RuntimeEnvironment.getApplication().resources.configuration
        val d = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        return Rect(0f, 0f, c.screenWidthDp * d, c.screenHeightDp * d)
    }

    companion object {
        private const val FIXED_TIME = 1_792_567_800_000L

        private val SCREENS = listOf(
            "wave_paused", "wave_playing", "wave_mini", "wave_tune", "player", "player_lyrics", "track_menu", "mini_player",
            "settings_interface", "settings_playback", "library", "search", "radio",
            "car", "car_tune", "car_queue", "car_lyrics", "car_liked", "car_radio", "car_stations",
            "car_playlists", "car_three", "auth_strip", "auth_code", "settings_drives", "settings_no_drive",
            "settings_about", "settings_support"
        )

        /** Phones, standing units (the Geely's 960×1280), flat units (the Monjaro's 1280×480, 1920×720), old 800×480. */
        private val SIZES = listOf(
            "w360dp-h640dp-port-xhdpi" to 1f,
            "w412dp-h915dp-port-xxhdpi" to 1f,
            "w915dp-h412dp-land-xxhdpi" to 1f,
            "w600dp-h1024dp-port-hdpi" to 1f,
            "w960dp-h1280dp-port-mdpi" to 1f,
            "w800dp-h480dp-land-mdpi" to 1f,
            "w1024dp-h600dp-land-mdpi" to 1f,
            "w1280dp-h480dp-land-mdpi" to 1f,
            "w1280dp-h720dp-land-mdpi" to 1f,
            "w1920dp-h720dp-land-mdpi" to 1f,
            "w1920dp-h1080dp-land-mdpi" to 1f,
            "w1280dp-h480dp-land-mdpi" to 1.2f,
            "w1920dp-h720dp-land-mdpi" to 0.8f,
            "w412dp-h915dp-port-xxhdpi" to 1.2f,
            "w1024dp-h600dp-land-mdpi" to 0.8f
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} x{2}")
        fun cases(): List<Array<Any>> = SCREENS.flatMap { s -> SIZES.map { (q, k) -> arrayOf<Any>(s, q, k) } }
    }
}

/** What the audit looks for in the semantics of a screen. Pure over the nodes; the messages name the culprits. */
internal object LayoutAudit {
    private const val MIN_TARGET_DP = 40f
    private const val TOLERANCE_PX = 1.5f

    /** [scale]: the interface scale the user chose; a target is 40 dp of the design, so 32 dp at 80%. */
    fun of(nodes: List<SemanticsNode>, window: Rect, scale: Float = 1f): List<String> {
        val density = nodes.firstOrNull()?.layoutInfo?.density?.density ?: 1f
        val problems = mutableListOf<String>()
        val clickable = nodes.filter { it.config.getOrNull(SemanticsActions.OnClick) != null && it.config.getOrNull(SemanticsProperties.Disabled) == null }
        // A scrim (a click-catcher over the whole window) puts everything before it underneath: only what is above counts
        val scrimIndex = clickable.indexOfLast { area(it.boundsInRoot) >= 0.9f * area(window) }
        val top = if (scrimIndex >= 0) clickable.drop(scrimIndex + 1) else clickable
        for (node in top) {
            val name = nameOf(node)
            val whole = Rect(node.positionInRoot, node.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) })
            if (!scrolls(node)) {
                if (!contains(window, whole)) problems += "$name sticks out of the screen: ${rectDp(whole, density)}"
                else if (area(node.boundsInRoot) < 0.97f * area(whole)) problems += "$name is cut by its container: ${rectDp(node.boundsInRoot, density)} of ${rectDp(whole, density)}"
            }
            val grow = node.touchBoundsInRoot.let { max(0f, it.width - node.boundsInRoot.width) to max(0f, it.height - node.boundsInRoot.height) }
            val w = (whole.width + grow.first) / density
            val h = (whole.height + grow.second) / density
            if (min(w, h) < MIN_TARGET_DP * scale - 0.5f) problems += "$name is only ${"%.0f".format(w)}×${"%.0f".format(h)} dp to hit"
        }
        // Two buttons over each other: neither inside the other in the tree, their visible parts meet
        for (i in top.indices) for (j in i + 1 until top.size) {
            val a = top[i]
            val b = top[j]
            if (related(a, b)) continue
            val meet = a.boundsInRoot.intersect(b.boundsInRoot)
            if (meet.width > 2 * density && meet.height > 2 * density) {
                problems += "${nameOf(a)} and ${nameOf(b)} overlap by ${"%.0f".format(meet.width / density)}×${"%.0f".format(meet.height / density)} dp"
            }
        }
        // Cut text: a line taller or wider than the place it got (an ellipsis is not a cut)
        for (node in nodes) {
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: continue
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            val layout = results.firstOrNull() ?: continue
            // A text too long for its place ends in an ellipsis on purpose; cut off without one, it is the problem
            val over = layout.didOverflowHeight
            if (over && layout.lineCount > 0 && !layout.isLineEllipsized(layout.lineCount - 1) && node.boundsInRoot.height > 0f) {
                problems += "text «${layout.layoutInput.text.text.take(40)}» is cut off"
            }
            // A button says what it does in full: "Те…" is no label (a track's long title may end in "…")
            // (a card with a title and an artist is content, not a label: only a button with one line of words)
            val button = node.config.getOrNull(SemanticsProperties.Role) == Role.Button && node.config.getOrNull(SemanticsActions.OnClick) != null &&
                node.config.getOrNull(SemanticsProperties.Text)?.size == 1
            if (button && layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) {
                problems += "button label «${layout.layoutInput.text.text.take(40)}» is cut to an ellipsis"
            }
        }
        return problems.distinct()
    }

    private fun scrolls(node: SemanticsNode): Boolean {
        var p = node.parent
        while (p != null) {
            if (p.config.getOrNull(SemanticsActions.ScrollBy) != null) return true
            p = p.parent
        }
        return false
    }

    private fun related(a: SemanticsNode, b: SemanticsNode): Boolean = ancestor(a, b) || ancestor(b, a)

    private fun ancestor(a: SemanticsNode, of: SemanticsNode): Boolean {
        var p = of.parent
        while (p != null) {
            if (p.id == a.id) return true
            p = p.parent
        }
        return false
    }

    private fun contains(outer: Rect, inner: Rect) =
        inner.left >= outer.left - TOLERANCE_PX && inner.top >= outer.top - TOLERANCE_PX &&
            inner.right <= outer.right + TOLERANCE_PX && inner.bottom <= outer.bottom + TOLERANCE_PX

    private fun area(r: Rect) = max(0f, r.width) * max(0f, r.height)

    private fun rectDp(r: Rect, d: Float) = "[${(r.left / d).toInt()},${(r.top / d).toInt()} ${(r.width / d).toInt()}×${(r.height / d).toInt()} dp]"

    private fun nameOf(node: SemanticsNode): String {
        val c = node.config
        val label = c.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: c.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: c.getOrNull(SemanticsActions.OnClick)?.label
            ?: c.getOrNull(SemanticsProperties.TestTag)
            ?: "#${node.id}"
        return "«${label.take(40)}»"
    }
}
