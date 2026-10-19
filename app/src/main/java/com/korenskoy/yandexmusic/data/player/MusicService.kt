package com.korenskoy.yandexmusic.data.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
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

@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var playerRepository: PlayerRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var audioManager: android.media.AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var wasPlayingBeforeTransientLoss = false

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeTransientLoss = false
                playerRepository.pause()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                wasPlayingBeforeTransientLoss = playerRepository.isPlaying.value
                playerRepository.pause()
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeTransientLoss) {
                    wasPlayingBeforeTransientLoss = false
                    playerRepository.resume()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "yandex_music_lite_playback_channel",
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

        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(com.korenskoy.yandexmusic.R.drawable.ic_notification)
        }
        setMediaNotificationProvider(notificationProvider)
        
        setupMediaSession()

        // Track isPlaying via StateFlow to request/abandon system audio focus without dropping it during player swaps
        serviceScope.launch {
            playerRepository.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    requestSystemAudioFocus()
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

    private fun requestSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                focusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    audioFocusChangeListener,
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupMediaSession(forwardingPlayer: ForwardingPlayer = createForwardingPlayer(playerRepository.getPlayer())) {
        val intent = Intent(this, com.korenskoy.yandexmusic.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = object : MediaLibrarySession.Callback {
            // Can implement browsing callbacks here for Android Auto / WearOS
        }

        if (mediaLibrarySession == null) {
            mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, sessionCallback)
                .setSessionActivity(pendingIntent)
                .build()
                
            addSession(mediaLibrarySession!!)
        } else {
            mediaLibrarySession?.player = forwardingPlayer
        }
    }
    
    private fun createForwardingPlayer(activePlayer: Player): ForwardingPlayer {
        return object : ForwardingPlayer(activePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            
            override fun seekToNext() {
                playerRepository.triggerSkipNext()
            }
            override fun seekToPrevious() {
                playerRepository.triggerSkipPrev()
            }
            override fun seekToNextMediaItem() {
                seekToNext()
            }
            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isCurrentlyPlaying = playerRepository.isPlaying.value
        if (!isCurrentlyPlaying) {
            playerRepository.stop()
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
}
