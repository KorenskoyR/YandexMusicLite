package com.korenskoy.yandexmusic.data.player

import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.annotation.SuppressLint

import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.Handler
import kotlinx.coroutines.delay
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import com.korenskoy.yandexmusic.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
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

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var libraryTree: MediaLibraryTree

    @Inject
    lateinit var reactions: TrackReactions

    @Inject
    lateinit var equalizer: SystemEqualizer



    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    @Inject
    lateinit var journal: PlaybackJournal

    /** The sound shared with other apps: the rules are in [FocusPolicy], Android's side is here. */
    private val focus = FocusPolicy(object : FocusPolicy.Actions {
        override fun request(): Int = requestSystemAudioFocus()
        override fun abandon() = abandonSystemAudioFocus()
        override fun wantsToPlay(): Boolean = playerRepository.isPlaying.value || playbackController.isStarting.value
        override fun pause(cause: StopCause) {
            if (cause == StopCause.FOCUS_LOST) {
                playbackController.pause(cause)
            } else {
                // For a while: the track stays loaded and goes on from the same place
                journal.expectPause(cause)
                playerRepository.pause()
            }
            // Who took the sound shows a moment later, when it plays
            serviceScope.launch {
                delay(SOUND_TAKER_DELAY_MS)
                journal.amendLastPause(soundTakenBy())
            }
        }
        override fun resume() = playbackController.play()
        override fun duck(on: Boolean) = playerRepository.setDucked(on)
    })

    /** Item picked in a car/browser; started by the play command that follows the selection. */
    private var pendingStart: (() -> Unit)? = null

    /** The saved session is being prepared for a Play from outside (headset, a head unit at power-on). */
    @Volatile
    private var resumptionPending = false

    private val startGuard = Handler(Looper.getMainLooper())

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        focus.onFocusChange(focusChange)
        // The alert or the call is over: an equalizer that lost the session gets it back now
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) equalizer.announce()
    }

    override fun onCreate() {
        super.onCreate()
        AppPresence.playerServiceRunning = true
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        // Headphones, a speaker or a car coming and going while the music plays: the journal tells when
        runCatching { audioManager?.registerAudioDeviceCallback(outputChanges, Handler(Looper.getMainLooper())) }

        // Channels exist from Android 8; on 7.1 the notification simply has none
        serviceScope.launch { playbackController.listenerPauses.collect { focus.onListenerPause() } }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Yandex Music Lite",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
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
            playerRepository.isPlaying.collect { isPlaying -> focus.onWantsToPlay(isPlaying) }
        }

        // Equalizer apps hear about the session when the sound starts and after a real pause, never per track
        equalizer.keepAnnounced(serviceScope, playerRepository.isPlaying)


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

    /** Android's answer: one of AUDIOFOCUS_REQUEST_*, or [FocusPolicy.UNAVAILABLE] without an AudioManager. */
    private fun requestSystemAudioFocus(): Int {
        val result = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7.1: the request without attributes, the same listener (no delayed gain there)
            try {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            } catch (e: SecurityException) {
                Log.e("MusicService", "Audio focus request failed", e)
                null
            }
        } else try {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                // Ducking is handled by us (both players must duck together)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            focusRequest = request
            audioManager?.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.e("MusicService", "Unexpected error", e)
            null
        }
        return result ?: FocusPolicy.UNAVAILABLE
    }

    /**
     * What took the sound, as far as Android tells another app: a call, a voice chat, a navigator, an assistant,
     * a notification or an alarm, a game; otherwise some other app with sound.
     */
    private fun soundTakenBy(): String {
        val am = audioManager ?: return SoundTaker.APP
        val mode = am.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE) return SoundTaker.CALL
        if (mode == AudioManager.MODE_IN_COMMUNICATION) return SoundTaker.VOICE_CHAT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return SoundTaker.APP
        val usages = runCatching { am.activePlaybackConfigurations.map { it.audioAttributes.usage } }.getOrDefault(emptyList())
        return SoundTaker.byUsages(usages)
    }

    private fun abandonSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            } else {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Unexpected error", e)
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
                    // Everything, plus the heart and "Не нравится" of the notification and the car
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionButtons.LIKE)
                        .add(SessionButtons.LIKED)
                        .add(SessionButtons.DISLIKE)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(commands)
                        .build()
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
                    .setAvailableSessionCommands(SessionCommands.EMPTY)
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

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                val track = playbackController.currentTrack.value
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                val action = customCommand.customAction
                when {
                    // The heart, liked or not: the same as the app's heart
                    SessionButtons.isHeart(action) -> reactions.toggleLike(track)
                    action == SessionButtons.DISLIKE.customAction -> reactions.dislike(track)
                    else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            // Headset/Bluetooth Play or the system media card when the app process is not running
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                isForPlayback: Boolean
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                resumptionPending = true
                serviceScope.launch {
                    val resumable = try {
                        playbackController.prepareResumption(startPlayback = isForPlayback)
                    } finally {
                        resumptionPending = false
                    }
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
            showReactionButtons(session)
        } else {
            mediaLibrarySession?.player = forwardingPlayer
        }
    }

    /** The heart and "Не нравится" follow the playing track and its like (from here or from the app). */
    private fun showReactionButtons(session: MediaSession) {
        serviceScope.launch {
            combine(playbackController.currentTrack, reactions.likedTrackIds, reactions.favoriteStationIds) { track, _, _ ->
                SessionButtons.buttonsFor(this@MusicService, track, track != null && reactions.isLiked(track))
            }
                .distinctUntilChanged()
                .collect { session.setMediaButtonPreferences(it) }
        }
    }

    private fun <T : Any> libraryFuture(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        serviceScope.launch {
            future.set(
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MusicService", "Browse request failed", e)
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
                // From outside the app: the notification, the lock screen, a headset, a car; who it was is kept
                playbackController.pause(StopCause.USER_DEVICE, mediaLibrarySession?.controllerForCurrentRequest?.packageName)
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
        // Started for a media button (a head unit sends Play at power-on): Android wants the service in the
        // foreground within seconds, and with nothing to resume the player's notification never comes —
        // "did not then call Service.startForeground()" (Teyes CC3, 1.14.0)
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON && !isPlaybackOngoing) {
            startGuard.removeCallbacksAndMessages(null)
            startGuard.postDelayed(::keepTheForegroundPromise, FOREGROUND_PROMISE_MS)
        }
        return START_NOT_STICKY
    }

    /**
     * The music did not start in time: the service goes to the foreground with a quiet note of its own, so Android
     * does not end the app. Nothing to resume — it leaves at once; still preparing — the note stays until the
     * player's own notification takes over.
     */
    // ServiceCompat passes the type only where Android has it (10+); before that it is ignored
    @SuppressLint("InlinedApi")
    private fun keepTheForegroundPromise() {
        if (isPlaybackOngoing) return
        val note = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_preparing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        val inForeground = runCatching {
            ServiceCompat.startForeground(this, PROMISE_NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }.onFailure { Log.w(TAG, "Could not keep the foreground promise", it) }.isSuccess
        if (inForeground) watchThePromise(PROMISE_WATCH_MS)
        else stopSelf()
    }

    /** Leaves the foreground of the quiet note: to the player's notification, or out when nothing plays. */
    private fun watchThePromise(leftMs: Long) {
        val playerTookOver = isPlaybackOngoing
        val nothingComing = !resumptionPending && !playerRepository.isPlaying.value
        if (playerTookOver || nothingComing || leftMs <= 0) {
            if (playerTookOver) NotificationManagerCompat.from(this).cancel(PROMISE_NOTIFICATION_ID)
            else {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        startGuard.postDelayed({ watchThePromise(leftMs - PROMISE_STEP_MS) }, PROMISE_STEP_MS)
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

    /** The first call lists what is already there: only later changes are news. */
    private var outputsKnown = false

    private val outputChanges = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!outputsKnown) {
                outputsKnown = true
                return
            }
            addedDevices.filter(::isPersonalOutput).forEach { journal.record(PlaybackJournal.Kind.OUTPUT_CHANGED, detail = "подключён " + AudioOutputs.describe(it)) }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            removedDevices.filter(::isPersonalOutput).forEach { journal.record(PlaybackJournal.Kind.OUTPUT_CHANGED, detail = "отключён " + AudioOutputs.describe(it)) }
        }
    }

    /** Headphones, a speaker, a car: not the call channel of the same Bluetooth device. */
    private fun isPersonalOutput(device: AudioDeviceInfo) = AudioOutputs.isExternal(device) && device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    override fun onDestroy() {
        runCatching { audioManager?.unregisterAudioDeviceCallback(outputChanges) }
        startGuard.removeCallbacksAndMessages(null)
        AppPresence.playerServiceRunning = false
        serviceScope.cancel()
        focus.abandon()
        equalizer.close()
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

        private const val SOUND_TAKER_DELAY_MS = 700L

        /** Android ends a service started for the foreground that is not there in 5–10 s: well before that. */
        private const val FOREGROUND_PROMISE_MS = 3_000L
        private const val PROMISE_STEP_MS = 1_000L
        /** A resumption still not done after this is not coming. */
        private const val PROMISE_WATCH_MS = 30_000L
        private const val PROMISE_NOTIFICATION_ID = 7_301
        private const val TAG = "MusicService"
    }
}
