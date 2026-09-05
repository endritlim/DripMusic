/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel

/**
 * Account settings: identity, channel switching, YouTube Music sync, the token editor
 * (advanced login) and integrations. Linked from the profile dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
) {
    val context = LocalContext.current

    val (accountNamePref) = rememberPreference(AccountNameKey, "")
    val (accountEmail) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId) = rememberPreference(DataSyncIdKey, "")
    val (authUser) = rememberPreference(InnerTubeAuthUserKey, "0")
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.account),
            items = listOfNotNull(
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
                                onClick = { showLogoutDialog = true },
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
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            navController.navigate("login")
                        }
                    }
                ),
                if (isLoggedIn) {
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.switch_youtube_channel)) },
                        icon = painterResource(R.drawable.account),
                        onClick = {
                            navController.navigate("switch_channel")
                        },
                    )
                } else null,
            ),
        )

        Spacer(Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.sync),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.yt_sync)) },
                    description = { Text(stringResource(R.string.yt_sync_desc)) },
                    icon = painterResource(R.drawable.cached),
                    trailingContent = {
                        Switch(
                            enabled = isLoggedIn,
                            checked = ytmSync,
                            onCheckedChange = onYtmSyncChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (ytmSync) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    enabled = isLoggedIn
                )
            ),
        )

        Spacer(Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.advanced),
            items = listOf(
                Material3SettingsItem(
                    title = {
                        Text(
                            when {
                                !isLoggedIn -> stringResource(R.string.advanced_login)
                                showToken -> stringResource(R.string.token_shown)
                                else -> stringResource(R.string.token_hidden)
                            }
                        )
                    },
                    icon = painterResource(R.drawable.token),
                    onClick = {
                        if (!isLoggedIn) showTokenEditor = true
                        else if (!showToken) showToken = true
                        else showTokenEditor = true
                    }
                ),
            ),
        )

        Spacer(Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.integrations),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.integrations)) },
                    icon = painterResource(R.drawable.integration),
                    onClick = {
                        navController.navigate("settings/integrations")
                    }
                )
            ),
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showLogoutDialog) {
        AccountLogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onLoggedOut = { },
        )
    }

    if (showTokenEditor) {
        val text = """
            ***INNERTUBE COOKIE*** =$innerTubeCookie
            ***VISITOR DATA*** =$visitorData
            ***DATASYNC ID*** =$dataSyncId
            ***AUTH USER*** =$authUser
            ***ACCOUNT NAME*** =$accountNamePref
            ***ACCOUNT EMAIL*** =$accountEmail
            ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()

        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                var cookie = ""
                var visitorDataValue = ""
                var dataSyncIdValue = ""
                var authUserValue = "0"
                var accountNameValue = ""
                var accountEmailValue = ""
                var accountChannelHandleValue = ""

                data.split("\n").forEach {
                    when {
                        it.startsWith("***INNERTUBE COOKIE*** =") -> cookie = it.substringAfter("=")
                        it.startsWith("***VISITOR DATA*** =") -> visitorDataValue = it.substringAfter("=")
                        it.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = it.substringAfter("=")
                        it.startsWith("***AUTH USER*** =") -> authUserValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> accountChannelHandleValue = it.substringAfter("=")
                    }
                }
                // Write all credentials atomically to DataStore and wait for completion
                // before restarting, preventing the race condition where the process
                // would be killed before async DataStore coroutines finished writing.
                accountSettingsViewModel.saveTokenAndRestart(
                    context = context,
                    cookie = cookie,
                    visitorData = visitorDataValue,
                    dataSyncId = dataSyncIdValue,
                    authUser = authUserValue,
                    accountName = accountNameValue,
                    accountEmail = accountEmailValue,
                    accountChannelHandle = accountChannelHandleValue,
                )
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = { fullText ->
                // Extract the cookie value from the formatted template line,
                // then validate it separately — avoids the bug where parseCookieString
                // received the entire multi-line template and failed to find "SAPISID"
                // as a key because the "***INNERTUBE COOKIE*** =" prefix shadowed it.
                val cookieLine = fullText.lines()
                    .find { it.startsWith("***INNERTUBE COOKIE*** =") }
                val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
                cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
            },
            extraContent = {
                Spacer(Modifier.height(8.dp))
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.account)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
