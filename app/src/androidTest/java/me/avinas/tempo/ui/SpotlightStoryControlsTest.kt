package me.avinas.tempo.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import me.avinas.tempo.ui.spotlight.SpotlightStoryPage
import me.avinas.tempo.ui.spotlight.SpotlightStoryScreen
import org.junit.Rule
import org.junit.Test

class SpotlightStoryControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pausePersistsWhileNavigatingByAccessibleControls() {
        compose.setContent {
            SpotlightStoryScreen(
                storyPages = listOf(
                    SpotlightStoryPage.ListeningMinutes("first", "First", 10, year = 2026),
                    SpotlightStoryPage.ListeningMinutes("second", "Second", 20, year = 2026)
                ),
                onClose = {}
            )
        }

        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Pause story").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Page 2 of 2").assertExists()
        compose.onNodeWithContentDescription("Resume story").assertExists()

        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Page 1 of 2").assertExists()
        compose.onNodeWithContentDescription("Resume story").performClick()
        compose.onNodeWithContentDescription("Pause story").assertExists()
    }
}
