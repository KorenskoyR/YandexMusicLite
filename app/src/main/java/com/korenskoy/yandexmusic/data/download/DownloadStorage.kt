package com.korenskoy.yandexmusic.data.download

import kotlinx.coroutines.flow.first
import android.net.Uri
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager.Place
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where the downloads live and what the removable SD card and a flash drive offer. */
data class StorageStatus(
    val cardAvailable: Boolean = false,
    val onCard: Boolean = false,
    val cardFreeBytes: Long = 0L,
    /** New downloads go to the flash drive (chosen; the drive may be out). */
    val onUsb: Boolean = false,
    /** A folder on a flash drive was picked once. */
    val usbChosen: Boolean = false,
    /** That folder is there now: the drive is in and the permission holds. */
    val usbAvailable: Boolean = false,
    val usbFreeBytes: Long = 0L,
    /** "USB-накопитель › YandexMusicLite". */
    val usbLabel: String? = null,
    /** Where the folder picker opens: a removable drive that is in, if any. */
    val usbPickerStart: Uri? = null,
    /** Moved so far and in all while the downloads move between the places; null otherwise. */
    val moving: Pair<Int, Int>? = null
) {
    val place: Place get() = when {
        onUsb -> Place.USB
        onCard -> Place.CARD
        else -> Place.INTERNAL
    }
}

/** Where the downloads live: the internal storage or, encrypted, the removable SD card or a flash drive. */
@Singleton
class DownloadStorage @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    private val card: CardStorage,
    private val usb: UsbStorage,
    private val downloads: TrackDownloadManager,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(StorageStatus())
    val state: StateFlow<StorageStatus> = _state.asStateFlow()
    private var moveJob: Job? = null

    init {
        scope.launch { combine(card.wanted, usb.wanted, usb.folder) { _, _, _ -> }.collectLatest { refresh() } }
        // A card or a flash drive put in or taken out while the app is open (a head unit never closes it)
        val volumes = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MEDIA_MOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_EJECT)
            addAction(android.content.Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: android.content.Intent) = refresh()
                },
                volumes,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    /** Looks at the card and the flash drive again (put in, taken out) and re-reads what is downloaded. */
    fun refresh() {
        scope.launch {
            val cardUsable = card.cardUsable
            val usbThere = usb.present
            _state.update {
                it.copy(
                    cardAvailable = cardUsable,
                    onCard = card.wanted.value && !usb.wanted.value,
                    cardFreeBytes = if (cardUsable) card.freeBytes() else 0L,
                    onUsb = usb.wanted.value,
                    usbChosen = usb.folder.value != null,
                    usbAvailable = usbThere,
                    usbFreeBytes = if (usbThere) usb.freeBytes() else 0L,
                    usbLabel = if (usbThere) usb.label() else null,
                    usbPickerStart = usb.pickerStart()
                )
            }
            downloads.refresh()
        }
    }

    /** The two-place switch of before. */
    fun moveTo(onCard: Boolean) = moveTo(if (onCard) Place.CARD else Place.INTERNAL)

    /** Switches where new downloads go and moves what is already downloaded there. */
    fun moveTo(place: Place) {
        if (moveJob?.isActive == true) return
        val reachable = when (place) {
            Place.INTERNAL -> true
            Place.CARD -> card.cardUsable
            Place.USB -> usb.present && card.key() != null
        }
        if (!reachable) {
            refresh()
            return
        }
        moveJob = scope.launch {
            choose(place)
            _state.update { it.copy(onCard = place == Place.CARD, onUsb = place == Place.USB, moving = 0 to 0) }
            val done = downloads.moveAll(place) { moved, total -> _state.update { it.copy(moving = moved to total) } }
            // Could not finish (the card or the drive was taken out): new downloads stay in the phone's memory
            if (!done && place != Place.INTERNAL) choose(Place.INTERNAL)
            _state.update { it.copy(moving = null) }
            refresh()
        }
    }

    /** A folder picked in the system window: kept, and the downloads move there. */
    fun useUsbFolder(tree: Uri) {
        scope.launch {
            if (!usb.keep(tree)) return@launch
            settings.setUsbFolder(tree.toString())
            // The folder flow must see the new value before the move checks the drive
            usb.folder.first { it == tree }
            moveTo(Place.USB)
        }
    }

    private suspend fun choose(place: Place) {
        settings.setDownloadsOnUsb(place == Place.USB)
        settings.setDownloadsOnSdCard(place == Place.CARD)
    }
}

/** The storage singletons from outside the graph (the on-device check of a flash drive picked in the system window). */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface StorageAccess {
    fun storage(): DownloadStorage
    fun downloads(): TrackDownloadManager
    fun usb(): UsbStorage
    fun card(): CardStorage
}
