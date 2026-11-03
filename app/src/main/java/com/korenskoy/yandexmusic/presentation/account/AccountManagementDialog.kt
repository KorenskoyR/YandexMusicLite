package com.korenskoy.yandexmusic.presentation.account

import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder

@Composable
fun AccountManagementDialog(
    accounts: List<UserAccount>,
    activeAccount: UserAccount?,
    defaultAccountUid: String? = null,
    onSetDefaultAccount: (String?) -> Unit = {},
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddNewAccount: () -> Unit,
    onLogoutCurrent: () -> Unit,
    onLogoutAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmLogoutCurrent by remember { mutableStateOf(false) }
    var confirmLogoutAll by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserAccount?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                        )),
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.account_management_upravlenie_akkauntami),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.account_management_pereklyuchenie_profiley_i_nastroyki),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_zakryt),
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accounts List
                    if (accounts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.account_saved_accounts_header, accounts.size),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(accounts, key = { it.uid }) { account ->
                                val isActive = account.uid == activeAccount?.uid
                                val initialLetter = (account.displayName.ifBlank { account.login }.firstOrNull() ?: 'Я')
                                    .uppercaseChar()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isActive) {
                                                // Selected = dark anodized metal (no colour), like the rest of the app
                                                Brush.verticalGradient(listOf(HiFi.AnodizedLight, HiFi.AnodizedDark))
                                            } else {
                                                Brush.horizontalGradient(
                                                    listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))
                                                )
                                            }
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isActive) Color(0x80C9CED8) else Color.White.copy(alpha = 0.08f)
                                            ),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable(enabled = !isActive) {
                                            onSwitchAccount(account.uid)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) {
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
                                                        fontSize = 17.sp
                                                    )
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = initialLetter.toString(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
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

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Default Account Star Toggle
                                    val isDefault = account.uid == defaultAccountUid
                                    IconButton(
                                        onClick = {
                                            if (isDefault) {
                                                onSetDefaultAccount(null)
                                            } else {
                                                onSetDefaultAccount(account.uid)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = stringResource(if (isDefault) R.string.account_is_default else R.string.account_make_default),
                                            tint = if (isDefault) Color.White else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Status Badge or Action
                                    if (isActive) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.account_management_aktiven),
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { accountToDelete = account },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.cd_udalit_profil),
                                                tint = HiFi.Danger.copy(alpha = 0.75f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.account_management_profil_po_umolchaniyu_avto),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // "+ Войти в другой аккаунт" Button
                    Button(
                        onClick = {
                            onAddNewAccount()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.account_management_voyti_v_drugoy_akkaunt),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // "Выйти из этого аккаунта" Button
                    Button(
                        onClick = { confirmLogoutCurrent = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x22FF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0x55FF5252))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = HiFi.Danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.account_management_vyyti_iz_etogo_akkaunta),
                            color = HiFi.Danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (accounts.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { confirmLogoutAll = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.account_management_vyyti_so_vseh_akkauntov),
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation: Logout Current Account
    if (confirmLogoutCurrent) {
        HiFiAlertDialog(
            onDismissRequest = { confirmLogoutCurrent = false },
            title = stringResource(R.string.account_management_vyyti_iz_akkaunta),
            content = { Text(stringResource(R.string.account_management_dannye_i_kesh_tekuschego), color = HiFi.TextSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_vyyti), { confirmLogoutCurrent = false; onLogoutCurrent(); onDismiss() }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { confirmLogoutCurrent = false }, HiFiDialogActionKind.Quiet) }
        )
    }

    // Confirmation: Logout All Accounts
    if (confirmLogoutAll) {
        HiFiAlertDialog(
            onDismissRequest = { confirmLogoutAll = false },
            title = stringResource(R.string.account_management_vyyti_so_vseh_akkauntov_2),
            content = { Text(stringResource(R.string.account_management_vse_sohranennye_uchetnye_zapisi), color = HiFi.TextSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_vyyti_so_vseh), { confirmLogoutAll = false; onLogoutAll(); onDismiss() }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { confirmLogoutAll = false }, HiFiDialogActionKind.Quiet) }
        )
    }

    // Confirmation: Remove Individual Account
    accountToDelete?.let { target ->
        HiFiAlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = stringResource(R.string.account_management_udalit_profil),
            content = { Text(stringResource(R.string.account_remove_confirm, target.displayName.ifBlank { target.login }.ifBlank { target.uid }), color = HiFi.TextSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_udalit), { val uidToRemove = target.uid; accountToDelete = null; onRemoveAccount(uidToRemove) }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { accountToDelete = null }, HiFiDialogActionKind.Quiet) }
        )
    }
}
