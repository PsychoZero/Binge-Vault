package com.bingevault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bingevault.data.*
import com.bingevault.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mediaId: Long,
    vm:      MainViewModel,
    onEdit:  (ContentType) -> Unit,
    onBack:  () -> Unit
) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val mws      = allItems.find { it.media.id == mediaId }

    if (mws == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val media   = mws.media
    var ascending by remember { mutableStateOf(true) }
    val seasons = mws.seasons.let { if (ascending) it.sortedBy { s -> s.sortOrder } else it.sortedByDescending { s -> s.sortOrder } }
    val ctx     = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(media.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { onEdit(media.type) }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Header: poster + meta ────────────────────────────────────────
            item(key = "header") {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.width(110.dp).height(165.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val imageData: Any? = media.posterPath?.let { vm.imageFile(it).takeIf { f -> f.exists() } }
                        if (imageData != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(imageData).crossfade(false).build(),
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val hue = ((media.title.hashCode() and 0x7FFFFFFF) % 360).toFloat()
                            Box(
                                Modifier.fillMaxSize().background(Color.hsv(hue, 0.35f, 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(media.title.take(2).uppercase(),
                                     style = MaterialTheme.typography.headlineSmall,
                                     color = Color.White.copy(alpha = 0.6f),
                                     fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(media.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AssistChip(onClick = {}, label = { Text(media.type.displayName, style = MaterialTheme.typography.labelSmall) })
                        if (media.tags.isNotBlank()) {
                            Text("Tags: ${media.tags}",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        val totalEp   = seasons.sumOf { it.totalEpisodes ?: 0 }
                        val watchedEp = seasons.sumOf { it.watchedEpisodes }
                        if (totalEp > 0)
                            Text("$watchedEp / $totalEp episodes watched",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item(key = "divider_top") {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }

            // ── Sort row ─────────────────────────────────────────────────────
            if (media.type != ContentType.MOVIE) {
                item(key = "sort_row") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = if (ascending) "First Added" else "Last Added",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick        = { ascending = !ascending },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (ascending) "Ascending" else "Descending",
                                 style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Seasons (virtualised — only visible ones are composed) ────────
            itemsIndexed(seasons, key = { _, s -> s.id }) { index, season ->
                SeasonSection(season = season, mediaType = media.type, vm = vm)
                if (index < seasons.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Season section ─────────────────────────────────────────────────────────────

@Composable
private fun SeasonSection(season: Season, mediaType: ContentType, vm: MainViewModel) {
    val haptic = LocalHapticFeedback.current

    // Stable typed update helpers
    val onWatched: (Int, Boolean) -> Unit = { n, finished ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.updateSeason(season.copy(watchedEpisodes = n, isFinished = finished))
    }
    val onFinish: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.updateSeason(season.copy(isFinished = true))
    }
    val onRewatch: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.updateSeason(season.copy(isFinished = false, watchedEpisodes = 0))
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = season.name,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f)
            )
            if (season.isOva) {
                SuggestionChip(onClick = {}, label = { Text("OVA", style = MaterialTheme.typography.labelSmall) })
                Spacer(Modifier.width(6.dp))
            }
            if (season.isFinished) {
                Icon(Icons.Default.CheckCircle, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            // ── MOVIE: watched toggle + rewatch ───────────────────────────
            mediaType == ContentType.MOVIE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Watched", Modifier.weight(1f))
                    if (season.isFinished) {
                        IconButton(onClick = onRewatch) {
                            Icon(Icons.Default.Replay, "Rewatch",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Switch(
                        checked         = season.isFinished,
                        onCheckedChange = { vm.updateSeason(season.copy(isFinished = it)) }
                    )
                }
            }

            // ── Known total: -1 | counter | +1  ──────────────────────────
            season.totalEpisodes != null -> {
                val total   = season.totalEpisodes
                val watched = season.watchedEpisodes
                var editing  by remember(season.id) { mutableStateOf(false) }
                var editText by remember(season.id) { mutableStateOf("") }

                LinearProgressIndicator(
                    progress = { if (total > 0) watched.toFloat() / total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.height(48.dp)
                ) {
                    // -1
                    OutlinedButton(
                        onClick        = { onWatched((watched - 1).coerceAtLeast(0), false) },
                        enabled        = watched > 0 && !season.isFinished,
                        modifier       = Modifier.fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) { Text("-1") }

                    // Tappable counter / inline edit field
                    if (editing) {
                        OutlinedTextField(
                            value         = editText,
                            onValueChange = { editText = it.filter { c -> c.isDigit() }.take(5) },
                            modifier      = Modifier.weight(1f).fillMaxHeight(),
                            singleLine    = true,
                            textStyle     = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                imeAction    = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                val n = editText.toIntOrNull()?.coerceIn(0, total) ?: watched
                                onWatched(n, n >= total)
                                editing = false
                            }),
                            placeholder = { Text("Ep #") }
                        )
                    } else {
                        Surface(
                            onClick        = { if (!season.isFinished) { editText = watched.toString(); editing = true } },
                            modifier       = Modifier.weight(1f).fillMaxHeight(),
                            shape          = MaterialTheme.shapes.small,
                            color          = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement   = Arrangement.Center,
                                horizontalAlignment   = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text     = if (season.isFinished) "Finished" else "Ep $watched / $total",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color    = if (season.isFinished) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // +1 or Rewatch
                    if (!season.isFinished) {
                        Button(
                            onClick        = {
                                val n = (watched + 1).coerceAtMost(total)
                                onWatched(n, n >= total)
                            },
                            modifier       = Modifier.fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) { Text("+1") }
                    } else {
                        IconButton(onClick = onRewatch, modifier = Modifier.fillMaxHeight()) {
                            Icon(Icons.Default.Replay, "Rewatch",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── Unknown total: counter | +1 | Finish / Rewatch ───────────
            else -> {
                var editingUnknown  by remember(season.id) { mutableStateOf(false) }
                var editTextUnknown by remember(season.id) { mutableStateOf("") }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.height(48.dp)
                ) {
                    // Tappable counter / inline edit
                    if (editingUnknown) {
                        OutlinedTextField(
                            value         = editTextUnknown,
                            onValueChange = { editTextUnknown = it.filter { c -> c.isDigit() }.take(5) },
                            modifier      = Modifier.weight(1f).fillMaxHeight(),
                            singleLine    = true,
                            textStyle     = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                imeAction    = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                val n = editTextUnknown.toIntOrNull() ?: season.watchedEpisodes
                                onWatched(n.coerceAtLeast(0), false)
                                editingUnknown = false
                            }),
                            placeholder = { Text("Ep #") }
                        )
                    } else {
                        Surface(
                            onClick        = { if (!season.isFinished) { editTextUnknown = season.watchedEpisodes.toString(); editingUnknown = true } },
                            modifier       = Modifier.weight(1f).fillMaxHeight(),
                            shape          = MaterialTheme.shapes.small,
                            color          = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text     = if (season.isFinished) "Finished" else "Episode ${season.watchedEpisodes}",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color    = if (season.isFinished) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                )
                                if (!season.isFinished) {
                                    Text(
                                        text  = "Tap to jump",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }

                    if (!season.isFinished) {
                        OutlinedButton(
                            onClick        = { onWatched(season.watchedEpisodes + 1, false) },
                            modifier       = Modifier.fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) { Text("+1") }
                        Button(
                            onClick        = onFinish,
                            modifier       = Modifier.fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) { Text("Finish") }
                    } else {
                        IconButton(onClick = onRewatch, modifier = Modifier.fillMaxHeight()) {
                            Icon(Icons.Default.Replay, "Rewatch",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
