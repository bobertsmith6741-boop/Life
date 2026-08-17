package com.mockpilot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF2E7D5B)
private val GreenLight = Color(0xFF39D98A)
private val Ink = Color(0xFF0B3D2E)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F0D2),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF4C6358),
    tertiary = Color(0xFF3D6373),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Ink,
    primaryContainer = Color(0xFF1B5240),
    onPrimaryContainer = Color(0xFFB6F0D2),
    secondary = Color(0xFFB3CCBE),
    tertiary = Color(0xFFA4CDDE),
)

@Composable
fun MockPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
