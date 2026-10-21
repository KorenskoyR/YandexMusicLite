package com.korenskoy.yandexmusic.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResponseWrapper<T>(
    @SerialName("invocationInfo") val invocationInfo: InvocationInfoDto? = null,
    @SerialName("error") val error: ErrorDto? = null,
    @SerialName("result") val result: T? = null
)

@Serializable
data class InvocationInfoDto(
    @SerialName("req-id") val reqId: String? = null,
    @SerialName("hostname") val hostname: String? = null
)

@Serializable
data class ErrorDto(
    @SerialName("name") val name: String,
    @SerialName("message") val message: String
)

@Serializable
data class AccountStatusResponseDto(
    @SerialName("account") val account: AccountDto? = null,
    @SerialName("uid") val directUid: JsonElement? = null,
    @SerialName("default_avatar_id") val defaultAvatarId: String? = null
) {
    val uidLong: Long?
        get() = account?.uidLong ?: when (directUid) {
            is kotlinx.serialization.json.JsonPrimitive -> directUid.content.toLongOrNull()
            else -> null
        }
    val uidString: String
        get() = account?.uidString?.takeIf { it.isNotEmpty() } ?: when (directUid) {
            is kotlinx.serialization.json.JsonPrimitive -> directUid.content
            null -> ""
            else -> directUid.toString()
        }
    val displayUserName: String
        get() = account?.displayUserName ?: if (uidString.isNotEmpty()) "UID: $uidString" else "Яндекс Аккаунт"

    val avatarUrl: String?
        get() = account?.avatarUrl ?: defaultAvatarId?.takeIf { it.isNotBlank() && it != "0/0-0" }?.let {
            "https://avatars.yandex.net/get-yapic/$it/islands-200"
        }
}

@Serializable
data class AccountDto(
    @SerialName("uid") val uid: JsonElement? = null,
    @SerialName("login") val login: String? = null,
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("firstName") val firstName: String? = null,
    @SerialName("secondName") val secondName: String? = null,
    @SerialName("default_avatar_id") val defaultAvatarId: String? = null,
    @SerialName("defaultAvatarId") val defaultAvatarIdCamel: String? = null,
    @SerialName("avatarUrl") val directAvatarUrl: String? = null
) {
    val uidLong: Long?
        get() = when (uid) {
            is kotlinx.serialization.json.JsonPrimitive -> uid.content.toLongOrNull()
            else -> null
        }
    val uidString: String
        get() = when (uid) {
            is kotlinx.serialization.json.JsonPrimitive -> uid.content
            null -> ""
            else -> uid.toString()
        }
    val displayUserName: String
        get() {
            if (!displayName.isNullOrBlank()) return displayName
            if (!fullName.isNullOrBlank()) return fullName
            val combinedName = listOfNotNull(firstName, secondName).joinToString(" ").trim()
            if (combinedName.isNotBlank()) return combinedName
            if (!login.isNullOrBlank()) return login
            return if (uidString.isNotEmpty()) "UID: $uidString" else "Яндекс Аккаунт"
        }

    val avatarId: String?
        get() = defaultAvatarId?.takeIf { it.isNotBlank() && it != "0/0-0" }
            ?: defaultAvatarIdCamel?.takeIf { it.isNotBlank() && it != "0/0-0" }

    val avatarUrl: String?
        get() = directAvatarUrl?.takeIf { it.isNotBlank() }
            ?: avatarId?.let { "https://avatars.yandex.net/get-yapic/$it/islands-200" }
}

@Serializable
data class YandexLoginInfoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("login") val login: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("real_name") val realName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("default_avatar_id") val defaultAvatarId: String? = null,
    @SerialName("is_avatar_empty") val isAvatarEmpty: Boolean? = null
) {
    val avatarUrl: String?
        get() = if (isAvatarEmpty != true && !defaultAvatarId.isNullOrBlank() && defaultAvatarId != "0/0-0") {
            "https://avatars.yandex.net/get-yapic/$defaultAvatarId/islands-200"
        } else null
}

@Serializable
data class TrackDetailsDto(
    @SerialName("id") val id: JsonElement,
    @SerialName("title") val title: String? = null,
    @SerialName("artists") val artists: List<ArtistDto> = emptyList(),
    @SerialName("coverUri") val coverUri: String? = null,
    @SerialName("durationMs") val durationMs: Long? = null,
    @kotlinx.serialization.Transient val isRadio: Boolean = false,
    @SerialName("albums") val albums: List<TrackAlbumDto> = emptyList()
) {
    val idString: String
        get() = if (id is kotlinx.serialization.json.JsonPrimitive) id.content else id.toString()

    /** Playlist edits address a track by the id + album pair. */
    val albumId: String?
        get() = (albums.firstOrNull()?.id as? kotlinx.serialization.json.JsonPrimitive)?.content
}

@Serializable
data class TrackAlbumDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("title") val title: String? = null
)

