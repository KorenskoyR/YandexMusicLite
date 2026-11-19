package com.korenskoy.yandexmusic

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * «Голосовой поиск»: an entry with no action to give, for button programs that start an app's screen by its name
 * only (MK+ on the Geely Monjaro cannot pass an action). It brings the app forward listening, exactly as the
 * command [MainActivity.ACTION_START_VOICE] does, and goes away at once. Not in the launcher: only the button
 * programs see it.
 */
class VoiceSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(startVoice(this))
        finish()
        // No flash of an empty screen between the button and the app
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /**
         * The app, brought forward or started, listening at once. With the app already open the intent must reach
         * its window (single top, clear top): a new task flag alone only brought the task forward and the request
         * to listen was lost («Проверить» did nothing while the settings were open).
         */
        fun startVoice(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_VOICE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** The command for button programs that run shell commands (MK+): no action needed. */
        fun adbCommand(context: android.content.Context): String =
            "am start -n ${context.packageName}/${VoiceSearchActivity::class.java.name}"
    }
}
