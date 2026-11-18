package me.avinas.tempo.ui.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import me.avinas.tempo.ui.appearance.AppearancePaletteResolver
import me.avinas.tempo.ui.appearance.AppearancePreset
import me.avinas.tempo.ui.appearance.AppearanceSettings
import me.avinas.tempo.ui.appearance.AppearanceTone
import me.avinas.tempo.ui.theme.TempoWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileColorsTest {
    @Test fun profileForegroundsMeetContrastTargetsAcrossPresetsAndTones() {
        val tones = listOf(
            AppearanceTone.DARK to true,
            AppearanceTone.LIGHT to false,
            AppearanceTone.OLED to true,
            AppearanceTone.SYSTEM to false,
            AppearanceTone.SYSTEM to true,
        )
        for (preset in AppearancePreset.entries) {
            for ((tone, systemDark) in tones) {
                val palette = AppearancePaletteResolver.resolve(AppearanceSettings(preset = preset, tone = tone), systemDark)
                val xpChip = TempoWarning.copy(alpha = 0.12f).compositeOver(palette.surface)
                val text = readableProfileColor(TempoWarning, xpChip, minimumRatio = 4.5f)
                assertTrue("$preset $tone XP chip", profileContrast(text, xpChip) >= 4.5f)

                val surface = if (palette.isDark) palette.elevatedSurface else palette.surfaceSunken
                val track = readableProfileColor(palette.border, surface, minimumRatio = 3f)
                assertTrue("$preset $tone progress outline", profileContrast(track, surface) >= 3f)

                val action = readableProfileColor(Color(0xFF9CA3AF), palette.surface, minimumRatio = 3f)
                val label = readableProfileColor(palette.onAccent, action, minimumRatio = 4.5f)
                assertTrue("$preset $tone action label", profileContrast(label, action) >= 4.5f)
                assertTrue("$preset $tone action boundary", profileContrast(action, palette.surface) >= 3f)
            }
        }
    }

    @Test fun alreadyReadableColorIsPreserved() {
        assertEquals(Color.Black, readableProfileColor(Color.Black, Color.White, minimumRatio = 4.5f))
    }
}