/** A user's playlist from `users/{uid}/playlists/list` (without tracks) or `playlists/{kind}` (with tracks). */
@Serializable
data class UserPlaylistDto(
    @SerialName("kind") val kind: JsonElement? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("revision") val revision: Int = 0,
    @SerialName("cover") val cover: PlaylistCoverDto? = null,
    @SerialName("ogImage") val ogImage: String? = null,
    @SerialName("visibility") val visibility: String? = null,
    @SerialName("tracks") val tracks: List<PlaylistTrackDto> = emptyList()
) {
    val isPublic: Boolean
        get() = visibility == "public"

    val kindString: String
        get() = (kind as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

    /** Cover template ("avatars.yandex.net/...%%"): the custom/first-track cover, else the mosaic's first tile. */
    val coverUri: String?
        get() = cover?.uri ?: cover?.itemsUri?.firstOrNull() ?: ogImage
}

@Serializable
data class PlaylistCoverDto(
    @SerialName("uri") val uri: String? = null,
    @SerialName("itemsUri") val itemsUri: List<String> = emptyList()
)

@Serializable
data class PlaylistTrackDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("albumId") val albumId: JsonElement? = null,
    @SerialName("track") val track: TrackDetailsDto? = null
) {
    val trackId: String?
        get() = track?.idString ?: (id as? kotlinx.serialization.json.JsonPrimitive)?.content

    val albumIdString: String?
        get() = (albumId as? kotlinx.serialization.json.JsonPrimitive)?.content ?: track?.albumId
}

@Serializable
data class ArtistDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("cover") val cover: PlaylistCoverDto? = null,
    @SerialName("ogImage") val ogImage: String? = null
) {
    val idString: String?
        get() = (id as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it != "0" }

    val coverUri: String?
        get() = cover?.uri ?: ogImage
}

/** An album page: `albums/{id}/with-tracks` (tracks in volumes, i.e. discs), or an album in a list. */
@Serializable
data class AlbumDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("coverUri") val coverUri: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("artists") val artists: List<ArtistDto> = emptyList(),
    @SerialName("volumes") val volumes: List<List<TrackDetailsDto>> = emptyList()
) {
    val idString: String?
        get() = (id as? kotlinx.serialization.json.JsonPrimitive)?.content

    val tracks: List<TrackDetailsDto>
        get() = volumes.flatten()
}

/** `artists/{id}/brief-info`: the artist, popular tracks and albums. */
@Serializable
data class ArtistBriefDto(
    @SerialName("artist") val artist: ArtistDto? = null,
    @SerialName("popularTracks") val popularTracks: List<TrackDetailsDto> = emptyList(),
    @SerialName("albums") val albums: List<AlbumDto> = emptyList()
)

@Serializable
data class TracksResponseDto(
    @SerialName("library") val library: LibraryDto? = null,
    @SerialName("tracks") val directTracks: List<LibraryTrackDto> = emptyList(),
    @SerialName("uid") val uid: JsonElement? = null,
    @SerialName("revision") val revision: Int? = null
) {
    val allTracks: List<LibraryTrackDto>
        get() = library?.tracks?.takeIf { it.isNotEmpty() } ?: directTracks
}

@Serializable
data class LibraryDto(
    @SerialName("uid") val uid: JsonElement? = null,
    @SerialName("revision") val revision: Int? = null,
    @SerialName("tracks") val tracks: List<LibraryTrackDto> = emptyList()
)

@Serializable
data class LibraryTrackDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("trackId") val trackId: JsonElement? = null,
    @SerialName("albumId") val albumId: JsonElement? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("track") val track: TrackDetailsDto? = null
) {
    val idString: String
        get() {
            if (track != null) return track.idString
            val element = id ?: trackId
            return when (element) {
                is kotlinx.serialization.json.JsonPrimitive -> element.content
                null -> ""
                else -> element.toString().trim('"')
            }
        }
}

@Serializable
data class DownloadInfoResponseDto(
    @SerialName("codec") val codec: String,
    @SerialName("bitrateInKbps") val bitrateInKbps: Int,
    @SerialName("gain") val gain: Boolean = false,
    @SerialName("preview") val preview: Boolean = false,
    @SerialName("downloadInfoUrl") val downloadInfoUrl: String,
    @SerialName("direct") val direct: Boolean = false
)

@Serializable
data class SearchResponseDto(
    @SerialName("tracks") val tracks: SearchTracksResultDto? = null,
    @SerialName("artists") val artists: SearchArtistsResultDto? = null,
    @SerialName("albums") val albums: SearchAlbumsResultDto? = null
)

@Serializable
data class SearchArtistsResultDto(
    @SerialName("results") val results: List<ArtistDto> = emptyList()
)

@Serializable
data class SearchAlbumsResultDto(
    @SerialName("results") val results: List<AlbumDto> = emptyList()
)

@Serializable
data class SearchTracksResultDto(
    @SerialName("results") val results: List<TrackDetailsDto> = emptyList()
)

@Serializable
data class RotorStationTracksResponseDto(
    @SerialName("radioIsAvailable") val radioIsAvailable: Boolean? = null,
    @SerialName("sequence") val sequence: List<RotorSequenceItemDto> = emptyList(),
    @SerialName("batchId") val batchId: String? = null
)

@Serializable
data class RotorSequenceItemDto(
    @SerialName("track") val track: TrackDetailsDto? = null,
    @SerialName("liked") val liked: Boolean? = null
)
