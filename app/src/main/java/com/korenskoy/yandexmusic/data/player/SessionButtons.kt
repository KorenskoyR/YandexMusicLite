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
 *
 * The heart is two commands, [LIKE] while not liked and [LIKED] once liked, both doing the same as the app's
 * heart. As one command with only its picture changed, the shade of some phones and the music widgets of head
 * units took it for the same button and kept drawing the empty heart after a like: a new command is a new button
 * to them, drawn anew. The liked heart is red, as in the app, where the picture is drawn as it is.
 */
@UnstableApi
internal object SessionButtons {
    // Built on first use: the choice of buttons below stays plain Kotlin (and unit-testable)
    val LIKE: SessionCommand by lazy { SessionCommand(LIKE_ACTION, Bundle.EMPTY) }
    val LIKED: SessionCommand by lazy { SessionCommand(LIKED_ACTION, Bundle.EMPTY) }
    val DISLIKE: SessionCommand by lazy { SessionCommand("com.korenskoy.yandexmusic.DISLIKE", Bundle.EMPTY) }

    enum class Kind { DISLIKE, LIKE, UNLIKE }

    /** The command of the heart in each state: never the same for "liked" and "not liked". Pure; unit-tested. */
    fun heartActionOf(kind: Kind): String = if (kind == Kind.UNLIKE) LIKED_ACTION else LIKE_ACTION

    /** A press of the heart, whichever of its two commands: the same as the app's heart. */
    fun isHeart(action: String): Boolean = action == LIKE_ACTION || action == LIKED_ACTION

    const val LIKE_ACTION = "com.korenskoy.yandexmusic.LIKE"
    const val LIKED_ACTION = "com.korenskoy.yandexmusic.LIKED"

    /** Which buttons to show, left to right. Pure; unit-tested. */
    fun kindsFor(track: TrackDetailsDto?, liked: Boolean): List<Kind> = when {
        // The user's own file: nothing to like or dislike in the service
        track == null || track.isLocal -> emptyList()
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
                    .setSessionCommand(if (kind == Kind.UNLIKE) LIKED else LIKE)
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build()
            }
        }
}
