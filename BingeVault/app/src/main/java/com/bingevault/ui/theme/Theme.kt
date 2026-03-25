package com.bingevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary          = Color(0xFFCF6679),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF6D1D2D),
    secondary        = Color(0xFF9C8FA0),
    background       = Color(0xFF0E0E0E),
    surface          = Color(0xFF1C1C1E),
    surfaceVariant   = Color(0xFF2C2C2E),
    onBackground     = Color(0xFFF0F0F0),
    onSurface        = Color(0xFFF0F0F0),
    error            = Color(0xFFFF6B6B)
)

@Composable
fun BingeVaultTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, content = content)
