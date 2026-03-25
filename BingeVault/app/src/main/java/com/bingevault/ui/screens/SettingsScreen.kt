package com.bingevault.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bingevault.AdManager
import com.bingevault.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel = viewModel()) {
    val ctx          = LocalContext.current
    val scope        = rememberCoroutineScope()
    var busy         by remember { mutableStateOf(false) }
    val crossSearch  by vm.crossSectionSearch.collectAsStateWithLifecycle()
    val gridColumns  by vm.gridColumns.collectAsStateWithLifecycle()
    val adFreeUntil  by vm.adFreeUntil.collectAsStateWithLifecycle()
    val isAdFree     = System.currentTimeMillis() < adFreeUntil

    // Format remaining ad-free time
    val adFreeLabel = remember(adFreeUntil) {
        if (System.currentTimeMillis() < adFreeUntil) {
            val remaining = adFreeUntil - System.currentTimeMillis()
            val hours     = remaining / (1000 * 60 * 60)
            val minutes   = (remaining % (1000 * 60 * 60)) / (1000 * 60)
            "Ad-free for ${hours}h ${minutes}m"
        } else null
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            vm.exportVault(ctx, uri)
                .onSuccess { Toast.makeText(ctx, "Vault exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            vm.mergeVault(ctx, uri)
                .onSuccess { n ->
                    val msg = if (n == 0) "Nothing new to import" else "$n title(s) imported"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(ctx, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Search",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape    = MaterialTheme.shapes.medium
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cross-section search", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Search across all sections, not just the one you're in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked         = crossSearch,
                        onCheckedChange = vm::setCrossSectionSearch
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Library",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape    = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Posters per row", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Portrait: $gridColumns columns  •  Landscape: auto-fills width",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { n ->
                            FilterChip(
                                selected = gridColumns == n,
                                onClick  = { vm.setGridColumns(n) },
                                label    = { Text("$n") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Ads ───────────────────────────────────────────────────────────
            Text(
                "Ads",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape    = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isAdFree && adFreeLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(adFreeLabel, style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text  = if (isAdFree) "Watch another ad to extend your ad-free time"
                                else "Watch a short ad to remove ads for 24 hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick  = {
                            val activity = ctx as? Activity ?: return@Button
                            if (!AdManager.isRewardedReady()) {
                                Toast.makeText(ctx, "Ad not ready yet, try again shortly", Toast.LENGTH_SHORT).show()
                                AdManager.preloadRewarded(ctx)
                                return@Button
                            }
                            AdManager.showRewarded(
                                activity    = activity,
                                onRewarded  = {
                                    vm.grantAdFreeDay()
                                    Toast.makeText(ctx, "Ads removed for 24 hours!", Toast.LENGTH_SHORT).show()
                                },
                                onDismissed = {}
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Watch ad — remove ads for 24 hours")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Vault",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            BackupCard(
                icon     = Icons.Default.FileUpload,
                title    = "Export",
                subtitle = "Save your entire vault to a .bvault file",
                enabled  = !busy,
                onClick  = { exportLauncher.launch("BingeVault_$timestamp.bvault") }
            )

            BackupCard(
                icon     = Icons.Default.FileDownload,
                title    = "Import",
                subtitle = "Load a .bvault file and add any missing titles",
                enabled  = !busy,
                onClick  = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
            )

            if (busy) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("App version") },
                supportingContent = { Text("BingeVault 1.0") },
                leadingContent    = { Icon(Icons.Default.Info, null) }
            )
        }
    }
}

@Composable
private fun BackupCard(
    icon:    ImageVector,
    title:   String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape    = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, null,
                 tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}
