package me.avinas.tempo.ui.spotlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class SpotlightShareSnapshotTest {
    @Test fun snapshotKeepsWeeklyValuesWhenAllTimeCardUsesTheSameId() {
        val weekly = SpotlightCardData.ListeningPeak(
            peakTime = "22:00", peakTimeRange = "22:00–00:00", totalMinutes = 35,
        )
        val allTime = weekly.copy(totalMinutes = 470)
        assertEquals(weekly.id, allTime.id)

        val buffer = ByteArrayOutputStream()
        ObjectOutputStream(buffer).use { it.writeObject(weekly) }
        val restored = ObjectInputStream(ByteArrayInputStream(buffer.toByteArray())).use {
            it.readObject() as SpotlightCardData.ListeningPeak
        }

        assertEquals(weekly, restored)
        assertNotEquals(allTime.totalMinutes, restored.totalMinutes)
    }
}
