package com.korenskoy.yandexmusic.data.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.14.8, the report: on some phones and head units the heart of the system's music controls stayed empty after
 * a like (it worked: the app's heart turned red). One command with only its picture changed looked like the same
 * button to them; now "liked" and "not liked" are two commands, and both do what the heart always did.
 */
@OptIn(UnstableApi::class)
class HeartButtonTest {

    @Test
    fun likedAndNotLikedAreTwoCommandsSoEveryShadeDrawsTheChange() {
        assertNotEquals(
            SessionButtons.heartActionOf(SessionButtons.Kind.LIKE),
            SessionButtons.heartActionOf(SessionButtons.Kind.UNLIKE)
        )
    }

    @Test
    fun bothHeartsDoWhatTheHeartAlwaysDid() {
        assertTrue(SessionButtons.isHeart(SessionButtons.heartActionOf(SessionButtons.Kind.LIKE)))
        assertTrue(SessionButtons.isHeart(SessionButtons.heartActionOf(SessionButtons.Kind.UNLIKE)))
        assertTrue("the command of earlier versions still works", SessionButtons.isHeart("com.korenskoy.yandexmusic.LIKE"))
        assertFalse("«Не нравится» is not the heart", SessionButtons.isHeart("com.korenskoy.yandexmusic.DISLIKE"))
    }
}
