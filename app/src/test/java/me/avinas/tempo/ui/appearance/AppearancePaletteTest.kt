package me.avinas.tempo.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppearancePaletteTest {
    @Test fun `unrecognized saved style resets to Abyss`() {
        assertEquals(AppearancePreset.ABYSS, AppearancePreset.decode("removed_preset"))
        assertEquals(AppearancePreset.ABYSS, AppearancePreset.decode(null))
    }

    @Test fun `unknown saved font falls back to bundled Tempo typography`() {
        assertEquals(AppearanceFontStyle.TEMPO, AppearanceFontStyle.decode(null))
        assertEquals(AppearanceFontStyle.TEMPO, AppearanceFontStyle.decode("missing_style"))
    }

    @Test fun `unrecognized tone retains dark backward compatibility`() {
        assertEquals(AppearanceTone.DARK, decodeAppearanceTone(null))
        assertEquals(AppearanceTone.DARK, decodeAppearanceTone("future_mode"))
    }

    @Test fun `each initial visual style has independent foreground background and accent`() {
        val palettes = AppearancePreset.entries.map(AppearancePaletteResolver::forPreset)
        assertEquals(4, palettes.map { it.background }.distinct().size)
        assertEquals(4, palettes.map { it.accent }.distinct().size)
        palettes.forEach {
            assertNotEquals(it.onSurface, it.background)
            assertNotEquals(it.accent, it.background)
            assertNotEquals(it.onAccent, it.accent)
        }
    }

    @Test fun `Abyss retains existing default background and accent`() {
        val abyss = AppearancePaletteResolver.forPreset(AppearancePreset.ABYSS)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A0E0E), abyss.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2FDBB8), abyss.accent)
    }
}
