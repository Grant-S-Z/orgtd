package dev.orgtd.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 一套主题 = 一个 Light + 一个 Dark 的 Material 3 配色。
 *
 * 只手工定角色色（primary/secondary/tertiary 及其容器、表面、描边），
 * error 系列沿用 M3 基线，避免每套主题都要重新检查一遍对比度。
 */
data class ThemePalette(
    val id: String,
    val name: String,
    val nameEn: String,
    val seed: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private fun light(
    primary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    secondaryContainer: Long,
    onSecondaryContainer: Long,
    tertiary: Long,
    tertiaryContainer: Long,
    onTertiaryContainer: Long,
    background: Long,
    onSurface: Long,
    surfaceVariant: Long,
    onSurfaceVariant: Long,
    outline: Long,
): ColorScheme = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onSurface),
    surface = Color(background),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    outline = Color(outline),
    outlineVariant = Color(surfaceVariant),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(background),
    surfaceContainer = Color(surfaceVariant),
    surfaceContainerHigh = Color(surfaceVariant),
    surfaceContainerHighest = Color(surfaceVariant),
)

private fun dark(
    primary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    secondaryContainer: Long,
    onSecondaryContainer: Long,
    tertiary: Long,
    tertiaryContainer: Long,
    onTertiaryContainer: Long,
    background: Long,
    onSurface: Long,
    surfaceVariant: Long,
    onSurfaceVariant: Long,
    outline: Long,
): ColorScheme = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    onSecondary = Color(0xFF0A120A),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(0xFF00201F),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onSurface),
    surface = Color(background),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    outline = Color(outline),
    outlineVariant = Color(surfaceVariant),
    surfaceContainerLowest = Color(0xFF0C0E0C),
    surfaceContainerLow = Color(background),
    surfaceContainer = Color(surfaceVariant),
    surfaceContainerHigh = Color(surfaceVariant),
    surfaceContainerHighest = Color(surfaceVariant),
)

object OrgtdPalettes {

    const val DYNAMIC = "dynamic"
    const val DEFAULT = "ocean"

    val forest = ThemePalette(
        id = "forest",
        name = "林间绿", nameEn = "Forest",
        seed = Color(0xFF3F6F52),
        light = light(
            primary = 0xFF3F6F52, primaryContainer = 0xFFC1EBCE, onPrimaryContainer = 0xFF00210F,
            secondary = 0xFF526350, secondaryContainer = 0xFFD5E8D2, onSecondaryContainer = 0xFF101F10,
            tertiary = 0xFF3A6668, tertiaryContainer = 0xFFBCEBED, onTertiaryContainer = 0xFF002021,
            background = 0xFFFBFDF8, onSurface = 0xFF191C19,
            surfaceVariant = 0xFFDEE5DB, onSurfaceVariant = 0xFF424940, outline = 0xFF72796F,
        ),
        dark = dark(
            primary = 0xFFA5D3B3, primaryContainer = 0xFF265133, onPrimaryContainer = 0xFFC1EBCE,
            secondary = 0xFFB9CCB9, secondaryContainer = 0xFF3A4B3A, onSecondaryContainer = 0xFFD5E8D2,
            tertiary = 0xFFA0CFD0, tertiaryContainer = 0xFF1E4A4C, onTertiaryContainer = 0xFFBCEBED,
            background = 0xFF191C19, onSurface = 0xFFE1E3DD,
            surfaceVariant = 0xFF424940, onSurfaceVariant = 0xFFC2C9BF, outline = 0xFF8C9388,
        ),
    )

    val ocean = ThemePalette(
        id = "ocean",
        name = "海盐蓝", nameEn = "Ocean",
        seed = Color(0xFF31628D),
        light = light(
            primary = 0xFF31628D, primaryContainer = 0xFFD0E4FF, onPrimaryContainer = 0xFF001D33,
            secondary = 0xFF526070, secondaryContainer = 0xFFD5E4F5, onSecondaryContainer = 0xFF0F1D2A,
            tertiary = 0xFF6A5777, tertiaryContainer = 0xFFF0DBFC, onTertiaryContainer = 0xFF251432,
            background = 0xFFF8F9FB, onSurface = 0xFF191C1E,
            surfaceVariant = 0xFFDFE2E6, onSurfaceVariant = 0xFF43474C, outline = 0xFF73777C,
        ),
        dark = dark(
            primary = 0xFF9ECBF9, primaryContainer = 0xFF14497A, onPrimaryContainer = 0xFFD0E4FF,
            secondary = 0xFFB9C8DB, secondaryContainer = 0xFF3A4859, onSecondaryContainer = 0xFFD5E4F5,
            tertiary = 0xFFD5BFE3, tertiaryContainer = 0xFF523F5F, onTertiaryContainer = 0xFFF0DBFC,
            background = 0xFF111416, onSurface = 0xFFE1E2E5,
            surfaceVariant = 0xFF43474C, onSurfaceVariant = 0xFFC3C7CC, outline = 0xFF8D9196,
        ),
    )

