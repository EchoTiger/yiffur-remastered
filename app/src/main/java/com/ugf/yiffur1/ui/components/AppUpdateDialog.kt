package com.ugf.yiffur1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ugf.yiffur1.BuildConfig
import com.ugf.yiffur1.data.update.AppUpdateInfo
import com.ugf.yiffur1.data.update.UpdateDownloadState
import com.ugf.yiffur1.util.Formatters

/**
 * In-app GitHub Releases Updater dialog supporting streaming APK downloads,
 * optional SHA-256 verification, Android 8+ Install Unknown Apps flow, and 1-tap install.
 */
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    canInstallPackages: Boolean,
    onStartDownload: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onOpenGitHubRelease: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Update Available")
                Text(
                    text = "v${BuildConfig.VERSION_NAME} → ${updateInfo.tagName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (updateInfo.apkSizeBytes > 0L) {
                    Text(
                        text = "${updateInfo.apkName ?: "APK"} · ${Formatters.formatFileSize(updateInfo.apkSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (downloadState.percent >= 0) {
                                LinearProgressIndicator(
                                    progress = { downloadState.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Downloading… ${downloadState.percent}% (${Formatters.formatFileSize(downloadState.bytesRead)} / ${Formatters.formatFileSize(downloadState.totalBytes)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "Downloading… ${Formatters.formatFileSize(downloadState.bytesRead)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "APK downloaded & ready to install!",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (!canInstallPackages) {
                                    Text(
                                        text = "Android requires granting \"Install unknown apps\" permission for Yiffur once.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    UpdateDownloadState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is UpdateDownloadState.Downloading -> {
                    Button(onClick = {}, enabled = false) {
                        Text("Downloading…")
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    if (canInstallPackages) {
                        Button(onClick = { onInstallApk(downloadState.apkFile) }) {
                            Icon(Icons.Rounded.InstallMobile, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install Now")
                        }
                    } else {
                        Button(onClick = onOpenInstallPermissionSettings) {
                            Text("Allow Install & Continue")
                        }
                    }
                }

                else -> {
                    if (!updateInfo.apkUrl.isNullOrBlank()) {
                        Button(onClick = onStartDownload) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Install")
                        }
                    } else {
                        Button(onClick = onOpenGitHubRelease) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open GitHub")
                        }
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (downloadState !is UpdateDownloadState.Downloading) {
                    TextButton(onClick = onSkipVersion) {
                        Text("Skip")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                }
            }
        }
    )
}
