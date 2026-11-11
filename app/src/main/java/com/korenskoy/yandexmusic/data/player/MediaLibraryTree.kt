package com.korenskoy.yandexmusic.data.player

import android.util.Log
import com.korenskoy.yandexmusic.data.download.OfflineCollections
import com.korenskoy.yandexmusic.data.download.OfflineRules
import kotlinx.coroutines.CancellationException
import com.korenskoy.yandexmusic.utils.CoverSize
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalRules
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.utils.toCoverUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Browse tree for Android Auto, Android Automotive and other media browsers:
 * My Wave, Liked, Downloaded and Radio. Playback requests go to [PlaybackController],
 * so the car gets the same queue, crossfade and offline handling as the app.
 */
@Singleton
class MediaLibraryTree @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val radioRepository: RadioRepository,
    private val downloadManager: TrackDownloadManager,
    private val playbackController: PlaybackController,
    private val voiceSearch: VoiceSearch,
    private val playlistsRepository: UserPlaylistsRepository,
    private val offlineCollections: OfflineCollections,
    private val localMusic: LocalMusicRepository
) {
    fun root(): MediaItem = folder(ROOT_ID, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        .withExtras(Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
        })

    /** Children of [parentId], or null for an unknown node. Reads the cached library when nothing is loaded yet. */
    suspend fun children(parentId: String): List<MediaItem>? {
        if (parentId == LIKED_ID || parentId == DOWNLOADED_ID) libraryRepository.loadCached()
        if (parentId == PLAYLISTS_ID) playlistsRepository.loadCached()
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            val kind = parentId.removePrefix(PLAYLIST_PREFIX)
            return playlistTracks(kind)?.map { trackItem("$PLAYLIST_TRACK_PREFIX$kind/", it) }
        }
        if (parentId.startsWith(LOCAL_FOLDER_PREFIX)) {
            val key = Uri.decode(parentId.removePrefix(LOCAL_FOLDER_PREFIX))
            return deviceQueue(key)?.map { localItem(key, it) }
        }
        return when (parentId) {
            ROOT_ID -> listOf(
                playable(
                    WAVE_ID, context.getString(R.string.auto_my_wave), context.getString(R.string.auto_my_wave_subtitle),
                    artwork = resourceUri(R.drawable.ic_notification), mediaType = MediaMetadata.MEDIA_TYPE_RADIO_STATION
                ),
                folder(LIKED_ID, context.getString(R.string.auto_liked), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                folder(DOWNLOADED_ID, context.getString(R.string.auto_downloaded), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                folder(PLAYLISTS_ID, context.getString(R.string.auto_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                folder(RADIO_ID, context.getString(R.string.auto_radio), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .withExtras(Bundle().apply { putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID) })
            )
            LIKED_ID -> libraryRepository.likedTracks.value.map { trackItem(LIKED_PREFIX, it) }
            // With music on the device: what the app downloaded and the device's own, side by side
            DOWNLOADED_ID -> if (localMusic.current().tracks.isEmpty()) {
                downloaded().map { trackItem(DOWNLOADED_PREFIX, it) }
            } else {
                listOf(
                    folder(DOWNLOADED_APP_ID, context.getString(R.string.auto_downloaded_in_app), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    folder(LOCAL_ID, context.getString(R.string.local_title), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                )
            }
            DOWNLOADED_APP_ID -> downloaded().map { trackItem(DOWNLOADED_PREFIX, it) }
            // The device's folders, as on the drive; with one folder, its tracks at once
            LOCAL_ID -> {
                val folders = LocalRules.folders(localMusic.current().tracks)
                if (folders.size > 1) {
                    folders.map { f ->
                        folder(LOCAL_FOLDER_PREFIX + Uri.encode(f.key), f.name ?: sourceLabel(f.source), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    }
                } else {
                    deviceQueue(ALL_DEVICE)?.map { localItem(ALL_DEVICE, it) }
                }
            }
            PLAYLISTS_ID -> playlistsRepository.playlists.value.map { playlist ->
                folder(PLAYLIST_PREFIX + playlist.kindString, playlist.title.orEmpty(), MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .withArtwork(playlist.coverUri.toCoverUrl(CoverSize.Medium))
            }
            RADIO_ID -> radioRepository.getBuiltinStations().map { station ->
                playable(
                    RADIO_PREFIX + station.id, station.name, station.frequency,
                    artwork = station.iconRes?.let { resourceUri(it) } ?: station.logoUrl.takeIf { it.isNotEmpty() }?.let(Uri::parse),
                    mediaType = MediaMetadata.MEDIA_TYPE_RADIO_STATION
                )
            }
            else -> null
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return root()
        val parent = when {
            mediaId.startsWith(PLAYLIST_TRACK_PREFIX) ->
                PLAYLIST_PREFIX + mediaId.removePrefix(PLAYLIST_TRACK_PREFIX).substringBefore('/')
            mediaId.startsWith(PLAYLIST_PREFIX) -> PLAYLISTS_ID
            mediaId.startsWith(LIKED_PREFIX) -> LIKED_ID
            mediaId.startsWith(DOWNLOADED_PREFIX) -> if (localMusic.current().tracks.isEmpty()) DOWNLOADED_ID else DOWNLOADED_APP_ID
            mediaId.startsWith(LOCAL_TRACK_PREFIX) -> mediaId.removePrefix(LOCAL_TRACK_PREFIX).substringBefore('|').let { key ->
                if (key == Uri.encode(ALL_DEVICE)) LOCAL_ID else LOCAL_FOLDER_PREFIX + key
            }
            mediaId.startsWith(LOCAL_FOLDER_PREFIX) -> LOCAL_ID
            mediaId == LOCAL_ID || mediaId == DOWNLOADED_APP_ID -> DOWNLOADED_ID
            mediaId.startsWith(RADIO_PREFIX) -> RADIO_ID
            else -> ROOT_ID
        }
        return children(parent)?.firstOrNull { it.mediaId == mediaId }
    }

    /**
     * Resolves a browse item, or a voice [searchQuery] when [mediaId] is empty, into the action that
     * starts it. The caller runs the action on the following play command, after media3 has moved the
     * service to the foreground (Android 15 denies audio focus otherwise).
     * @return null for an id this tree does not know.
     */
    suspend fun startAction(mediaId: String, searchQuery: String? = null): (() -> Unit)? {
        libraryRepository.loadCached()
        val liked = libraryRepository.likedTracks.value
        return when {
            mediaId.isEmpty() && !searchQuery.isNullOrBlank() -> searchAction(searchQuery.trim())
            mediaId.isEmpty() || mediaId == WAVE_ID -> ({ playbackController.playMyVibe() })
            mediaId.startsWith(LIKED_PREFIX) -> queueAction(mediaId.removePrefix(LIKED_PREFIX), liked, liked)
            mediaId.startsWith(DOWNLOADED_PREFIX) -> queueAction(mediaId.removePrefix(DOWNLOADED_PREFIX), downloaded(), liked)
            mediaId.startsWith(PLAYLIST_TRACK_PREFIX) -> {
                val (kind, trackId) = mediaId.removePrefix(PLAYLIST_TRACK_PREFIX).split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                queueAction(trackId, playlistTracks(kind).orEmpty(), liked)
            }
            mediaId.startsWith(LOCAL_TRACK_PREFIX) -> {
                val (key, trackId) = mediaId.removePrefix(LOCAL_TRACK_PREFIX).split('|', limit = 2).let { Uri.decode(it[0]) to it.getOrElse(1) { "" } }
                val queue = deviceQueue(key).orEmpty()
                if (queue.none { it.idString == trackId }) null else ({ playbackController.playOnDevice(trackId, queue) })
            }
            mediaId.startsWith(RADIO_PREFIX) -> radioRepository.getBuiltinStations()
                .firstOrNull { it.id == mediaId.removePrefix(RADIO_PREFIX) }
                ?.let { station -> { playbackController.playRadioStation(station) } }
            else -> null
        }
    }

    /**
     * "Play <station>" picks the station, anything else what the search thinks was meant (as the car's
     * microphone does); My Wave when nothing matches or there is no connection: something always plays.
     */
    private suspend fun searchAction(query: String): () -> Unit {
        val pick = try {
            voiceSearch.pick(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("MediaLibraryTree", "Voice search failed", e)
            null
        }
        return when (pick) {
            is VoiceSearch.Pick.Station -> ({ playbackController.playRadioStation(pick.station) })
            is VoiceSearch.Pick.Tracks -> ({ playbackController.playTrack(pick.tracks.first().idString, pick.tracks, PlaylistSource.SEARCH) })
            is VoiceSearch.Pick.Offline -> offlineAction(pick.target)
            is VoiceSearch.Pick.Nothing, null -> ({ playbackController.playMyVibe() })
        }
    }

    /** "Включи с флешки", "включи скачанное": what lies there; the wave when nothing does. */
    private suspend fun offlineAction(target: VoicePhrase.Offline): () -> Unit {
        if (target == VoicePhrase.Offline.DOWNLOADED) {
            val downloaded = downloaded()
            val liked = libraryRepository.likedTracks.value
            return downloaded.firstOrNull()?.let { queueAction(it.idString, downloaded, liked) } ?: { playbackController.playMyVibe() }
        }
        val only = when (target) {
            VoicePhrase.Offline.PHONE -> LocalSource.PHONE
            VoicePhrase.Offline.SD -> LocalSource.SD
            VoicePhrase.Offline.FLASH -> LocalSource.FLASH
            else -> null
        }
        val tracks = localMusic.current().tracks.filter { only == null || it.source == only }
        val queue = localMusic.toDtos(LocalRules.folders(tracks).flatMap { it.tracks })
        val first = queue.firstOrNull() ?: return { playbackController.playMyVibe() }
        return { playbackController.playOnDevice(first.idString, queue) }
    }

    /** The device's music in folder order: all of it ([ALL_DEVICE]) or one folder; null for a folder that is gone. */
    private suspend fun deviceQueue(folderKey: String): List<TrackDetailsDto>? {
        val folders = LocalRules.folders(localMusic.current().tracks)
        val tracks = if (folderKey == ALL_DEVICE) folders.flatMap { it.tracks } else folders.firstOrNull { it.key == folderKey }?.tracks ?: return null
        return localMusic.toDtos(tracks)
    }

    /** A file of the device's music; its cover is the app's own and stays in the app, so the list shows none. */
    private fun localItem(folderKey: String, track: TrackDetailsDto): MediaItem = playable(
        LOCAL_TRACK_PREFIX + Uri.encode(folderKey) + "|" + track.idString,
        track.title.orEmpty(),
        track.artists.joinToString(", ") { it.name.orEmpty() },
        artwork = null,
        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC
    )

    private fun sourceLabel(source: LocalSource): String = context.getString(
        when (source) {
            LocalSource.PHONE -> R.string.local_source_phone
            LocalSource.SD -> R.string.local_source_sd
            LocalSource.FLASH -> R.string.local_source_flash
        }
    )

    private fun queueAction(trackId: String, playlist: List<TrackDetailsDto>, liked: List<TrackDetailsDto>): (() -> Unit)? {
        if (playlist.none { it.idString == trackId }) return null
        return {
            playbackController.updateLibrary(liked)
            playbackController.playTrack(trackId, playlist, PlaylistSource.LIBRARY)
        }
    }

    /** Tracks of a playlist (cached per revision); null for an unknown playlist or a failed load. */
    private suspend fun playlistTracks(kind: String): List<TrackDetailsDto>? {
        playlistsRepository.loadCached()
        val playlist = playlistsRepository.playlists.value.firstOrNull { it.kindString == kind } ?: return null
        return try {
            playlistsRepository.tracks(playlist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("MediaLibraryTree", "Playlist $kind failed to load", e)
            null
        }
    }

    /** Everything on the device, as "Скачанные" in the app: liked tracks, then the kept playlists' and albums'. */
    private fun downloaded(): List<TrackDetailsDto> = OfflineRules.downloadedTracks(
        libraryRepository.likedTracks.value, offlineCollections.all.value, downloadManager.downloadedTrackIds.value
    )

    private fun trackItem(prefix: String, track: TrackDetailsDto): MediaItem = playable(
        prefix + track.idString,
        track.title.orEmpty(),
        track.artists.joinToString(", ") { it.name.orEmpty() },
        artwork = track.coverUri.toCoverUrl(CoverSize.Medium)?.let(Uri::parse),
        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC
    )

    private fun folder(id: String, title: String, mediaType: Int): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build()
        )
        .build()

    private fun playable(id: String, title: String, subtitle: String, artwork: Uri?, mediaType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setArtworkUri(artwork)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    private fun MediaItem.withArtwork(url: String?): MediaItem =
        if (url == null) this else buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setArtworkUri(url.toUri()).build()).build()

    private fun MediaItem.withExtras(extras: Bundle): MediaItem =
        buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()

    private fun resourceUri(resId: Int): Uri = "android.resource://${BuildConfig.APPLICATION_ID}/$resId".toUri()

    companion object {
        const val ROOT_ID = "root"
        const val WAVE_ID = "wave"
        const val LIKED_ID = "liked"
        const val DOWNLOADED_ID = "downloaded"
        const val DOWNLOADED_APP_ID = "downloaded_app"
        const val LOCAL_ID = "device"
        private const val LOCAL_FOLDER_PREFIX = "devfolder/"
        private const val LOCAL_TRACK_PREFIX = "devtrack/"
        /** The key of "all the device's music" in a track's id. */
        private const val ALL_DEVICE = "*"
        const val RADIO_ID = "radio"
        const val PLAYLISTS_ID = "playlists"
        private const val PLAYLIST_PREFIX = "playlist/"
        private const val PLAYLIST_TRACK_PREFIX = "pltrack/"
        private const val LIKED_PREFIX = "liked/"
        private const val DOWNLOADED_PREFIX = "downloaded/"
        private const val RADIO_PREFIX = "radio/"

        // Content style keys understood by Android Auto and Automotive OS
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST = 1
        private const val CONTENT_STYLE_GRID = 2
    }
}
