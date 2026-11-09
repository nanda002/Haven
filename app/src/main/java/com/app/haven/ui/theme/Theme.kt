package com.app.haven.ui.theme

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
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)

private val LightColorScheme = lightColorScheme(
    primary = HavenBlue,
    onPrimary = Color.White,
    primaryContainer = HavenBlueContainer,
    onPrimaryContainer = HavenBlueDark,
    inversePrimary = Color(0xFF9CC9F0),

    secondary = Color(0xFF52677D),
    onSecondary = Color.White,
    secondaryContainer = HavenBlueContainer,   // nav bar highlight, chips
    onSecondaryContainer = HavenBlueDark,

    tertiary = Color(0xFF4A7FA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEBF7),
    onTertiaryContainer = HavenBlueDark,

    background = HavenBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = HavenBackground,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE1EBF3),
    onSurfaceVariant = Color(0xFF44525F),
    surfaceTint = HavenBlue,
    inverseSurface = Color(0xFF2F3A45),
    inverseOnSurface = Color(0xFFEFF5FA),

    // cards, nav bar, dialogs, text fields all draw from these
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FBFE),
    surfaceContainer = Color(0xFFEAF3FA),
    surfaceContainerHigh = Color(0xFFE3EEF7),
    surfaceContainerHighest = Color(0xFFDCE8F2),

    outline = Color(0xFF7A8FA3),
    outlineVariant = Color(0xFFC3D3E0)
)

@Composable
fun HavenTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
