package dev.orgtd.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** true 时使用 iOS 风格的列表、分段控件与底栏。 */
val LocalAppleStyle = staticCompositionLocalOf { false }
