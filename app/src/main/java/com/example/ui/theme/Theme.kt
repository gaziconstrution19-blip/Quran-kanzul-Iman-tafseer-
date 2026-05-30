package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = OlivePrimaryDark,
    secondary = OliveHighlightDark,
    tertiary = KhakiTextDark,
    background = OliveDarkBackground,
    surface = OliveDarkSurface,
    onPrimary = OliveDarkBackground,
    onSecondary = CreamTextDark,
    onBackground = CreamTextDark,
    onSurface = CreamTextDark,
    surfaceVariant = OliveDarkSurfaceVariant,
    onSurfaceVariant = SageTextDark,
    outline = OliveDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = OlivePrimaryLight,
    secondary = OliveHighlightLight,
    tertiary = KhakiTextLight,
    background = CreamBackgroundLight,
    surface = WhiteSurfaceLight,
    onPrimary = CreamBackgroundLight,
    onSecondary = CharcoalTextLight,
    onBackground = CharcoalTextLight,
    onSurface = CharcoalTextLight,
    surfaceVariant = CreamSurfaceVariantLight,
    onSurfaceVariant = SageTextLight,
    outline = CreamOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enforce our elegant unified theme choice
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
