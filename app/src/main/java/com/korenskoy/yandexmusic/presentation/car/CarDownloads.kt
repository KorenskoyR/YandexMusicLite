package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.local.localSourceLabel
import com.korenskoy.yandexmusic.presentation.local.localSourcesText
import com.korenskoy.yandexmusic.utils.russianQuantityString

/** A folder of the user's music for the car's "Скачанные". */
@Immutable
data class CarDeviceFolder(val key: String, val name: String?, val count: Int, val source: LocalSource)

/**
 * The user's own music for the car: with it, the "Скачанные" tile opens a choice — what the app downloaded,
 * the music on the device, its folders — instead of starting the downloads at once.
 */
@Immutable
data class CarDeviceMusic(
    val downloadedCount: Int,
    val deviceCount: Int,
    /** Where the music lies: the phone, the SD card, a flash drive. */
    val sources: List<LocalSource>,
    val folders: List<CarDeviceFolder>
) {
    /** The panel is worth a step only when there is music on the device. */
    val opensPanel: Boolean get() = deviceCount > 0
}

/** How many folders the car's panel lists: the most a driver would look through. */
const val CAR_DEVICE_FOLDERS = 12

/**
 * "Скачанные" in the car, over the cover's place: two big rows (what the app downloaded, the music on the device)
 * and the device's folders, the flash drive's first. One tap plays; nothing else to choose while driving.
 */
@Composable
internal fun CarDownloadsPanel(
    u: Scale,
    modifier: Modifier,
    music: CarDeviceMusic,
    onPlayDownloaded: () -> Unit,
    onPlayDevice: (folderKey: String?) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(30f)))
            .background(HiFi.SheetFill.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = u(16f), end = u(16f), top = u(18f), bottom = u(8f))
    ) {
        Row(Modifier.padding(start = u(8f), end = u(2f), bottom = u(10f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.car_source_downloaded), color = Color.White, fontSize = u.sp(28f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(stringResource(R.string.car_downloads_caption), color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(u(56f))
                    .hifiGlass(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_zakryt), tint = Color.White, modifier = Modifier.size(u(24f)))
            }
        }
        val appSubtitle = if (music.downloadedCount > 0) {
            russianQuantityString(music.downloadedCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many)
        } else {
            stringResource(R.string.car_downloads_in_app_none)
        }
        val deviceSubtitle = listOf(
            russianQuantityString(music.deviceCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
            localSourcesText(music.sources)
        ).filter { it.isNotBlank() }.joinToString(" · ")
        val inApp = stringResource(R.string.car_downloads_in_app)
        val onDevice = stringResource(R.string.local_title)
        val foldersTitle = stringResource(if (music.folders.isNotEmpty() && music.folders.all { it.source == LocalSource.FLASH }) R.string.car_downloads_folders_flash else R.string.car_downloads_folders).uppercase()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(u(10f)),
            contentPadding = PaddingValues(bottom = u(8f))
        ) {
            item(key = "app") {
                SourceRow(u, Icons.Rounded.DownloadDone, inApp, appSubtitle, enabled = music.downloadedCount > 0, onClick = onPlayDownloaded)
            }
            item(key = "device") {
                SourceRow(u, Icons.Rounded.PhoneAndroid, onDevice, deviceSubtitle, enabled = true, onClick = { onPlayDevice(null) })
            }
            if (music.folders.size > 1) {
                item(key = "folders_title") {
                    Text(
                        foldersTitle,
                        color = Color.White.copy(alpha = 0.60f), fontSize = u.sp(13f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em,
                        modifier = Modifier.padding(start = u(10f), top = u(8f))
                    )
                }
                items(music.folders, key = { it.key }) { folder -> FolderRow(u, folder) { onPlayDevice(folder.key) } }
            }
        }
    }
}

@Composable
private fun SourceRow(u: Scale, icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u(22f))
    val description = stringResource(R.string.car_next_play_cd, title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(100f))
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle" }
            .padding(horizontal = u(16f), vertical = u(14f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(u(72f))
                .clip(RoundedCornerShape(u(18f)))
                .background(HiFi.Placeholder)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(u(18f))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.45f), modifier = Modifier.size(u(34f)))
        }
        Spacer(Modifier.width(u(18f)))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.55f), fontSize = u.sp(23f), lineHeight = u.sp(29f), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.64f), fontSize = u.sp(16f), lineHeight = u.sp(22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) {
            Spacer(Modifier.width(u(12f)))
            HiFiKnob(onClick = onClick, contentDescription = description, size = u(60f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(u(28f)))
            }
        }
    }
}

@Composable
private fun FolderRow(u: Scale, folder: CarDeviceFolder, onClick: () -> Unit) {
    val name = folder.name ?: localSourceLabel(folder.source)
    val description = stringResource(R.string.car_next_play_cd, name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(72f))
            .clip(RoundedCornerShape(u(16f)))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = u(12f), vertical = u(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(u(30f)))
        Spacer(Modifier.width(u(16f)))
        Text(name, color = Color.White, fontSize = u.sp(21f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(u(12f)))
        Text(folder.count.toString(), color = Color.White.copy(alpha = 0.55f), fontSize = u.sp(17f), maxLines = 1)
    }
}
