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
import java.time.LocalDateTime

/** Card 91adf47e: Alerts.kt gives exactly what alerts.py gives – every case of shared/cases/alerts.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertsTest {
    private fun events(array: JSONArray) = (0 until array.length()).map { Store.eventFrom(array.getJSONObject(it)) }

    private fun json(alert: Alert) = JSONObject().put("key", alert.key).put("at", alert.at).put("minutes", alert.minutes).put("id", alert.id)
        .put("title", alert.title).put("start", alert.start).put("end", alert.end).put("allDay", alert.allDay).put("location", alert.location)

    private fun alertFrom(json: JSONObject) = Alert(json.getString("key"), json.getString("at"), json.getInt("minutes"), json.getString("id"),
        json.getString("title"), json.getString("start"), json.getString("end"), json.getBoolean("allDay"), json.getString("location"))

    private fun canonical(json: JSONObject) = json.keys().asSequence().sorted().joinToString { "$it=${json.get(it)}" }

    @Test
    fun sameAsUbuntu() {
        val cases = JSONArray(File(System.getProperty("lical.cases"), "alerts.json").readText())
        assertTrue(cases.length() >= 27)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            when (case.getString("kind")) {
                "label" -> assertEquals(case.toString(), case.getString("result"),
                    Alerts.label(if (case.isNull("minutes")) null else case.getInt("minutes"), case.getBoolean("allDay")))
                "due" -> {
                    val found = Alerts.due(events(case.getJSONArray("events")), LocalDateTime.parse(case.getString("after")), LocalDateTime.parse(case.getString("until")))
                    val expected = case.getJSONArray("result")
                    assertEquals(case.getString("after"), (0 until expected.length()).map { canonical(expected.getJSONObject(it)) }, found.map { canonical(json(it)) })
                }
                "set" -> {
                    val given = case.getJSONArray("alerts").let { array -> (0 until array.length()).map { if (array.isNull(it)) null else array.getInt(it) } }
                    val result = Alerts.setAlerts(Store.eventFrom(case.getJSONObject("event")), given)
                    assertEquals(case.toString(), canonical(case.getJSONObject("result")), canonical(Store.eventJson(result)))
                }
                "next" -> assertEquals(case.getString("result"),
                    Alerts.nextTime(events(case.getJSONArray("events")), LocalDateTime.parse(case.getString("after")))!!.toString())
                "text" -> assertEquals(case.getString("result"), Alerts.text(alertFrom(case.getJSONObject("alert")), LocalDateTime.parse(case.getString("now"))))
            }
        }
    }
}
