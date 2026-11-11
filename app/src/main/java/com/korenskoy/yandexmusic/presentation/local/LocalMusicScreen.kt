package com.korenskoy.yandexmusic.presentation.local

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.local.LocalArtist
import com.korenskoy.yandexmusic.data.local.LocalFolder
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalRules
import com.korenskoy.yandexmusic.data.local.LocalSort
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.data.local.LocalTrack
import com.korenskoy.yandexmusic.presentation.hifi.AppScale
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiEmptyState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiFilterPill
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTextAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenu
import com.korenskoy.yandexmusic.presentation.main.components.PlayingBars
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.russianQuantityString

/** "Телефон", "SD-карта", "Флешка". */
@Composable
fun localSourceLabel(source: LocalSource): String = stringResource(
    when (source) {
        LocalSource.PHONE -> R.string.local_source_phone
        LocalSource.SD -> R.string.local_source_sd
        LocalSource.FLASH -> R.string.local_source_flash
    }
)

@Composable
private fun localWhere(source: LocalSource): String = stringResource(
    when (source) {
        LocalSource.PHONE -> R.string.local_where_phone
        LocalSource.SD -> R.string.local_where_sd
        LocalSource.FLASH -> R.string.local_where_flash
    }
)

/** "телефон, SD-карта, флешка": where the music lies, in that order. */
@Composable
fun localSourcesText(sources: Collection<LocalSource>): String {
    val names = LocalSource.entries.filter { it in sources }.map { localWhere(it) }
    return names.joinToString(", ")
}

/** What the page needs from the app: the music and what to do with it. */
@Immutable
data class LocalMusicUi(
    val library: LocalMusicRepository.Library,
    /** The page was opened before: the card's "new" mark is gone. */
    val seen: Boolean = true,
    val toTrack: (LocalTrack) -> TrackDetailsDto = { error("No tracks in a preview") },
    val onPlay: (track: LocalTrack, queue: List<LocalTrack>) -> Unit = { _, _ -> },
    val onPlayAll: (queue: List<LocalTrack>, shuffle: Boolean) -> Unit = { _, _ -> },
    val onAccessChanged: () -> Unit = {},
    val onAddFolder: (Uri) -> Unit = {},
    val onRemoveFolder: (String) -> Unit = {},
    /** Where the folder window opens: the flash drive that is in; null — as the system likes. */
    val pickerStart: () -> Uri? = { null },
    val onOpened: () -> Unit = {}
)

/** "Музыка с устройства" has a place in "Скачанные": music found, or a question that may find some. */
val LocalMusicRepository.Library.showsCard: Boolean
    get() = scanned && (!hasAccess || tracks.isNotEmpty() || folders.isNotEmpty())

/**
 * The folder "Музыка с устройства" at the head of "На устройстве": wider than the playlists beside it and with a
 * phone instead of a cover, so it reads as a folder, not one more playlist.
 */
