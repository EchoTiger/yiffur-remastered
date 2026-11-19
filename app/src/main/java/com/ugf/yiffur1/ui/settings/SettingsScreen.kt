package com.ugf.yiffur1.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ugf.yiffur1.BuildConfig
import com.ugf.yiffur1.R
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.FeedLayoutMode
import com.ugf.yiffur1.domain.model.ThemeMode
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import com.ugf.yiffur1.domain.perf.PerformanceMode
import com.ugf.yiffur1.domain.perf.PerformanceTier
import com.ugf.yiffur1.notifications.NotificationHelper
import com.ugf.yiffur1.ui.theme.RatingExplicitColor
import com.ugf.yiffur1.ui.theme.RatingQuestionableColor
import com.ugf.yiffur1.ui.theme.RatingSafeColor
import com.ugf.yiffur1.util.Formatters
import kotlin.math.roundToInt

private data class CreditEntry(val stringRes: Int, val avatarRes: Int)

private val YiffurCredits = listOf(
    CreditEntry(R.string.creditText, R.drawable.echotiger),
    CreditEntry(R.string.creditTextPhil, R.drawable.phil),
    CreditEntry(R.string.creditTextAljninja, R.drawable.aljninja),
    CreditEntry(R.string.creditTextMaxwell, R.drawable.multidaxio),
    CreditEntry(R.string.creditTextDoges, R.drawable.unknownuser),
    CreditEntry(R.string.creditTextHandshake, R.drawable.handhsakelogo),
    CreditEntry(R.string.creditTextLetestroll, R.drawable.letestroll),
    CreditEntry(R.string.creditTextBribi, R.drawable.bribi),
    CreditEntry(R.string.creditTextAstroNoot, R.drawable.astronoot),
    CreditEntry(R.string.creditTextBurton, R.drawable.mxburton),
    CreditEntry(R.string.creditTextLalkin, R.drawable.lalkin),
    CreditEntry(R.string.creditTextNaughtyVaporeon, R.drawable.naughtyvapreon),
    CreditEntry(R.string.creditTextMisterCake, R.drawable.mistercake),
    CreditEntry(R.string.creditTextNightKey, R.drawable.nightkey),
    CreditEntry(R.string.creditTextTigerKing, R.drawable.unknownuser),
    CreditEntry(R.string.creditTextRubi, R.drawable.rubi),
    CreditEntry(R.string.creditTextPridesnow, R.drawable.pridesnow),
    CreditEntry(R.string.creditTextTyz, R.drawable.tyz),
    CreditEntry(R.string.creditTextLoren, R.drawable.loren)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenBlacklist: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val perfState by viewModel.performanceState.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showLoginDialog by remember { mutableStateOf(false) }
    var showUgcConfirmDialog by remember { mutableStateOf(false) }
    var showHdFullscreenWarning by remember { mutableStateOf(false) }
    var creditsExpanded by remember { mutableStateOf(false) }
    var notifPermissionGranted by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPermissionGranted = granted || NotificationHelper.hasNotificationPermission(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(context, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(context, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
        notifPermissionGranted = NotificationHelper.hasNotificationPermission(context)
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Banner Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_yiffuricon),
                        contentDescription = "Yiffur",
                        modifier = Modifier.size(48.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Yiffur Remastered", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) · e621 API v2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. Account Card
            item {
                SettingsGroupCard(title = "e621 Account") {
                    val creds = state.credentials
                    if (creds == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Not signed in", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Sign in with your API key to sync favorites, vote, comment, and sync your account blacklist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.clearLoginError()
                                    showLoginDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Login, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign In")
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = state.userProfile?.name ?: creds.username,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = state.userProfile?.let {
                                                "${it.levelString} · ${Formatters.formatCompactCount(it.favoriteCount)} favs · Limit ${it.tagQueryLimit} tags"
                                            } ?: "Encrypted in Android Keystore",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = viewModel::logout,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Adaptive Performance & Hardware Telemetry Card
            item {
                SettingsGroupCard(title = "Adaptive Performance & Hardware") {
                    val tierColor = when (perfState.effectiveTier) {
                        PerformanceTier.ULTRA -> RatingSafeColor
                        PerformanceTier.BALANCED -> MaterialTheme.colorScheme.primary
                        PerformanceTier.POWER_SAVER -> RatingQuestionableColor
                        PerformanceTier.SURVIVAL -> RatingExplicitColor
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Bolt, null, tint = tierColor, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Active Tier: ${perfState.effectiveTier.label}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tierColor
                                    )
                                }
                                Text(
                                    text = "${perfState.smoothnessPercent}% smooth",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val hw = perfState.hardware
                            val ramGb = String.format("%.1f", hw.totalRamMb / 1024.0)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Memory,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "$ramGb GB RAM · ${hw.cpuCores} cores · API ${hw.apiLevel} · HW: ${hw.supportedHwCodecSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val statusLine = buildString {
                                append("Thermal: ${perfState.thermal.label} · RAM: ${perfState.memory.label}")
                                if (perfState.isPowerSaveMode) append(" · Battery Saver")
                                if (perfState.isMeteredNetwork) append(" · Metered")
                            }
                            Text(
                                text = statusLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            perfState.activeReason?.let { reason ->
                                Text(
                                    text = "Live status: $reason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Performance Profile",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PerformanceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.performanceMode == mode,
                                onClick = { viewModel.setPerformanceMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Text(
                        text = state.settings.performanceMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = "Data Saver on Metered Networks",
                        subtitle = "Uses compact 256px WebP previews and caps auto-video to 720p on cellular/metered connections",
                        checked = state.settings.dataSaverOnMetered,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(dataSaverOnMetered = v) } }
                    )
                }
            }

            // 3. Updates & Push Notifications Card
            item {
                SettingsGroupCard(title = "Updates & Push Notifications") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = availableUpdate?.let { "Update ready: ${it.tagName}" }
                                    ?: "Installed: v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (availableUpdate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GitHub Releases · ${context.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (availableUpdate != null) {
                            Button(
                                onClick = onOpenUpdateDialog,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Update")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdatesNow(onUpdateFound = onOpenUpdateDialog) },
                                enabled = !isCheckingUpdates,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Checking…")
                                } else {
                                    Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check Now")
                                }
                            }
                        }
                    }

                    if (!notifPermissionGranted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notifications Disabled",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Allow notifications to receive update alerts, FCM pushes, and watched tag alerts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            runCatching { context.startActivity(intent) }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Enable")
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = "Automatic Update Checks",
                        subtitle = "Checks GitHub Releases in the background every 12 hours and notifies when a new build drops",
                        checked = state.settings.autoCheckUpdates,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCheckUpdates = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Push Announcements (FCM)",
                        subtitle = if (viewModel.isFirebaseConfigured) {
                            "Connected to Firebase Cloud Messaging (yiffur_announcements & yiffur_updates)"
                        } else {
                            "Local-only build (Firebase config not bundled)"
                        },
                        checked = state.settings.pushAnnouncementsEnabled,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(pushAnnouncementsEnabled = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Saved Search / Artist Alerts",
                        subtitle = "Checks up to 5 saved tag shortcuts every 6 hours and notifies when new artwork appears",
                        checked = state.settings.watchedTagAlertsEnabled,
                        onCheckedChange = { v ->
                            if (v && !notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.updateSettings { it.copy(watchedTagAlertsEnabled = v) }
                        }
                    )

                    if (!fcmToken.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Yiffur FCM Token", fcmToken))
                                    Toast.makeText(context, "FCM push token copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FCM Device Token: ${fcmToken!!.take(18)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy FCM Token",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 4. Source & Content Filtering
            item {
                SettingsGroupCard(title = "Image Source & Content Filtering") {
                    Text(
                        text = "Active Host",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isE926 = state.settings.apiHost == ApiHost.E926
                        FilterChip(
                            selected = isE926,
                            onClick = { viewModel.setApiHost(ApiHost.E926) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Shield, null, tint = RatingSafeColor, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("e926.net (Safe)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isE926,
                            onClick = {
                                if (state.settings.hasAccepted18PlusUgc) {
                                    viewModel.setApiHost(ApiHost.E621)
                                } else {
                                    showUgcConfirmDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Shield, null, tint = RatingExplicitColor, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("e621.net (All)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsSwitchRow(
                        title = "Show Safe (S) posts",
                        checked = state.settings.showSafe,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSafe = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Questionable (Q) posts",
                        subtitle = if (state.settings.apiHost.isSafeOnly) "Requires e621.net source" else null,
                        checked = state.settings.showQuestionable,
                        enabled = !state.settings.apiHost.isSafeOnly,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showQuestionable = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Explicit (E) posts",
                        subtitle = if (state.settings.apiHost.isSafeOnly) "Requires e621.net source" else null,
                        checked = state.settings.showExplicit,
                        enabled = !state.settings.apiHost.isSafeOnly,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showExplicit = v) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = onOpenBlacklist,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Blacklist (${state.blacklistCount} rules)")
                    }

                    SettingsSwitchRow(
                        title = "Disable Yiffur Blacklist",
                        subtitle = "Temporarily bypass all local blacklist rules",
                        checked = state.settings.disableBlacklist,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(disableBlacklist = v) } }
                    )
                }
            }

            // 5. Appearance & Feed Layout
            item {
                SettingsGroupCard(title = "Appearance & Feed Layout") {
                    Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Text("Feed Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FeedLayoutMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.layoutMode == mode,
                                onClick = { viewModel.setLayoutMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    if (state.settings.layoutMode != FeedLayoutMode.VERTICAL_LIST) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Grid Columns", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${state.settings.gridColumns}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = state.settings.gridColumns.toFloat(),
                                onValueChange = { viewModel.setGridColumns(it.roundToInt()) },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Crop Uniform Thumbnails",
                        checked = state.settings.cropThumbnails,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(cropThumbnails = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Score & Favorite Overlay",
                        checked = state.settings.showVotesOverlay,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showVotesOverlay = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Rating Badge (S / Q / E)",
                        checked = state.settings.showRatingBadge,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showRatingBadge = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Video / GIF Indicators",
                        checked = state.settings.showVideoIndicator,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showVideoIndicator = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Search History Tabs",
                        checked = state.settings.showSearchTabs,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSearchTabs = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Show Quick-Browse Shortcuts Button",
                        checked = state.settings.showQuickBrowseButton,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showQuickBrowseButton = v) } }
                    )
                }
            }

            // 6. Media Quality & Video Playback
            item {
                SettingsGroupCard(title = "Media Quality & Playback") {
                    SettingsSwitchRow(
                        title = "HD Thumbnails",
                        subtitle = "Uses 850px WebP samples on high-tier devices (auto-scales down under load)",
                        checked = state.settings.hdThumbnails,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hdThumbnails = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Original Resolution in Fullscreen",
                        subtitle = "Loads tiled full-res original immediately (auto-upgrades on zoom when off)",
                        checked = state.settings.hdFullscreen,
                        onCheckedChange = { v ->
                            if (v && !state.settings.hdFullscreen) {
                                showHdFullscreenWarning = true
                            } else {
                                viewModel.updateSettings { it.copy(hdFullscreen = v) }
                            }
                        }
                    )
                    SettingsSwitchRow(
                        title = "Download Original Resolution",
                        checked = state.settings.hdDownloads,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hdDownloads = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Mute Videos by Default",
                        checked = state.settings.muteVideosByDefault,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(muteVideosByDefault = v) } }
                    )

                    Text("Default Video Stream", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoQualityPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = state.settings.videoQuality == pref,
                                onClick = { viewModel.setVideoQuality(pref) },
                                label = { Text(pref.label) }
                            )
                        }
                    }
                }
            }

            // 7. Favorites Sync
            item {
                SettingsGroupCard(title = "Favorites") {
                    SettingsSwitchRow(
                        title = "Sync Favorites to e621 Account",
                        subtitle = "Automatically favorite/unfavorite on e621 when signed in",
                        checked = state.settings.syncFavoritesToE621,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(syncFavoritesToE621 = v) } }
                    )
                    SettingsSwitchRow(
                        title = "Disable Local Yiffur Favorites",
                        subtitle = "Only use your online e621 account favorites list",
                        checked = state.settings.disableLocalFavorites,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(disableLocalFavorites = v) } }
                    )
                }
            }

            // 8. Privacy, Cache & Backup
            item {
                SettingsGroupCard(title = "Privacy, Cache & Backup") {
                    SettingsSwitchRow(
                        title = "Hide in App Switcher (Privacy Flag)",
                        subtitle = "Prevents Android Recents thumbnails & screenshots",
                        checked = state.settings.hideFromRecents,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hideFromRecents = v) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Media & Video Disk Cache", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Formatters.formatFileSize(state.cacheSizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = viewModel::clearImageCache, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Cache")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("yiffur_backup_v2.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Backup, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                    Text(
                        "Supports Yiffur v2 JSON backups AND legacy Yiffur v1 exports. Includes settings, blacklist rules, saved searches, and ${state.localFavoritesCount} local favorites. API keys are never exported.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 9. Community & Credits
            item {
                SettingsGroupCard(title = "Community & Credits") {
                    Surface(
                        onClick = { onOpenUrl("https://discord.gg/PUqBtpS") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_discord),
                                contentDescription = "Discord",
                                modifier = Modifier.size(34.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Join our Discord!", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Community chat, support, and updates",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creditsExpanded = !creditsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Creator & Translators (${YiffurCredits.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (creditsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = creditsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            YiffurCredits.forEach { credit ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(credit.avatarRes),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = stringResource(credit.stringRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // e621 Account Login Dialog
    if (showLoginDialog) {
        var username by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!state.isVerifyingLogin) showLoginDialog = false },
            icon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign in to e621") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your e621 username and API Key (Account → Manage API Keys on e621.net). Stored locally using AES-256 Keystore encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.loginError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { onOpenUrl("https://${state.settings.apiHost.domain}/users/home") }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open e621 Account Page to Get API Key")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.login(username, apiKey) {
                            showLoginDialog = false
                        }
                    },
                    enabled = !state.isVerifyingLogin
                ) {
                    if (state.isVerifyingLogin) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Verify & Sign In")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLoginDialog = false },
                    enabled = !state.isVerifyingLogin
                ) { Text("Cancel") }
            }
        )
    }

    // 18+ Source Confirmation Dialog (preserved from original Yiffur UGC gate)
    if (showUgcConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUgcConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Lock, null, tint = RatingExplicitColor) },
            title = { Text("Switch to e621.net (18+)?") },
            text = {
                Text(
                    "e621.net includes adult/NSFW artwork in addition to safe artwork.\n\nBy continuing, you confirm you are at least 18 years old and agree to abide by local laws and e621's Terms of Service.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showUgcConfirmDialog = false
                    viewModel.setApiHost(ApiHost.E621, acceptedUgc = true)
                }) { Text("I am 18+ · Switch") }
            },
            dismissButton = {
                TextButton(onClick = { showUgcConfirmDialog = false }) { Text("Stay on e926") }
            }
        )
    }

    if (showHdFullscreenWarning) {
        AlertDialog(
            onDismissRequest = { showHdFullscreenWarning = false },
            title = { Text("High Data Usage Notice") },
            text = {
                Text(
                    "Original full-resolution images can exceed 10–20 MB each. Recommended on Wi-Fi or unlimited plans. Even with this off, pinching to zoom automatically upgrades to sharp HD tiles!"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showHdFullscreenWarning = false
                    viewModel.updateSettings { it.copy(hdFullscreen = true) }
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showHdFullscreenWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
