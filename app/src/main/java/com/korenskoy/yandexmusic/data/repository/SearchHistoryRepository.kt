package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/** The last searches of each account, newest first; kept on the device only. */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    private fun key(folder: String?) = stringPreferencesKey("history_" + (folder ?: "default"))

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<String>> = authRepository.activeAccount.flatMapLatest { account ->
        context.searchHistoryStore.data.map { prefs -> decode(prefs[key(account?.folderName)]) }
    }

    suspend fun add(query: String) {
        val text = query.trim().takeIf { it.length > 1 } ?: return
        update { list -> (listOf(text) + list.filterNot { it.equals(text, ignoreCase = true) }).take(MAX) }
    }

    suspend fun remove(query: String) = update { list -> list - query }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(change: (List<String>) -> List<String>) {
        val k = key(authRepository.getActiveAccount()?.folderName)
        context.searchHistoryStore.edit { prefs -> prefs[k] = encode(change(decode(prefs[k]))) }
    }

    private fun decode(raw: String?) = raw?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    private fun encode(list: List<String>) = list.joinToString(SEPARATOR)

    private companion object {
        const val MAX = 10
        const val SEPARATOR = "\u001F" // unit separator: never typed into a search box
    }
}
