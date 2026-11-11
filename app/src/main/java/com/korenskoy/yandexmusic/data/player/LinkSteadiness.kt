package com.korenskoy.yandexmusic.data.player

/**
 * Whether the link is uneven: the music had to wait for data in the middle of a track. One wait longer than
 * [LONG_STALL_MS], or two waits within [WINDOW_MS], make it uneven; [STEADY_MS] without a wait make it steady
 * again. Between "good" and "weak": the server answers, but not evenly enough for a heavy stream.
 * Pure; unit-tested.
 */
internal class LinkSteadiness(private val now: () -> Long) {
    private val stallStarts = ArrayDeque<Long>()
    private var stallSince: Long? = null
    private var lastStallAt: Long? = null

    var uneven: Boolean = false
        private set

    /** The music ran dry mid-track. @return true when this made the link uneven. */
    fun onStall(): Boolean {
        val at = now()
        stallSince = at
        lastStallAt = at
        stallStarts.addLast(at)
        while (stallStarts.isNotEmpty() && at - stallStarts.first() > WINDOW_MS) stallStarts.removeFirst()
        return becomeUnevenIf(stallStarts.size >= 2)
    }

    /** Asked [LONG_STALL_MS] after a stall began. @return true when this made the link uneven. */
    fun onStallLasted(): Boolean {
        val since = stallSince ?: return false
        return becomeUnevenIf(now() - since >= LONG_STALL_MS)
    }

    /** The music is back. @return how long it stood, or null when it was not standing. */
    fun onStallOver(): Long? {
        val since = stallSince ?: return null
        stallSince = null
        val at = now()
        lastStallAt = at
        return at - since
    }

    /** @return true when the link has just become steady again: no wait for [STEADY_MS]. */
    fun checkSteady(): Boolean {
        if (!uneven || stallSince != null) return false
        val last = lastStallAt ?: return false
        if (now() - last < STEADY_MS) return false
        uneven = false
        stallStarts.clear()
        return true
    }

    fun reset() {
        stallStarts.clear()
        stallSince = null
        lastStallAt = null
        uneven = false
    }

    private fun becomeUnevenIf(condition: Boolean): Boolean {
        if (uneven || !condition) return false
        uneven = true
        return true
    }

    companion object {
        const val LONG_STALL_MS = 1_500L
        const val WINDOW_MS = 120_000L
        const val STEADY_MS = 300_000L
    }
}

/** Where a track that is not downloaded comes from while the link is uneven. */
enum class SmoothOrigin { COPY, LIGHT_STREAM, CHOSEN_STREAM }

/** Pure; unit-tested. */
object SmoothPlan {
    /**
     * With "Не прерывать музыку" on and the link uneven, a copy from the cache plays if there is one, else a
     * light ordinary stream; otherwise the stream of the chosen quality, as before.
     */
    fun originFor(uneven: Boolean, smoothOn: Boolean, hasCopy: Boolean): SmoothOrigin = when {
        !uneven || !smoothOn -> SmoothOrigin.CHOSEN_STREAM
        hasCopy -> SmoothOrigin.COPY
        else -> SmoothOrigin.LIGHT_STREAM
    }

    /** A light stream is about this heavy: plenty for a car, a fifth of a lossless one. */
    const val LIGHT_KBPS = 192
}
