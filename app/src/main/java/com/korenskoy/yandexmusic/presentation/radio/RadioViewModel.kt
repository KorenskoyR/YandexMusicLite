package com.korenskoy.yandexmusic.presentation.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FM stations and favourites (a favourite station is the radio's "like" in the players). */
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val stations: StateFlow<List<RadioStation>> = MutableStateFlow(radioRepository.getBuiltinStations()).asStateFlow()

    val favoriteStationIds: StateFlow<Set<String>> = radioRepository.favoriteStationIds
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun play(station: RadioStation) = playbackController.playRadioStation(station)

    fun toggleFavorite(stationId: String) {
        viewModelScope.launch { radioRepository.toggleFavorite(stationId) }
    }

    fun removeFavorite(stationId: String) {
        viewModelScope.launch { radioRepository.removeFavorite(stationId) }
    }
}
