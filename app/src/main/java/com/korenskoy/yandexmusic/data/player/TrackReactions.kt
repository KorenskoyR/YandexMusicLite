package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.download.DownloadScheduler
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Like and dislike, the same from every place that offers them: the app's screens and the player in
 * the notification shade, on the lock screen and in the car. For a station the heart is "favourite".
 * Process-wide, so the notification's buttons work while no screen of the app is open.
 */
@Singleton
class TrackReactions @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val radioRepository: RadioRepository,
    private val playbackController: PlaybackController,
    private val downloadManager: TrackDownloadManager,
    private val downloadScheduler: DownloadScheduler,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val likedTrackIds: StateFlow<Set<String>> = libraryRepository.likedIds
    val favoriteStationIds: StateFlow<Set<String>> = radioRepository.favoriteStationIds
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Whether the heart of [track] is filled: a liked track, or a favourite station. */
    fun isLiked(track: TrackDetailsDto): Boolean =
        if (track.isRadio) stationId(track) in favoriteStationIds.value else track.idString in likedTrackIds.value

    /** The heart: like / unlike a track (a new like is downloaded when auto-download is on), favourite a station. */
    fun toggleLike(track: TrackDetailsDto) {
        if (track.isRadio) {
            scope.launch { radioRepository.toggleFavorite(stationId(track)) }
            return
        }
        val trackId = track.idString
        val like = trackId !in likedTrackIds.value
        libraryRepository.setLiked(track, like) { ok ->
            if (!ok) return@setLiked
            scope.launch {
                if (!like) downloadManager.removeTrack(trackId)
                else if (settingsRepository.autoDownloadLikedTracks.first()) downloadScheduler.downloadTrack(trackId)
            }
        }
    }

    /** "Не нравится": the track leaves the wave and the next one plays; a station leaves the favourites. */
    fun dislike(track: TrackDetailsDto) {
        if (track.isRadio) {
            scope.launch { radioRepository.removeFavorite(stationId(track)) }
            return
        }
        libraryRepository.dislike(track)
        playbackController.onTrackDisliked(track.idString)
        scope.launch { downloadManager.removeTrack(track.idString) }
    }

    private fun stationId(track: TrackDetailsDto) = track.idString.removePrefix("radio_")
}
