package com.buge.store.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buge.store.R
import com.buge.store.data.ColorMode
import com.buge.store.data.ContrastMode
import com.buge.store.data.DownloadInfo
import com.buge.store.data.StoreAppDto
import com.buge.store.data.ThemeMode
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: StoreUiState,
    contentPadding: PaddingValues,
    onFilterChange: (LibraryFilter) -> Unit,
    onOpenApp: (StoreAppDto) -> Unit,
    onToggleFavourite: (StoreAppDto) -> Unit,
    onAction: (StoreAppDto) -> Unit,
    onOpen: (String) -> Unit,
    onInstall: (DownloadInfo) -> Unit,
    onCancelDownload: (DownloadInfo) -> Unit,
) {
    val apps = when (state.libraryFilter) {
        LibraryFilter.UPDATES -> state.apps.filter { state.installStates[it.packageName]?.isUpdateAvailable == true }
        LibraryFilter.INSTALLED -> state.apps.filter { state.installStates[it.packageName]?.installedVersion != null }
        LibraryFilter.FAVOURITES -> state.apps.filter { it.packageName in state.favouritePackages }
        LibraryFilter.DOWNLOADS -> state.downloads.mapNotNull { download -> state.apps.firstOrNull { it.packageName == download.packageName } }.distinctBy { it.packageName }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.library)) }) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding() + innerPadding.calculateTopPadding()),
        ) {
            LibraryFilters(state.libraryFilter, onFilterChange)
            if (apps.isEmpty()) {
                LibraryEmpty(state.libraryFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        StoreAppCard(
                            app = app,
                            installState = state.installStates[app.packageName] ?: com.buge.store.data.AppInstallState(),
                            isFavourite = app.packageName in state.favouritePackages,
                            download = state.downloadFor(app.packageName),
                            onOpenApp = { onOpenApp(app) },
                            onToggleFavourite = { onToggleFavourite(app) },
                            onAction = { onAction(app) },
                            onOpen = { onOpen(app.packageName) },
                            onInstall = { state.downloadFor(app.packageName)?.let(onInstall) },
                            onCancelDownload = { state.downloadFor(app.packageName)?.let(onCancelDownload) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilters(selected: LibraryFilter, onFilterChange: (LibraryFilter) -> Unit) {
    val labels = listOf(
        LibraryFilter.UPDATES to stringResource(R.string.updates),
        LibraryFilter.INSTALLED to stringResource(R.string.installed_apps),
        LibraryFilter.FAVOURITES to stringResource(R.string.favourites),
        LibraryFilter.DOWNLOADS to stringResource(R.string.downloads),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (filter, label) ->
            FilterChip(selected = selected == filter, onClick = { onFilterChange(filter) }, label = { Text(label) })
        }
    }
}

@Composable
private fun LibraryEmpty(filter: LibraryFilter) {
    val text = when (filter) {
        LibraryFilter.UPDATES, LibraryFilter.INSTALLED -> stringResource(R.string.no_updates)
        LibraryFilter.FAVOURITES -> stringResource(R.string.no_favourites)
        LibraryFilter.DOWNLOADS -> stringResource(R.string.no_downloads)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(32.dp)) }
}

private enum class SettingsPicker {
    THEME,
    COLOUR_SOURCE,
    CONTRAST,
    LANGUAGE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: StoreUiState,
    contentPadding: PaddingValues,
    onThemeMode: (ThemeMode) -> Unit,
    onColorMode: (ColorMode) -> Unit,
    onContrastMode: (ContrastMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
) {
    var activePicker by remember { mutableStateOf<SettingsPicker?>(null) }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.system_default),
        ThemeMode.LIGHT to stringResource(R.string.light),
        ThemeMode.DARK to stringResource(R.string.dark),
    )
    val colourOptions = listOf(
        ColorMode.DYNAMIC to stringResource(R.string.dynamic_colour),
        ColorMode.BUGE_BLUE to stringResource(R.string.buge_blue),
        ColorMode.TEAL to stringResource(R.string.teal),
        ColorMode.VIOLET to stringResource(R.string.violet),
        ColorMode.CORAL to stringResource(R.string.coral),
        ColorMode.EVERGREEN to stringResource(R.string.evergreen),
    )
    val contrastOptions = listOf(
        ContrastMode.STANDARD to stringResource(R.string.standard),
        ContrastMode.MEDIUM to stringResource(R.string.medium),
        ContrastMode.HIGH to stringResource(R.string.high),
    )
    val languageOptions = listOf(
        "" to stringResource(R.string.language_system_default),
        "en" to stringResource(R.string.language_english_option),
        "fr" to stringResource(R.string.language_french_option),
        "de" to stringResource(R.string.language_german_option),
        "ru" to stringResource(R.string.language_russian_option),
        "pt" to stringResource(R.string.language_portuguese_option),
        "pt-BR" to stringResource(R.string.language_portuguese_brazil_option),
        "es" to stringResource(R.string.language_spanish_option),
        "zh" to stringResource(R.string.language_chinese_simplified_option),
        "zh-TW" to stringResource(R.string.language_chinese_traditional_option),
        "ar" to stringResource(R.string.language_arabic_option),
        "ja" to stringResource(R.string.language_japanese_option),
        "ko" to stringResource(R.string.language_korean_option),
    )

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + innerPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.appearance), Icons.Default.Palette) {
                    SettingsOptionCard(
                        title = stringResource(R.string.theme),
                        selectedValue = themeOptions.labelFor(state.preferences.themeMode),
                        onClick = { activePicker = SettingsPicker.THEME },
                    )
                    SettingsOptionCard(
                        title = stringResource(R.string.colour_source),
                        selectedValue = colourOptions.labelFor(state.preferences.colorMode),
                        onClick = { activePicker = SettingsPicker.COLOUR_SOURCE },
                    )
                    SettingsOptionCard(
                        title = stringResource(R.string.contrast),
                        selectedValue = contrastOptions.labelFor(state.preferences.contrastMode),
                        onClick = { activePicker = SettingsPicker.CONTRAST },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.accessibility), Icons.Default.AccessibilityNew) {
                    SettingSwitch(
                        title = stringResource(R.string.reduce_motion),
                        subtitle = stringResource(R.string.reduce_motion_summary),
                        checked = state.preferences.reduceMotion,
                        onChange = onReduceMotion,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.language), Icons.Default.Language) {
                    SettingsOptionCard(
                        title = stringResource(R.string.language),
                        selectedValue = languageOptions.labelFor(state.preferences.selectedLanguage),
                        onClick = { activePicker = SettingsPicker.LANGUAGE },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.data), Icons.Default.Storage) {
                    SettingStatic(title = stringResource(R.string.last_updated), subtitle = state.lastUpdated.ifBlank { stringResource(R.string.offline_catalogue) })
                    SettingStatic(title = stringResource(R.string.api_source), subtitle = "Buge Store API v1")
                }
            }
            item {
                SettingsGroup(stringResource(R.string.about), Icons.Default.Info) {
                    SettingStatic(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.open_source_store))
                    SettingStatic(title = "com.buge.store", subtitle = "1.0.4")
                }
            }
        }
    }

    when (activePicker) {
        SettingsPicker.THEME -> OptionPickerDialog(
            title = stringResource(R.string.theme),
            options = themeOptions,
            selected = state.preferences.themeMode,
            onSelect = onThemeMode,
            onDismiss = { activePicker = null },
        )
        SettingsPicker.COLOUR_SOURCE -> OptionPickerDialog(
            title = stringResource(R.string.colour_source),
            options = colourOptions,
            selected = state.preferences.colorMode,
            onSelect = onColorMode,
            onDismiss = { activePicker = null },
        )
        SettingsPicker.CONTRAST -> OptionPickerDialog(
            title = stringResource(R.string.contrast),
            options = contrastOptions,
            selected = state.preferences.contrastMode,
            onSelect = onContrastMode,
            onDismiss = { activePicker = null },
        )
        SettingsPicker.LANGUAGE -> OptionPickerDialog(
            title = stringResource(R.string.language),
            options = languageOptions,
            selected = state.preferences.selectedLanguage,
            onSelect = onLanguage,
            onDismiss = { activePicker = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun SettingsOptionCard(title: String, selectedValue: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    selectedValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> OptionPickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(options, key = { (value, _) -> value.toString() }) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == value, onClick = null)
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {},
    )
}

private fun <T> List<Pair<T, String>>.labelFor(selected: T): String {
    return firstOrNull { (value, _) -> value == selected }?.second ?: first().second
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingStatic(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
