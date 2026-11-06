package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.res.stringResource
import com.korenskoy.yandexmusic.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion

enum class MainTab(@param:androidx.annotation.StringRes val title: Int, val icon: ImageVector, val route: String) {
    HOME(R.string.tab_wave, Icons.Rounded.Waves, "home"),
    SEARCH(R.string.tab_search, Icons.Rounded.Search, "search"),
    LIBRARY(R.string.tab_music, Icons.Rounded.LibraryMusic, "library"),
    RADIO(R.string.tab_radio, Icons.Rounded.Radio, "radio"),
    CAR(R.string.tab_car, Icons.Rounded.DirectionsCar, "car")
}

private val BarShape = RoundedCornerShape(32.dp)
private val IndicatorShape = RoundedCornerShape(27.dp)

private fun Modifier.glassBar(): Modifier = this
    .hifiSoftShadow(BarShape, 18.dp)
    .clip(BarShape)
    .background(HiFi.BarFill)
    .border(1.dp, Color.White.copy(alpha = 0.14f), BarShape)

/**
 * The tab bar: a floating glass capsule with five tabs in the usual order. The light pill under the
 * active tab slides with a spring; the active icon is blue.
 */
@Composable
fun FloatingGlassBottomBar(
    currentRoute: String,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    fixedTabHeight: Dp? = null
) {
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val scaleFactor = if (fixedTabHeight != null) 1f else (windowSize.widthDp / 400f).coerceIn(0.9f, 1.2f)
    val tabs = MainTab.entries
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val tabHeight = fixedTabHeight ?: (54.dp * scaleFactor).coerceIn(50.dp, 62.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassBar()
            .padding(5.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(tabHeight)) {
            val tabWidth = maxWidth / tabs.size
            val indicatorX by animateDpAsState(tabWidth * activeIndex, HiFiMotion.spring(), label = "tab_indicator")
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .width(tabWidth)
                    .height(tabHeight)
                    .clip(IndicatorShape)
                    .background(Color.White.copy(alpha = 0.13f))
            )
            Row(Modifier.fillMaxWidth().height(tabHeight)) {
                tabs.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab.route == currentRoute,
                        scaleFactor = scaleFactor,
                        modifier = Modifier.weight(1f).height(tabHeight),
                        onClick = { onNavigate(tab) }
                    )
                }
            }
        }
    }
}

/**
 * The landscape dock: the same tabs and the mini-player in one row along the bottom. That row is taken
 * by the mini-player anyway, so a page loses no height and gets the whole width. The tabs keep their
 * place when there is no mini-player.
 */
@Composable
fun FloatingGlassDock(
    currentRoute: String,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit
) {
    val widthDp = com.korenskoy.yandexmusic.utils.currentWindowSizeDp().widthDp
    Row(
        modifier = modifier.fillMaxWidth().height(DockHeight),
        horizontalArrangement = Arrangement.spacedBy(DockGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingGlassBottomBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            modifier = Modifier.width(dockTabsWidth(widthDp).dp),
            fixedTabHeight = DockHeight - 10.dp
        )
        Box(Modifier.weight(1f)) { player() }
    }
}

/**
 * Lists fade out before they reach the dock: rows showing through the tabs are hard to read at a glance.
 * Draws only, so touches pass through to the page.
 */
@Composable
fun DockScrim(modifier: Modifier = Modifier) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(navigationBar + DockClearance + 36.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to HiFi.Scrim, 1f to HiFi.Scrim))
    )
}

/** Five roomy tabs on a head unit, tighter on a phone on its side, and never all of a very narrow window. */
internal fun dockTabsWidth(windowWidthDp: Int): Int = when {
    windowWidthDp >= 960 -> 430
    else -> minOf(330, (windowWidthDp * 0.52f).toInt())
}

/** Rows of covers on a side: the wider the screen, the more of them, so a cover stays a cover and not a poster. */
internal fun landscapeColumns(windowWidthDp: Int): Int = (windowWidthDp / 210).coerceIn(4, 6)

val DockHeight = 62.dp
val DockBottomGap = 10.dp
private val DockGap = 12.dp

/** How far a landscape page keeps its content from the bottom: the dock and some air above it. */
val DockClearance = DockHeight + DockBottomGap + 12.dp

@Composable
private fun TabItem(tab: MainTab, selected: Boolean, scaleFactor: Float, modifier: Modifier, onClick: () -> Unit) {
    val iconColor by animateColorAsState(
        if (selected) HiFi.Accent else Color.White.copy(alpha = 0.86f),
        HiFiMotion.standard(),
        label = "tab_icon_${tab.route}"
    )
    val iconSize: Dp = 23.dp * scaleFactor
    Box(
        modifier = modifier
            .clip(IndicatorShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = tab.icon, contentDescription = stringResource(tab.title), tint = iconColor, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tab.title),
                color = if (selected) Color.White else Color.White.copy(alpha = 0.80f),
                fontSize = (10.5f * scaleFactor).sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                // The icon already announces the tab; don't read the label twice
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}
