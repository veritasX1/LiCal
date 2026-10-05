package io.github.veritasx1.lical

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Card 7a9187d6: events in another time zone exactly as rules.py – every case of shared/cases/zones.json (Berlin). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZonesTest {
    private val berlin = "Europe/Berlin"

    private fun events(array: JSONArray) = (0 until array.length()).map { Store.eventFrom(array.getJSONObject(it)) }

    private fun row(item: Occurrence) = JSONArray(listOf(item.key, item.start, item.end, item.zoneStart ?: JSONObject.NULL, item.zoneEnd ?: JSONObject.NULL))

    @Test
    fun sameAsUbuntu() {
        val cases = JSONArray(File(System.getProperty("lical.cases"), "zones.json").readText())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            when (case.getString("kind")) {
                "occurrences" -> {
                    val found = Rules.occurrences(events(case.getJSONArray("events")), LocalDate.parse(case.getString("from")),
                        LocalDate.parse(case.getString("to")), berlin)
                    assertEquals(case.getString("from"), case.getJSONArray("result").toString(), JSONArray(found.map(::row)).toString())
                }
                "to_zone" -> assertEquals(case.getString("result"), Rules.stamp(Moment(Rules.toEventZone(Store.eventFrom(case.getJSONObject("event")),
                    LocalDateTime.parse(case.getString("moment")), berlin), true)))
                "for_editing" -> {
                    val json = case.getJSONObject("item")
                    val event = Store.eventFrom(json)
                    val item = Occurrence(event, json.getString("start"), json.getString("end"), json.getString("key"), json.getString("zoneStart"), json.getString("zoneEnd"))
                    val edited = Rules.forEditing(item)
                    val expected = case.getJSONObject("result")
                    assertEquals(expected.getString("start") to expected.getString("end"), edited.start to edited.end)
                }
            }
        }
    }
}
