package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MainTab(val title: String, val icon: ImageVector, val route: String) {
    HOME("Главная", Icons.Default.Home, "home"),
    SEARCH("Поиск", Icons.Default.Search, "search"),
    LIBRARY("Моя Музыка", Icons.Default.LibraryMusic, "library"),
    RADIO("FM Радио", Icons.Default.Radio, "radio"),
    CAR("В авто", Icons.Default.DirectionsCar, "car")
}

@Composable
fun FloatingGlassBottomBar(
    currentRoute: String,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaleFactor = (configuration.screenWidthDp / 400f).coerceIn(0.9f, 1.2f)

    val tabs = MainTab.entries
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val isRadioSelected = currentRoute == MainTab.RADIO.route

    val outerRadius = 26.dp
    val innerRadius = 20.dp // Concentric alignment: 26dp - 6dp padding = 20dp
    val barPadding = 6.dp
    val tabHeight = (54.dp * scaleFactor).coerceIn(48.dp, 62.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(outerRadius),
                spotColor = Color(0x99000000),
                ambientColor = Color(0x55000000)
            )
            .clip(RoundedCornerShape(outerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE620202A),
                        Color(0xD0161620),
                        Color(0xE613131A)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x4DFFFFFF),
                            Color(0x15FFFFFF),
                            Color(0x33FFFFFF)
                        )
                    )
                ),
                shape = RoundedCornerShape(outerRadius)
            )
            .padding(barPadding)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(tabHeight)
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / tabs.size

            val indicatorOffsetX by animateDpAsState(
                targetValue = tabWidth * activeIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "bottom_sliding_liquid_glass"
            )

            val indicatorBgBrush = if (isRadioSelected) {
                Brush.verticalGradient(listOf(Color(0x45FF9900), Color(0x25FF9900)))
            } else {
                Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x18FFFFFF)))
            }
            val indicatorBorderColor = if (isRadioSelected) Color(0x66FF9900) else Color(0x40FFFFFF)

            // Sliding Liquid Glass Indicator (pure glass + glow border without raster shadow quads)
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .height(tabHeight)
                    .offset(x = indicatorOffsetX)
                    .clip(RoundedCornerShape(innerRadius))
                    .background(indicatorBgBrush)
                    .border(
                        BorderStroke(1.2.dp, indicatorBorderColor),
                        shape = RoundedCornerShape(innerRadius)
                    )
            )

            // Tab Items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tabHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val isRadio = tab == MainTab.RADIO

                    val iconSize by animateDpAsState(
                        targetValue = if (selected) 24.dp * scaleFactor else 21.dp * scaleFactor,
                        animationSpec = spring(dampingRatio = 0.75f),
                        label = "icon_size_bottom_${tab.route}"
                    )

                    val activeIconColor = if (isRadio) Color(0xFFFF9900) else Color(0xFFFFCC00)
                    val activeTextColor = if (isRadio) Color(0xFFFF9900) else Color.White

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(tabHeight)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onNavigate(tab)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (selected) activeIconColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(iconSize)
                            )
                            Spacer(modifier = Modifier.height(3.dp * scaleFactor))
                            BasicText(
                                text = tab.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = if (selected) activeTextColor else Color.White.copy(alpha = 0.5f),
                                    fontSize = (10.5f * scaleFactor).sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    background = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingGlassNavigationRail(
    currentRoute: String,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaleFactor = (configuration.screenWidthDp / 800f).coerceIn(0.9f, 1.3f)
    val capsuleWidth = (92.dp * scaleFactor).coerceIn(84.dp, 110.dp)

    val tabs = MainTab.entries
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val isRadioSelected = currentRoute == MainTab.RADIO.route

    val outerRadius = 26.dp
    val railPadding = 6.dp
    val innerRadius = 20.dp // Concentric alignment: 26dp - 6dp padding = 20dp

    val tabHeight = (64.dp * scaleFactor).coerceIn(58.dp, 72.dp)
    val tabSpacing = (6.dp * scaleFactor).coerceIn(4.dp, 8.dp)

    val indicatorOffsetY by animateDpAsState(
        targetValue = (tabHeight + tabSpacing) * activeIndex,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rail_sliding_liquid_glass"
    )

    val indicatorBgBrush = if (isRadioSelected) {
        Brush.verticalGradient(listOf(Color(0x45FF9900), Color(0x25FF9900)))
    } else {
        Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x18FFFFFF)))
    }
    val indicatorBorderColor = if (isRadioSelected) Color(0x66FF9900) else Color(0x40FFFFFF)

    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(outerRadius),
                spotColor = Color(0x99000000),
                ambientColor = Color(0x55000000)
            )
            .clip(RoundedCornerShape(outerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE620202A),
                        Color(0xD0161620),
                        Color(0xE613131A)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x4DFFFFFF),
                            Color(0x15FFFFFF),
                            Color(0x33FFFFFF)
                        )
                    )
                ),
                shape = RoundedCornerShape(outerRadius)
            )
            .width(capsuleWidth)
            .padding(railPadding)
    ) {
        // Sliding Liquid Glass Indicator (pure glass + glow border without raster shadow quads)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tabHeight)
                .offset(y = indicatorOffsetY)
                .clip(RoundedCornerShape(innerRadius))
                .background(indicatorBgBrush)
                .border(
                    BorderStroke(1.2.dp, indicatorBorderColor),
                    shape = RoundedCornerShape(innerRadius)
                )
        )

        // Tab Items
        Column(
            modifier = Modifier.wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tabSpacing)
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val isRadio = tab == MainTab.RADIO

                val iconSize by animateDpAsState(
                    targetValue = if (selected) 25.dp * scaleFactor else 22.dp * scaleFactor,
                    animationSpec = spring(dampingRatio = 0.75f),
                    label = "icon_size_rail_${tab.route}"
                )

                val activeIconColor = if (isRadio) Color(0xFFFF9900) else Color(0xFFFFCC00)
                val activeTextColor = if (isRadio) Color(0xFFFF9900) else Color.White

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tabHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onNavigate(tab)
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (selected) activeIconColor else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                        BasicText(
                            text = tab.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = if (selected) activeTextColor else Color.White.copy(alpha = 0.5f),
                                fontSize = (10.5f * scaleFactor).sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                background = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
