package me.avinas.tempo.ui.home.components

import androidx.compose.ui.graphics.Color
import me.avinas.tempo.ui.appearance.AppearancePaletteResolver
import me.avinas.tempo.ui.appearance.AppearancePreset
import me.avinas.tempo.ui.appearance.AppearanceSettings
import me.avinas.tempo.ui.appearance.AppearanceTone
import me.avinas.tempo.ui.profile.profileContrast
import me.avinas.tempo.ui.profile.readableProfileColor
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaysOverviewContrastTest {
    @Test
    fun overviewTextAndArtworkAccentsRemainReadableAcrossPalettes() {
        val tones = listOf(
            AppearanceTone.DARK to true,
            AppearanceTone.LIGHT to false,
            AppearanceTone.OLED to true,
            AppearanceTone.SYSTEM to true,
            AppearanceTone.SYSTEM to false,
        )
        val artworkColors = listOf(Color(0xFF7651B5), Color(0xFF2FDBB8), Color.White, Color.Black)
        for (preset in AppearancePreset.entries) {
            for ((tone, systemDark) in tones) {
                val palette = AppearancePaletteResolver.resolve(AppearanceSettings(preset = preset, tone = tone), systemDark)
                val surface = if (palette.isDark) palette.elevatedSurface else palette.surfaceSunken
                val muted = readableProfileColor(palette.mutedText, surface, 4.5f)
                assertTrue("$preset $tone muted text", profileContrast(muted, surface) >= 4.5f)
                for (candidate in artworkColors) {
                    val accent = readableProfileColor(candidate, surface, 4.5f)
                    assertTrue("$preset $tone accent", profileContrast(accent, surface) >= 4.5f)
                    val buttonText = readableProfileColor(palette.onAccent, accent, 4.5f)
                    assertTrue("$preset $tone button", profileContrast(buttonText, accent) >= 4.5f)
                }
            }
        }
    }
}
