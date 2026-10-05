package io.github.veritasx1.lical

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Card 7a9187d6: Holidays.kt gives exactly what holidays.py gives – every case of shared/cases/holidays.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HolidaysTest {
    @Test
    fun sameAsUbuntu() {
        val cases = JSONArray(File(System.getProperty("lical.cases"), "holidays.json").readText())
        assertTrue(cases.length() >= 90)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            when (case.getString("kind")) {
                "easter" -> assertEquals(case.getString("result"), Holidays.easter(case.getInt("year")).toString())
                "days" -> assertEquals("${case.getInt("year")} ${case.getString("state")}", case.getJSONArray("result").toString(),
                    JSONArray(Holidays.days(case.getInt("year"), case.getString("state")).map { JSONArray(listOf(it.first.toString(), it.second)) }).toString())
                "events" -> assertEquals(case.getJSONArray("result").toString(), JSONArray(Holidays.events(LocalDate.parse(case.getString("first")),
                    LocalDate.parse(case.getString("last")), case.getString("state")).map { Store.eventJson(it) }).toString())
            }
        }
    }
}
