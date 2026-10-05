package io.github.veritasx1.lical

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Card 502ccfb2: Editing.kt gives what editing.py gives – every case of shared/cases/editing.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditingTest {
    private fun moment(text: String) = Rules.parse(text)
    private fun json(event: Event) = canonical(Store.eventJson(event))

    @Test
    fun sameAsUbuntu() {
        val cases = JSONArray(File(System.getProperty("lical.cases"), "editing.json").readText())
        assertTrue(cases.length() >= 20)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val label = case.toString().take(120)
            when (case.getString("kind")) {
                "new" -> {
                    val hour = if (case.isNull("hour")) null else case.getInt("hour")
                    val made = Editing.newEvent(LocalDate.parse(case.getString("day")), LocalDateTime.parse(case.getString("now")), "privat", "n", hour)
                    assertEquals(label, canonical(case.getJSONObject("result")), json(made))
                }
                "start" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.setStart(Store.eventFrom(case.getJSONObject("event")), moment(case.getString("value")))))
                "end" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.setEnd(Store.eventFrom(case.getJSONObject("event")), moment(case.getString("value")))))
                "allday" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.setAllDay(Store.eventFrom(case.getJSONObject("event")), case.getBoolean("on"))))
                "label" -> assertEquals(label, case.getString("result"), Editing.repeatLabel(if (case.isNull("rrule")) null else case.getString("rrule")))
                "series" -> assertEquals(label, canonical(case.getJSONObject("result")), json(Editing.applyToSeries(
                    Store.eventFrom(case.getJSONObject("series")), case.getString("occurrence"), Store.eventFrom(case.getJSONObject("edited")))))
                "skip" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.skipOccurrence(Store.eventFrom(case.getJSONObject("series")), case.getString("occurrence"))))
                "absence" -> assertEquals(label, canonical(case.getJSONObject("result")), json(Editing.setAbsence(
                    Store.eventFrom(case.getJSONObject("event")), if (case.isNull("absence")) null else case.getString("absence"))))
                "deputy" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.setDeputy(Store.eventFrom(case.getJSONObject("event")), case.getString("name"))))
                "weeks" -> assertEquals(label, canonical(case.getJSONObject("result")),
                    json(Editing.absenceWeeks(Store.eventFrom(case.getJSONObject("event")), case.getInt("weeks"))))
                "absence_text" -> assertEquals(label, case.getString("result"), Editing.absenceText(Store.eventFrom(case.getJSONObject("event"))))
                else -> error("unbekannter Fall")
            }
        }
    }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
