package com.jarvis.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentOrange,
    background = NavyBackground,
    surface = NavyCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(0xFFFF6B6B)
)

@Composable
fun JarvisAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
