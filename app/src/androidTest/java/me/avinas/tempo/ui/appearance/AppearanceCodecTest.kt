package me.avinas.tempo.ui.appearance

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceCodecTest {
    @Test fun roundTripPreservesCompleteDraft() {
        val settings = AppearanceSettings(
            preset = AppearancePreset.EMBER, tone = AppearanceTone.OLED,
            fontStyle = AppearanceFontStyle.ARCHIVE_SERIF, reduceMotion = true,
            accentSource = AppearanceAccentSource.ARTWORK, density = AppearanceDensity.COMPACT,
            shape = AppearanceShape.SHARP, boldText = true, surface = AppearanceSurface.SOLID,
            adaptiveEffects = false, nowListening = AppearanceNowListening.HIDDEN,
            hiddenHomeSections = setOf(AppearanceHomeSection.SPOTLIGHT),
            homeOrder = AppearanceHomeSection.entries.reversed(),
            statsTab = AppearanceStatsTab.ALBUMS, statsRange = AppearanceStatsRange.YEAR,
        )
        assertEquals(settings, AppearanceCodec.decode(AppearanceCodec.encode(settings)))
    }

    @Test fun oldAndUnknownFieldsFallBackWithoutDuplicatingCards() {
        val decoded = AppearanceCodec.decode("""{"version":1,"preset":"future","density":"future",
            "homeOrder":["INSIGHTS","INSIGHTS","unknown"],"hiddenHome":["unknown","TODAY"]}""")
        assertEquals(AppearancePreset.ABYSS, decoded.preset)
        assertEquals(AppearanceDensity.COMFORTABLE, decoded.density)
        assertEquals(AppearanceHomeSection.INSIGHTS, decoded.homeOrder.first())
        assertEquals(AppearanceHomeSection.entries.toSet(), decoded.homeOrder.toSet())
        assertEquals(AppearanceHomeSection.entries.size, decoded.homeOrder.size)
        assertEquals(setOf(AppearanceHomeSection.TODAY), decoded.hiddenHomeSections)
    }

    @Test fun futureOversizedAndMalformedFilesAreRejected() {
        listOf("""{"version":999}""", "x".repeat(AppearanceCodec.MAX_BYTES + 1), "[]", "{}").forEach {
            assertTrue(runCatching { AppearanceCodec.decode(it) }.isFailure)
        }
    }
}
