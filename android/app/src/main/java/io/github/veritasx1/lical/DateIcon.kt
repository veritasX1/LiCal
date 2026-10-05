package io.github.veritasx1.lical

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.content.res.ResourcesCompat
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/** Apple's calendar icon with today's date, drawn as a picture (the same design as Ubuntu's icon.py):
 *  white squircle, the weekday bold in red ("So."), the day of the month very large and black. */
object DateIcon {
    val WEEKDAYS = listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")

    /** Apple's continuous corners: a superellipse |x|^5 + |y|^5 = 1. */
    fun squircle(left: Float, top: Float, size: Float, exponent: Double = 5.0, steps: Int = 96): Path {
        val half = size / 2
        val path = Path()
        for (step in 0 until steps) {
            val angle = 2 * Math.PI * step / steps
            val x = left + half + half * (sign(cos(angle)) * abs(cos(angle)).let { Math.pow(it, 2 / exponent) }).toFloat()
            val y = top + half + half * (sign(sin(angle)) * abs(sin(angle)).let { Math.pow(it, 2 / exponent) }).toFloat()
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    fun draw(context: Context, day: LocalDate, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = size / 512f
        val inter = runCatching { ResourcesCompat.getFont(context, R.font.inter) }.getOrNull()
        val face = squircle(40 * unit, 40 * unit, 432 * unit)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0); setShadowLayer(8 * unit, 0f, 5 * unit, Color.argb(60, 0, 0, 0)) }
        canvas.drawPath(face, shadow)
        canvas.drawPath(face, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 40 * unit, 0f, 472 * unit, Color.WHITE, Color.rgb(0xEC, 0xEC, 0xEF), Shader.TileMode.CLAMP)
        })
        fun text(value: String, y: Float, textSize: Float, weight: Int, color: Int, tracking: Float = 0f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = inter
                this.textSize = textSize * unit
                this.color = color
                textAlign = Paint.Align.CENTER
                fontVariationSettings = "'wght' $weight"
                letterSpacing = tracking
            }
            canvas.drawText(value, 256 * unit, y * unit, paint)
        }
        text(WEEKDAYS[day.dayOfWeek.value - 1], 170f, 98f, 700, Color.rgb(0xFF, 0x3B, 0x30))
        text(day.dayOfMonth.toString(), 408f, 276f, 500, Color.BLACK, -0.022f)
        return bitmap
    }
}
