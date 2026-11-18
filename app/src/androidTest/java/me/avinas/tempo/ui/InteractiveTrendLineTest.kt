package me.avinas.tempo.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.avinas.tempo.ui.home.components.InteractiveTrendLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InteractiveTrendLineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingAfterSameLabelRefreshUsesCurrentValueAndCallback() {
        val points = mutableStateOf(listOf(1f, 2f))
        val callbackVersion = mutableStateOf(1)
        val selections = mutableListOf<Triple<Int, Float, String>>()
        compose.setContent {
            val version = callbackVersion.value
            InteractiveTrendLine(
                dataPoints = points.value,
                labels = listOf("Mon", "Tue"),
                modifier = Modifier.size(200.dp, 100.dp).testTag("trend"),
                onValueSelected = { value, label -> selections.add(Triple(version, value, label)) },
            )
        }

        val chart = compose.onNodeWithTag("trend")
        chart.performTouchInput { click(Offset(width * 0.9f, center.y)) }
        compose.runOnIdle {
            assertEquals(Triple(1, 2f, "Tue"), selections.last())
            points.value = listOf(10f, 20f)
            callbackVersion.value = 2
        }
        compose.waitForIdle()
        chart.performTouchInput { click(Offset(width * 0.9f, center.y)) }
        compose.runOnIdle { assertEquals(Triple(2, 20f, "Tue"), selections.last()) }
    }
}
