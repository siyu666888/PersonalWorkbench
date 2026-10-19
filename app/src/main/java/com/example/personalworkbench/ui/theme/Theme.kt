package com.example.personalworkbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 功能语义色 (Success/Warning/Danger/Info) 并非 Material 3 ColorScheme 标准字段，
 * 通过 CompositionLocal 暴露给全站使用 (UI规范 1.2 功能语义色)。
 */
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val ai: Color,
    val aiContainer: Color
)

val LocalSemanticColors = compositionLocalOf {
    SemanticColors(
        success = Success,
        warning = Warning,
        danger = Danger,
        info = Info,
        ai = Ai,
        aiContainer = AiContainer
    )
}

// ---- 浅色主题 ColorScheme (对应 UI规范 1.2 浅色色值) ----
private val LightColorScheme = lightColorScheme(
    primary = Purple500,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Purple100,
    onPrimaryContainer = Purple900,
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D1B20),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Error500,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Surface0,
    onBackground = Color(0xFF1D1B20),
    surface = Surface0,
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Purple200,
    scrim = Color(0xFF000000)
)

// ---- 深色主题 ColorScheme (对应 UI规范 1.2 深色色值) ----
private val DarkColorScheme = darkColorScheme(
    primary = Purple200,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Purple700,
    onPrimaryContainer = Purple100,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Error300,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343B),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Purple500,
    scrim = Color(0xFF000000)
)

/**
 * 应用主题入口。
 * darkTheme 由调用方依据 DataStore 中的「主题偏好」与系统设置共同决定；
 * accentIndex 选择强调色预设 (见 AccentOptions)，会覆盖 primary 系列色。
 */
@Composable
fun PersonalWorkbenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    // 由强调色基准值推算浅色/深色下的 primary 系列 (M3 近似色阶)
    val base = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val lightPrimary = base
    val lightOnPrimary = Color.White
    val lightPrimaryContainer = lerp(base, Color.White, 0.84f)
    val lightOnPrimaryContainer = lerp(base, Color.Black, 0.32f)
    val darkPrimary = lerp(base, Color.White, 0.40f)
    val darkOnPrimary = lerp(base, Color.Black, 0.62f)
    val darkPrimaryContainer = lerp(base, Color.Black, 0.50f)
    val darkOnPrimaryContainer = lerp(base, Color.White, 0.82f)

    val lightScheme = LightColorScheme.copy(
        primary = lightPrimary,
        onPrimary = lightOnPrimary,
        primaryContainer = lightPrimaryContainer,
        onPrimaryContainer = lightOnPrimaryContainer
    )
    val darkScheme = DarkColorScheme.copy(
        primary = darkPrimary,
        onPrimary = darkOnPrimary,
        primaryContainer = darkPrimaryContainer,
        onPrimaryContainer = darkOnPrimaryContainer
    )

    val colorScheme = if (darkTheme) darkScheme else lightScheme
    val semantic = SemanticColors(
        success = Success,
        warning = Warning,
        danger = Danger,
        info = Info,
        ai = if (darkTheme) AiLight else Ai,
        aiContainer = if (darkTheme) AiContainerDark else AiContainer
    )
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PersonalWorkbenchTypography,
            shapes = PersonalWorkbenchShapes,
            content = content
        )
    }
}
