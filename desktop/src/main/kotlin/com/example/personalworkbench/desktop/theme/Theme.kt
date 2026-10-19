package com.example.personalworkbench.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 桌面版主题（V1.4.1 重设计）
 *
 * 与 Android 版的关键差异（此前「配色不协调」的根因）：
 *  1. **中性色偏冷**：Android 版直接用 M3 紫调 surface（Surface0 #FEF7FF 等），
 *     在大屏上大面积铺开会显得偏紫发灰；桌面版改用**冷中性灰**（#F6F7F9 → #E2E6EB）
 *     建立 5 级表面层级，主色只用于强调，不再浸染背景。
 *  2. **主色加深提纯**：#6750A4 → #5A4FCF，白底对比度更高、点击态更明确。
 *  3. **层级靠描边而非重阴影**：桌面端用 1px 淡边框区分卡片，避免 Material 默认阴影过重。
 */

/** 功能语义色（非 M3 标准字段，通过 CompositionLocal 暴露） */
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val ai: Color,
    val aiContainer: Color,
    val border: Color
)

val LocalSemanticColors = compositionLocalOf {
    SemanticColors(
        success = Success,
        warning = Warning,
        danger = Danger,
        info = Info,
        ai = Ai,
        aiContainer = AiContainer,
        border = Color(0xFFE2E6EB)
    )
}

// ---------------- 浅色 ----------------
private val LightColors = lightColorScheme(
    primary = Color(0xFF5A4FCF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E5FF),
    onPrimaryContainer = Color(0xFF1B1560),
    secondary = Color(0xFF5A6270),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEFF3),
    onSecondaryContainer = Color(0xFF1A1D24),
    tertiary = Color(0xFF0F7B6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F0EA),
    onTertiaryContainer = Color(0xFF06302A),
    error = Color(0xFFD93A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE0E0),
    onErrorContainer = Color(0xFF5C1414),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1D24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5A6270),
    outline = Color(0xFFB9C0CA),
    outlineVariant = Color(0xFFE2E6EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFEBEEF2),
    surfaceContainerHighest = Color(0xFFE2E6EB),
    inverseSurface = Color(0xFF262A31),
    inverseOnSurface = Color(0xFFF2F4F7),
    inversePrimary = Color(0xFFC5C0FF),
    scrim = Color(0xFF000000)
)

// ---------------- 深色 ----------------
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9A2FF),
    onPrimary = Color(0xFF1B1560),
    primaryContainer = Color(0xFF322C6B),
    onPrimaryContainer = Color(0xFFE7E5FF),
    secondary = Color(0xFFB6BEC9),
    onSecondary = Color(0xFF262A31),
    secondaryContainer = Color(0xFF3A424E),
    onSecondaryContainer = Color(0xFFEDEFF3),
    tertiary = Color(0xFF6FD3C0),
    onTertiary = Color(0xFF06302A),
    tertiaryContainer = Color(0xFF14564C),
    onTertiaryContainer = Color(0xFFD3F0EA),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF5C1414),
    errorContainer = Color(0xFF7A2222),
    onErrorContainer = Color(0xFFFBE0E0),
    background = Color(0xFF101216),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF16191F),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF2B313A),
    onSurfaceVariant = Color(0xFF9AA3B0),
    outline = Color(0xFF49515D),
    outlineVariant = Color(0xFF2B313A),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF16191F),
    surfaceContainer = Color(0xFF1C2027),
    surfaceContainerHigh = Color(0xFF232830),
    surfaceContainerHighest = Color(0xFF2B313A),
    inverseSurface = Color(0xFFE6E9EE),
    inverseOnSurface = Color(0xFF16191F),
    inversePrimary = Color(0xFF5A4FCF),
    scrim = Color(0xFF000000)
)

@Composable
fun PersonalWorkbenchTheme(
    darkTheme: Boolean = false,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val base = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val light = LightColors.copy(
        primary = base,
        onPrimary = Color.White,
        primaryContainer = lerp(base, Color.White, 0.86f),
        onPrimaryContainer = lerp(base, Color.Black, 0.34f)
    )
    val dark = DarkColors.copy(
        primary = lerp(base, Color.White, 0.42f),
        onPrimary = lerp(base, Color.Black, 0.64f),
        primaryContainer = lerp(base, Color.Black, 0.48f),
        onPrimaryContainer = lerp(base, Color.White, 0.84f)
    )
    val scheme = if (darkTheme) dark else light
    val semantic = SemanticColors(
        success = if (darkTheme) Color(0xFF5BD98C) else Success,
        warning = if (darkTheme) Color(0xFFF0A93B) else Warning,
        danger = if (darkTheme) Color(0xFFFF8A8A) else Danger,
        info = if (darkTheme) Color(0xFF6FB0FF) else Info,
        ai = if (darkTheme) AiLight else Ai,
        aiContainer = if (darkTheme) AiContainerDark else AiContainer,
        border = scheme.outlineVariant
    )
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DesktopTypography,
            shapes = DesktopShapes,
            content = content
        )
    }
}
