package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.dto.AccountStatusResponseDto
import com.korenskoy.yandexmusic.data.dto.DownloadInfoResponseDto
import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import com.korenskoy.yandexmusic.data.dto.SearchResponseDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.TracksResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface YandexMusicApi {

    @GET("account/status")
    suspend fun getAccountStatus(): ResponseWrapper<AccountStatusResponseDto>

    @GET("https://login.yandex.ru/info?format=json")
    suspend fun getYandexLoginInfo(): com.korenskoy.yandexmusic.data.dto.YandexLoginInfoDto

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("tracks")
    suspend fun getTracksMetadata(
        @retrofit2.http.Field("track-ids") trackIds: List<String>
    ): ResponseWrapper<List<TrackDetailsDto>>

    @GET("tracks")
    suspend fun getTracksMetadataQuery(
        @retrofit2.http.Query("track-ids") trackIds: String
    ): ResponseWrapper<List<TrackDetailsDto>>

    @GET("users/{userId}/likes/tracks")
    suspend fun getLikedTracks(
        @Path("userId") userId: String,
        @retrofit2.http.Query("rich-tracks") richTracks: Boolean = true
    ): ResponseWrapper<TracksResponseDto>

    @GET("users/{userId}/likes/tracks")
    suspend fun getLikedTracksRaw(
        @Path("userId") userId: String,
        @retrofit2.http.Query("rich-tracks") richTracks: Boolean = false
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @GET("users/{userId}/playlists/{kind}")
    suspend fun getPlaylistRaw(
        @Path("userId") userId: String,
        @Path("kind") kind: String = "3"
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @GET("users/{userId}/playlists/list")
    suspend fun getPlaylistsListRaw(
        @Path("userId") userId: String
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @GET("users/{userId}/playlists/list")
    suspend fun getUserPlaylists(
        @Path("userId") userId: String
    ): ResponseWrapper<List<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto>>

    @GET("users/{userId}/playlists/{kind}")
    suspend fun getUserPlaylist(
        @Path("userId") userId: String,
        @Path("kind") kind: String
    ): ResponseWrapper<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto>

    @GET("tracks/{trackId}/download-info")
    suspend fun getTrackDownloadInfo(
        @Path("trackId") trackId: String
    ): ResponseWrapper<List<DownloadInfoResponseDto>>

    @GET
    suspend fun getRawUrl(@retrofit2.http.Url url: String): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @GET("search")
    suspend fun search(
        @retrofit2.http.Query("text") text: String,
        @retrofit2.http.Query("type") type: String = "all",
        @retrofit2.http.Query("page") page: Int = 0
    ): ResponseWrapper<SearchResponseDto>

    @retrofit2.http.POST("users/{userId}/likes/tracks/add-multiple")
    @retrofit2.http.FormUrlEncoded
    suspend fun likeTrack(
        @Path("userId") userId: String,
        @retrofit2.http.Field("track-ids") trackIds: String
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @retrofit2.http.POST("users/{userId}/likes/tracks/remove")
    @retrofit2.http.FormUrlEncoded
    suspend fun unlikeTrack(
        @Path("userId") userId: String,
        @retrofit2.http.Field("track-ids") trackIds: String
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @retrofit2.http.POST("users/{userId}/dislikes/tracks/add-multiple")
    @retrofit2.http.FormUrlEncoded
    suspend fun dislikeTrack(
        @Path("userId") userId: String,
        @retrofit2.http.Field("track-ids") trackIds: String
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @retrofit2.http.POST("users/{userId}/dislikes/tracks/remove")
    @retrofit2.http.FormUrlEncoded
    suspend fun removeDislikeTrack(
        @Path("userId") userId: String,
        @retrofit2.http.Field("track-ids") trackIds: String
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>

    @GET("rotor/station/user:onyourwave/tracks")
    suspend fun getMyWaveTracks(
        @retrofit2.http.Query("queue") queue: String? = null
    ): ResponseWrapper<com.korenskoy.yandexmusic.data.dto.RotorStationTracksResponseDto>

    @retrofit2.http.POST("rotor/station/user:onyourwave/feedback")
    @retrofit2.http.FormUrlEncoded
    suspend fun sendRotorFeedback(
        @retrofit2.http.Field("type") type: String,
        @retrofit2.http.Field("trackId") trackId: String,
        @retrofit2.http.Field("albumId") albumId: String? = null,
        @retrofit2.http.Field("from") from: String = "radio-user-onyourwave",
        @retrofit2.http.Field("totalPlayedSeconds") totalPlayedSeconds: Double? = null
    ): ResponseWrapper<kotlinx.serialization.json.JsonElement>
}
