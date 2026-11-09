package com.korenskoy.yandexmusic.data.player

import kotlin.math.pow

/**
 * Volume normalization from the loudness the service measures for every track (EBU R128, the "r128"
 * field). Loud masters are brought down to [TARGET_LUFS]; quieter ones are left alone, because raising
 * them would need headroom the track does not have and would clip. Pure; unit-tested.
 */
object Loudness {
    /** About what streaming services aim at; a typical pop master is at -6..-9 LUFS. */
    const val TARGET_LUFS = -14.0
    /** Never more than this much quieter: a wrong measurement must not make a track nearly silent. */
    const val MAX_CUT_DB = -12.0

    /** The volume multiplier (1 = unchanged) for a track measured at [integratedLufs]; null loudness — unchanged. */
    fun gainFor(integratedLufs: Double?): Float {
        if (integratedLufs == null || integratedLufs.isNaN() || integratedLufs > 0.0 || integratedLufs < -70.0) return 1f
        val cutDb = (TARGET_LUFS - integratedLufs).coerceIn(MAX_CUT_DB, 0.0)
        return 10.0.pow(cutDb / 20.0).toFloat()
    }
}
