package me.avinas.tempo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.avinas.tempo.ui.components.TempoDialogBody
import me.avinas.tempo.ui.components.TempoDialogPrimaryButton
import me.avinas.tempo.ui.components.TempoDialogSurface
import me.avinas.tempo.ui.components.TempoDialogTitle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TempoDialogSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trailingActionIsReachableInShortWindowWithLargeText() {
        var confirmed = false
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                Box(Modifier.width(320.dp).height(240.dp)) {
                    TempoDialogSurface {
                        TempoDialogTitle("Restore listening history")
                        TempoDialogBody(
                            List(12) { "The selected backup contains listening history." }
                                .joinToString(" ")
                        )
                        TempoDialogPrimaryButton("Restore history", { confirmed = true })
                    }
                }
            }
        }

        compose.onNodeWithText("Restore history").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
