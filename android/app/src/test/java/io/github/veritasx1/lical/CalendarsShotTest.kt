package io.github.veritasx1.lical

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** „Kalender hinzufügen“ (Olaf 07.10.) the way a person does it: Kalender → hinzufügen → name, Lila → Fertig; pictures with -Pshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class CalendarsShotTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getProperty("lical.shots")
    private fun shot(name: String) { compose.waitForIdle(); if (out != null) captureScreenRoboImage("$out/$name.png") }

    @Test
    fun addCalendarLikeApple() {
        val store = Store(null).apply { replaceAll(Store.demoEvents()) }
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Month, startDay = LocalDate.now()) }
        compose.onNodeWithText("Kalender").performClick()
        shot("kalender-liste")
        compose.onNodeWithText("Kalender hinzufügen").performClick()
        compose.onNodeWithContentDescription("Name des Kalenders").performTextInput("Entwicklung")
        compose.onNodeWithText("Lila").performClick()
        shot("kalender-hinzufuegen")
        compose.onAllNodesWithText("Fertig").onLast().performClick()   // the editor's, over the list's
        shot("kalender-liste-neu")
        assertTrue(store.calendars.any { it.name == "Entwicklung" && it.color == "purple" })
    }
}
