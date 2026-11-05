package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

/**
 * The player's own buttons in the notification shade, on the lock screen and in the car: "Не нравится"
 * left of the transport controls and the heart right of them, as in the app's player. A station gets
 * only the heart (its favourite).
 */
@UnstableApi
internal object SessionButtons {
    // Built on first use: the choice of buttons below stays plain Kotlin (and unit-testable)
    val LIKE: SessionCommand by lazy { SessionCommand("com.korenskoy.yandexmusic.LIKE", Bundle.EMPTY) }
    val DISLIKE: SessionCommand by lazy { SessionCommand("com.korenskoy.yandexmusic.DISLIKE", Bundle.EMPTY) }

    enum class Kind { DISLIKE, LIKE, UNLIKE }

    /** Which buttons to show, left to right. Pure; unit-tested. */
    fun kindsFor(track: TrackDetailsDto?, liked: Boolean): List<Kind> = when {
        track == null -> emptyList()
        track.isRadio -> listOf(if (liked) Kind.UNLIKE else Kind.LIKE)
        else -> listOf(Kind.DISLIKE, if (liked) Kind.UNLIKE else Kind.LIKE)
    }

    fun buttonsFor(context: Context, track: TrackDetailsDto?, liked: Boolean): List<CommandButton> =
        kindsFor(track, liked).map { kind ->
            val station = track?.isRadio == true
            when (kind) {
                Kind.DISLIKE -> CommandButton.Builder(CommandButton.ICON_THUMB_DOWN_UNFILLED)
                    .setCustomIconResId(R.drawable.ic_notification_dislike)
                    .setDisplayName(context.getString(R.string.cd_ne_nravitsya))
                    .setSessionCommand(DISLIKE)
                    .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
                Kind.LIKE, Kind.UNLIKE -> CommandButton.Builder(
                    if (kind == Kind.UNLIKE) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
                )
                    .setCustomIconResId(if (kind == Kind.UNLIKE) R.drawable.ic_notification_liked else R.drawable.ic_notification_like)
                    .setDisplayName(
                        context.getString(
                            when {
                                kind == Kind.UNLIKE && station -> R.string.notification_station_unfavorite
                                kind == Kind.UNLIKE -> R.string.notification_unlike
                                station -> R.string.notification_station_favorite
                                else -> R.string.cd_nravitsya
                            }
                        )
                    )
                    .setSessionCommand(LIKE)
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            }
        }
}
