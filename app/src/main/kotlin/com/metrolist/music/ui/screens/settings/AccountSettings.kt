/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Logout confirmation with the clear/keep data choice. Shared by the profile dialog and the
 * account settings screen; [onLoggedOut] runs after the logout completes.
 */
@Composable
fun AccountLogoutDialog(
    onDismiss: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val (_, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.logout_dialog_title)) },
        content = {
            Text(
                text = stringResource(R.string.logout_dialog_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        },
        buttons = {
            TextButton(
                onClick = {
                    Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                    scope.launch {
                        try {
                            Timber.d("[LOGOUT_CLEAR] Starting clear and logout process")
                            // Forget account first (stops all sync), then clear data.
                            // This prevents background syncs from re-adding songs.
                            accountSettingsViewModel.logoutAndClearLibraryData(context)
                            Timber.d("[LOGOUT_CLEAR] Library data cleared and account forgotten")
                        } catch (e: Exception) {
                            Timber.e(e, "[LOGOUT_CLEAR] Error clearing library data, proceeding with logout")
                            reportException(e)
                        }
                        onInnerTubeCookieChange("")
                        Timber.d("[LOGOUT_CLEAR] Logout complete")
                        onDismiss()
                        onLoggedOut()
                    }
                }
            ) {
                Text(stringResource(R.string.logout_clear))
            }
            TextButton(
                onClick = {
                    Timber.d("[LOGOUT_KEEP] User chose to keep data")
                    scope.launch {
                        Timber.d("[LOGOUT_KEEP] Starting logout process (keeping data)")
                        accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                        Timber.d("[LOGOUT_KEEP] Logout complete")
                        onDismiss()
                        onLoggedOut()
                    }
                }
            ) {
                Text(stringResource(R.string.logout_keep))
            }
        }
    )
}

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val uriHandler = LocalUriHandler.current

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showLogoutDialog) {
            AccountLogoutDialog(
                onDismiss = { showLogoutDialog = false },
                onLoggedOut = onClose,
            )
        }

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoggedIn && accountImageUrl != null) {
                                AsyncImage(
                                    model = accountImageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )

                                Spacer(Modifier.width(12.dp))
                            }

                            Text(
                                text = if (isLoggedIn) accountName else stringResource(R.string.login),
                            )
                        }
                    },
                    icon = if (!isLoggedIn) painterResource(R.drawable.login) else null,
                    trailingContent = {
                        if (isLoggedIn) {
                            OutlinedButton(
                                onClick = {
                                    Timber.d("[LOGOUT] User clicked logout button, showing dialog")
                                    showLogoutDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.action_logout))
                            }
                        }
                    },
                    onClick = {
                        onClose()
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            navController.navigate("login")
                        }
                    }
                ),
            ),
            useLowContrast = true
        )

        Spacer(Modifier.height(8.dp))

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.account_and_sync)) },
                        icon = painterResource(R.drawable.account),
                        onClick = {
                            onClose()
                            navController.navigate("settings/account")
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.settings)) },
                        icon = painterResource(R.drawable.settings),
                        showBadge = BuildConfig.UPDATER_AVAILABLE &&
                            Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, latestVersionName),
                        onClick = {
                            onClose()
                            navController.navigate("settings")
                        }
                    )
                )

                if (BuildConfig.UPDATER_AVAILABLE && Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, latestVersionName)) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.new_version_available)) },
                                description = { Text(latestVersionName) },
                                icon = painterResource(R.drawable.update),
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
            },
            useLowContrast = true
        )
    }
}
