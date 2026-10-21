package com.korenskoy.yandexmusic.presentation.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.SearchHistoryRepository
import com.korenskoy.yandexmusic.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The search tab: search as you type, recent searches, and the charts shown before a query. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val historyRepository: SearchHistoryRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _found = MutableStateFlow(SearchRepository.SearchResults())
    /** Artists and albums matching the query (tracks are in [results]). */
    val found: StateFlow<SearchRepository.SearchResults> = _found.asStateFlow()

    private val _results = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val results: StateFlow<List<TrackDetailsDto>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val history: StateFlow<List<String>> = historyRepository.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weeklyHits: StateFlow<List<TrackDetailsDto>> = libraryRepository.weeklyHits
    val trendTracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.trendTracks

    fun loadMoreHits() = libraryRepository.loadMoreHits()

    fun loadMoreTrends() = libraryRepository.loadMoreTrends()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _query.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length > 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(300L) // typing pause
                    _isSearching.value = true
                    val found = searchRepository.search(trimmed)
                    _found.value = found
                    _results.value = found.tracks
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "Search error", e)
                } finally {
                    _isSearching.value = false
                }
            }
        } else {
            _found.value = SearchRepository.SearchResults()
            _results.value = emptyList()
            _isSearching.value = false
        }
    }

    /**
     * Keeps the query in the recent searches. Called when the user commits to it (the keyboard's search
     * key, or opening a result), not on every keystroke — "ра", "рад" are not searches.
     */
    fun rememberQuery() {
        val text = _query.value
        viewModelScope.launch { historyRepository.add(text) }
    }

    fun removeFromHistory(query: String) {
        viewModelScope.launch { historyRepository.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }
}
