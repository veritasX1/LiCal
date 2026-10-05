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
import java.time.ZoneId

/** Card b483dadf: Ics.kt writes and reads iCalendar exactly like ics.py – every case of
 *  shared/cases/ics.json (written by linux/tests/test_ics.py, for Berlin time). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class IcsTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun sameAsUbuntu() {
        val cases = JSONArray(File(System.getProperty("lical.cases"), "ics.json").readText())
        assertTrue(cases.length() >= 7)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            when (case.getString("kind")) {
                "to" -> {
                    val event = Store.eventFrom(case.getJSONObject("event").put("id", "x"))
                    assertEquals(case.getString("result"), Ics.toIcs(event))
                    val back = Ics.fromIcs(Ics.toIcs(event), berlin)!!
                    assertEquals(event.copy(id = "", calendar = ""), back)
                }
                "from" -> {
                    val got = Ics.fromIcs(case.getString("text"), berlin)
                    if (case.isNull("result")) assertEquals(null, got)
                    else assertEquals(case.getString("text"), canonical(case.getJSONObject("result")), canonical(lean(got!!)))
                }
            }
        }
    }

    /** The phone's own code reads back – umlauts, notes on request, never id/calendar/absence/deputy. */
    @Test
    fun qrCodeReadsBack() {
        val event = Event("geheim1", "arbeit", "Übergabe Büro", false, "2026-10-07T08:30", "2026-10-07T09:15", location = "Köln, Domplatte",
            notes = "privat", absence = "abwesend", deputy = "Jens")
        val text = Ics.toIcs(sharedEvent(event, withNotes = false))
        assertTrue("geheim1" !in text && "arbeit" !in text && "privat" !in text && "Jens" !in text)
        val read = decode(qrMatrix(text)!!)
        assertEquals(text, read)
        assertEquals(event.copy(id = "", calendar = "", notes = "", absence = null, deputy = null), Ics.fromIcs(read, berlin))
        assertEquals("privat", Ics.fromIcs(decode(qrMatrix(Ics.toIcs(sharedEvent(event, withNotes = true)))!!), berlin)!!.notes)
        assertEquals(null, qrMatrix("x".repeat(5000)))
    }

    /** A QR code drawn by Ubuntu's share dialog (picture from test_mac_gui.py --shots) – or a phone screenshot with
     *  -Pqrtitle – reads with the phone's reader. */
    @Test
    fun ubuntuCodeReads() {
        val file = System.getProperty("lical.qrpng") ?: return
        val image = android.graphics.BitmapFactory.decodeFile(file)
        val pixels = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
        val source = com.google.zxing.RGBLuminanceSource(image.width, image.height, pixels)
        val text = com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)),
            mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true, com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8")).text
        val event = Ics.fromIcs(text, berlin)!!
        when (val title = System.getProperty("lical.qrtitle")) {
            null -> assertEquals("Zahnarzt" to "Dr. Weber, Köln", event.title to event.location)
            else -> assertEquals(title, event.title)  // e.g. a phone screenshot
        }
        println("QR gelesen: ${event.title}, ${event.start}, ${event.location}")
    }

    private fun decode(matrix: com.google.zxing.common.BitMatrix): String {
        val scale = 6  // like on a screen: several pixels per module
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height) { if (matrix[(it % width) / scale, (it / width) / scale]) 0xFF000000.toInt() else -1 }
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        return com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)),
            mapOf(com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }

    /** The event's JSON without id and calendar – what ics.py returns. */
    private fun lean(event: Event) = Store.eventJson(event).apply { remove("id"); remove("calendar") }

    private fun canonical(json: JSONObject) = json.keys().asSequence().sorted().joinToString { "$it=${json.get(it)}" }
}
