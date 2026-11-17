package me.avinas.tempo.ui.appearance

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Shared design tokens. These intentionally keep the legacy Abyss values unchanged.
 * All four initial options run on dark compatible surfaces so older screens with
 * fixed white text remain legible. True light mode is the next migration stage.
 */
data class AppearancePalette(
    val preset: AppearancePreset,
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val topBackground: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val surfaceSunken: Color,
    val onSurface: Color,
    val mutedText: Color,
    val border: Color,
) {
    fun materialColorScheme(): ColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = elevatedSurface,
        onPrimaryContainer = onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = elevatedSurface,
        onSecondaryContainer = onSurface,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = elevatedSurface,
        onSurfaceVariant = mutedText,
        surfaceContainerLowest = surfaceSunken,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = elevatedSurface,
        surfaceContainerHighest = elevatedSurface,
        surfaceDim = surfaceSunken,
        surfaceBright = elevatedSurface,
        inverseSurface = onSurface,
        inverseOnSurface = background,
        inversePrimary = accent,
        surfaceTint = accent,
        outline = border,
        outlineVariant = border.copy(alpha = 0.55f),
    )
}

object AppearancePaletteResolver {
    fun forPreset(preset: AppearancePreset): AppearancePalette = when (preset) {
        AppearancePreset.ABYSS -> AppearancePalette(
            preset = preset,
            accent = Color(0xFF2FDBB8), onAccent = Color(0xFF0A0E0E),
            background = Color(0xFF0A0E0E), topBackground = Color(0xFF101414),
            surface = Color(0xFF101414), elevatedSurface = Color(0xFF141818),
            surfaceSunken = Color(0xFF080A0A),
            onSurface = Color(0xFFE6EAEA), mutedText = Color(0xFF9AA2A2),
            border = Color(0xFF33403E),
        )
        AppearancePreset.OBSIDIAN_GOLD -> AppearancePalette(
            preset = preset,
            accent = Color(0xFFDDBA70), onAccent = Color(0xFF17110A),
            background = Color(0xFF050608), topBackground = Color(0xFF17191B),
            surface = Color(0xFF17191B), elevatedSurface = Color(0xFF242528),
            surfaceSunken = Color(0xFF050506),
            onSurface = Color(0xFFF0E7D2), mutedText = Color(0xFFAEA595),
            border = Color(0xFF65553B),
        )
        AppearancePreset.AURORA -> AppearancePalette(
            preset = preset,
            accent = Color(0xFFA78BFA), onAccent = Color(0xFF120B2B),
            background = Color(0xFF0F102A), topBackground = Color(0xFF211C40),
            surface = Color(0xFF20203C), elevatedSurface = Color(0xFF302A54),
            surfaceSunken = Color(0xFF0B0A20),
            onSurface = Color(0xFFF2ECFF), mutedText = Color(0xFFB7AFD2),
            border = Color(0xFF544776),
        )
        // Glacier is initially offered as an ice-blue *dark-compatible* style.
        // Light Glacier is preview-only until the rest of the UI uses semantic tokens.
        AppearancePreset.GLACIER -> AppearancePalette(
            preset = preset,
            accent = Color(0xFF72D0F2), onAccent = Color(0xFF082638),
            background = Color(0xFF0A1A27), topBackground = Color(0xFF173449),
            surface = Color(0xFF142A3C), elevatedSurface = Color(0xFF213F53),
            surfaceSunken = Color(0xFF07151F),
            onSurface = Color(0xFFF1F9FF), mutedText = Color(0xFFA9C5D7),
            border = Color(0xFF436478),
        )
    }
}
