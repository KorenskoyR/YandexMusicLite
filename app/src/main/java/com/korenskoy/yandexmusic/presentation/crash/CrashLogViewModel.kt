package com.korenskoy.yandexmusic.presentation.crash

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.crash.CrashLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The crash journal in the settings: what is recorded, sending it (the user picks how), clearing it. */
@HiltViewModel
class CrashLogViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _reports = MutableStateFlow<List<CrashLog.Report>>(emptyList())
    val reports: StateFlow<List<CrashLog.Report>> = _reports.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _reports.value = withContext(Dispatchers.IO) { CrashLog.reports(context) } }
    }

    /** The system share sheet with the reports as text: e-mail, a messenger — whatever the user chooses. */
    fun send() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { CrashLog.fullText(context) }
            if (text.isBlank()) return@launch
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_log_subject, BuildConfig.VERSION_NAME))
                .putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.crash_log_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CrashLog.clear(context) }
            _reports.value = emptyList()
        }
    }
}
