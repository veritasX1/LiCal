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

/** Card 93f6a101: Rules.kt gives exactly what rules.py gives – every case of shared/cases/rules.json
 *  (written by linux/tests/test_rules.py). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RulesTest {
    private fun cases(): JSONArray = JSONArray(File(System.getProperty("lical.cases"), "rules.json").readText())

    private fun events(array: JSONArray) = (0 until array.length()).map { Store.eventFrom(array.getJSONObject(it)) }

    @Test
    fun sameAsUbuntu() {
        val cases = cases()
        assertTrue(cases.length() >= 16)
        var checked = 0
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            when (case.getString("kind")) {
                "month_weeks" -> {
                    val weeks = Rules.monthWeeks(case.getInt("year"), case.getInt("month"), case.getInt("firstWeekday"))
                    assertEquals(case.toString(), case.getJSONArray("result").toString(), JSONArray(weeks.map { JSONArray(it.map(Rules::stamp)) }).toString())
                }
                "occurrences" -> {
                    val found = Rules.occurrences(events(case.getJSONArray("events")), LocalDate.parse(case.getString("from")), LocalDate.parse(case.getString("to")))
                    assertEquals(case.getString("from"), case.getJSONArray("result").toString(), JSONArray(found.map { JSONArray(listOf(it.key, it.start, it.end)) }).toString())
                }
                "week_layout" -> {
                    val monday = LocalDate.parse(case.getString("week"))
                    val week = (0 until 7).map { monday.plusDays(it.toLong()) }
                    val items = Rules.occurrences(events(case.getJSONArray("events")), week.first(), week.last().plusDays(1))
                    val layout = Rules.weekLayout(items, week)
                    val expected = case.getJSONObject("result")
                    val bars = JSONArray(layout.bars.map { JSONObject().put("key", it.key).put("first", it.first).put("last", it.last).put("lane", it.lane) })
                    assertEquals("Balken $monday", canonical(expected.getJSONArray("bars")), canonical(bars))
                    val lines = JSONObject().apply { for (column in 0 until 7) put(column.toString(), JSONArray(layout.lines.getValue(column))) }
                    assertEquals("Zeilen $monday", canonical(expected.getJSONObject("lines")), canonical(lines))
                    assertEquals("Spuren $monday", expected.getJSONArray("lanes").toString(), JSONArray(layout.lanes.toList()).toString())
                    val rows = case.getJSONObject("rows")
                    for (capacity in listOf(1, 2, 3, 6)) {
                        val actual = JSONArray((0 until 7).map { column ->
                            val cell = Rules.cellRows(layout, column, capacity)
                            JSONArray(listOf(cell.lanes, JSONArray(cell.lines), cell.hidden))
                        })
                        assertEquals("Zellen $monday/$capacity", rows.getJSONArray(capacity.toString()).toString(), actual.toString())
                    }
                }
                "timeline" -> {
                    val day = LocalDate.parse(case.getString("day"))
                    val items = Rules.occurrences(events(case.getJSONArray("events")), day, day.plusDays(1))
                    val blocks = JSONArray(Rules.timelineLayout(items, day).map {
                        JSONObject().put("key", it.key).put("top", it.top).put("bottom", it.bottom).put("column", it.column).put("columns", it.columns)
                    })
                    assertEquals("Zeitleiste $day", canonical(case.getJSONArray("result")), canonical(blocks))
                }
            }
            checked++
        }
        assertEquals(cases.length(), checked)
    }

    /** JSON with sorted object keys, so field order does not matter. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
