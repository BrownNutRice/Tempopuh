package me.avinas.tempo.ui.spotlight.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasViewportTest {
    @Test fun resizingPreservesCardCenterRelativeWidthAndRotation() {
        val original = CanvasViewport(width = 360f, height = 640f, density = 1f)
        val rotated = CanvasViewport(width = 270f, height = 480f, density = 1.5f)
        val saved = original.normalize(x = 20f, y = -120f, scale = 0.5f, rotation = -15f, isCard = true)
        val restored = rotated.project(saved, isCard = true)
        assertEquals(0.5f * rotated.width, restored.x + 160f * rotated.density, 0.001f)
        assertEquals(0.25f * rotated.height, restored.y + 280f * rotated.density, 0.001f)
        assertEquals(160f / original.width, restored.scale * 320f * rotated.density / rotated.width, 0.001f)
        assertEquals(-15f, restored.rotation, 0f)
    }

    @Test fun resizingPreservesTextAnchorAndScale() {
        val original = CanvasViewport(width = 360f, height = 640f, density = 1f)
        val restoredViewport = CanvasViewport(width = 540f, height = 960f, density = 2f)
        val saved = original.normalize(x = 90f, y = 480f, scale = 1.2f, rotation = 27f, isCard = false)
        val restored = restoredViewport.project(saved, isCard = false)
        assertEquals(135f, restored.x, 0.001f)
        assertEquals(720f, restored.y, 0.001f)
        assertEquals(0.9f, restored.scale, 0.001f)
        assertEquals(27f, restored.rotation, 0f)
    }
}
