package me.avinas.tempo.ui.spotlight.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import me.avinas.tempo.ui.spotlight.SpotlightCardData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasDraftCodecTest {
    @Test fun savesCardReferencesBackgroundAndTextFormatting() {
        val viewport = CanvasViewport(360f, 640f, 1f)
        val card = SpotlightCardData.ListeningPeak(peakTime = "21:00", peakTimeRange = "21–22", totalMinutes = 42)
        val text = CanvasTextItem(
            text = "मेरी शाम",
            style = TextStyle(fontPreset = FontPreset.Mono, color = Color.Yellow, isBold = true,
                alignment = TextAlign.Right, hasBackground = true, backgroundColor = Color.Blue),
        )
        val state = CanvasState(
            canvasItems = listOf(CanvasCardItem(cardData = card, rotation = 12f, zIndex = 2)),
            textItems = listOf(text),
            selectedBackground = CanvasBackground.Aurora,
            showTextEditor = true,
            editingTextId = text.id,
        )
        val draft = CanvasDraft.from(state, viewport)
        val decoded = requireNotNull(CanvasDraftCodec.decode(CanvasDraftCodec.encode(draft)))
        assertEquals(draft, decoded)
        val restored = decoded.restore(viewport, listOf(card))
        assertEquals(state.canvasItems.single().cardData, restored.canvasItems.single().cardData)
        assertEquals(2, restored.canvasItems.single().zIndex)
        assertEquals(text, restored.textItems.single())
        assertEquals(state.selectedBackground, restored.selectedBackground)
        assertEquals(text.id, restored.editingTextId)
    }

    @Test fun rejectsOversizedAndInvalidPayloads() {
        assertNull(CanvasDraftCodec.decode("x".repeat(24_001)))
        assertNull(CanvasDraftCodec.decode("{"))
    }
}
