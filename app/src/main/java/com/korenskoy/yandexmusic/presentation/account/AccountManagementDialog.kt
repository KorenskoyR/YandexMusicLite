package com.korenskoy.yandexmusic.presentation.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                        )),
                        RoundedCornerShape(26.dp)
                    ),
                color = Color(0xFF141419),
                tonalElevation = 8.dp
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
                                text = "Управление аккаунтами",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Переключение профилей и настройки входа",
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
                                contentDescription = "Закрыть",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accounts List
                    if (accounts.isNotEmpty()) {
                        Text(
                            text = "СОХРАНЁННЫЕ АККАУНТЫ (${accounts.size})",
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
                                                Brush.horizontalGradient(
                                                    listOf(Color(0x33FF3366), Color(0x18FF9900))
                                                )
                                            } else {
                                                Brush.horizontalGradient(
                                                    listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))
                                                )
                                            }
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isActive) Color(0x66FF3366) else Color.White.copy(alpha = 0.08f)
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
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFFF3366), Color(0xFFFF9900))
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF33333E), Color(0xFF22222A))
                                                    )
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initialLetter.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
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
                                            contentDescription = if (isDefault) "По умолчанию" else "Сделать по умолчанию",
                                            tint = if (isDefault) Color(0xFFFFD700) else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Status Badge or Action
                                    if (isActive) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0x2800E676))
                                                .border(BorderStroke(1.dp, Color(0x6600E676)), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Активен",
                                                color = Color(0xFF00E676),
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
                                                contentDescription = "Удалить профиль",
                                                tint = Color(0xFFFF5252).copy(alpha = 0.75f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "★ — профиль по умолчанию (авто-вход при старте)",
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
                        shape = RoundedCornerShape(14.dp),
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
                            text = "Войти в другой аккаунт",
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x22FF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0x55FF5252))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Выйти из этого аккаунта",
                            color = Color(0xFFFF5252),
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
                                text = "Выйти со всех аккаунтов",
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
        AlertDialog(
            onDismissRequest = { confirmLogoutCurrent = false },
            title = {
                Text(
                    text = "Выйти из аккаунта?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Данные и кэш текущего профиля будут удалены с устройства. Другие сохранённые профили останутся неизменными.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.5.sp
                )
            },
            containerColor = Color(0xFF1E1E26),
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogoutCurrent = false
                        onLogoutCurrent()
                        onDismiss()
                    }
                ) {
                    Text("Выйти", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutCurrent = false }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Confirmation: Logout All Accounts
    if (confirmLogoutAll) {
        AlertDialog(
            onDismissRequest = { confirmLogoutAll = false },
            title = {
                Text(
                    text = "Выйти со всех аккаунтов?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Все сохранённые учётные записи и локальные кэши будут полностью удалены из приложения.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.5.sp
                )
            },
            containerColor = Color(0xFF1E1E26),
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogoutAll = false
                        onLogoutAll()
                        onDismiss()
                    }
                ) {
                    Text("Выйти со всех", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutAll = false }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Confirmation: Remove Individual Account
    accountToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = {
                Text(
                    text = "Удалить профиль?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                val name = target.displayName.ifBlank { target.login }.ifBlank { target.uid }
                Text(
                    text = "Удалить сохранённый профиль \"$name\" и его локальные данные?",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.5.sp
                )
            },
            containerColor = Color(0xFF1E1E26),
            confirmButton = {
                TextButton(
                    onClick = {
                        val uidToRemove = target.uid
                        accountToDelete = null
                        onRemoveAccount(uidToRemove)
                    }
                ) {
                    Text("Удалить", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
