package io.github.veritasx1.lical

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Card 45400a07: a tap shows the event first – its notes whole, links tappable (limail:// back into the mail). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class DetailsTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getProperty("lical.shots")

    @Test
    fun detailsWithLinks() {
        val day = LocalDate.of(2026, 10, 8)
        val notes = "Zustellung zwischen 10 und 14 Uhr.\n\n— E-Mail von Paketdienst: Ihr Paket kommt heute\nlimail://message/abc123%40paket.example"
        val store = Store(null).apply {
            events.add(Event(id = "p1", calendar = "privat", title = "Paket", allDay = false, start = "2026-10-08T10:00", end = "2026-10-08T14:00", notes = notes))
        }
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = day) }
        compose.onNodeWithText("Paket").performClick()
        compose.onNodeWithText("Bearbeiten").assertExists()
        compose.onNodeWithText(notes).assertExists()
        compose.waitForIdle()
        if (out != null) captureScreenRoboImage("$out/termin-details.png")
        val linked = linked(notes, androidx.compose.ui.graphics.Color.Blue)
        val links = linked.getLinkAnnotations(0, linked.length).map { (it.item as androidx.compose.ui.text.LinkAnnotation.Url).url }
        assertEquals(listOf("limail://message/abc123%40paket.example"), links)
        compose.onNodeWithText("Bearbeiten").performClick()
        assertTrue(compose.onNodeWithText("Termin bearbeiten").fetchSemanticsNode() != null)
    }
}
