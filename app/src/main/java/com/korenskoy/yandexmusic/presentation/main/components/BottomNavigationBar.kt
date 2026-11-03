package com.korenskoy.yandexmusic.presentation.main.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion

enum class MainTab(val title: String, val icon: ImageVector, val route: String) {
    HOME("Волна", Icons.Rounded.Waves, "home"),
    SEARCH("Поиск", Icons.Rounded.Search, "search"),
    LIBRARY("Музыка", Icons.Rounded.LibraryMusic, "library"),
    RADIO("Радио", Icons.Rounded.Radio, "radio"),
    CAR("В авто", Icons.Rounded.DirectionsCar, "car")
}

private val BarShape = RoundedCornerShape(32.dp)
private val IndicatorShape = RoundedCornerShape(27.dp)

private fun Modifier.glassBar(): Modifier = this
    .shadow(elevation = 18.dp, shape = BarShape, spotColor = Color(0x99000000), ambientColor = Color(0x55000000))
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
    modifier: Modifier = Modifier
) {
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val scaleFactor = (windowSize.widthDp / 400f).coerceIn(0.9f, 1.2f)
    val tabs = MainTab.entries
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val tabHeight = (54.dp * scaleFactor).coerceIn(50.dp, 62.dp)

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

/** The same tabs as a vertical capsule for landscape screens (phones on their side, tablets). */
@Composable
fun FloatingGlassNavigationRail(
    currentRoute: String,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val scaleFactor = (windowSize.widthDp / 800f).coerceIn(0.9f, 1.3f)
    val railWidth = (88.dp * scaleFactor).coerceIn(80.dp, 106.dp)
    val tabHeight = (62.dp * scaleFactor).coerceIn(56.dp, 70.dp)
    val spacing = 6.dp
    val tabs = MainTab.entries
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val indicatorY by animateDpAsState((tabHeight + spacing) * activeIndex, HiFiMotion.spring(), label = "rail_indicator")

    Box(
        modifier = modifier
            .width(railWidth)
            .glassBar()
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, indicatorY.roundToPx()) }
                .fillMaxWidth()
                .height(tabHeight)
                .clip(IndicatorShape)
                .background(Color.White.copy(alpha = 0.13f))
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab.route == currentRoute,
                    scaleFactor = scaleFactor,
                    modifier = Modifier.fillMaxWidth().height(tabHeight),
                    onClick = { onNavigate(tab) }
                )
            }
        }
    }
}

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
            Icon(imageVector = tab.icon, contentDescription = tab.title, tint = iconColor, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.title,
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
