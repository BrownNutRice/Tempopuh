package me.avinas.tempo.ui.appearance

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color

/**
 * Shared design tokens. These intentionally keep the legacy Abyss values unchanged.
 * Presets have dedicated Light surface colors as well as Dark surface roles.
 * Some artwork-specific illustrations intentionally retain their design colors.
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
    val isDark: Boolean = true,
) {
    fun materialColorScheme(): ColorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
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
        AppearancePreset.EMBER -> AppearancePalette(
            preset = preset, accent = Color(0xFFFF91A3), onAccent = Color(0xFF300D18),
            background = Color(0xFF1C0D15), topBackground = Color(0xFF301822),
            surface = Color(0xFF28151E), elevatedSurface = Color(0xFF3A202B),
            surfaceSunken = Color(0xFF150910), onSurface = Color(0xFFFFEDF1),
            mutedText = Color(0xFFD0AEB8), border = Color(0xFF70414D),
        )
        AppearancePreset.SOLSTICE -> AppearancePalette(
            preset = preset, accent = Color(0xFFF0B47D), onAccent = Color(0xFF291508),
            background = Color(0xFF1B1510), topBackground = Color(0xFF30251C),
            surface = Color(0xFF2A2119), elevatedSurface = Color(0xFF3B2E22),
            surfaceSunken = Color(0xFF14100C), onSurface = Color(0xFFFFF3E4),
            mutedText = Color(0xFFCAB9A4), border = Color(0xFF705A42),
        )
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
        AppearancePreset.VERDANT -> AppearancePalette(
            preset = preset, accent = Color(0xFFACD47E), onAccent = Color(0xFF16230D),
            background = Color(0xFF111C12), topBackground = Color(0xFF233524),
            surface = Color(0xFF1B2C1D), elevatedSurface = Color(0xFF293D29),
            surfaceSunken = Color(0xFF0B140D), onSurface = Color(0xFFF1F7E8),
            mutedText = Color(0xFFB9C9AF), border = Color(0xFF526E50),
        )
        AppearancePreset.SLATE -> AppearancePalette(
            preset = preset, accent = Color(0xFFBDC9DB), onAccent = Color(0xFF18202D),
            background = Color(0xFF16191F), topBackground = Color(0xFF272D38),
            surface = Color(0xFF1E252E), elevatedSurface = Color(0xFF303946),
            surfaceSunken = Color(0xFF101218), onSurface = Color(0xFFF1F3F7),
            mutedText = Color(0xFFBAC2CF), border = Color(0xFF576273),
        )
        // Glacier Night can also resolve to the experimental Glacier Light palette.
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

    fun resolve(settings: AppearanceSettings, systemDark: Boolean = true): AppearancePalette {
        val dark = when (settings.tone) {
            AppearanceTone.LIGHT -> false
            AppearanceTone.SYSTEM -> systemDark
            else -> true
        }
        val base = forPreset(settings.preset)
        val palette = when {
            settings.tone == AppearanceTone.OLED -> base.copy(
                background = Color.Black, topBackground = Color.Black, surfaceSunken = Color.Black,
            )
            dark -> base
            else -> lightPalette(base)
        }
        val custom = if (settings.accentSource == AppearanceAccentSource.CUSTOM) {
            normalizeAccentHex(settings.customAccent)?.toLong(16)
        } else null
        return if (custom != null) withAccent(palette, Color(0xFF000000L or custom)) else palette
    }

    private fun lightPalette(base: AppearancePalette): AppearancePalette {
        val (canvas, accent) = when (base.preset) {
            AppearancePreset.ABYSS -> Color(0xFFEAF5F0) to Color(0xFF00614E)
            AppearancePreset.OBSIDIAN_GOLD -> Color(0xFFF6EED8) to Color(0xFF6A4B05)
            AppearancePreset.AURORA -> Color(0xFFF0EAF9) to Color(0xFF5E3799)
            AppearancePreset.GLACIER -> Color(0xFFE8F2FA) to Color(0xFF155782)
            AppearancePreset.EMBER -> Color(0xFFFAE9EF) to Color(0xFF942443)
            AppearancePreset.SOLSTICE -> Color(0xFFFFEEDC) to Color(0xFF8B3A1B)
            AppearancePreset.VERDANT -> Color(0xFFEDF4E3) to Color(0xFF3D5E22)
            AppearancePreset.SLATE -> Color(0xFFEDF0F4) to Color(0xFF46556D)
        }
        return base.copy(
            accent = accent,
            onAccent = Color.White,
            background = canvas,
            topBackground = lerp(canvas, Color.White, 0.68f),
            surface = lerp(canvas, Color.White, 0.55f),
            elevatedSurface = lerp(canvas, accent, 0.08f),
            surfaceSunken = lerp(canvas, accent, 0.14f),
            onSurface = lerp(Color(0xFF17191D), accent, 0.20f),
            mutedText = lerp(Color(0xFF404650), accent, 0.26f),
            border = lerp(accent, canvas, 0.38f),
            isDark = false,
        )
    }

    /** Resolve candidate text or icon color against the surfaces behind it. */
    fun readableAccent(palette: AppearancePalette, candidate: Color, surface: Color = palette.surface): Color =
        contrastAdjusted(candidate, if (palette.isDark) Color.White else Color.Black, listOf(surface))

    /** Adjust decorative accent until it is readable against its actual surface. */
    fun withAccent(palette: AppearancePalette, candidate: Color): AppearancePalette {
        val target = if (palette.isDark) Color.White else Color.Black
        val accent = contrastAdjusted(
            candidate, target, listOf(palette.surface, palette.elevatedSurface, palette.surfaceSunken),
        )
        val onAccent = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White
        return palette.copy(accent = accent, onAccent = onAccent)
    }

    private fun contrastAdjusted(candidate: Color, target: Color, surfaces: List<Color>): Color {
        val opaque = candidate.copy(alpha = 1f)
        for (step in 0..20) {
            val color = lerp(opaque, target, step / 20f)
            if (surfaces.all { contrast(color, it) >= 4.5f }) return color
        }
        return target
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

}
