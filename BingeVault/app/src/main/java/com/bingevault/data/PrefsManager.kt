package com.bingevault.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("bingevault_prefs", Context.MODE_PRIVATE)

    // ── Cross-section search ───────────────────────────────────────────────────

    private val _crossSectionSearch = MutableStateFlow(
        prefs.getBoolean(KEY_CROSS_SEARCH, true)
    )
    val crossSectionSearch: StateFlow<Boolean> = _crossSectionSearch

    fun setCrossSectionSearch(enabled: Boolean) {
        _crossSectionSearch.value = enabled
        prefs.edit().putBoolean(KEY_CROSS_SEARCH, enabled).apply()
    }

    // ── Grid columns (2–4) ────────────────────────────────────────────────────

    private val _gridColumns = MutableStateFlow(
        prefs.getInt(KEY_GRID_COLUMNS, 2).coerceIn(2, 4)
    )
    val gridColumns: StateFlow<Int> = _gridColumns

    fun setGridColumns(n: Int) {
        _gridColumns.value = n.coerceIn(2, 4)
        prefs.edit().putInt(KEY_GRID_COLUMNS, n).apply()
    }

    // ── Ad-free period ────────────────────────────────────────────────────────
    // Stores a UTC epoch ms timestamp; if System.currentTimeMillis() < value, ads are hidden.

    private val _adFreeUntil = MutableStateFlow(
        prefs.getLong(KEY_AD_FREE_UNTIL, 0L)
    )
    val adFreeUntil: StateFlow<Long> = _adFreeUntil

    val isAdFree: Boolean get() = System.currentTimeMillis() < _adFreeUntil.value

    fun grantAdFreeDay() {
        val expiry = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        _adFreeUntil.value = expiry
        prefs.edit().putLong(KEY_AD_FREE_UNTIL, expiry).apply()
    }
        private const val KEY_CROSS_SEARCH  = "cross_section_search"
        private const val KEY_GRID_COLUMNS  = "grid_columns"
        private const val KEY_AD_FREE_UNTIL = "ad_free_until"

        @Volatile private var INSTANCE: PrefsManager? = null
        fun get(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
