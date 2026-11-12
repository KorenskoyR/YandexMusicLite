package com.korenskoy.yandexmusic.data.repository

import com.korenskoy.yandexmusic.domain.model.RadioStation

/** Which station plays: from «Радио» in the car, and on "next" / "previous". Pure; unit-tested. */
object RadioRules {
    /** «Радио»: the station that played last; without one, the first favourite; without favourites, the first of all. */
    fun start(stations: List<RadioStation>, favourites: Set<String>, lastId: String?): RadioStation? =
        stations.firstOrNull { it.id == lastId }
            ?: stations.firstOrNull { it.id in favourites }
            ?: stations.firstOrNull()

    /**
     * "Next" ([step] 1) or "previous" (-1) from [currentId]: among the favourites, like a car radio's presets, when
     * a favourite plays and there are at least two of them; among all the stations otherwise. Round the list.
     */
    fun step(stations: List<RadioStation>, favourites: Set<String>, currentId: String, step: Int): RadioStation? {
        val presets = stations.filter { it.id in favourites }
        val ring = if (currentId in favourites && presets.size >= 2) presets else stations
        val index = ring.indexOfFirst { it.id == currentId }
        if (index == -1 || ring.isEmpty()) return null
        return ring[Math.floorMod(index + step, ring.size)]
    }
}
