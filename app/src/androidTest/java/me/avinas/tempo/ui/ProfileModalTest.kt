package me.avinas.tempo.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import me.avinas.tempo.ui.profile.ProfileModal
import org.junit.Rule
import org.junit.Test

class ProfileModalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backDismissesOnlyTheTopModal() {
        val detailOpen = mutableStateOf(true)
        val shareOpen = mutableStateOf(true)
        compose.setContent {
            Text("Profile content")
            if (detailOpen.value) {
                ProfileModal("Badge details", onDismiss = { detailOpen.value = false }) {
                    Text("Details content")
                }
            }
            if (shareOpen.value) {
                ProfileModal("Share preview", onDismiss = { shareOpen.value = false }) {
                    Text("Share content")
                }
            }
        }

        compose.onNodeWithText("Share content").assertExists()
        pressSystemBack()
        compose.onNodeWithText("Share content").assertDoesNotExist()
        compose.onNodeWithText("Details content").assertExists()
        pressSystemBack()
        compose.onNodeWithText("Details content").assertDoesNotExist()
        compose.onNodeWithText("Profile content").assertExists()
    }

    private fun pressSystemBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        check(automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), true))
        check(automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), true))
        compose.waitForIdle()
    }
}
