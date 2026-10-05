package io.github.veritasx1.lical

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Card 93f6a101: month, day and year view with example data, light and dark (pictures with -Pshots=<folder>),
 *  and the way between them: year → month → day → back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreensShotTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getProperty("lical.shots")

    private fun store() = Store(null).apply { replaceAll(Store.demoEvents()) }

    private fun shot(name: String) {
        compose.waitForIdle()
        if (out != null) compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun light() = screens(false)

    @Test
    fun dark() = screens(true)

    private fun screens(dark: Boolean) {
        val suffix = if (dark) "-dunkel" else ""
        val tuesday = Rules.weekStart(LocalDate.now()).plusDays(1)
        compose.setContent { LiCalApp(store(), dark = dark, start = Screen.Month, startDay = tuesday) }
        shot("monat$suffix")  // compact (Apple's default): marks under the numbers
        for ((label, name) in listOf("Gestapelt" to "monat-gestapelt", "Details" to "monat-details")) {
            compose.onNodeWithContentDescription("Monatsansicht wählen").performClick()
            compose.onNodeWithText(label).performClick()
            shot("$name$suffix")
        }
        compose.onNodeWithContentDescription("Monatsansicht wählen").performClick()
        compose.onNodeWithText("Kompakt").performClick()
        // Month → day (tap a day), the week strip and the hours.
        compose.onAllNodesWithText(tuesday.dayOfMonth.toString(), useUnmergedTree = true).onFirst().performClick()
        shot("tag$suffix")
        // Day → month → year.
        compose.onNodeWithText(MONTHS[tuesday.monthValue - 1], useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tuesday.year.toString(), useUnmergedTree = true).performClick()
        shot("jahr$suffix")
    }
}
