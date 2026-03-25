package com.bingevault.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.bingevault.data.ContentType
import com.bingevault.data.PrefsManager
import com.bingevault.ui.screens.*
import com.bingevault.viewmodel.MainViewModel

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav   = rememberNavController()
    val back  by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route

    // Ad-free check — recompose every time adFreeUntil changes
    val adFreeUntil by vm.adFreeUntil.collectAsStateWithLifecycle()
    val showAds = System.currentTimeMillis() >= adFreeUntil

    Scaffold(
        bottomBar = {
            Column {
                // Banner sits directly above the nav bar, hidden during ad-free period
                if (showAds) {
                    BannerAdView()
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = route == "library" || route?.startsWith("detail") == true || route?.startsWith("add_edit") == true,
                        onClick  = { nav.navigate("library") { launchSingleTop = true; popUpTo("library") { inclusive = false } } },
                        icon     = { Icon(Icons.Default.VideoLibrary, null) },
                        label    = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = route == "settings",
                        onClick  = { nav.navigate("settings") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Default.Settings, null) },
                        label    = { Text("Settings") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController       = nav,
            startDestination    = "library",
            modifier            = Modifier.padding(padding),
            enterTransition     = { EnterTransition.None },
            exitTransition      = { ExitTransition.None },
            popEnterTransition  = { EnterTransition.None },
            popExitTransition   = { ExitTransition.None }
        ) {
            composable("library") {
                LibraryScreen(
                    vm     = vm,
                    onAdd  = { type -> nav.navigate("add_edit/-1/${type.name}") },
                    onItem = { id   -> nav.navigate("detail/$id") }
                )
            }

            composable("settings") {
                SettingsScreen(vm = vm)
            }

            composable(
                "add_edit/{id}/{type}",
                arguments = listOf(
                    navArgument("id")   { type = NavType.LongType  },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { entry ->
                val id      = entry.arguments!!.getLong("id")
                val defType = ContentType.valueOf(entry.arguments!!.getString("type")!!)
                AddEditScreen(mediaId = id, defaultType = defType, vm = vm) { nav.popBackStack() }
            }

            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments!!.getLong("id")
                DetailScreen(
                    mediaId = id,
                    vm      = vm,
                    onEdit  = { type -> nav.navigate("add_edit/$id/${type.name}") },
                    onBack  = { nav.popBackStack() }
                )
            }
        }
    }
}
