package com.bingevault.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.bingevault.data.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = Repository(app)
    private val prefs = PrefsManager.get(app)

    // ── Active section ────────────────────────────────────────────────────────

    private val _activeType = MutableStateFlow(ContentType.SERIES)
    val activeType: StateFlow<ContentType> = _activeType

    // ── Search (debounced 120 ms so fast typing doesn't thrash the filter) ────

    private val _searchRaw = MutableStateFlow("")
    val search: StateFlow<String> = _searchRaw          // raw value → bound to TextField

    private val _searchDebounced = _searchRaw
        .debounce(120)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ── Pagination ────────────────────────────────────────────────────────────

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page

    // ── Settings ──────────────────────────────────────────────────────────────

    val crossSectionSearch: StateFlow<Boolean> = prefs.crossSectionSearch
    val gridColumns: StateFlow<Int>            = prefs.gridColumns
    val adFreeUntil: StateFlow<Long>           = prefs.adFreeUntil

    fun setCrossSectionSearch(v: Boolean) = prefs.setCrossSectionSearch(v)
    fun setGridColumns(n: Int)            = prefs.setGridColumns(n)
    fun grantAdFreeDay()                  = prefs.grantAdFreeDay()

    // ── All items ─────────────────────────────────────────────────────────────

    val allItems: StateFlow<List<MediaWithSeasons>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Filtered display list ─────────────────────────────────────────────────

    val displayItems: StateFlow<List<MediaWithSeasons>> = combine(
        allItems, _activeType, _searchDebounced, crossSectionSearch
    ) { items, type, q, crossSearch ->
        if (q.isBlank()) {
            // No query → always show active section only
            items.filter { it.media.type == type }
        } else {
            val base = if (crossSearch) items else items.filter { it.media.type == type }
            base.filter { mws ->
                mws.media.title.contains(q, ignoreCase = true) ||
                mws.media.tags.split(",").any { t -> t.trim().contains(q, ignoreCase = true) }
            }
        }.sortedBy { it.media.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Actions ───────────────────────────────────────────────────────────────

    fun setType(type: ContentType) { _activeType.value = type; _page.value = 0 }
    fun setSearch(q: String)       { _searchRaw.value = q;     _page.value = 0 }
    fun setPage(p: Int)            { _page.value = p }

    fun save(item: MediaItem, seasons: List<Season>) =
        viewModelScope.launch { repo.save(item, seasons) }

    fun updateSeason(season: Season) =
        viewModelScope.launch { repo.updateSeason(season) }

    fun delete(items: List<MediaItem>) =
        viewModelScope.launch { repo.delete(items) }

    suspend fun saveImage(uri: Uri): String? = repo.saveImage(uri)

    fun imageFile(name: String): File = repo.imageFile(name)

    // ── Backup ────────────────────────────────────────────────────────────────

    suspend fun exportVault(context: android.content.Context, destUri: Uri): Result<Unit> =
        BackupManager.export(context, allItems.value, destUri)

    suspend fun mergeVault(context: android.content.Context, srcUri: Uri): Result<Int> =
        BackupManager.merge(context, srcUri, allItems.value, AppDatabase.get(context).mediaDao())
}
