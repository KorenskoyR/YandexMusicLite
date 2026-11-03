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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
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

    Dialog(
        onDismissRequest = {}, // Modal startup dialog cannot be dismissed without selecting
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 440.dp)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    ),
                color = HiFiDialogDefaults.Container,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
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
                                    Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.startup_account_picker_vyberite_profil),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.startup_account_picker_vhod_v_yandex_music),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(R.string.startup_account_picker_dostupnye_akkaunty),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Accounts List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
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
                                            // Selected = dark anodized metal, no colour
                                            Brush.verticalGradient(listOf(HiFi.AnodizedLight, HiFi.AnodizedDark))
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f))
                                            )
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0x80C9CED8) else Color.White.copy(alpha = 0.08f)
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
                                        .background(
                                            if (isSelected) {
                                                Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(HiFi.SteelLight, HiFi.SteelDark)
                                                )
                                            }
                                        ),
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
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        )
                                    } else {
                                        Text(
                                            text = initialLetter.toString(),
                                            color = Color.White,
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
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (account.login.isNotBlank() && account.displayName != account.login) {
                                        Text(
                                            text = account.login,
                                            color = Color.White.copy(alpha = 0.45f),
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
                                            .background(HiFi.AnodizedLight)
                                            .border(BorderStroke(1.dp, HiFi.SteelEdge), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
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
                                // Monochrome like the rest of the dialog: a white box with a dark tick
                                checkedColor = Color.White,
                                uncheckedColor = Color.White.copy(alpha = 0.4f),
                                checkmarkColor = HiFi.Ground
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.startup_account_picker_zapomnit_kak_profil_po),
                            color = Color.White.copy(alpha = 0.85f),
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onAddNewAccount() }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.startup_account_picker_novyy),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Confirm & Enter button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                // The Hi-Fi metal capsule: steel rim, anodized face
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(HiFi.MetalEdge))
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(HiFi.KnobFace, center = androidx.compose.ui.geometry.Offset.Zero, radius = 900f))
                                .clickable(enabled = selectedUid.isNotBlank()) {
                                    onSelectAccount(selectedUid, rememberAsDefault)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.startup_account_picker_voyti),
                                color = Color.White,
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
