package io.github.veritasx1.lical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Olaf 07.10.: „Kalender hinzufügen“ like Apple – name and color, not shared; edit; delete with its events (the last one stays). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class CalendarsTest {
    @Test
    fun addEditDelete() {
        val store = Store(null)
        val before = store.calendars.size
        val entwicklung = store.addCalendar("  Entwicklung ", "purple")
        assertEquals(before + 1, store.calendars.size)
        assertEquals("Entwicklung", store.calendar(entwicklung.id)?.name)
        assertEquals("purple", store.calendar(entwicklung.id)?.color)
        store.updateCalendar(entwicklung.id, "Projektplan", "brown")
        assertEquals("Projektplan" to "brown", store.calendar(entwicklung.id)!!.let { it.name to it.color })
        store.events.add(Event(id = "e1", calendar = entwicklung.id, title = "Testtermin", allDay = false, start = "2026-10-08T10:00", end = "2026-10-08T11:00"))
        assertTrue(store.deleteCalendar(entwicklung.id))
        assertTrue(store.events.none { it.calendar == entwicklung.id })
        while (store.calendars.size > 1) store.deleteCalendar(store.calendars.first().id)
        assertFalse(store.deleteCalendar(store.calendars.single().id))   // the last calendar stays
    }
}
