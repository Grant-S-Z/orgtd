package dev.orgtd.ui.theme

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

private val Green = Color(0xFF3F6F52)
private val GreenDark = Color(0xFFA8D5B5)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC1EBCE),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF526350),
    background = Color(0xFFFBFDF8),
    surface = Color(0xFFFBFDF8),
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF0A381D),
    primaryContainer = Color(0xFF265133),
    onPrimaryContainer = Color(0xFFC1EBCE),
    secondary = Color(0xFFB9CCB9),
    background = Color(0xFF191C19),
    surface = Color(0xFF191C19),
)

@Composable
fun OrgtdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
