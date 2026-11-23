package dev.orgtd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 全局主题。配色来自 [OrgtdPalettes]，也可以在 Android 12+ 跟随壁纸取色。
 *
 * @param themeId 主题 id，或 [OrgtdPalettes.DYNAMIC]
 * @param darkTheme 是否深色；由调用方根据「跟随系统 / 浅色 / 深色」决定
 */
@Composable
fun OrgtdTheme(
    themeId: String = OrgtdPalettes.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = themeId == OrgtdPalettes.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val palette = OrgtdPalettes.byId(themeId)

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> palette.dark
        else -> palette.light
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
