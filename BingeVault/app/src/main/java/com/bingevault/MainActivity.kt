package com.bingevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.bingevault.ui.AppRoot
import com.bingevault.ui.theme.BingeVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: content draws behind status + nav bars on all screen sizes.
        // safeDrawingPadding() in AppRoot keeps composables out of system UI areas.
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BingeVaultTheme {
                AppRoot()
            }
        }
    }
}
