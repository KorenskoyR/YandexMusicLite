package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.repository.CatalogRepository
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.data.repository.SearchRepository
import com.korenskoy.yandexmusic.domain.model.RadioStation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What to play for a spoken phrase — the car's microphone, Android Auto and the assistant ("play … on Yandex
 * Music Lite") all ask the same way. A named station plays that station; otherwise the search's best result
 * decides: a track plays first with the other found tracks after it, an artist plays their popular tracks, an
 * album plays the album.
 */
@Singleton
class VoiceSearch @Inject constructor(
    private val search: SearchRepository,
    private val catalog: CatalogRepository,
    private val radio: RadioRepository
) {
    sealed interface Pick {
        /** [query] is what was searched for, the phrase without "включи" and the like. */
        val query: String

        data class Station(override val query: String, val station: RadioStation) : Pick
        data class Tracks(override val query: String, val tracks: List<TrackDetailsDto>) : Pick
        data class Nothing(override val query: String) : Pick
    }

    /** Throws on a network error, so the caller can tell "nothing found" from "no connection". */
    suspend fun pick(phrase: String): Pick {
        val spoken = VoicePhrase.of(phrase)
        val query = spoken.query
        if (query.isBlank()) {
            // Just "включи радио": the radio, as the car's "Радио" button starts it
            val first = if (spoken.wantsRadio) radio.getBuiltinStations().firstOrNull() else null
            return if (first != null) Pick.Station(phrase.trim(), first) else Pick.Nothing(phrase.trim())
        }
        VoicePhrase.station(query, spoken.wantsRadio, radio.getBuiltinStations())?.let { return Pick.Station(query, it) }
        val found = search.search(query)
        val tracks = when (val best = found.best) {
            is SearchRepository.Best.Track -> (listOf(best.track) + found.tracks).distinctBy { it.idString }
            is SearchRepository.Best.Artist -> best.artist.idString
                ?.let { id -> runCatching { catalog.artist(id).popularTracks }.getOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: found.tracks
            is SearchRepository.Best.Album -> best.album.idString
                ?.let { id -> runCatching { catalog.album(id).tracks }.getOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: found.tracks
            null -> found.tracks
        }.filter { !it.isRadio && it.idString.isNotEmpty() }
        return if (tracks.isEmpty()) Pick.Nothing(query) else Pick.Tracks(query, tracks)
    }
}

/** A spoken phrase made into a search: the words of asking dropped. Pure; unit-tested. */
object VoicePhrase {
    data class Spoken(val query: String, val wantsRadio: Boolean)

    /** "включи", "поставь пожалуйста", "найди мне песню" … at the start; "пожалуйста" at the end. */
    private val asking = Regex(
        "^(?:(?:пожалуйста|алиса|окей|ок|слушай|давай|включи(?:те)?|включить|поставь(?:те)?|поставить|найди(?:те)?|найти|" +
            "сыграй(?:те)?|играй|запусти(?:те)?|хочу|послушать|послушаем)[\\s,]+)+",
        RegexOption.IGNORE_CASE
    )
    private val what = Regex("^(?:(?:мне|нам|песню|песни|трек|треки|музыку|альбом|исполнителя|группу|группы)\\s+)+", RegexOption.IGNORE_CASE)
    private val radioWords = Regex("^(?:(?:радио|радиостанцию|станцию|волну)(?:\\s+|$))+", RegexOption.IGNORE_CASE)
    private val trailing = Regex("[\\s,]+пожалуйста$", RegexOption.IGNORE_CASE)

    fun of(phrase: String): Spoken {
        var text = phrase.trim().trimEnd('.', '!', '?', ',').trim()
        text = trailing.replace(text, "")
        text = asking.replace(text, "")
        text = what.replace(text, "")
        val wantsRadio = radioWords.containsMatchIn(text)
        text = radioWords.replace(text, "").trim()
        return Spoken(text.ifEmpty { if (wantsRadio) "" else phrase.trim() }, wantsRadio)
    }

    /** The search text alone: for the search page's field. */
    fun query(phrase: String): String = of(phrase).query.ifBlank { phrase.trim() }

    /**
     * The station a phrase names. Asked for a station ("включи радио Европа Плюс"), a part of its name is
     * enough; otherwise only its whole name, so "включи рок" stays a search and does not tune in to a rock station.
     */
    fun station(query: String, wantsRadio: Boolean, stations: List<RadioStation>): RadioStation? {
        val whole = normalize(query)
        if (whole.isEmpty()) return null
        stations.firstOrNull { normalize(it.name) == whole }?.let { return it }
        if (!wantsRadio) return null
        // "радио Европа": a part of the name, the word "радио" aside ("Новое Радио" counts as "новое" only here)
        val part = withoutRadio(whole)
        if (part.isEmpty()) return null
        return stations.firstOrNull { withoutRadio(normalize(it.name)).contains(part) }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun withoutRadio(text: String): String =
        text.split(' ').filter { it != "радио" && it != "radio" }.joinToString(" ")
}
