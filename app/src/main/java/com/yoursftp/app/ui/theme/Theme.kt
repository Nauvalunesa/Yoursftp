package com.yoursftp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),         // Sky Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF4F46E5),       // Indigo
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),      // Clean white-grey
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    outline = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),         // Premium Neon Sky Blue
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF818CF8),       // Neon Indigo
    onSecondary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),      // Slate 900
    surface = Color(0xFF1E293B),         // Slate 800
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    outline = Color(0xFF475569)          // Slate 600
)

@Composable
fun YoursFtpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
