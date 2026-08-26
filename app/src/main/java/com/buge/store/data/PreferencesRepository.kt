package com.buge.store.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "buge_store_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val colorMode = stringPreferencesKey("color_mode")
        val contrastMode = stringPreferencesKey("contrast_mode")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val language = stringPreferencesKey("language")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { values ->
        UserPreferences(
            themeMode = values.enum(Keys.themeMode, ThemeMode.SYSTEM),
            colorMode = values.enum(Keys.colorMode, ColorMode.DYNAMIC),
            contrastMode = values.enum(Keys.contrastMode, ContrastMode.STANDARD),
            reduceMotion = values[Keys.reduceMotion] ?: false,
            selectedLanguage = values[Keys.language] ?: "",
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = value.name }
    suspend fun setColorMode(value: ColorMode) = context.dataStore.edit { it[Keys.colorMode] = value.name }
    suspend fun setContrastMode(value: ContrastMode) = context.dataStore.edit { it[Keys.contrastMode] = value.name }
    suspend fun setReduceMotion(value: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = value }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, fallback: T): T {
        return this[key]?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    }
}
