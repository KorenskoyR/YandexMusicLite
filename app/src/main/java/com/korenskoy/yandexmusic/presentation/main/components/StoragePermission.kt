package com.korenskoy.yandexmusic.presentation.main.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The storage permissions a drive typed by its path needs on this Android, not yet given. */
internal object StoragePermission {
    fun missing(context: Context): List<String> = needed(Build.VERSION.SDK_INT).filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    /** Reading up to Android 12 (later the system no longer has the permission), writing up to Android 10. Pure. */
    fun needed(sdk: Int): List<String> = buildList {
        if (sdk <= Build.VERSION_CODES.S_V2) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (sdk <= Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
