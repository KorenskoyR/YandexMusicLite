package com.korenskoy.yandexmusic.presentation.account

import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.korenskoy.yandexmusic.presentation.hifi.AppDialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import com.korenskoy.yandexmusic.domain.model.UserAccount

@Composable
fun StartupAccountPickerDialog(
    accounts: List<UserAccount>,
    activeAccount: UserAccount?,
    onSelectAccount: (uid: String, rememberAsDefault: Boolean) -> Unit,
    onAddNewAccount: () -> Unit
) {
    var selectedUid by remember(activeAccount, accounts) {
        mutableStateOf(activeAccount?.uid ?: accounts.firstOrNull()?.uid ?: "")
    }
    var rememberAsDefault by remember { mutableStateOf(false) }

    val window = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()

    AppDialog(
        onDismissRequest = {}, // Modal startup dialog cannot be dismissed without selecting
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(HiFi.palette.glassBorder, HiFi.palette.cardBorder)
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    ),
                color = HiFiDialogDefaults.Container,
                tonalElevation = 0.dp
            ) {
                val palette = HiFi.palette
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(palette.steelLight, palette.steelDark))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = palette.onMetal,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.startup_account_picker_vyberite_profil),
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.startup_account_picker_vhod_v_yandex_music),
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(R.string.startup_account_picker_dostupnye_akkaunty),
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Accounts List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (window.heightDp < 520) 140.dp else 220.dp)
                    ) {
                        items(accounts, key = { it.uid }) { account ->
                            val isSelected = account.uid == selectedUid
                            val initialLetter = (account.displayName.ifBlank { account.login }.firstOrNull() ?: 'Я')
                                .uppercaseChar()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.verticalGradient(listOf(palette.anodizedLight, palette.anodizedDark))
                                        } else {
                                            Brush.horizontalGradient(listOf(palette.cardFill, palette.cardFill))
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) palette.steelEdge.copy(alpha = 0.55f) else palette.cardBorder
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedUid = account.uid }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(palette.steelLight, palette.steelDark))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!account.avatarUrl.isNullOrBlank()) {
                                        SubcomposeAsyncImage(
                                            model = account.avatarUrl,
                                            contentDescription = stringResource(R.string.cd_avatar),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            error = {
                                                Text(
                                                    text = initialLetter.toString(),
                                                    color = palette.onMetal,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        )
                                    } else {
                                        Text(
                                            text = initialLetter.toString(),
                                            color = palette.onMetal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Name and login
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.displayName.ifBlank { account.login }.ifBlank { "UID: ${account.uid}" },
                                        color = palette.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (account.login.isNotBlank() && account.displayName != account.login) {
                                        Text(
                                            text = account.login,
                                            color = palette.textTertiary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(palette.anodizedLight)
                                            .border(BorderStroke(1.dp, palette.steelEdge), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = palette.textPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Remember as default checkbox row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { rememberAsDefault = !rememberAsDefault }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberAsDefault,
                            onCheckedChange = { rememberAsDefault = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.textPrimary,
                                uncheckedColor = palette.textTertiary,
                                checkmarkColor = palette.ground
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.startup_account_picker_zapomnit_kak_profil_po),
                            color = palette.textPrimary.copy(alpha = 0.85f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add account button
                        Row(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.pillFill)
                                .clickable { onAddNewAccount() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = palette.textPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.startup_account_picker_novyy),
                                color = palette.textPrimary.copy(alpha = 0.8f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Confirm & Enter button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(palette.metalEdge))
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(palette.knobFace, center = androidx.compose.ui.geometry.Offset.Zero, radius = 900f))
                                .clickable(enabled = selectedUid.isNotBlank()) {
                                    onSelectAccount(selectedUid, rememberAsDefault)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.startup_account_picker_voyti),
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