@Composable
fun LocalFolderCard(library: LocalMusicRepository.Library, isNew: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val title = stringResource(R.string.local_title)
    Column(Modifier.width(164.dp).hifiPress(onClick)) {
        Box(
            Modifier
                .width(164.dp)
                .height(132.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF3A4152), Color(0xFF1C2130), Color(0xFF0E1118))))
                .border(1.dp, Color(0x4DC9CED8), shape),
            contentAlignment = Alignment.Center
        ) {
            Box {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = Color(0xFFF4F6FA), modifier = Modifier.size(44.dp))
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 14.dp, y = 6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF15171F))
                        .border(1.dp, Color(0x6BC9CED8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFFF4F6FA), modifier = Modifier.size(17.dp))
                }
            }
        }
        Text(title, style = HiFiType.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (library.hasAccess || library.tracks.isNotEmpty()) tracksText(library.tracks.size) else stringResource(R.string.local_card_tap),
                style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
            if (isNew) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.local_card_new),
                    color = Color(0xFF9CC3FF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(HiFi.Accent.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun tracksText(count: Int): String = russianQuantityString(count, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many)

private enum class LocalView { TRACKS, FOLDERS, ARTISTS }

/**
 * "Музыка с устройства": what lies on the phone, the SD card and the flash drives. Where it lies (chips, only those
 * present), as tracks, as folders (people often keep their music as folders — those are their playlists) or by
 * artist; "Слушать" and "Перемешать" for what is shown. Before the first look Android asks for the access once.
 */
@Composable
fun LocalMusicPage(
    ui: LocalMusicUi,
    currentTrackId: String?,
    isPlaying: Boolean,
    contentBottom: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens on this view (screenshots): 0 tracks, 1 folders, 2 artists. */
    initialView: Int = 0
) {
    val context = LocalContext.current
    val library = ui.library
    LaunchedEffect(Unit) { ui.onOpened() }
    // Back from the settings (the access given or taken there), or from the system's window
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ui.onAccessChanged() }

    var deniedForever by rememberSaveable { mutableStateOf(false) }
    val askAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.onAccessChanged()
        // Refused with "don't ask again" (or once too often): Android no longer shows its window
        deniedForever = !granted && context.findActivity()?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, LocalMusicRepository.PERMISSION) } == true
    }
    val requestAccess: () -> Unit = {
        if (deniedForever) {
            val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            runCatching { context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            askAccess.launch(LocalMusicRepository.PERMISSION)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) ui.onAddFolder(uri) }
    val addFolder: () -> Unit = {
        // Some head units have no system window to pick a folder in
        runCatching { pickFolder.launch(ui.pickerStart()) }.onFailure { e ->
            if (e !is ActivityNotFoundException) throw e
            Toast.makeText(context, R.string.local_no_folder_picker, Toast.LENGTH_LONG).show()
        }
    }

    var sourceName by rememberSaveable { mutableStateOf<String?>(null) }
    var viewIndex by rememberSaveable { mutableStateOf(initialView.coerceIn(0, LocalView.entries.size - 1)) }
    var sortName by rememberSaveable { mutableStateOf(LocalSort.RECENT.name) }
    var openedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var openedArtist by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openedFolder != null || openedArtist != null) {
        openedFolder = null
        openedArtist = null
    }

    val sources = remember(library.tracks) { LocalSource.entries.filter { s -> library.tracks.any { it.source == s } } }
    val source = sourceName?.let { LocalSource.valueOf(it) }?.takeIf { it in sources }
    val shown = remember(library.tracks, source) { if (source == null) library.tracks else library.tracks.filter { it.source == source } }
    val sort = LocalSort.valueOf(sortName)
    val sorted = remember(shown, sort) { LocalRules.sorted(shown, sort) }
    val folders = remember(shown) { LocalRules.folders(shown) }
    val artists = remember(shown) { LocalRules.artists(shown) }
    val view = LocalView.entries[viewIndex]
    val folder = openedFolder?.let { key -> folders.firstOrNull { it.key == key } }
    val artist = openedArtist?.let { key -> artists.firstOrNull { artistKey(it) == key } }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showSource = sources.size > 1 && source == null

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = contentBottom)
    ) {
        item(key = "header") {
            val subtitle = when {
                !library.scanned -> ""
                library.tracks.isEmpty() && !library.hasAccess -> stringResource(R.string.local_access_subtitle)
                library.tracks.isEmpty() -> ""
                else -> listOf(tracksText(library.tracks.size), localSourcesText(sources)).filter { it.isNotBlank() }.joinToString(" · ")
            }
            PageHeader(stringResource(R.string.local_title), subtitle, onBack)
        }
        when {
            !library.scanned -> item(key = "loading") { HiFiSkeletonRows(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), count = 6) }
            !library.hasAccess && library.folders.isEmpty() -> item(key = "access") {
                AccessCard(deniedForever, requestAccess, addFolder)
            }
            folder != null || artist != null -> {
                val tracks = folder?.tracks ?: artist?.tracks.orEmpty()
                item(key = "group_header") {
                    val title = folder?.let { it.name ?: localSourceLabel(it.source) } ?: artist?.name ?: stringResource(R.string.local_unknown_artist)
                    GroupHeader(title, tracks.size, folder?.source?.takeIf { showSource }) { openedFolder = null; openedArtist = null }
                }
                item(key = "group_buttons") { PlayButtons({ ui.onPlayAll(tracks, false) }, { ui.onPlayAll(tracks, true) }) }
                trackRows(tracks, ui, currentTrackId, isPlaying, showSource && folder == null, isLandscape)
            }
            else -> {
                if (!library.hasAccess) item(key = "banner") { AccessBanner(requestAccess) }
                if (sources.size > 1) item(key = "sources") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        item(key = "all") { HiFiFilterPill(stringResource(R.string.local_filter_all), source == null, { sourceName = null }) }
                        items(sources, key = { it.name }) { s -> HiFiFilterPill(localSourceLabel(s), source == s, { sourceName = s.name }) }
                    }
                }
                if (library.tracks.isNotEmpty()) item(key = "views") {
                    HiFiSegmented(
                        options = listOf(stringResource(R.string.local_view_tracks), stringResource(R.string.local_view_folders), stringResource(R.string.local_view_artists)),
                        selectedIndex = viewIndex,
                        onSelect = { viewIndex = it },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        fontSize = 14.sp
                    )
                }
                if (library.tracks.isEmpty()) {
                    item(key = "empty") {
                        HiFiEmptyState(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(if (library.readingFolders) R.string.local_reading else R.string.local_empty_title),
                            message = stringResource(R.string.local_empty_message).takeUnless { library.readingFolders }
                        )
                    }
                } else when (view) {
                    LocalView.TRACKS -> {
                        item(key = "buttons") { PlayButtons({ ui.onPlayAll(sorted, false) }, { ui.onPlayAll(sorted, true) }) }
                        item(key = "sort") { SortHeader(sort) { sortName = it.name } }
                        trackRows(sorted, ui, currentTrackId, isPlaying, showSource, isLandscape)
                    }
                    LocalView.FOLDERS -> {
                        item(key = "folders_gap") { Spacer(Modifier.height(10.dp)) }
                        items(folders, key = { "f:" + it.key }) { f ->
                            GroupRow(Icons.Rounded.Folder, f.name ?: localSourceLabel(f.source), f.tracks.size, f.source.takeIf { showSource }) { openedFolder = f.key }
                        }
                    }
                    LocalView.ARTISTS -> {
                        item(key = "artists_gap") { Spacer(Modifier.height(10.dp)) }
                        items(artists, key = { "a:" + artistKey(it) }) { a ->
                            GroupRow(Icons.Rounded.Person, a.name ?: stringResource(R.string.local_unknown_artist), a.tracks.size, null) { openedArtist = artistKey(a) }
                        }
                    }
                }
                item(key = "folders_added") { AddedFolders(library, ui.onRemoveFolder, addFolder) }
            }
        }
    }
}

