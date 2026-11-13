package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R

/** The phone and its settings that bear on stops of the music: added to the journal when it is shared. */
object PlaybackEnvironment {

    fun describe(context: Context): String {
        val r = context.resources
        fun onOff(on: Boolean) = r.getString(if (on) R.string.journal_env_on else R.string.journal_env_off)
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val network = r.getString(
            when {
                caps == null -> R.string.journal_env_none
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.journal_env_wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.journal_env_mobile
                else -> R.string.journal_env_other
            }
        )
        val dataSaver = runCatching { cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrNull() == true
        val background = r.getString(if (BackgroundWork.restricted(context)) R.string.journal_env_restricted else R.string.journal_env_allowed)
        return listOf(
            r.getString(R.string.journal_env_device, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
            r.getString(R.string.journal_env_network, network),
            r.getString(R.string.journal_env_power_save, onOff(power?.isPowerSaveMode == true)),
            r.getString(R.string.journal_env_background, background),
            r.getString(R.string.journal_env_data_saver, onOff(dataSaver))
        ).joinToString("\n")
    }
}
