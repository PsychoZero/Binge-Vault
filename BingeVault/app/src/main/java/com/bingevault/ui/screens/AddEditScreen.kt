package com.bingevault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bingevault.data.*
import com.bingevault.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// ── Season form state ─────────────────────────────────────────────────────────

private data class SeasonForm(
    val id:           Long    = 0,
    val name:         String  = "",
    val totalStr:     String  = "",   // numeric string or "" for unknown
    val unknownTotal: Boolean = false,
    val isOva:        Boolean = false,
    val watched:      Int     = 0,
    val isFinished:   Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    mediaId:     Long,           // -1 = new
    defaultType: ContentType,
    vm:          MainViewModel,
    onDone:      () -> Unit
) {
    val isNew    = mediaId == -1L
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val existing = remember(allItems, mediaId) { allItems.find { it.media.id == mediaId } }

    var title       by remember { mutableStateOf("") }
    var tags        by remember { mutableStateOf("") }
    var type        by remember { mutableStateOf(defaultType) }
    var pickedUri   by remember { mutableStateOf<Uri?>(null) }
    var savedPath   by remember { mutableStateOf<String?>(null) }
    val seasons     = remember { mutableStateListOf<SeasonForm>() }
    val scope       = rememberCoroutineScope()

    // Pre-populate when editing an existing item, or seed defaults for new items
    LaunchedEffect(existing, isNew) {
        if (!isNew && existing != null) {
            val mws = existing
            title     = mws.media.title
            tags      = mws.media.tags
            type      = mws.media.type
            savedPath = mws.media.posterPath
            seasons.clear()
            mws.seasons.sortedBy { it.sortOrder }.forEach { s ->
                seasons.add(SeasonForm(
                    id           = s.id,
                    name         = s.name,
                    totalStr     = s.totalEpisodes?.toString() ?: "",
                    unknownTotal = s.totalEpisodes == null,
                    isOva        = s.isOva,
                    watched      = s.watchedEpisodes,
                    isFinished   = s.isFinished
                ))
            }
        } else if (isNew && seasons.isEmpty()) {
            // Seed first entry so the form is never blank
            when (defaultType) {
                ContentType.MOVIE  -> seasons.add(SeasonForm(name = "Movie",    unknownTotal = true))
                ContentType.SERIES -> seasons.add(SeasonForm(name = "Season 1", unknownTotal = true))
                ContentType.ANIME  -> seasons.add(SeasonForm(name = "Season 1", unknownTotal = true))
            }
        }
    }

    // Image picker
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri = uri
    }

    val isMovie = type == ContentType.MOVIE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add ${type.displayName}" else "Edit") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Poster ───────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { imageLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val ctx = LocalContext.current
                val imageData: Any? = pickedUri
                    ?: savedPath?.let { vm.imageFile(it).takeIf { f -> f.exists() } }

                if (imageData != null) {
                    AsyncImage(
                        model            = ImageRequest.Builder(ctx).data(imageData).crossfade(true).build(),
                        contentDescription = "Poster",
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                }
                Icon(
                    Icons.Default.AddPhotoAlternate, "Add poster",
                    Modifier.size(40.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            // ── Type selector (only when adding new) ─────────────────────────
            if (isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentType.entries.forEach { ct ->
                        FilterChip(
                            selected = type == ct,
                            onClick  = {
                                type = ct
                                seasons.clear()
                                if (ct == ContentType.MOVIE)
                                    seasons.add(SeasonForm(name = "Movie", unknownTotal = true))
                            },
                            label    = { Text(ct.displayName) }
                        )
                    }
                }
            }

            // ── Title ────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                label         = { Text("Title") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            // ── Tags ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = tags,
                onValueChange = { tags = it },
                label         = { Text("Tags (comma-separated)") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                placeholder   = { Text("e.g. action, sci-fi, favourite") }
            )

            // ── Seasons / OVAs ───────────────────────────────────────────────
            Text(
                text  = if (isMovie) "Entry" else if (type == ContentType.ANIME) "Seasons & OVAs" else "Seasons",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            seasons.forEachIndexed { idx, s ->
                SeasonFormCard(
                    form        = s,
                    showOvaToggle = type == ContentType.ANIME,
                    showEpisodes  = !isMovie,
                    canRemove     = if (isMovie) false else seasons.size > 1,
                    onChange    = { updated -> seasons[idx] = updated },
                    onRemove    = { seasons.removeAt(idx) }
                )
            }

            // Add season button (not for movies)
            if (!isMovie) {
                OutlinedButton(
                    onClick  = {
                        val n = seasons.size + 1
                        seasons.add(SeasonForm(name = "Season $n"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (type == ContentType.ANIME) "Add Season / OVA" else "Add Season")
                }
            }

            // ── Save button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    scope.launch {
                        val path = pickedUri?.let { vm.saveImage(it) } ?: savedPath
                        val item = MediaItem(
                            id         = if (isNew) 0L else mediaId,
                            title      = title.trim(),
                            type       = type,
                            tags       = tags.trim(),
                            posterPath = path
                        )
                        val seasonList = seasons.mapIndexed { i, f ->
                            Season(
                                id             = if (isNew) 0L else f.id,
                                name           = f.name.ifBlank { "Season ${i + 1}" },
                                totalEpisodes  = if (f.unknownTotal) null else f.totalStr.toIntOrNull(),
                                watchedEpisodes = f.watched,
                                isOva          = f.isOva,
                                isFinished     = f.isFinished,
                                sortOrder      = i
                            )
                        }
                        vm.save(item, seasonList)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = title.isNotBlank()
            ) {
                Text("Add")
            }
        }
    }
}

// ── Individual season card ─────────────────────────────────────────────────────

@Composable
private fun SeasonFormCard(
    form:          SeasonForm,
    showOvaToggle: Boolean,
    showEpisodes:  Boolean,
    canRemove:     Boolean,
    onChange:      (SeasonForm) -> Unit,
    onRemove:      () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    label         = { Text("Name") },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // OVA toggle (anime only)
            if (showOvaToggle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OVA", modifier = Modifier.weight(1f))
                    Switch(checked = form.isOva, onCheckedChange = { onChange(form.copy(isOva = it)) })
                }
            }

            // Episode count (not for movies)
            if (showEpisodes) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total episodes unknown", modifier = Modifier.weight(1f),
                         style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked         = form.unknownTotal,
                        onCheckedChange = { onChange(form.copy(unknownTotal = it, totalStr = "")) }
                    )
                }
                if (!form.unknownTotal) {
                    OutlinedTextField(
                        value         = form.totalStr,
                        onValueChange = { onChange(form.copy(totalStr = it.filter { c -> c.isDigit() })) },
                        label         = { Text("Total episodes") },
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true
                    )
                }
            }
        }
    }
}
