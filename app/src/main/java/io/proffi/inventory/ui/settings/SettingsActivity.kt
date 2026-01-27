package io.proffi.inventory.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MobileScreenShare
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.scanner.DownloadState
import io.proffi.inventory.scanner.ScannerType
import io.proffi.inventory.scanner.SdkDownloadManager
import io.proffi.inventory.scanner.SdkLibraryConfig
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.LanguageHelper
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var showLanguageDialog by remember { mutableStateOf(false) }
            var showThemeDialog by remember { mutableStateOf(false) }
            var showScannerDialog by remember { mutableStateOf(false) }
            var showDownloadDialog by remember { mutableStateOf(false) }
            var isChangingLanguage by remember { mutableStateOf(false) }
            var contentKey by remember { mutableStateOf(0) }
            var currentScannerType by remember { mutableStateOf(ScannerType.CAMERA) }
            var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
            var selectedScannerForDownload by remember { mutableStateOf<ScannerType?>(null) }

            val sdkDownloadManager = remember { SdkDownloadManager(context) }

            LaunchedEffect(Unit) {
                val scannerPrefs = ScannerPreferences(context)
                currentScannerType = scannerPrefs.scannerType.first()
            }

            AnimatedContent(
                targetState = contentKey,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "language_change_animation"
            ) { _ ->
                MaterialTheme {
                    SettingsScreen(
                        onBackPressed = { finish() },
                        onLanguageClick = { showLanguageDialog = true },
                        onThemeClick = { showThemeDialog = true },
                        onScannerClick = { showScannerDialog = true },
                        currentScannerType = currentScannerType
                    )

                    if (showLanguageDialog) {
                        LanguageSelectionDialog(
                            isLoading = isChangingLanguage,
                            currentLanguage = LanguageHelper.getCurrentLanguage(),
                            onDismiss = {
                                if (!isChangingLanguage) {
                                    showLanguageDialog = false
                                }
                            },
                            onLanguageSelected = { languageCode ->
                                coroutineScope.launch {
                                    isChangingLanguage = true
                                    // Сохраняем выбор
                                    LanguageHelper.saveLanguage(context, languageCode)
                                    // Применяем немедленно
                                    LanguageHelper.applyLanguage(languageCode)
                                    // Даём время на анимацию fade out
                                    delay(150)
                                    isChangingLanguage = false
                                    showLanguageDialog = false
                                    // Небольшая задержка перед recreate для плавности
                                    delay(150)
                                    // Перезапускаем Activity для применения изменений
                                    recreate()
                                }
                            }
                        )
                    }

                if (showThemeDialog) {
                    ThemeSelectionDialog(
                        onDismiss = { showThemeDialog = false },
                        onThemeSelected = { _ ->
                            showThemeDialog = false
                            // TODO: Реализовать смену темы позже
                        }
                    )
                }

                if (showScannerDialog) {
                    ScannerSelectionDialog(
                        currentScannerType = currentScannerType,
                        sdkDownloadManager = sdkDownloadManager,
                        onDismiss = { showScannerDialog = false },
                        onScannerSelected = { scannerType ->
                            // Проверяем, требуется ли загрузка SDK
                            val config = SdkLibraryConfig.getConfig(scannerType)
                            if (config != null && SdkLibraryConfig.requiresDownload(scannerType)) {
                                // Проверяем, загружен ли SDK
                                if (!sdkDownloadManager.isSdkDownloaded(config)) {
                                    // Нужно загрузить SDK
                                    selectedScannerForDownload = scannerType
                                    showScannerDialog = false
                                    showDownloadDialog = true

                                    // Запускаем загрузку
                                    lifecycleScope.launch {
                                        sdkDownloadManager.downloadSdk(config).collect { state ->
                                            downloadState = state

                                            // Если успешно загружено, устанавливаем сканер
                                            if (state is DownloadState.Success || state is DownloadState.AlreadyDownloaded) {
                                                val scannerPrefs = ScannerPreferences(context)
                                                scannerPrefs.setScannerType(scannerType)
                                                currentScannerType = scannerType
                                            }
                                        }
                                    }
                                } else {
                                    // SDK уже загружен, просто переключаем
                                    lifecycleScope.launch {
                                        val scannerPrefs = ScannerPreferences(context)
                                        scannerPrefs.setScannerType(scannerType)
                                        currentScannerType = scannerType
                                        showScannerDialog = false
                                    }
                                }
                            } else {
                                // Встроенный сканер (камера), не требует загрузки
                                lifecycleScope.launch {
                                    val scannerPrefs = ScannerPreferences(context)
                                    scannerPrefs.setScannerType(scannerType)
                                    currentScannerType = scannerType
                                    showScannerDialog = false
                                }
                            }
                        }
                    )
                }

                if (showDownloadDialog && selectedScannerForDownload != null) {
                    ScannerDownloadDialog(
                        scannerName = stringResource(selectedScannerForDownload!!.displayNameRes),
                        downloadState = downloadState,
                        onDismiss = {
                            showDownloadDialog = false
                            downloadState = DownloadState.Idle
                            selectedScannerForDownload = null
                        },
                        onRetry = {
                            // Повторная попытка загрузки
                            val config = SdkLibraryConfig.getConfig(selectedScannerForDownload!!)
                            if (config != null) {
                                lifecycleScope.launch {
                                    sdkDownloadManager.downloadSdk(config).collect { state ->
                                        downloadState = state

                                        if (state is DownloadState.Success || state is DownloadState.AlreadyDownloaded) {
                                            val scannerPrefs = ScannerPreferences(context)
                                            scannerPrefs.setScannerType(selectedScannerForDownload!!)
                                            currentScannerType = selectedScannerForDownload!!
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onLanguageClick: () -> Unit,
    onThemeClick: () -> Unit,
    onScannerClick: () -> Unit,
    currentScannerType: ScannerType
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Секция: Внешний вид
            SettingsSectionHeader(title = stringResource(R.string.settings_appearance_section))

            // Язык
            SettingsItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_subtitle),
                onClick = onLanguageClick
            )

            Divider()

            // Тема
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                onClick = onThemeClick
            )

            Divider()

            // Секция: Другие настройки (для будущего)
            SettingsSectionHeader(title = stringResource(R.string.settings_other_section))

            // Выбор устройства сканирования
            SettingsItem(
                icon = Icons.Default.MobileScreenShare,
                title = stringResource(R.string.settings_scanner_device),
                subtitle = stringResource(currentScannerType.displayNameRes),
                onClick = onScannerClick
            )

            Divider()

            // Заглушка для будущих настроек
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = 2.dp,
                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_coming_soon),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colors.onSurface
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun LanguageSelectionDialog(
    isLoading: Boolean = false,
    currentLanguage: String = LanguageHelper.LANGUAGE_SYSTEM,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(text = stringResource(R.string.language_select_title))
        },
        text = {
            Column {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_applying_language),
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                } else {
                    // Русский
                    LanguageOptionButton(
                        languageCode = LanguageHelper.LANGUAGE_RUSSIAN,
                        languageName = stringResource(R.string.language_russian),
                        isSelected = currentLanguage == LanguageHelper.LANGUAGE_RUSSIAN,
                        onClick = { onLanguageSelected(LanguageHelper.LANGUAGE_RUSSIAN) }
                    )

                    Divider()

                    // English
                    LanguageOptionButton(
                        languageCode = LanguageHelper.LANGUAGE_ENGLISH,
                        languageName = stringResource(R.string.language_english),
                        isSelected = currentLanguage == LanguageHelper.LANGUAGE_ENGLISH,
                        onClick = { onLanguageSelected(LanguageHelper.LANGUAGE_ENGLISH) }
                    )

                    Divider()

                    // Системный
                    LanguageOptionButton(
                        languageCode = LanguageHelper.LANGUAGE_SYSTEM,
                        languageName = stringResource(R.string.language_system),
                        isSelected = currentLanguage == LanguageHelper.LANGUAGE_SYSTEM,
                        onClick = { onLanguageSelected(LanguageHelper.LANGUAGE_SYSTEM) }
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.inventory_conflict_ok))
                }
            }
        }
    )
}

