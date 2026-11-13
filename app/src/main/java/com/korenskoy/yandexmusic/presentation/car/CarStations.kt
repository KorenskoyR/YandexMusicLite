package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.utils.browserUserAgent
import com.korenskoy.yandexmusic.utils.russianQuantityString

/** The stations as the car shows them: the list, the favourites, the one on air. */
class CarRadio(
    val stations: List<RadioStation>,
    val favourites: Set<String>,
    /** The id of the station on air, if the radio plays. */
    val onAir: String?,
    val onPlay: (RadioStation) -> Unit,
    val onToggleFavourite: (String) -> Unit
) {
    val favouriteStations: List<RadioStation> get() = stations.filter { it.id in favourites }
}

/** "Станции · 6 избранных": the glass button under the station's card, as «Очередь» is under a track's cover. */
@Composable
internal fun StationsPill(u: Scale, favourites: Int, onClick: () -> Unit) {
    val count = if (favourites > 0) {
        russianQuantityString(favourites, R.string.car_stations_fav_one, R.string.car_stations_fav_few, R.string.car_stations_fav_many)
    } else null
    val description = stringResource(R.string.car_stations) + (count?.let { ", $it" } ?: "")
    Row(
        Modifier
            .height(u(64f))
            .hifiGlass(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = u(28f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(24f)))
        Spacer(Modifier.width(u(12f)))
        Text(stringResource(R.string.car_stations), color = Color.White, fontSize = u.sp(20f), fontWeight = FontWeight.Bold, maxLines = 1)
        if (count != null) Text(" · $count", color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(20f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * «Станции» over the cover's place: the favourites (or all of them), a tap plays one, the heart keeps it or lets it
 * go. Opens on the favourites, on all of them while there are none.
 */
@Composable
internal fun CarStationsPanel(u: Scale, radio: CarRadio, modifier: Modifier, onClose: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(30f)))
            .background(HiFi.SheetFill.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = u(16f), end = u(16f), top = u(18f), bottom = u(8f))
    ) {
        Row(Modifier.padding(start = u(8f), end = u(2f), bottom = u(8f)), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.car_stations), color = Color.White, fontSize = u.sp(28f), fontWeight = FontWeight.ExtraBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
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
        CarStationsList(u, radio, Modifier.weight(1f).fillMaxWidth(), onPlayed = onClose)
    }
}

/** The switch «Избранные · 6 / Все · 37» and the rows under it. [onPlayed] follows a tap that starts a station. */
@Composable
internal fun CarStationsList(u: Scale, radio: CarRadio, modifier: Modifier, onPlayed: () -> Unit = {}) {
    var showAll by rememberSaveable { mutableStateOf(radio.favourites.isEmpty()) }
    val shown = if (showAll) radio.stations else radio.favouriteStations
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(u(60f))
                .hifiRecessed(u(30f))
                .padding(u(4f))
                .selectableGroup()
        ) {
            for ((all, text) in listOf(
                false to stringResource(R.string.car_stations_favourites, radio.favouriteStations.size),
                true to stringResource(R.string.car_stations_all, radio.stations.size)
            )) {
                val on = all == showAll
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .then(if (on) Modifier.hifiAnodized(CircleShape) else Modifier)
                        .selectable(selected = on, role = Role.RadioButton) { showAll = all },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text, color = if (on) Color.White else Color.White.copy(alpha = 0.72f), fontSize = u.sp(17f),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(u(10f)))
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.car_stations_none),
                color = Color.White.copy(alpha = 0.66f), fontSize = u.sp(18f), lineHeight = u.sp(26f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = u(16f), vertical = u(24f))
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(shown, key = { it.id }) { station ->
                    StationRow(
                        u, station, onAir = station.id == radio.onAir, favourite = station.id in radio.favourites,
                        onPlay = { radio.onPlay(station); onPlayed() },
                        onToggle = { radio.onToggleFavourite(station.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StationRow(u: Scale, station: RadioStation, onAir: Boolean, favourite: Boolean, onPlay: () -> Unit, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(u(22f))
    val playLabel = stringResource(R.string.car_station_play_cd, station.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(80f))
            .clip(shape)
            .then(if (onAir) Modifier.hifiAnodized(shape) else Modifier)
            .padding(start = u(10f), end = u(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = u(80f))
                .clip(shape)
                .clickable(role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
                .semantics(mergeDescendants = true) { contentDescription = playLabel },
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogo(u, station)
            Spacer(Modifier.width(u(16f)))
            Column(Modifier.weight(1f)) {
                Text(station.name, color = Color.White, fontSize = u.sp(22f), lineHeight = u.sp(28f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onAir) {
                        Box(Modifier.size(u(8f)).clip(CircleShape).background(HiFi.Live))
                        Spacer(Modifier.width(u(6f)))
                        Text(
                            stringResource(R.string.car_station_on_air) + "  ", color = Color.White, fontSize = u.sp(12f),
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 0.14.em, maxLines = 1
                        )
                    }
                    Text(
                        // On air the mark takes the room of the genre: the frequency and the stream's format stay whole
                        listOfNotNull(station.frequency, station.format, station.category.title.takeIf { !onAir }).joinToString(" · "), color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(15f),
                        lineHeight = u.sp(20f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(u(12f)))
        }
        val heartLabel = stringResource(if (favourite) R.string.car_station_unfavourite_cd else R.string.car_station_favourite_cd, station.name)
        Box(
            Modifier
                .size(u(56f))
                .hifiGlass(CircleShape)
                .clickable(role = Role.Button, onClickLabel = heartLabel, onClick = onToggle)
                .semantics { contentDescription = heartLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null,
                tint = if (favourite) HiFi.Love else Color.White.copy(alpha = 0.86f), modifier = Modifier.size(u(26f))
            )
        }
    }
}

/** The station's logo on its brand colours, as the radio page shows it; its initial when it has none. */
@Composable
private fun StationLogo(u: Scale, station: RadioStation) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(u(14f))
    Box(
        Modifier
            .size(u(56f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(station.brandColorStart), Color(station.brandColorEnd)))),
        contentAlignment = Alignment.Center
    ) {
        val model: Any? = station.iconRes ?: station.logoUrl.ifEmpty { null }
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(model).crossfade(true).browserUserAgent().build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(u(6f))
            )
        } else {
            Text(station.name.take(1), color = Color.White, fontSize = u.sp(24f), fontWeight = FontWeight.ExtraBold)
        }
    }
}
