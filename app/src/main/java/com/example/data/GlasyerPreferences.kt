package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.glasyerDataStore by preferencesDataStore(name = "glasyer_prefs")

class GlasyerPreferences(private val context: Context) {
    private val keyRecentSearches = stringPreferencesKey("recent_searches_pipe")
    private val keyDownloads = stringSetPreferencesKey("downloaded_video_ids")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyLang = stringPreferencesKey("app_language")

    val recentSearches: Flow<List<String>> = context.glasyerDataStore.data.map { prefs ->
        val raw = prefs[keyRecentSearches].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split("||").filter { it.isNotBlank() }
    }

    val downloadedVideoIds: Flow<Set<String>> = context.glasyerDataStore.data.map { prefs ->
        prefs[keyDownloads] ?: emptySet()
    }

    val themeMode: Flow<String> = context.glasyerDataStore.data.map { prefs ->
        prefs[keyTheme] ?: "system"
    }

    val language: Flow<String> = context.glasyerDataStore.data.map { prefs ->
        prefs[keyLang] ?: "hi"
    }

    suspend fun addSearchQuery(query: String) {
        val clean = query.trim().replace("||", " ")
        if (clean.isBlank()) return
        context.glasyerDataStore.edit { prefs ->
            val current = prefs[keyRecentSearches].orEmpty()
                .split("||")
                .filter { it.isNotBlank() && !it.equals(clean, ignoreCase = true) }
            val updated = (listOf(clean) + current).take(10)
            prefs[keyRecentSearches] = updated.joinToString("||")
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.glasyerDataStore.edit { prefs ->
            val updated = prefs[keyRecentSearches].orEmpty()
                .split("||")
                .filter { it.isNotBlank() && it != query }
            prefs[keyRecentSearches] = updated.joinToString("||")
        }
    }

    suspend fun clearSearches() {
        context.glasyerDataStore.edit { it.remove(keyRecentSearches) }
    }

    suspend fun toggleDownload(videoId: String): Boolean {
        var added = false
        context.glasyerDataStore.edit { prefs ->
            val current = prefs[keyDownloads] ?: emptySet()
            if (videoId in current) {
                prefs[keyDownloads] = current - videoId
                added = false
            } else {
                prefs[keyDownloads] = current + videoId
                added = true
            }
        }
        return added
    }

    suspend fun setThemeMode(mode: String) {
        context.glasyerDataStore.edit { it[keyTheme] = mode }
    }

    suspend fun setLanguage(lang: String) {
        context.glasyerDataStore.edit { it[keyLang] = lang }
    }
}