@Composable
fun LanguageOptionButton(
    languageCode: String,
    languageName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = languageName,
                style = MaterialTheme.typography.body1,
                color = if (isSelected) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.onSurface
                }
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_theme_dialog_title))
        },
        text = {
            Column {
                TextButton(
                    onClick = { onThemeSelected("light") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_light),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider()

                TextButton(
                    onClick = { onThemeSelected("dark") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_dark),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider()

                TextButton(
                    onClick = { onThemeSelected("system") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_system),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.inventory_conflict_ok))
            }
        }
    )
}

@Composable
fun ScannerSelectionDialog(
    currentScannerType: ScannerType,
    sdkDownloadManager: SdkDownloadManager,
    onDismiss: () -> Unit,
    onScannerSelected: (ScannerType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_scanner_dialog_title))
        },
        text = {
            Column {
                ScannerType.values().forEach { scannerType ->
                    val config = SdkLibraryConfig.getConfig(scannerType)
                    val isDownloaded = config?.let { sdkDownloadManager.isSdkDownloaded(it) } ?: true
                    val requiresDownload = SdkLibraryConfig.requiresDownload(scannerType)

                    ScannerOptionButton(
                        scannerType = scannerType,
                        isSelected = currentScannerType == scannerType,
                        isDownloaded = isDownloaded,
                        requiresDownload = requiresDownload,
                        onClick = { onScannerSelected(scannerType) }
                    )
                    if (scannerType != ScannerType.values().last()) {
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.inventory_conflict_ok))
            }
        }
    )
}

@Composable
fun ScannerOptionButton(
    scannerType: ScannerType,
    isSelected: Boolean,
    isDownloaded: Boolean,
    requiresDownload: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(scannerType.displayNameRes),
                    style = MaterialTheme.typography.body1,
                    color = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.onSurface
                    }
                )

                // Показываем статус загрузки
                if (requiresDownload) {
                    Text(
                        text = if (isDownloaded) {
                            stringResource(R.string.scanner_sdk_downloaded)
                        } else {
                            stringResource(R.string.scanner_sdk_not_downloaded)
                        },
                        style = MaterialTheme.typography.caption,
                        color = if (isDownloaded) {
                            MaterialTheme.colors.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        }
                    )
                }
            }

            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

