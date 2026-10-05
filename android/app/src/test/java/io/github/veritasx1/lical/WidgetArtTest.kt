package io.github.veritasx1.lical

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Card b1a0984b: the iPhone's calendar widgets drawn with the demo events – what comes next, the
 *  month, the list; light and dark. Pictures (and the widget picker's previews) with -Pshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetArtTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val out = System.getProperty("lical.shots")

    @Test
    fun upcomingLeavesOutWhatIsOver() {
        val store = Store(null).apply { replaceAll(Store.demoEvents(LocalDate.of(2026, 10, 6))) }
        val now = LocalDate.of(2026, 10, 7).atTime(10, 0)  // Wednesday: Zahnarzt 8:30 is over, Mittag 12:30 to come
        val titles = WidgetArt.upcoming(store, now).map { it.title }
        assertTrue(titles.toString(), "Zahnarzt" !in titles.take(3) && titles.first() == "Mittag mit Jens")
        assertEquals(now.toLocalDate().atTime(13, 30), WidgetArt.nextChange(store, now))
    }

    @Test
    fun pictures() {
        val store = Store(null).apply { replaceAll(Store.demoEvents(LocalDate.of(2026, 10, 6))) }
        val now = LocalDate.of(2026, 10, 7).atTime(10, 0)
        val sizes = listOf(Triple("als-naechstes", WidgetArt.Kind.UpNext, 160f to 160f), Triple("als-naechstes-breit", WidgetArt.Kind.UpNext, 340f to 160f),
            Triple("monat", WidgetArt.Kind.Month, 160f to 160f), Triple("liste", WidgetArt.Kind.List, 340f to 360f))
        for (dark in listOf(false, true)) for ((name, kind, size) in sizes) {
            val bitmap = WidgetArt.draw(context, kind, store, now, size.first, size.second, dark)
            assertEquals((size.first * context.resources.displayMetrics.density).toInt(), bitmap.width)
            if (out != null) {
                File(out).mkdirs()
                File(out, "widget-$name${if (dark) "-dunkel" else ""}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
