package io.proffi.inventory.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.LanguageHelper
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var showLanguageDialog by remember { mutableStateOf(false) }
            var showThemeDialog by remember { mutableStateOf(false) }
            var isChangingLanguage by remember { mutableStateOf(false) }

            MaterialTheme {
                SettingsScreen(
                    onBackPressed = { finish() },
                    onLanguageClick = { showLanguageDialog = true },
                    onThemeClick = { showThemeDialog = true }
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
                                isChangingLanguage = false
                                showLanguageDialog = false
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
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onLanguageClick: () -> Unit,
    onThemeClick: () -> Unit
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
