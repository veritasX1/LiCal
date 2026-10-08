package io.github.veritasx1.lical

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Card 502ccfb2: create, edit and delete events on the phone (iPhone-style sheet): ＋ → title →
 *  time wheel → calendar → ✓; tap the event → all-day → ✓; a series loses one occurrence; all of it
 *  written to the file. Pictures with -Pshots=<folder>. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class EditorFlowTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getProperty("lical.shots")

    private fun shot(name: String) {
        compose.waitForIdle()
        if (out != null) compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun createEditDelete() {
        val file = File.createTempFile("lical", ".json").apply { delete() }
        val store = Store(file)
        val day = LocalDate.now().plusDays(2)
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = day) }
        compose.onNodeWithContentDescription("Neuer Termin").performClick()
        compose.onNodeWithContentDescription("Titel").performTextReplacement("Zahnarzt")
        compose.onNodeWithText("Ort oder Videoanruf", useUnmergedTree = true).assertExists()
        // Start time: open the wheel and pick 11 o'clock.
        compose.onNodeWithContentDescription("Beginn Uhrzeit 09:00").performClick()
        compose.onAllNodesWithText("11").onLast().performClick()  // the wheel (the week strip may show an 11 too)
        compose.onNodeWithText("Aus QR-Code übernehmen").assertExists()  // card b483dadf: only for a new event
        compose.onAllNodesWithText("Kalender").onLast().performScrollTo().performClick()  // the editor's row (the toolbar has one too)
        compose.onAllNodesWithText("Familie")[0].performScrollTo().performClick()
        shot("blatt-neu")
        compose.onNodeWithContentDescription("Sichern").performClick()
        compose.waitForIdle()
        assertEquals(1, store.events.size)
        val event = store.events.first()
        assertEquals("Zahnarzt", event.title)
        assertEquals("${day}T11:00", event.start)
        assertEquals("${day}T12:00", event.end)  // the length stays
        assertEquals("familie", event.calendar)
        assertEquals(1, Store(file).events.size)  // in the file

        // Tap the event in the day view → editor → all-day → ✓.
        compose.onNodeWithText("Zahnarzt").performClick()
        compose.onNodeWithText("Bearbeiten").performClick()   // a tap shows the event first (45400a07)
        compose.onNodeWithText("Termin bearbeiten").assertExists()
        shot("blatt-bearbeiten")
        compose.onNodeWithText("Ganztägig").assertExists()
        // Pass it on as a QR code (card b483dadf).
        compose.onNodeWithText("Aus QR-Code übernehmen").assertDoesNotExist()
        compose.onNodeWithText("Als QR-Code teilen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("QR-Code des Termins").assertExists()
        compose.onNodeWithText("Notizen mitgeben").assertExists()
        shot("qr-teilen")
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithContentDescription("QR-Code des Termins").assertDoesNotExist()
        store.put(store.events.first().copy(rrule = "FREQ=DAILY"))  // make it a series (as if chosen under "Wiederholen")
        compose.onNodeWithContentDescription("Abbrechen").performClick()
        compose.waitForIdle()

        // Delete only one occurrence of the series (now daily: the next day's page shows it too).
        compose.onAllNodesWithText("Zahnarzt").onFirst().performClick()
        compose.onNodeWithText("Bearbeiten").performClick()   // a tap shows the event first (45400a07)
        compose.onNodeWithText("Termin löschen").performClick()
        compose.onNodeWithText("Nur diesen Termin löschen").performClick()
        compose.waitForIdle()
        assertEquals(listOf(day.toString()), store.events.first().exdates)
        assertTrue(store.occurrences(day, day.plusDays(1)).isEmpty() && store.occurrences(day.plusDays(1), day.plusDays(2)).size == 1)
    }

    @Test
    fun absenceWithDeputy() {
        val store = Store(null)
        val day = LocalDate.now().plusDays(3)
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = day) }
        compose.onNodeWithContentDescription("Neuer Termin").performClick()
        compose.onNodeWithText("Abwesenheit").performClick()
        compose.onNodeWithText("Urlaub").performClick()
        compose.onNodeWithContentDescription("Vertretung").performTextReplacement("Jens")
        compose.onNodeWithContentDescription("2 Wochen").performClick()
        shot("blatt-abwesenheit")
        compose.onNodeWithContentDescription("Sichern").performClick()
        compose.waitForIdle()
        val event = store.events.single()
        assertEquals("urlaub", event.absence)
        assertEquals("Jens", event.deputy)
        assertEquals("Urlaub", event.title)  // the empty title becomes the kind's name
        assertTrue(event.allDay)
        assertEquals(day.toString(), event.start)
        assertEquals(day.plusDays(14).toString(), event.end)
    }

    @Test
    fun alerts() {
        val store = Store(null)
        val day = LocalDate.now().plusDays(4)
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = day) }
        compose.onNodeWithContentDescription("Neuer Termin").performClick()
        compose.onNodeWithContentDescription("Titel").performTextReplacement("Zahnarzt")
        compose.onNodeWithText("Zweiter Hinweis").assertDoesNotExist()  // only once there is a first
        compose.onNodeWithText("Hinweis").performScrollTo().performClick()
        compose.onNodeWithText("15 Minuten vorher").performScrollTo().performClick()
        compose.onNodeWithText("Zweiter Hinweis").performScrollTo().performClick()
        compose.onNodeWithText("1 Tag vorher").performScrollTo().performClick()
        shot("blatt-hinweise")
        compose.onNodeWithContentDescription("Sichern").performClick()
        compose.waitForIdle()
        assertEquals(listOf(15, 1440), store.events.single().alerts)
        // Opened again: the alerts are shown.
        compose.onNodeWithText("Zahnarzt").performClick()
        compose.onNodeWithText("Bearbeiten").performClick()   // a tap shows the event first (45400a07)
        compose.onNodeWithText("Termin bearbeiten").assertExists()
        compose.onNodeWithText("15 Minuten vorher", substring = true).performScrollTo().assertExists()  // the row's value
        compose.onNodeWithText("1 Tag vorher", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun holidays() {
        val store = Store(null)
        val unity = LocalDate.of(2026, 10, 3)
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = unity) }
        compose.onNodeWithText("Tag der Deutschen Einheit").performClick()
        // Read-only: the details, without „Bearbeiten“ (like Apple's holiday calendar).
        compose.onNodeWithText("Bearbeiten").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("ganztägig").fetchSemanticsNodes().size >= 2)   // day view and details
        compose.onNodeWithText("Abwesenheit").assertDoesNotExist()
        shot("feiertag")
        compose.onNodeWithText("Fertig").performClick()
        // Bundesland: Allerheiligen only with one that has it.
        assertTrue(store.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)).isEmpty())
        store.setHolidays(store.holidays.copy(state = "NW"))
        assertEquals("Allerheiligen", store.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)).single().title)
        assertTrue(store.writableCalendars().none { it.id == Holidays.CALENDAR })
        store.setVisible(Holidays.CALENDAR, false)
        assertTrue(store.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)).isEmpty())
    }

    @Test
    fun otherTimeZone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
        val store = Store(null)
        val call = Event("ny", "arbeit", "Call New York", false, "2026-10-19T09:00", "2026-10-19T10:00", rrule = "FREQ=WEEKLY", tz = "America/New_York")
        store.put(call)
        val day = LocalDate.of(2026, 10, 26)  // Berlin already in winter time, New York not yet: 14:00 here
        assertEquals("2026-10-26T14:00", store.occurrences(day, day.plusDays(1)).single().start)
        compose.setContent { LiCalApp(store, dark = false, start = Screen.Day, startDay = day) }
        compose.onNodeWithText("Call New York").performClick()
        compose.onNodeWithText("Bearbeiten").performClick()   // a tap shows the event first (45400a07)
        compose.onNodeWithContentDescription("Beginn Uhrzeit 09:00").assertExists()  // its own time
        compose.onNodeWithText("New York (Amerika)", substring = true).performScrollTo().assertExists()
        shot("blatt-zeitzone")
        compose.onNodeWithContentDescription("Sichern").performClick()
        compose.waitForIdle()
        assertEquals(call, store.events.single())  // saved unchanged – nothing shifted
    }

    @Test
    fun pictureLight() = picture(false)

    @Test
    fun pictureDark() = picture(true)

    private fun picture(dark: Boolean) {
        if (out == null) return
        val store = Store(null).apply { replaceAll(Store.demoEvents()) }
        val tuesday = Rules.weekStart(LocalDate.now()).plusDays(1)
        compose.setContent { LiCalApp(store, dark = dark, start = Screen.Day, startDay = tuesday) }
        compose.onNodeWithText("Kundentermin Müller").performClick()
        compose.onNodeWithText("Bearbeiten").performClick()   // a tap shows the event first (45400a07)
        compose.onNodeWithContentDescription("Beginn Uhrzeit 11:00").performClick()
        shot(if (dark) "blatt-dunkel" else "blatt-uhrzeit")
    }
}
