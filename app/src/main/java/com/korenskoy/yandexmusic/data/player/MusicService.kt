package com.korenskoy.yandexmusic.data.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionError
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var libraryTree: MediaLibraryTree

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var wasPlayingBeforeTransientLoss = false

    /** Item picked in a car/browser; started by the play command that follows the selection. */
    private var pendingStart: (() -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeTransientLoss = false
                playerRepository.setDucked(false)
                playbackController.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call, voice assistant: pause and resume afterwards
                wasPlayingBeforeTransientLoss = playerRepository.isPlaying.value
                playerRepository.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Navigation prompt, notification sound: keep playing quieter, like Apple Music
                playerRepository.setDucked(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                playerRepository.setDucked(false)
                if (wasPlayingBeforeTransientLoss) {
                    wasPlayingBeforeTransientLoss = false
                    playbackController.play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Yandex Music Lite",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления плеера Yandex Music Lite"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PLAYBACK_CHANNEL_ID)
            .setChannelName(com.korenskoy.yandexmusic.R.string.app_name)
            .build()
            .apply { setSmallIcon(com.korenskoy.yandexmusic.R.drawable.ic_notification) }
        setMediaNotificationProvider(notificationProvider)

        setupMediaSession()

        // Track isPlaying via StateFlow to request/abandon system audio focus without dropping it during player swaps
        serviceScope.launch {
            playerRepository.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    if (!hasAudioFocus && !requestSystemAudioFocus()) {
                        // Focus denied (e.g. an active phone call): don't play over it
                        playerRepository.pause()
                    }
                } else if (!wasPlayingBeforeTransientLoss) {
                    abandonSystemAudioFocus()
                }
            }
        }

        // Dynamically update MediaLibrarySession when active player swaps
        serviceScope.launch {
            playerRepository.activePlayerState.collect { activePl ->
                val forwardingPl = createForwardingPlayer(activePl)
                if (mediaLibrarySession == null) {
                    setupMediaSession(forwardingPl)
                } else {
                    mediaLibrarySession?.player = forwardingPl
                }
            }
        }
    }

    /** Returns true when playback may proceed (focus granted now or delayed). */
    private fun requestSystemAudioFocus(): Boolean {
        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    // Ducking is handled by us (both players must duck together)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                focusRequest = request
                audioManager?.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Unexpected error", e)
            null
        }
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasAudioFocus = true
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                // Resumes on AUDIOFOCUS_GAIN
                wasPlayingBeforeTransientLoss = true
                false
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> false
            else -> true // AudioManager unavailable: don't block playback
        }
    }

    private fun abandonSystemAudioFocus() {
        hasAudioFocus = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Unexpected error", e)
        }
    }

    private fun setupMediaSession(forwardingPlayer: ForwardingPlayer = createForwardingPlayer(playerRepository.getPlayer())) {
        // Tapping the notification opens the full-screen player, like Apple Music
        val intent = Intent(this, com.korenskoy.yandexmusic.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.korenskoy.yandexmusic.MainActivity.EXTRA_OPEN_PLAYER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = object : MediaLibrarySession.Callback {
            /**
             * The service is exported, so any installed app may connect. The system, Bluetooth and this
             * app are trusted; other apps (widgets, watch/car launchers) get transport controls only.
             */
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                if (controller.isTrusted || controller.packageName == packageName || controller.packageName in CAR_HOSTS) {
                    return super.onConnect(session, controller)
                }
                val transportOnly = Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TIMELINE
                    )
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailablePlayerCommands(transportOnly)
                    .setAvailableSessionCommands(androidx.media3.session.SessionCommands.EMPTY)
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> =
                Futures.immediateFuture(LibraryResult.ofItem(libraryTree.root(), params))

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
                val children = libraryTree.children(parentId)
                    ?: return@libraryFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val from = (page.toLong() * pageSize).coerceAtMost(children.size.toLong()).toInt()
                val to = (from.toLong() + pageSize).coerceAtMost(children.size.toLong()).toInt()
                LibraryResult.ofItemList(children.subList(from, to), params)
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> = libraryFuture {
                libraryTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

            // A car or another browser picked an item: PlaybackController builds the real queue,
            // the forwarding player ignores the metadata-only items it is then given.
            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                val index = if (startIndex in mediaItems.indices) startIndex else 0
                serviceScope.launch {
                    val picked = mediaItems.getOrNull(index)
                    val start = libraryTree.startAction(picked?.mediaId.orEmpty(), picked?.requestMetadata?.searchQuery)
                    if (start != null) {
                        pendingStart = start
                        future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, index, startPositionMs))
                    } else {
                        future.setException(UnsupportedOperationException("Unknown media item"))
                    }
                }
                return future
            }

            // Headset/Bluetooth Play or the system media card when the app process is not running
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                isForPlayback: Boolean
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    val resumable = playbackController.prepareResumption(startPlayback = isForPlayback)
                    if (resumable == null) {
                        future.setException(UnsupportedOperationException("No saved session to resume"))
                    } else {
                        future.set(MediaSession.MediaItemsWithStartPosition(listOf(resumable.first), 0, resumable.second))
                    }
                }
                return future
            }
        }

        if (mediaLibrarySession == null) {
            val session = MediaLibrarySession.Builder(this, forwardingPlayer, sessionCallback)
                .setSessionActivity(pendingIntent)
                .build()
            mediaLibrarySession = session
            addSession(session)
        } else {
            mediaLibrarySession?.player = forwardingPlayer
        }
    }

    private fun <T : Any> libraryFuture(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        serviceScope.launch {
            future.set(
                try {
                    block()
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Browse request failed", e)
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            )
        }
        return future
    }

    /**
     * Transport commands from the notification, lock screen and headsets go through the
     * [PlaybackController], so they get the same queue, crossfade and error recovery as the app UI.
     */
    private fun createForwardingPlayer(activePlayer: Player): ForwardingPlayer {
        return object : ForwardingPlayer(activePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)

            override fun play() {
                startPendingOrPlay()
            }
            override fun pause() {
                pendingStart = null
                playbackController.pause()
            }
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) startPendingOrPlay() else pause()
            }
            override fun prepare() {
                // A failed/idle stream is re-resolved by the controller on play()
            }
            // The queue belongs to PlaybackController; external controllers (resumption, system UI)
            // must not replace the loaded stream with metadata-only items.
            override fun setMediaItem(mediaItem: MediaItem) = Unit
            override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) = Unit
            override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) = Unit
            override fun setMediaItems(mediaItems: MutableList<MediaItem>) = Unit
            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = Unit
            override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = Unit
            override fun seekTo(positionMs: Long) {
                playbackController.seekTo(positionMs)
            }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                playbackController.seekTo(positionMs)
            }
            override fun seekToNext() {
                playbackController.playNext()
            }
            override fun seekToPrevious() {
                playbackController.playPrev()
            }
            override fun seekToNextMediaItem() {
                seekToNext()
            }
            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }
        }
    }

    private fun startPendingOrPlay() {
        val start = pendingStart
        pendingStart = null
        if (start != null) start() else playbackController.play()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackController.persistNow()
        if (!playerRepository.isPlaying.value) {
            // Paused: remove the notification but keep the session, so the app reopens on the same track.
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        abandonSystemAudioFocus()
        mediaLibrarySession?.run {
            removeSession(this)
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "yandex_music_lite_playback_channel"

        /** Android Auto and the Automotive OS media center: allowed to browse and pick what to play. */
        private val CAR_HOSTS = setOf("com.google.android.projection.gearhead", "com.android.car.media")
    }
}
