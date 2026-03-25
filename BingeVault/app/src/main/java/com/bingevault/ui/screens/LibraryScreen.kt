package com.bingevault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bingevault.data.*
import com.bingevault.viewmodel.MainViewModel
import java.io.File

private const val PAGE_SIZE = 20

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onAdd: (ContentType) -> Unit,
    onItem: (Long) -> Unit
) {
    val activeType   by vm.activeType.collectAsStateWithLifecycle()
    val search       by vm.search.collectAsStateWithLifecycle()
    val page         by vm.page.collectAsStateWithLifecycle()
    val displayItems by vm.displayItems.collectAsStateWithLifecycle()
    val gridColumns  by vm.gridColumns.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var selectedIds  by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val haptic        = LocalHapticFeedback.current

    // Keep selection valid after deletes — use a Set lookup instead of linear scan
    val displayIds by remember { derivedStateOf { displayItems.map { it.media.id }.toHashSet() } }
    LaunchedEffect(displayIds) {
        if (selectedIds.isNotEmpty())
            selectedIds = selectedIds.intersect(displayIds)
    }

    // Stable pagination — only recalculate when list or page changes
    val totalPages by remember { derivedStateOf {
        ((displayItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }}
    val pageItems by remember { derivedStateOf {
        displayItems.drop(page * PAGE_SIZE).take(PAGE_SIZE)
    }}

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(visible = !selectionMode, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(
                    onClick        = { onAdd(activeType) },
                    shape          = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add", Modifier.size(30.dp))
                }
            }
        }
    ) { scaffoldPadding ->
        Column(Modifier.padding(scaffoldPadding).fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = search,
                    onValueChange = vm::setSearch,
                    modifier      = Modifier.weight(1f).height(52.dp),
                    singleLine    = true,
                    placeholder   = { Text("Search…", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon   = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    shape         = RoundedCornerShape(50),
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor    = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor      = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(Modifier.width(8.dp))

                if (selectionMode) {
                    IconButton(onClick = {
                        vm.delete(displayItems.filter { it.media.id in selectedIds }.map { it.media })
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                } else {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                        DropdownMenu(
                            expanded         = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier         = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            ContentType.entries.forEach { type ->
                                val active = type == activeType
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text       = type.displayName,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            color      = if (active) MaterialTheme.colorScheme.primary
                                                         else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = if (active) ({
                                        Icon(Icons.Default.CheckCircle, null,
                                             tint = MaterialTheme.colorScheme.primary)
                                    }) else null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.setType(type)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Section title ────────────────────────────────────────────────
            when {
                search.isBlank() -> Text(
                    text     = activeType.displayName,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                )
                selectionMode -> Text(
                    text     = "${selectedIds.size} selected",
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                )
            }

            // ── Grid ─────────────────────────────────────────────────────────
            if (displayItems.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing here yet.\nTap + to add.",
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            } else {
                // minSize drives both the setting and landscape auto-expansion:
                // 2 col → 160dp, 3 col → 110dp, 4 col → 80dp
                // In landscape the extra width fits more columns automatically.
                val minItemDp = when (gridColumns) { 3 -> 110.dp; 4 -> 80.dp; else -> 160.dp }
                LazyVerticalGrid(
                    columns               = GridCells.Adaptive(minItemDp),
                    modifier              = Modifier.weight(1f),
                    contentPadding        = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp)
                ) {
                    items(pageItems, key = { it.media.id }) { mws ->
                        val id         = mws.media.id
                        val selected   = id in selectedIds
                        val imageFile  = remember(mws.media.posterPath) {
                            mws.media.posterPath?.let { vm.imageFile(it) }
                        }
                        PosterItem(
                            item        = mws,
                            isSelected  = selected,
                            imageFile   = imageFile,
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onClick = {
                                if (selectionMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                } else {
                                    onItem(id)
                                }
                            }
                        )
                    }   // end items
                }       // end LazyVerticalGrid
            }           // end else

            // ── Pagination ───────────────────────────────────────────────────
            if (totalPages > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.setPage(page - 1) }, enabled = page > 0) {
                        Icon(Icons.Default.ChevronLeft, null)
                    }
                    Text("${page + 1} / $totalPages", style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { vm.setPage(page + 1) }, enabled = page < totalPages - 1) {
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

// ── Poster card ────────────────────────────────────────────────────────────────

@Composable
private fun PosterItem(
    item:        MediaWithSeasons,
    isSelected:  Boolean,
    imageFile:   File?,
    onLongPress: () -> Unit,
    onClick:     () -> Unit
) {
    // Memo-ise expensive values so they survive recomposition
    val placeholderColor = remember(item.media.title) {
        val hue = ((item.media.title.hashCode() and 0x7FFFFFFF) % 360).toFloat()
        Color.hsv(hue, 0.35f, 0.25f)
    }
    val initials = remember(item.media.title) { item.media.title.take(2).uppercase() }
    val ctx      = LocalContext.current
    val imgModel = remember(imageFile) {
        imageFile?.takeIf { it.exists() }?.let { f ->
            ImageRequest.Builder(ctx)
                .data(f)
                .memoryCacheKey(f.path)
                .diskCacheKey(f.path)
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(item.media.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap       = { onClick() }
                )
            }
    ) {
        if (imgModel != null) {
            AsyncImage(
                model              = imgModel,
                contentDescription = item.media.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(placeholderColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = initials,
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title overlay
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Text(
                text      = item.media.title,
                style     = MaterialTheme.typography.labelMedium,
                color     = Color.White,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }

        // Selection overlay
        if (isSelected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)))
            Icon(
                imageVector        = Icons.Default.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
            )
        }
    }
}
