package de.kruemmel.rufwaechter.ui.theme

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
import de.kruemmel.rufwaechter.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8C7),
    secondary = Color(0xFF4D6358),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70DBAC),
    primaryContainer = Color(0xFF005139),
    secondary = Color(0xFFB4CCBF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RufWaechterTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
