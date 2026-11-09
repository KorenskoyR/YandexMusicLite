package com.korenskoy.yandexmusic.data.download

import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where the downloads live and what the removable SD card offers. */
data class StorageStatus(
    val cardAvailable: Boolean = false,
    val onCard: Boolean = false,
    val cardFreeBytes: Long = 0L,
    /** Moved so far and in all while the downloads move between the two places; null otherwise. */
    val moving: Pair<Int, Int>? = null
)

/** Where the downloads live: the internal storage or, encrypted, the removable SD card. */
@Singleton
class DownloadStorage @Inject constructor(
    private val card: CardStorage,
    private val downloads: TrackDownloadManager,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(StorageStatus())
    val state: StateFlow<StorageStatus> = _state.asStateFlow()
    private var moveJob: Job? = null

    init {
        scope.launch { card.wanted.collectLatest { refresh() } }
    }

    /** Looks at the card again (put in, taken out) and re-reads what is downloaded. */
    fun refresh() {
        scope.launch {
            val usable = card.cardUsable
            _state.update { it.copy(cardAvailable = usable, onCard = card.wanted.value, cardFreeBytes = if (usable) card.freeBytes() else 0L) }
            downloads.refresh()
        }
    }

    /** Switches where new downloads go and moves what is already downloaded there. */
    fun moveTo(onCard: Boolean) {
        if (moveJob?.isActive == true) return
        if (onCard && !card.cardUsable) {
            refresh()
            return
        }
        moveJob = scope.launch {
            settings.setDownloadsOnSdCard(onCard)
            _state.update { it.copy(onCard = onCard, moving = 0 to 0) }
            val done = downloads.moveAll(onCard) { moved, total -> _state.update { it.copy(moving = moved to total) } }
            // Could not finish (the card was taken out): new downloads stay where the tracks are
            if (!done && onCard) settings.setDownloadsOnSdCard(false)
            _state.update { it.copy(moving = null) }
            refresh()
        }
    }
}
