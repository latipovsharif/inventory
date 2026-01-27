package io.proffi.inventory.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.scanner.DownloadState
import kotlin.math.roundToInt

/**
 * Диалог загрузки SDK для устройства сканирования
 */
@Composable
fun ScannerDownloadDialog(
    scannerName: String,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // Не позволяем закрыть во время загрузки
            if (downloadState !is DownloadState.Downloading) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.scanner_download_title, scannerName),
                style = MaterialTheme.typography.h6
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        Text(
                            text = stringResource(R.string.scanner_download_ready),
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.Checking -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.scanner_download_checking),
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.AlreadyDownloaded -> {
                        Text(
                            text = "✓ " + stringResource(R.string.scanner_download_already_downloaded),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.Downloading -> {
                        // Прогресс бар
                        LinearProgressIndicator(
                            progress = downloadState.progress / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Процент
                        Text(
                            text = "${downloadState.progress}%",
                            style = MaterialTheme.typography.h5,
                            color = MaterialTheme.colors.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Размер
                        val downloadedMB = downloadState.downloadedBytes / (1024.0 * 1024.0)
                        val totalMB = downloadState.totalBytes / (1024.0 * 1024.0)

                        Text(
                            text = if (downloadState.totalBytes > 0) {
                                stringResource(
                                    R.string.scanner_download_size,
                                    downloadedMB.roundToInt(),
                                    totalMB.roundToInt()
                                )
                            } else {
                                stringResource(
                                    R.string.scanner_download_size_unknown,
                                    downloadedMB.roundToInt()
                                )
                            },
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.scanner_download_please_wait),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.Success -> {
                        Text(
                            text = "✓ " + stringResource(R.string.scanner_download_success),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.scanner_download_ready_to_use),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }

                    is DownloadState.Error -> {
                        Text(
                            text = "✗ " + stringResource(R.string.scanner_download_error),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Success,
                is DownloadState.AlreadyDownloaded -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.scanner_download_done))
                    }
                }
                is DownloadState.Error -> {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.scanner_download_retry))
                    }
                }
                is DownloadState.Downloading,
                is DownloadState.Checking -> {
                    // Нет кнопки во время загрузки
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.scanner_download_cancel))
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState is DownloadState.Error) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.scanner_download_cancel))
                }
            }
        }
    )
}
