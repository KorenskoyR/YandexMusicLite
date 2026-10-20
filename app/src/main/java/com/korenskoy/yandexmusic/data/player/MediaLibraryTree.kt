package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.RadioRepository
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
    private val api: YandexMusicApi
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
        return when (parentId) {
            ROOT_ID -> listOf(
                playable(
                    WAVE_ID, context.getString(R.string.auto_my_wave), context.getString(R.string.auto_my_wave_subtitle),
                    artwork = resourceUri(R.drawable.ic_notification), mediaType = MediaMetadata.MEDIA_TYPE_RADIO_STATION
                ),
                folder(LIKED_ID, context.getString(R.string.auto_liked), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                folder(DOWNLOADED_ID, context.getString(R.string.auto_downloaded), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                folder(RADIO_ID, context.getString(R.string.auto_radio), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .withExtras(Bundle().apply { putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID) })
            )
            LIKED_ID -> libraryRepository.likedTracks.value.map { trackItem(LIKED_PREFIX, it) }
            DOWNLOADED_ID -> downloaded().map { trackItem(DOWNLOADED_PREFIX, it) }
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
            mediaId.startsWith(LIKED_PREFIX) -> LIKED_ID
            mediaId.startsWith(DOWNLOADED_PREFIX) -> DOWNLOADED_ID
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
            mediaId.startsWith(RADIO_PREFIX) -> radioRepository.getBuiltinStations()
                .firstOrNull { it.id == mediaId.removePrefix(RADIO_PREFIX) }
                ?.let { station -> { playbackController.playRadioStation(station) } }
            else -> null
        }
    }

    /** "Play <station>" picks the station, anything else the top search results; My Wave when nothing matches. */
    private suspend fun searchAction(query: String): () -> Unit {
        radioRepository.getBuiltinStations().firstOrNull { it.name.contains(query, ignoreCase = true) }?.let { station ->
            return { playbackController.playRadioStation(station) }
        }
        val found = try {
            api.search(query).result?.tracks?.results.orEmpty().distinctBy { it.idString }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MediaLibraryTree", "Voice search failed", e)
            emptyList()
        }
        val first = found.firstOrNull() ?: return { playbackController.playMyVibe() }
        return { playbackController.playTrack(first.idString, found, PlaylistSource.SEARCH) }
    }

    private fun queueAction(trackId: String, playlist: List<TrackDetailsDto>, liked: List<TrackDetailsDto>): (() -> Unit)? {
        if (playlist.none { it.idString == trackId }) return null
        return {
            playbackController.updateLibrary(liked)
            playbackController.playTrack(trackId, playlist, PlaylistSource.LIBRARY)
        }
    }

    private fun downloaded(): List<TrackDetailsDto> =
        libraryRepository.likedTracks.value.filter { downloadManager.isKnownDownloaded(it.idString) }

    private fun trackItem(prefix: String, track: TrackDetailsDto): MediaItem = playable(
        prefix + track.idString,
        track.title.orEmpty(),
        track.artists.joinToString(", ") { it.name.orEmpty() },
        artwork = track.coverUri?.let { uri ->
            Uri.parse(if (uri.startsWith("http")) uri else "https://" + uri.replace("%%", "400x400"))
        },
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

    private fun MediaItem.withExtras(extras: Bundle): MediaItem =
        buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()

    private fun resourceUri(resId: Int): Uri = Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/$resId")

    companion object {
        const val ROOT_ID = "root"
        const val WAVE_ID = "wave"
        const val LIKED_ID = "liked"
        const val DOWNLOADED_ID = "downloaded"
        const val RADIO_ID = "radio"
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
