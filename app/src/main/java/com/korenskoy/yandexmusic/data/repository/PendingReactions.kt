package com.korenskoy.yandexmusic.data.repository

/** A like or dislike that could not reach the server yet. */
data class PendingReaction(val trackId: String, val kind: Kind) {
    enum class Kind { LIKE, UNLIKE, DISLIKE }

    val saved: String get() = "${kind.name}:$trackId"
}

/** The queue of reactions made without a connection. Pure; unit-tested. */
object PendingReactions {
    /** Only the last word about a track counts: a like taken back before it was sent is never sent. */
    fun add(queue: List<PendingReaction>, new: PendingReaction): List<PendingReaction> =
        queue.filterNot { it.trackId == new.trackId } + new

    fun parse(saved: String): PendingReaction? {
        val kind = PendingReaction.Kind.entries.firstOrNull { it.name == saved.substringBefore(':') } ?: return null
        val id = saved.substringAfter(':', "").takeIf { it.isNotEmpty() } ?: return null
        return PendingReaction(id, kind)
    }
}