    val amber = ThemePalette(
        id = "amber",
        name = "琥珀暖沙", nameEn = "Amber",
        seed = Color(0xFF7D5800),
        light = light(
            primary = 0xFF7D5800, primaryContainer = 0xFFFFDEA6, onPrimaryContainer = 0xFF271900,
            secondary = 0xFF6C5D3F, secondaryContainer = 0xFFF4E0BC, onSecondaryContainer = 0xFF251A04,
            tertiary = 0xFF4C6545, tertiaryContainer = 0xFFCEEBC2, onTertiaryContainer = 0xFF082100,
            background = 0xFFFFF8F0, onSurface = 0xFF1F1B16,
            surfaceVariant = 0xFFEDE0CF, onSurfaceVariant = 0xFF4F4539, outline = 0xFF817567,
        ),
        dark = dark(
            primary = 0xFFEEBE72, primaryContainer = 0xFF5E4200, onPrimaryContainer = 0xFFFFDEA6,
            secondary = 0xFFD8C4A0, secondaryContainer = 0xFF52452F, onSecondaryContainer = 0xFFF4E0BC,
            tertiary = 0xFFB3CFA6, tertiaryContainer = 0xFF354D2F, onTertiaryContainer = 0xFFCEEBC2,
            background = 0xFF1F1B16, onSurface = 0xFFEDE0D4,
            surfaceVariant = 0xFF4F4539, onSurfaceVariant = 0xFFD3C4B4, outline = 0xFF9C8F80,
        ),
    )

    val iris = ThemePalette(
        id = "iris",
        name = "鸢尾紫", nameEn = "Iris",
        seed = Color(0xFF6750A4),
        light = light(
            primary = 0xFF6750A4, primaryContainer = 0xFFEADDFF, onPrimaryContainer = 0xFF21005D,
            secondary = 0xFF625B71, secondaryContainer = 0xFFE8DEF8, onSecondaryContainer = 0xFF1D192B,
            tertiary = 0xFF7D5260, tertiaryContainer = 0xFFFFD8E4, onTertiaryContainer = 0xFF31111D,
            background = 0xFFFFFBFE, onSurface = 0xFF1C1B1F,
            surfaceVariant = 0xFFE7E0EC, onSurfaceVariant = 0xFF49454F, outline = 0xFF79747E,
        ),
        dark = dark(
            primary = 0xFFD0BCFF, primaryContainer = 0xFF4F378B, onPrimaryContainer = 0xFFEADDFF,
            secondary = 0xFFCCC2DC, secondaryContainer = 0xFF4A4458, onSecondaryContainer = 0xFFE8DEF8,
            tertiary = 0xFFEFB8C8, tertiaryContainer = 0xFF633B48, onTertiaryContainer = 0xFFFFD8E4,
            background = 0xFF1C1B1F, onSurface = 0xFFE6E1E5,
            surfaceVariant = 0xFF49454F, onSurfaceVariant = 0xFFCAC4D0, outline = 0xFF938F99,
        ),
    )

    val graphite = ThemePalette(
        id = "graphite",
        name = "石墨灰", nameEn = "Graphite",
        seed = Color(0xFF5D5F5F),
        light = light(
            primary = 0xFF5D5F5F, primaryContainer = 0xFFE2E3E2, onPrimaryContainer = 0xFF1A1C1C,
            secondary = 0xFF5E605F, secondaryContainer = 0xFFE2E3E2, onSecondaryContainer = 0xFF1A1C1C,
            tertiary = 0xFF5E5F62, tertiaryContainer = 0xFFE3E3E4, onTertiaryContainer = 0xFF1B1C1E,
            background = 0xFFFBFBF9, onSurface = 0xFF1A1C1B,
            surfaceVariant = 0xFFE2E3E1, onSurfaceVariant = 0xFF444846, outline = 0xFF747876,
        ),
        dark = dark(
            primary = 0xFFC5C7C4, primaryContainer = 0xFF454745, onPrimaryContainer = 0xFFE2E3E1,
            secondary = 0xFFC5C7C4, secondaryContainer = 0xFF454745, onSecondaryContainer = 0xFFE2E3E1,
            tertiary = 0xFFC7C6CB, tertiaryContainer = 0xFF47464A, onTertiaryContainer = 0xFFE4E2E7,
            background = 0xFF121413, onSurface = 0xFFE2E3E1,
            surfaceVariant = 0xFF444846, onSurfaceVariant = 0xFFC4C7C4, outline = 0xFF8E918E,
        ),
    )

    val all: List<ThemePalette> = listOf(forest, ocean, amber, iris, graphite)

    fun byId(id: String): ThemePalette = all.firstOrNull { it.id == id } ?: forest

    /** 「跟随壁纸」只在 Android 12+ 有意义。 */
    val dynamicEntry = ThemePalette(
        id = DYNAMIC,
        name = "跟随壁纸", nameEn = "Wallpaper",
        seed = Color(0xFF7A8B7F),
        light = forest.light,
        dark = forest.dark,
    )
}

/** 主题名按当前界面语言显示。 */
fun ThemePalette.displayName(english: Boolean): String = if (english) nameEn else name
