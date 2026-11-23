package dev.orgtd.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.orgtd.AppStrings

/** 当前语言下的文案；由 MainActivity 按设置注入。 */
val LocalStrings = staticCompositionLocalOf { AppStrings(false) }