private fun artistKey(artist: LocalArtist): String = artist.name?.lowercase().orEmpty()

private fun LazyListScope.trackRows(
    tracks: List<LocalTrack>,
    ui: LocalMusicUi,
    currentTrackId: String?,
    isPlaying: Boolean,
    showSource: Boolean,
    twoColumns: Boolean
) {
    if (twoColumns) {
        items(tracks.chunked(2), key = { row -> row.joinToString("|") { it.id } }, contentType = { "local_row" }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { track ->
                    Box(Modifier.weight(1f)) { LocalTrackRow(track, ui, isPlaying && track.id == currentTrackId, showSource) { ui.onPlay(track, tracks) } }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        items(tracks, key = { it.id }, contentType = { "local_track" }) { track ->
            LocalTrackRow(track, ui, isPlaying && track.id == currentTrackId, showSource) { ui.onPlay(track, tracks) }
        }
    }
}

/** One file: its cover (or a note), title, artist and where it lies, length. A long press opens the track's menu. */
@Composable
private fun LocalTrackRow(track: LocalTrack, ui: LocalMusicUi, playing: Boolean, showSource: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val menu = LocalTrackMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hifiPress(onClick = onClick, role = null, onLongClick = { menu?.open(ui.toTrack(track), null) })
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(HiFi.Placeholder), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
            AsyncImage(
                model = ImageRequest.Builder(context).cover(track.art, CoverSize.Small).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (playing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { PlayingBars(size = 18.dp) }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = HiFiType.Body.copy(fontSize = HiFiType.RowTitle.fontSize, lineHeight = HiFiType.RowTitle.lineHeight),
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val artist = track.artist ?: stringResource(R.string.local_unknown_artist)
            Text(
                if (showSource) "$artist · ${localWhere(track.source)}" else artist,
                style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (track.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(durationText(track.durationMs), color = HiFi.TextTertiary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** "4:12", "1:02:45". */
internal fun durationText(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(onClick = onBack, contentDescription = stringResource(R.string.cd_nazad)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HiFiType.LargeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An opened folder or artist: its name under the page's header, and the way back to all of them. */
@Composable
private fun GroupHeader(title: String, count: Int, source: LocalSource?, onBack: () -> Unit) {
    val back = stringResource(R.string.local_back_to_folders)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .hifiPress(onBack)
            .semantics { contentDescription = back }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HiFiType.CardTitle.copy(fontSize = 20.sp, lineHeight = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(tracksText(count), source?.let { localWhere(it) }).joinToString(" · "), style = HiFiType.Caption, maxLines = 1)
        }
    }
}

@Composable
private fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetalPillButton(stringResource(R.string.library_listen), onPlay, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
        GlassPillButton(stringResource(R.string.library_shuffle), onShuffle, Modifier.weight(1f), icon = Icons.Rounded.Shuffle)
    }
}

@Composable
private fun sortLabel(sort: LocalSort): String = stringResource(
    when (sort) {
        LocalSort.RECENT -> R.string.local_sort_recent
        LocalSort.TITLE -> R.string.local_sort_title
        LocalSort.ARTIST -> R.string.local_sort_artist
    }
)

/** "ТРЕКИ" with the order beside it ("Добавлены недавно ▾"), which opens the choice. */
@Composable
private fun SortHeader(sort: LocalSort, onSort: (LocalSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = sortLabel(sort)
    HiFiSectionHeader(stringResource(R.string.local_view_tracks), Modifier.padding(start = 12.dp, end = 4.dp, top = 16.dp, bottom = 2.dp)) {
        val description = stringResource(R.string.local_sort_cd, current)
        Box {
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .hifiPress({ open = true })
                    .semantics { contentDescription = description }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
            val appDensity = LocalDensity.current
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = HiFiDialogDefaults.Container,
                border = BorderStroke(1.dp, HiFiDialogDefaults.Rim)
            ) {
                AppScale(appDensity) {
                    val colors = MenuDefaults.itemColors(textColor = Color.White)
                    LocalSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(option), fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { open = false; onSort(option) },
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, count: Int, source: LocalSource?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .hifiPress(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(icon, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HiFiType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(tracksText(count), source?.let { localWhere(it) }).joinToString(" · "), style = HiFiType.Caption, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
    }
}

/** Before the first look: why the access, Android's one question, and the way for head units that hide the drive. */
@Composable
private fun AccessCard(deniedForever: Boolean, onAllow: () -> Unit, onAddFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF3A4152), Color(0xFF15171F))))
                    .border(1.dp, Color(0x5CC9CED8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFFF4F6FA), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.local_access_title), style = HiFiType.CardTitle.copy(fontSize = 21.sp, lineHeight = 27.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (deniedForever) R.string.local_access_denied else R.string.local_access_message),
                color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(22.dp))
            MetalPillButton(
                stringResource(if (deniedForever) R.string.local_access_settings else R.string.local_access_button),
                onAllow, Modifier.fillMaxWidth().widthIn(max = 360.dp), height = 50.dp
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.local_access_note), color = Color.White.copy(alpha = 0.50f), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.local_add_folder_hint), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))
        GlassPillButton(stringResource(R.string.local_add_folder), onAddFolder, Modifier.fillMaxWidth(), icon = Icons.Rounded.CreateNewFolder, height = 48.dp)
    }
}

/** The music of the added folders shows, the rest of the device waits for the access. */
@Composable
private fun AccessBanner(onAllow: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .hifiGlass(RoundedCornerShape(18.dp), fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.local_access_banner), style = HiFiType.Caption, modifier = Modifier.weight(1f))
        HiFiTextAction(stringResource(R.string.local_access_banner_button), onAllow, color = Color.White)
    }
}

/** The folders added by hand, each can be taken off the list; "Добавить папку" for one more. */
@Composable
private fun AddedFolders(library: LocalMusicRepository.Library, onRemove: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 22.dp)) {
        if (library.folders.isNotEmpty()) {
            HiFiSectionHeader(stringResource(R.string.local_folders_added), Modifier.padding(bottom = 6.dp))
            library.folders.forEach { folder ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(folder.name, style = HiFiType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    GlassCircleButton(onClick = { onRemove(folder.uri) }, contentDescription = stringResource(R.string.local_folder_remove_cd, folder.name), size = 36.dp) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        GlassPillButton(stringResource(R.string.local_add_folder), onAdd, Modifier.fillMaxWidth(), icon = Icons.Rounded.CreateNewFolder, height = 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.local_add_folder_hint), color = Color.White.copy(alpha = 0.50f), fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** For [LocalFolder] lists elsewhere (the car): the folder's name, or its drive's for the root. */
@Composable
fun localFolderName(folder: LocalFolder): String = folder.name ?: localSourceLabel(folder.source)
