package com.tresorsdeparis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldDark,
    onPrimary = Color.Black,
    secondaryContainer = GoldDark.copy(alpha = 0.25f),
)

private val LightColorScheme = lightColorScheme(
    primary = GoldLight,
    onPrimary = Color.White,
    secondaryContainer = GoldLight.copy(alpha = 0.18f),
)

@Composable
fun TresorsDeParisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
