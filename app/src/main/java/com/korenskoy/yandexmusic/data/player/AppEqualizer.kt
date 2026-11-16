package com.korenskoy.yandexmusic.data.player

import android.content.Context
import androidx.core.content.edit
import com.korenskoy.yandexmusic.data.player.eq.EqDesign
import com.korenskoy.yandexmusic.data.player.eq.EqSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's own equalizer: what is set, the ready-made and the user's own settings. The sound itself is shaped by
 * [com.korenskoy.yandexmusic.data.player.eq.EqualizerProcessor] inside both players, reading [current]. It works on any
 * device; the system's equalizer (when the device has one) is offered inside, second.
 */
@Singleton
class AppEqualizer @Inject constructor(
    @ApplicationContext context: Context
) {
    @Serializable
    data class Preset(
        val id: String,
        val name: String,
        val gains: List<Float>,
        val bass: Float = 0f,
        val treble: Float = 0f,
        val custom: Boolean = false
    )

    data class State(
        val settings: EqSettings = EqSettings(),
        /** The setting chosen, while nothing was moved after it; null — the user's own curve. */
        val presetId: String? = FLAT_ID,
        val presets: List<Preset> = BUILT_IN,
        /** The device's own equalizer can be opened (offered inside, second). */
        val systemAvailable: Boolean = false,
        /** The device's equalizer is an app of its own: its name. */
        val systemName: String? = null,
        /** "Без — сравнить" held: the music without the equalizer for now (not kept). */
        val comparing: Boolean = false
    ) {
        val preset: Preset? get() = presets.firstOrNull { it.id == presetId }
    }

    @Serializable
    private data class Saved(
        val enabled: Boolean = false,
        val gains: List<Float> = List(EqDesign.BANDS.size) { 0f },
        val bass: Float = 0f,
        val treble: Float = 0f,
        val preamp: Float = 0f,
        val noClipping: Boolean = true,
        val presetId: String? = FLAT_ID,
        val custom: List<Preset> = emptyList()
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * What the players' processors read at each buffer (any thread). The same object until something changes:
     * a processor recomputes its filters only for a new one.
     */
    @Volatile
    var current: EqSettings = effective(_state.value)
        private set

    private fun effective(s: State): EqSettings = if (s.comparing) s.settings.copy(enabled = false) else s.settings

    private fun publish(next: State) {
        _state.value = next
        current = effective(next)
    }

    private fun load(): State {
        val saved = prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()
        val gains = saved.gains.takeIf { it.size == EqDesign.BANDS.size }?.map(::clamp) ?: List(EqDesign.BANDS.size) { 0f }
        return State(
            settings = EqSettings(saved.enabled, gains, clamp(saved.bass), clamp(saved.treble), saved.preamp.coerceIn(-EqDesign.MAX_DB, MAX_PREAMP), saved.noClipping),
            presetId = saved.presetId,
            presets = BUILT_IN + saved.custom.map { it.copy(custom = true) }
        )
    }

    /** "Без — сравнить": held, the music plays without the equalizer. */
    @Synchronized
    fun setComparing(on: Boolean) {
        if (_state.value.comparing != on) publish(_state.value.copy(comparing = on))
    }

    @Synchronized
    fun setEnabled(on: Boolean) = change { it.copy(settings = it.settings.copy(enabled = on)) }

    /** A slider moved: the curve is the user's own from now. */
    @Synchronized
    fun setGain(band: Int, db: Float) = change { s ->
        if (band !in s.settings.gains.indices) s
        else s.copy(settings = s.settings.copy(gains = s.settings.gains.toMutableList().also { it[band] = clamp(db) }), presetId = null)
    }

    @Synchronized
    fun setBass(db: Float) = change { it.copy(settings = it.settings.copy(bass = clamp(db)), presetId = null) }

    @Synchronized
    fun setTreble(db: Float) = change { it.copy(settings = it.settings.copy(treble = clamp(db)), presetId = null) }

    /** The preamp set by hand: the automatic one is off from now. */
    @Synchronized
    fun setPreamp(db: Float) = change { it.copy(settings = it.settings.copy(preamp = db.coerceIn(-EqDesign.MAX_DB, MAX_PREAMP), noClipping = false)) }

    @Synchronized
    fun setNoClipping(on: Boolean) = change { it.copy(settings = it.settings.copy(noClipping = on)) }

    /** A setting picked: its curve, and the equalizer on (picking one means wanting to hear it). */
    @Synchronized
    fun usePreset(id: String) = change { s ->
        val p = s.presets.firstOrNull { it.id == id } ?: return@change s
        s.copy(settings = s.settings.copy(enabled = true, gains = p.gains.map(::clamp), bass = p.bass, treble = p.treble), presetId = p.id)
    }

    /** The curve as it is now, kept under [name]; picked at once. */
    @Synchronized
    fun saveCustom(name: String) = change { s ->
        val preset = Preset("custom_" + UUID.randomUUID().toString().take(8), name.trim().ifEmpty { DEFAULT_CUSTOM_NAME }, s.settings.gains, s.settings.bass, s.settings.treble, custom = true)
        s.copy(presets = s.presets + preset, presetId = preset.id)
    }

    @Synchronized
    fun renameCustom(id: String, name: String) = change { s ->
        s.copy(presets = s.presets.map { if (it.id == id && it.custom && name.isNotBlank()) it.copy(name = name.trim()) else it })
    }

    @Synchronized
    fun deleteCustom(id: String) = change { s ->
        s.copy(presets = s.presets.filterNot { it.id == id && it.custom }, presetId = s.presetId.takeIf { it != id })
    }

    /** Every band, the shelves and the preamp back to zero: the music as it is. */
    @Synchronized
    fun reset() = change {
        it.copy(settings = it.settings.copy(gains = List(EqDesign.BANDS.size) { 0f }, bass = 0f, treble = 0f, preamp = 0f, noClipping = true), presetId = FLAT_ID)
    }

    private fun change(edit: (State) -> State) {
        val next = edit(_state.value)
        if (next == _state.value) return
        publish(next)
        val s = next.settings
        val saved = Saved(s.enabled, s.gains, s.bass, s.treble, s.preamp, s.noClipping, next.presetId, next.presets.filter { it.custom })
        prefs.edit { putString(KEY, json.encodeToString(Saved.serializer(), saved)) }
    }

    companion object {
        private const val PREFS = "app_equalizer"
        private const val KEY = "settings_v2"
        private const val MAX_PREAMP = 6f
        private const val DEFAULT_CUSTOM_NAME = "Моя"
        const val FLAT_ID = "flat"

        private fun clamp(db: Float) = db.coerceIn(-EqDesign.MAX_DB, EqDesign.MAX_DB)

        /** Ready-made curves (31 Hz … 16 kHz), mild by design: a car's speakers and a phone's both take them. */
        val BUILT_IN = listOf(
            Preset(FLAT_ID, "Ровный", List(10) { 0f }),
            Preset("rock", "Рок", listOf(4f, 3.5f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4.5f)),
            Preset("pop", "Поп", listOf(-1f, 0f, 2f, 3.5f, 4f, 3f, 1.5f, 0f, -0.5f, -1f)),
            Preset("bass", "Басы", listOf(7f, 6f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
            Preset("v_shape", "Басы и верха", listOf(6f, 5f, 3f, 0.5f, -1.5f, -1.5f, 0f, 2.5f, 4.5f, 5.5f)),
            Preset("vocal", "Вокал", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3.5f, 2f, 0.5f, -1f)),
            Preset("electronic", "Электроника", listOf(5f, 4.5f, 1.5f, 0f, -1.5f, 1f, 0.5f, 1.5f, 4f, 5f)),
            Preset("hiphop", "Хип-хоп", listOf(6f, 5f, 2f, 3f, -1f, -1f, 1f, -0.5f, 1.5f, 2.5f)),
            Preset("jazz", "Джаз", listOf(3f, 2f, 1f, 2f, -1.5f, -1.5f, 0f, 1.5f, 3f, 3.5f)),
            Preset("classical", "Классика", listOf(4f, 3f, 2.5f, 1.5f, -1f, -1f, 0f, 2f, 3f, 3.5f)),
            Preset("acoustic", "Акустика", listOf(3.5f, 3f, 2.5f, 1f, 1.5f, 1.5f, 2.5f, 3f, 2.5f, 1.5f)),
            Preset("car", "В машине", listOf(3f, 2.5f, 1f, -0.5f, -1.5f, 0f, 1.5f, 2.5f, 2f, 1f)),
            Preset("softer_top", "Мягче верха", listOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -3f, -5f, -7f))
        )
    }
}
