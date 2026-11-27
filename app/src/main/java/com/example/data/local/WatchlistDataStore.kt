package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.favoriteWatchlistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_watchlist_prefs"
)

/**
 * Preferences DataStore persistence layer for the user's favorite watchlist tickers.
 */
class WatchlistDataStore(private val context: Context) {

    private object Keys {
        val FAVORITE_TICKERS_CSV = stringPreferencesKey("favorite_tickers_csv")
        val INITIALIZED = booleanPreferencesKey("favorite_tickers_initialized")
        val AUTO_REFRESH = booleanPreferencesKey("favorite_watchlist_auto_refresh")
    }

    fun observeFavorites(): Flow<List<String>> =
        context.favoriteWatchlistDataStore.data.map { prefs ->
            val initialized = prefs[Keys.INITIALIZED] ?: false
            val raw = prefs[Keys.FAVORITE_TICKERS_CSV]
            if (!initialized && raw == null) {
                DEFAULT_FAVORITES
            } else {
                parseTickers(raw.orEmpty())
            }
        }

    fun observeAutoRefresh(): Flow<Boolean> =
        context.favoriteWatchlistDataStore.data.map { prefs ->
            prefs[Keys.AUTO_REFRESH] ?: true
        }

    suspend fun loadFavorites(): List<String> = observeFavorites().first()

    suspend fun seedIfUninitialized(initialSymbols: List<String>) {
        context.favoriteWatchlistDataStore.edit { prefs ->
            val alreadyInit = prefs[Keys.INITIALIZED] ?: false
            if (!alreadyInit) {
                val seeded = (initialSymbols.mapNotNull(::normalizeTicker) + DEFAULT_FAVORITES)
                    .distinct()
                    .take(MAX_FAVORITES)
                prefs[Keys.FAVORITE_TICKERS_CSV] = seeded.joinToString(",")
                prefs[Keys.INITIALIZED] = true
            }
        }
    }

    suspend fun addTickers(rawInput: String): List<String> {
        val toAdd = parseTickers(rawInput)
        if (toAdd.isEmpty()) return loadFavorites()
        var result: List<String> = emptyList()
        context.favoriteWatchlistDataStore.edit { prefs ->
            val current = currentListFrom(prefs)
            val updated = (current + toAdd).distinct().take(MAX_FAVORITES)
            prefs[Keys.FAVORITE_TICKERS_CSV] = updated.joinToString(",")
            prefs[Keys.INITIALIZED] = true
            result = updated
        }
        return result
    }

    suspend fun removeTicker(symbol: String): List<String> {
        val clean = normalizeTicker(symbol) ?: return loadFavorites()
        var result: List<String> = emptyList()
        context.favoriteWatchlistDataStore.edit { prefs ->
            val current = currentListFrom(prefs)
            val updated = current.filterNot { it.equals(clean, ignoreCase = true) }
            prefs[Keys.FAVORITE_TICKERS_CSV] = updated.joinToString(",")
            prefs[Keys.INITIALIZED] = true
            result = updated
        }
        return result
    }

    suspend fun setAutoRefresh(enabled: Boolean) {
        context.favoriteWatchlistDataStore.edit { prefs ->
            prefs[Keys.AUTO_REFRESH] = enabled
        }
    }

    private fun currentListFrom(prefs: Preferences): List<String> {
        val initialized = prefs[Keys.INITIALIZED] ?: false
        val raw = prefs[Keys.FAVORITE_TICKERS_CSV]
        return if (!initialized && raw == null) DEFAULT_FAVORITES else parseTickers(raw.orEmpty())
    }

    companion object {
        val DEFAULT_FAVORITES = listOf("NVDA", "AAPL", "MSFT", "TSLA")
        val SUGGESTED_TICKERS = listOf("SPY", "QQQ", "META", "AMD", "AMZN", "GOOGL", "PLTR", "COIN")
        const val MAX_FAVORITES = 20

        private val TICKER_REGEX = Regex("^[A-Z][A-Z0-9.\\-]{0,6}$")

        fun normalizeTicker(raw: String): String? {
            val clean = raw.trim().uppercase(Locale.US).removePrefix("$")
            return clean.takeIf { it.isNotEmpty() && TICKER_REGEX.matches(it) }
        }

        fun parseTickers(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return raw
                .split(',', ' ', '\n', ';', '\t')
                .mapNotNull(::normalizeTicker)
                .distinct()
                .take(MAX_FAVORITES)
        }
    }
}
