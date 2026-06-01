package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = GoldenBrass,
    secondary = AntiqueGold,
    tertiary = SacredGreen,
    background = DeepTeal,
    surface = TealSurface,
    onPrimary = DeepTeal,
    onSecondary = DeepTeal,
    onBackground = WhiteTealHint,
    onSurface = WhiteTealHint,
    surfaceVariant = Color(0xFF0C3C40),
    onSurfaceVariant = WhiteTealHint
)

private val LightColorScheme = lightColorScheme(
    primary = TealSurface,
    secondary = GoldenBrass,
    tertiary = SacredGreen,
    background = Color(0xFFF0F7F7),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = DeepTeal,
    onBackground = DeepTeal,
    onSurface = DeepTeal,
    surfaceVariant = Color(0xFFDFECEE),
    onSurfaceVariant = DeepTeal
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We default dynamicColor to false to maintain our stunning traditional color identity
    dynamicColor: Boolean = false,
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
