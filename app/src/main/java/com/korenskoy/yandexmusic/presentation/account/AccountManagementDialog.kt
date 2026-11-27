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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.korenskoy.yandexmusic.presentation.hifi.AppDialog
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

    val window = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()

    val palette = HiFi.palette
    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(if (window.widthDp < 340) 8.dp else 12.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(
                            listOf(palette.glassBorder, palette.cardBorder)
                        )),
                        RoundedCornerShape(28.dp)
                    ),
                color = HiFiDialogDefaults.Container,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (window.widthDp < 340) 14.dp else 20.dp, vertical = 20.dp)
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
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.account_management_pereklyuchenie_profiley_i_nastroyki),
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(palette.pillFill)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_zakryt),
                                tint = palette.textPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accounts List
                    if (accounts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.account_saved_accounts_header, accounts.size),
                            color = palette.textTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (window.heightDp < 520) 140.dp else 220.dp)
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
                                                Brush.verticalGradient(listOf(palette.anodizedLight, palette.anodizedDark))
                                            } else {
                                                Brush.horizontalGradient(listOf(palette.cardFill, palette.cardFill))
                                            }
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isActive) palette.steelEdge.copy(alpha = 0.55f) else palette.cardBorder
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
                                                        fontSize = 17.sp
                                                    )
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = initialLetter.toString(),
                                                color = palette.onMetal,
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
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = stringResource(if (isDefault) R.string.account_is_default else R.string.account_make_default),
                                            tint = if (isDefault) palette.textPrimary else palette.textTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Status Badge or Action
                                    if (isActive) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(palette.pillFill)
                                                .border(BorderStroke(1.dp, palette.glassBorder), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = palette.textPrimary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.account_management_aktiven),
                                                color = palette.textPrimary.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { accountToDelete = account },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.cd_udalit_profil),
                                                tint = palette.danger.copy(alpha = 0.85f),
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
                            color = palette.textTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val actionFont = if (window.widthDp < 340) 13.sp else 14.sp
                    val actionPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

                    // "+ Войти в другой аккаунт" Button
                    Button(
                        onClick = {
                            onAddNewAccount()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        contentPadding = actionPadding,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.pillFill
                        ),
                        border = BorderStroke(1.dp, palette.glassBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.account_management_voyti_v_drugoy_akkaunt),
                            color = palette.textPrimary,
                            fontSize = actionFont,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // "Выйти из этого аккаунта" Button
                    Button(
                        onClick = { confirmLogoutCurrent = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        contentPadding = actionPadding,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.danger.copy(alpha = if (palette.isDark) 0.14f else 0.10f)
                        ),
                        border = BorderStroke(1.dp, palette.danger.copy(alpha = if (palette.isDark) 0.35f else 0.40f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = palette.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.account_management_vyyti_iz_etogo_akkaunta),
                            color = palette.danger,
                            fontSize = actionFont,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                color = palette.textTertiary,
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
            content = { Text(stringResource(R.string.account_management_dannye_i_kesh_tekuschego), color = palette.textSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_vyyti), { confirmLogoutCurrent = false; onLogoutCurrent(); onDismiss() }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { confirmLogoutCurrent = false }, HiFiDialogActionKind.Quiet) }
        )
    }

    // Confirmation: Logout All Accounts
    if (confirmLogoutAll) {
        HiFiAlertDialog(
            onDismissRequest = { confirmLogoutAll = false },
            title = stringResource(R.string.account_management_vyyti_so_vseh_akkauntov_2),
            content = { Text(stringResource(R.string.account_management_vse_sohranennye_uchetnye_zapisi), color = palette.textSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_vyyti_so_vseh), { confirmLogoutAll = false; onLogoutAll(); onDismiss() }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { confirmLogoutAll = false }, HiFiDialogActionKind.Quiet) }
        )
    }

    // Confirmation: Remove Individual Account
    accountToDelete?.let { target ->
        HiFiAlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = stringResource(R.string.account_management_udalit_profil),
            content = { Text(stringResource(R.string.account_remove_confirm, target.displayName.ifBlank { target.login }.ifBlank { target.uid }), color = palette.textSecondary, fontSize = 15.sp) },
            confirm = { HiFiDialogAction(stringResource(R.string.account_management_udalit), { val uidToRemove = target.uid; accountToDelete = null; onRemoveAccount(uidToRemove) }, HiFiDialogActionKind.Danger) },
            dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), { accountToDelete = null }, HiFiDialogActionKind.Quiet) }
        )
    }
}
