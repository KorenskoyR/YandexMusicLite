package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.domain.model.AppThemeMode
import com.korenskoy.yandexmusic.domain.model.RepeatMode
import com.korenskoy.yandexmusic.domain.model.ThemeScheduleType
import com.korenskoy.yandexmusic.presentation.main.components.TamperBannerOverlay
import com.korenskoy.yandexmusic.data.updater.InstallState
import com.korenskoy.yandexmusic.data.updater.UpdateCheckResult
import com.korenskoy.yandexmusic.presentation.update.UpdateDialog
import com.korenskoy.yandexmusic.presentation.account.NoPlusScreen
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
@Config(sdk = [28, 30, 33, 35, 36], application = Application::class)
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
    private fun show(darkTheme: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density * scale, system.fontScale)) {
                YandexMusicTheme(darkTheme = darkTheme) {
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

    private fun player(
        lyricsOpen: Boolean,
        wheelOnLeft: Boolean = true,
        darkTheme: Boolean = true,
        repeatAndShuffle: Boolean = false
    ) = show(darkTheme = darkTheme) {
        FullScreenPlayer(
            track = tracks[1], isPlaying = true, isLiked = false, progress = { 61_000L }, queue = tracks,
            onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
            onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
            sourceLabel = if (repeatAndShuffle) "Мне нравится" else "Моя волна",
            onTrackWave = {},
            offline = com.korenskoy.yandexmusic.data.download.TrackOfflineState.NONE,
            initialLyricsOpen = lyricsOpen,
            coverOnRight = wheelOnLeft,
            repeatMode = if (repeatAndShuffle) RepeatMode.ONE else RepeatMode.OFF,
            shuffled = repeatAndShuffle,
            showRepeatAndShuffle = repeatAndShuffle
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
        layout: CarLayout = CarLayout.AUTO,
        wheelOnLeft: Boolean = true,
        underCover: com.korenskoy.yandexmusic.domain.model.CarUnderCover = com.korenskoy.yandexmusic.domain.model.CarUnderCover.QUEUE,
        darkTheme: Boolean = true,
        repeatMode: RepeatMode = RepeatMode.OFF
    ) = show(darkTheme = darkTheme) {
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
            playlists = carPlaylists(tracks[1].idString), initialPlaylistsOpen = playlistsOpen, layout = layout,
            steeringWheelOnLeft = wheelOnLeft, underCover = underCover, repeatMode = repeatMode
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
            // The wheel on the right: every car layout and panel mirrored, the player's cover on the other side
            "car_right" -> car(wheelOnLeft = false)
            "car_tune_right" -> car(tune = true, wheelOnLeft = false)
            "car_queue_right" -> car(queue = true, wheelOnLeft = false)
            "car_lyrics_right" -> car(lyricsOpen = true, wheelOnLeft = false)
            "car_liked_right" -> car(source = PlaylistSource.LIBRARY, shuffled = true, wheelOnLeft = false)
            "car_radio_right" -> car(source = PlaylistSource.RADIO, onRadio = true, wheelOnLeft = false)
            "car_stations_right" -> car(source = PlaylistSource.RADIO, onRadio = true, stationsOpen = true, wheelOnLeft = false)
            "car_playlists_right" -> car(playlistsOpen = true, wheelOnLeft = false)
            "car_three_right" -> car(layout = CarLayout.THREE, wheelOnLeft = false)
            "car_next" -> car(underCover = com.korenskoy.yandexmusic.domain.model.CarUnderCover.NEXT)
            "car_next_right" -> car(wheelOnLeft = false, underCover = com.korenskoy.yandexmusic.domain.model.CarUnderCover.NEXT)
            "account_management" -> show {
                val accs = listOf(
                    com.korenskoy.yandexmusic.domain.model.UserAccount("1", "ivan.petrov", "Иван Петров", "tok1", hasPlus = true),
                    com.korenskoy.yandexmusic.domain.model.UserAccount("2", "guest", "Гостевой", "tok2", hasPlus = false)
                )
                com.korenskoy.yandexmusic.presentation.account.AccountManagementDialog(
                    accounts = accs, activeAccount = accs[0], defaultAccountUid = accs[0].uid,
                    onSwitchAccount = {}, onRemoveAccount = {}, onAddNewAccount = {},
                    onLogoutCurrent = {}, onLogoutAll = {}, onDismiss = {}
                )
            }
            "startup_account_picker" -> show {
                val accs = listOf(
                    com.korenskoy.yandexmusic.domain.model.UserAccount("1", "ivan.petrov", "Иван Петров", "tok1", hasPlus = true),
                    com.korenskoy.yandexmusic.domain.model.UserAccount("2", "guest", "Гостевой", "tok2", hasPlus = false)
                )
                com.korenskoy.yandexmusic.presentation.account.StartupAccountPickerDialog(
                    accounts = accs, activeAccount = accs[0], onSelectAccount = { _, _ -> }, onAddNewAccount = {}
                )
            }
            "quality_details" -> show {
                com.korenskoy.yandexmusic.presentation.player.QualityDetailsDialog(
                    details = com.korenskoy.yandexmusic.data.player.PlaybackController.QualityDetails(
                        playing = "FLAC · 44.1 кГц · 16 бит",
                        offered = "flac-mp4, aac-mp4, mp3",
                        hasPlus = true
                    ),
                    onDismiss = {}
                )
            }
            "crash_log" -> show {
                val reports = listOf(
                    com.korenskoy.yandexmusic.data.crash.CrashLog.Report(
                        file = java.io.File("crash_1.txt"),
                        time = java.util.Date(FIXED_TIME),
                        summary = "IllegalStateException: Player released unexpectedly during reconnect"
                    )
                )
                com.korenskoy.yandexmusic.presentation.crash.CrashLogDialog(
                    reports = reports, onSend = {}, onClear = {}, onDismiss = {}
                )
            }
            "playlist_name_dialog" -> show {
                com.korenskoy.yandexmusic.presentation.main.components.PlaylistNameDialog(
                    title = "Новый плейлист",
                    confirmLabel = "Создать",
                    initialName = "В дорогу 2026",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
            "voice_listening" -> show {
                com.korenskoy.yandexmusic.presentation.voice.VoiceListeningWindow(
                    state = com.korenskoy.yandexmusic.presentation.voice.Listening.Missed,
                    onRetry = {},
                    onOpenSettings = {},
                    onClose = {}
                )
            }
            "queue_sheet" -> show {
                com.korenskoy.yandexmusic.presentation.player.QueueSheet(
                    visible = true,
                    split = com.korenskoy.yandexmusic.presentation.player.QueueSplit(tracks[0], tracks.drop(1)),
                    isPlaying = true,
                    sourceLabel = "Моя волна",
                    isFetchingMore = false,
                    fullHeight = false,
                    onDismiss = {},
                    onTrackSelect = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    onLoadMore = {}
                )
            }
            "playlist_screen" -> show {
                com.korenskoy.yandexmusic.presentation.main.PlaylistScreen(
                    opened = com.korenskoy.yandexmusic.presentation.main.OpenedPlaylist(
                        playlist = playlists[0], tracks = tracks, isLoading = false
                    ),
                    currentTrackId = "1",
                    isPlaying = true,
                    likedTrackIds = setOf("1"),
                    downloadedTrackIds = emptySet(),
                    contentBottom = 84.dp,
                    onBack = {},
                    onPlay = {},
                    onTrackClick = {},
                    onToggleLike = {},
                    onRetry = {},
                    onRename = {},
                    onDelete = {}
                )
            }
            "catalog_album" -> show {
                val album = com.korenskoy.yandexmusic.data.dto.AlbumDto(
                    id = JsonPrimitive(100),
                    title = "Нечего терять",
                    year = 1991,
                    trackCount = tracks.size,
                    artists = listOf(ArtistDto(id = JsonPrimitive(10), name = "Янка Дягилева")),
                    volumes = listOf(tracks)
                )
                com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen(
                    state = com.korenskoy.yandexmusic.presentation.catalog.CatalogPageState(
                        page = com.korenskoy.yandexmusic.presentation.catalog.CatalogPage.Album("100", "Нечего терять"),
                        album = album,
                        isLoading = false
                    ),
                    currentTrackId = "1",
                    isPlaying = true,
                    likedTrackIds = setOf("1"),
                    contentBottom = 84.dp,
                    actions = com.korenskoy.yandexmusic.presentation.catalog.CatalogActions(
                        onBack = {}, onRetry = {}, onOpenArtist = { _, _ -> }, onOpenAlbum = { _, _ -> },
                        onPlay = { _, _, _ -> }, onToggleLike = {}
                    )
                )
            }
            "local_music" -> show {
                val localTracks = tracks.mapIndexed { idx, t ->
                    com.korenskoy.yandexmusic.data.local.LocalTrack(
                        id = "local_m$idx",
                        uri = "content://media/external/audio/media/$idx",
                        title = t.title.orEmpty(),
                        artist = t.artists.firstOrNull()?.name,
                        album = "На устройстве",
                        durationMs = 195_000L,
                        source = if (idx % 2 == 0) com.korenskoy.yandexmusic.data.local.LocalSource.PHONE else com.korenskoy.yandexmusic.data.local.LocalSource.FLASH,
                        folder = "Music",
                        addedAt = 1_700_000_000L
                    )
                }
                com.korenskoy.yandexmusic.presentation.local.LocalMusicPage(
                    ui = com.korenskoy.yandexmusic.presentation.local.LocalMusicUi(
                        library = com.korenskoy.yandexmusic.data.local.LocalMusicRepository.Library(
                            tracks = localTracks, hasAccess = true, scanned = true
                        ),
                        toTrack = { lt -> track(lt.id, lt.title, lt.artist.orEmpty()) }
                    ),
                    currentTrackId = "local_m0",
                    isPlaying = true,
                    contentBottom = 84.dp,
                    onBack = {}
                )
            }
            "player_right" -> player(lyricsOpen = false, wheelOnLeft = false)
            "player_lyrics_right" -> player(lyricsOpen = true, wheelOnLeft = false)
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
            "no_plus" -> show { NoPlusScreen(account = "ivan.petrov", checking = false, onCheck = {}, onOtherAccount = {}, onSignOut = {}) }
            "update_failed" -> show {
                UpdateDialog(
                    updateInfo = UpdateCheckResult.UpdateAvailable("1.14.5", "Yandex Music Lite v1.14.5", "• 🛠 Исправления и улучшения", "https://github.com/x.apk", 9_000_000L),
                    downloadProgress = 2f, onStartDownload = {}, onInstall = {}, onDismiss = {},
                    installState = InstallState.NoAnswer, onRetry = {}, onSaveFile = {}
                )
            }
            "equalizer" -> show {
                val rock = com.korenskoy.yandexmusic.data.player.AppEqualizer.BUILT_IN.first { it.id == "rock" }
                val eq = com.korenskoy.yandexmusic.data.player.AppEqualizer.State(
                    settings = com.korenskoy.yandexmusic.data.player.eq.EqSettings(enabled = true, gains = rock.gains, bass = 3f, treble = 2f),
                    presetId = rock.id,
                    presets = com.korenskoy.yandexmusic.data.player.AppEqualizer.BUILT_IN +
                        com.korenskoy.yandexmusic.data.player.AppEqualizer.Preset("custom_1", "Моя в машине", rock.gains, custom = true)
                )
                SettingsSheet(state = SettingsUiState(appEqualizer = eq, equalizerAvailable = true), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.EQUALIZER)
            }
            "settings_no_drive" -> show {
                SettingsSheet(state = SettingsUiState(storage = StorageStatus(pickerAvailable = false)), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
            }
            "player_repeat" -> player(lyricsOpen = false, repeatAndShuffle = true)
            "player_light" -> player(lyricsOpen = false, darkTheme = false, repeatAndShuffle = true)
            "car_repeat" -> car(source = PlaylistSource.LIBRARY, shuffled = true, repeatMode = RepeatMode.ALL)
            "car_light" -> car(source = PlaylistSource.LIBRARY, shuffled = true, darkTheme = false, repeatMode = RepeatMode.ONE)
            "search_topchart" -> show {
                SearchScreen(
                    query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, topChart = tracks, trendTracks = tracks,
                    isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                    onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино")
                )
            }
            "search_light" -> show(darkTheme = false) {
                SearchScreen(
                    query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, topChart = tracks, trendTracks = tracks,
                    isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                    onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино")
                )
            }
            "settings_schedule" -> show {
                SettingsSheet(
                    state = SettingsUiState(
                        themeMode = AppThemeMode.SCHEDULE,
                        themeScheduleType = ThemeScheduleType.CUSTOM,
                        fullscreenMode = true
                    ),
                    actions = SettingsActions(),
                    onDismiss = {},
                    initialPage = SettingsPage.INTERFACE
                )
            }
            "settings_light" -> show(darkTheme = false) {
                SettingsSheet(
                    state = SettingsUiState(
                        themeMode = AppThemeMode.SCHEDULE,
                        themeScheduleType = ThemeScheduleType.CUSTOM,
                        fullscreenMode = true
                    ),
                    actions = SettingsActions(),
                    onDismiss = {},
                    initialPage = SettingsPage.INTERFACE
                )
            }
            "radio_light" -> {
                val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
                show(darkTheme = false) {
                    RadioScreen(
                        stations = stations, allStations = stations, selectedCategory = RadioCategory.ALL,
                        onCategorySelect = {}, currentStationId = stations.first().id, isPlaying = true,
                        favoriteStationIds = setOf(stations[1].id), onStationClick = {}, onToggleFavorite = {}, onPlayPause = {}
                    )
                }
            }
            "tamper_dark" -> show(darkTheme = true) { TamperBannerOverlay() }
            "tamper_light" -> show(darkTheme = false) { TamperBannerOverlay() }
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
            "car_playlists", "car_three", "car_right", "car_tune_right", "car_queue_right", "car_lyrics_right",
            "car_liked_right", "car_radio_right", "car_stations_right", "car_playlists_right", "car_three_right",
            "car_next", "car_next_right", "account_management", "startup_account_picker", "quality_details",
            "crash_log", "playlist_name_dialog", "voice_listening", "queue_sheet", "playlist_screen",
            "catalog_album", "local_music",
            "player_right", "player_lyrics_right", "auth_strip", "auth_code", "settings_drives", "settings_no_drive",
            "settings_about", "settings_support", "no_plus", "update_failed", "equalizer",
            "player_repeat", "player_light", "car_repeat", "car_light", "search_topchart", "search_light",
            "settings_schedule", "settings_light", "radio_light", "tamper_dark", "tamper_light"
        )

        /**
         * The screens the app meets, each as the window it gives in dp (pixels ÷ density) with the density bucket it
         * draws in; the scale the interface may get on top of it. Gathered for 1.15 from the market as a whole:
         * phones by StatCounter's most common viewports, tablets by the common panels, head units by what the
         * aftermarket sells most (1024×600 and 1280×720 at 9–10″, QLED 1280×720, 2K 2000×1200, 1920×720 bars) and
         * by the cars' own screens (Geely, Changan, Chery 12.3″ 1920×720, vertical Geely 1440×1920, the Monjaro's
         * 1760×720 at ~198 dpi, Android Automotive portrait screens), and split windows beside a navigator.
         */
        private val SIZES = listOf(
            // Phones (StatCounter: 360×800, 393×873, 412×915, 384×854, 360×780 the most common Android viewports)
            "w360dp-h800dp-port-xhdpi" to 1f,
            "w393dp-h873dp-port-xxhdpi" to 1f,
            "w412dp-h915dp-port-xxhdpi" to 1f,
            "w384dp-h854dp-port-xhdpi" to 1f,
            "w360dp-h780dp-port-xxhdpi" to 1f,
            "w360dp-h640dp-port-xhdpi" to 1f,
            "w320dp-h568dp-port-hdpi" to 1f,
            "w412dp-h915dp-port-xxhdpi" to 1.2f,
            // Phones on their side, in the car holder
            "w800dp-h360dp-land-xhdpi" to 1f,
            "w873dp-h393dp-land-xxhdpi" to 1f,
            "w915dp-h412dp-land-xxhdpi" to 1f,
            // Foldables
            "w673dp-h841dp-port-xxhdpi" to 1f,
            // Tablets: 7–8″ (1200×1920 at 320), 10″ (1200×1920 at 240, 1600×2560 at 320), 12″, half in split screen
            "w600dp-h960dp-port-xhdpi" to 1f,
            "w600dp-h1024dp-port-hdpi" to 1f,
            "w800dp-h1280dp-port-xhdpi" to 1f,
            "w1280dp-h800dp-land-xhdpi" to 1f,
            "w1366dp-h1024dp-land-xhdpi" to 1f,
            "w636dp-h752dp-port-xhdpi" to 1f,
            // Aftermarket head units lying: 1024×600 and 1280×720 at 160 (the most sold 9–10″), 1280×720 at 240,
            // 1280×800, 2000×1200 at 240 and 320 (Teyes CC3 2K), 1920×720 at 160 and 240, 1600×720, old 800×480
            "w1024dp-h600dp-land-mdpi" to 1f,
            "w1024dp-h600dp-land-mdpi" to 0.8f,
            "w1280dp-h720dp-land-mdpi" to 1f,
            "w853dp-h480dp-land-hdpi" to 1f,
            "w1280dp-h800dp-land-mdpi" to 1f,
            "w1333dp-h800dp-land-hdpi" to 1f,
            "w1000dp-h600dp-land-xhdpi" to 1f,
            "w1920dp-h720dp-land-mdpi" to 1f,
            "w1920dp-h720dp-land-mdpi" to 0.8f,
            "w1280dp-h480dp-land-mdpi" to 1f,
            "w1280dp-h480dp-land-mdpi" to 1.2f,
            "w1600dp-h720dp-land-mdpi" to 1f,
            "w800dp-h480dp-land-mdpi" to 1f,
            "w1920dp-h1080dp-land-mdpi" to 1f,
            // The cars' own screens: the Monjaro (1760×720 at ~198), 2.5K 2560×1440 at 320, vertical Geely
            // (1440×1920 at 320) and the vertical units (1080×1920 at 240, 768×1024 at 160 — also Android
            // Automotive's portrait screens), the Geely Galaxy's 960×1280
            "w1422dp-h582dp-land-hdpi" to 1f,
            "w1280dp-h720dp-land-xhdpi" to 1f,
            "w720dp-h960dp-port-xhdpi" to 1f,
            "w720dp-h1280dp-port-hdpi" to 1f,
            "w768dp-h1024dp-port-mdpi" to 1f,
            "w960dp-h1280dp-port-mdpi" to 1f,
            // A head unit's split screen beside a navigator: a third, a half and two thirds of 1280×720, a half and a
            // third of 1920×720, a half of 1024×600, a launcher's lower 40 %, short windows
            "w426dp-h720dp-port-mdpi" to 1f,
            "w640dp-h720dp-port-mdpi" to 1f,
            "w853dp-h720dp-land-mdpi" to 1f,
            "w960dp-h720dp-land-mdpi" to 1f,
            "w512dp-h600dp-port-mdpi" to 1f,
            "w700dp-h560dp-land-mdpi" to 1f,
            "w1550dp-h349dp-land-mdpi" to 1f,
            "w420dp-h500dp-port-mdpi" to 1f,
            "w480dp-h420dp-land-mdpi" to 1f,
            // Windows of any shape (1.15): a phone's split screen standing (a half, a third) and lying (a half, a
            // third), Android 16's smallest free window on a tablet (386×352), free squares, a standing tablet split
            // in two and in thirds, Carlinkit's 40/60 beside CarPlay on 1280×720 and 1920×720, a launcher's lower
            // strip on 1280×720 and 1024×600, a desktop window
            "w412dp-h450dp-port-xxhdpi" to 1f,
            "w412dp-h300dp-land-xxhdpi" to 1f,
            "w457dp-h412dp-land-xxhdpi" to 1f,
            "w300dp-h412dp-port-xxhdpi" to 1f,
            "w386dp-h352dp-land-xhdpi" to 1f,
            "w500dp-h500dp-port-xhdpi" to 1f,
            "w700dp-h700dp-port-xhdpi" to 1f,
            "w800dp-h640dp-land-xhdpi" to 1f,
            "w800dp-h426dp-land-xhdpi" to 1f,
            "w768dp-h720dp-land-mdpi" to 1f,
            "w1152dp-h720dp-land-mdpi" to 1f,
            "w1280dp-h288dp-land-mdpi" to 1f,
            "w1024dp-h240dp-land-mdpi" to 1f,
            "w1100dp-h700dp-land-xhdpi" to 1f,
            // 1.16.1 matrix expansion (devices_and_dpi.json + Issue #31/#36 reproductions):
            "w444dp-h800dp-port-hdpi" to 1f,
            "w667dp-h400dp-land-hdpi" to 1f,
            "w672dp-h720dp-port-mdpi" to 1f,
            "w1248dp-h720dp-land-mdpi" to 1f,
            "w320dp-h240dp-land-mdpi" to 1f,
            "w420dp-h300dp-land-hdpi" to 1f,
            "w1600dp-h600dp-land-hdpi" to 1f,
            "w1440dp-h810dp-land-xhdpi" to 1f,
            "w1080dp-h1920dp-port-mdpi" to 1f,
            "w756dp-h813dp-port-xxxhdpi" to 1f,
            "w360dp-h374dp-port-xhdpi" to 1f,
            "w853dp-h533dp-land-xxhdpi" to 1f,
            "w640dp-h400dp-land-xhdpi" to 1f,
            "w340dp-h520dp-port-xxhdpi" to 1f,
            "w768dp-h640dp-land-mdpi" to 1f,
            "w900dp-h460dp-land-mdpi" to 1.35f
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
            // Scrolled out of view (a panel that scrolls on a short screen): its touch area is not known here, it is
            // checked where it is in view
            if (scrolls(node) && area(node.boundsInRoot) < 0.97f * area(whole)) continue
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
