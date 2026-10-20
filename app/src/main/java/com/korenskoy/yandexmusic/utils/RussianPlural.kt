package com.korenskoy.yandexmusic.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Russian plural form for [count]. The app's UI is Russian-only, while Android `plurals` follow the
 * device language (an English phone would print "37 радиостанции"), so the rule is applied here.
 */
fun russianPluralIndex(count: Int): Int {
    val n = kotlin.math.abs(count)
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> 0                                   // 1, 21, 101: трек
        mod10 in 2..4 && mod100 !in 12..14 -> 1                           // 2-4, 22-24: трека
        else -> 2                                                          // 0, 5-20, 25-30: треков
    }
}

@Composable
fun russianQuantityString(count: Int, @StringRes one: Int, @StringRes few: Int, @StringRes many: Int): String =
    stringResource(listOf(one, few, many)[russianPluralIndex(count)], count)
