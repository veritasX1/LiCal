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

/** The date widget's picture: Apple's icon with the day – red weekday on top, black number, transparent corners. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateIconTest {
    @Test
    fun drawsTheDay() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val bitmap = DateIcon.draw(context, LocalDate.of(2026, 10, 28), 256)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(2, 2)))  // outside the squircle
        var red = 0
        var black = 0
        for (y in 0 until 256) for (x in 0 until 256) {
            val p = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
            if (y < 100 && r > 200 && g < 110 && b < 110) red++
            if (y > 100 && r < 40 && g < 40 && b < 40 && android.graphics.Color.alpha(p) > 200) black++
        }
        assertTrue("red weekday ($red)", red > 150)
        assertTrue("black number ($black)", black > 1500)
        System.getProperty("lical.shots")?.let { out ->
            File(out).mkdirs()
            File(out, "datum-widget.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
