package me.avinas.tempo.ui.appearance

import org.junit.Assert.*
import org.junit.Test

class AppearanceRuntimePolicyTest {
    private fun resolve(settings: AppearanceSettings = AppearanceSettings(), animations: Boolean = true,
                        power: Boolean = false, lowRam: Boolean = false, thermal: Boolean = false,
                        foreground: Boolean = true) = AppearanceRuntimePolicy.resolve(
        settings, animations, power, lowRam, thermal, foreground,
    )

    @Test fun `power thermal and background constraints cannot be opted out`() {
        val settings = AppearanceSettings(adaptiveEffects = false)
        listOf(resolve(settings, power = true), resolve(settings, thermal = true),
            resolve(settings, foreground = false)).forEach {
            assertTrue(it.reduceMotion)
            assertTrue(it.solidSurfaces)
            assertFalse(it.allowArtworkAccent)
            assertFalse(it.allowArtworkBackdrop)
        }
    }

    @Test fun `low RAM adaptation is optional but system animation setting is respected`() {
        assertTrue(resolve(lowRam = true).solidSurfaces)
        assertFalse(resolve(AppearanceSettings(adaptiveEffects = false), lowRam = true).solidSurfaces)
        assertTrue(resolve(animations = false).reduceMotion)
        assertTrue(resolve(AppearanceSettings(reduceMotion = true)).reduceMotion)
    }

    @Test fun `solid finish suppresses backdrop without suppressing static accent`() {
        val policy = resolve(AppearanceSettings(surface = AppearanceSurface.SOLID))
        assertTrue(policy.solidSurfaces)
        assertFalse(policy.allowArtworkBackdrop)
        assertTrue(policy.allowArtworkAccent)
    }
}
