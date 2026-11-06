package com.korenskoy.yandexmusic.data.player

import android.media.MediaCodecList
import android.media.MediaFormat
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Compatible" sound: some old head units play a FLAC track in silence, with no error anywhere, so the app
 * cannot notice by itself and the user chooses. In this mode everything goes through the ordinary stream.
 * Nothing chosen yet: compatible only on a device without a FLAC decoder.
 */
@Singleton
class SoundMode(
    private val chosen: Flow<Boolean?>,
    private val save: suspend (Boolean) -> Unit,
    private val hasFlacDecoder: () -> Boolean
) {
    @Inject
    constructor(settings: SettingsRepository) : this(settings.compatibleSound, settings::setCompatibleSound, ::deviceHasFlacDecoder)

    private val decoderPresent by lazy(hasFlacDecoder)

    private fun isCompatible(choice: Boolean?): Boolean = choice ?: !decoderPresent

    /** For the main thread. False for the moment it takes the saved choice to load. */
    val compatible: StateFlow<Boolean> = chosen.map(::isCompatible)
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, false)

    /** Always the saved choice: a request must not slip through while it loads. */
    suspend fun isCompatible(): Boolean = isCompatible(chosen.first())

    /** Returns once [compatible] says so too, so what happens next already sees the new mode. */
    suspend fun choose(enabled: Boolean) {
        save(enabled)
        compatible.first { it == enabled }
    }
}

/** A device that cannot answer keeps playing as it did. */
internal fun deviceHasFlacDecoder(): Boolean = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS)
        .findDecoderForFormat(MediaFormat.createAudioFormat("audio/flac", 44_100, 2)) != null
}.getOrDefault(true)
