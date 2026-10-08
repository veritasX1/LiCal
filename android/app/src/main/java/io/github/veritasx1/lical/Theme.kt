package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.I18n
import io.github.veritasx1.lical.i18n.tr
import java.time.LocalDate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colors and type like iOS 26 Calendar: Apple's system colors (light and dark), label grays, and
 *  Inter (free, close to SF Pro). Ubuntu: theme.py. */
private val SYSTEM_LIGHT = mapOf(
    "red" to Color(0xFFFF3B30), "orange" to Color(0xFFFF9500), "yellow" to Color(0xFFFFCC00), "green" to Color(0xFF34C759),
    "mint" to Color(0xFF00C7BE), "teal" to Color(0xFF30B0C7), "blue" to Color(0xFF007AFF), "indigo" to Color(0xFF5856D6),
    "purple" to Color(0xFFAF52DE), "pink" to Color(0xFFFF2D55), "brown" to Color(0xFFA2845E),
)
private val SYSTEM_DARK = mapOf(
    "red" to Color(0xFFFF453A), "orange" to Color(0xFFFF9F0A), "yellow" to Color(0xFFFFD60A), "green" to Color(0xFF30D158),
    "mint" to Color(0xFF63E6E2), "teal" to Color(0xFF40C8E0), "blue" to Color(0xFF0A84FF), "indigo" to Color(0xFF5E5CE6),
    "purple" to Color(0xFFBF5AF2), "pink" to Color(0xFFFF375F), "brown" to Color(0xFFAC8E68),
)

class Palette(val dark: Boolean) {
    val background = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val label = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color(0x99EBEBF5) else Color(0x993C3C43)
    val tertiary = if (dark) Color(0x4DEBEBF5) else Color(0x4D3C3C43)
    val separator = if (dark) Color(0xFF38383A) else Color(0xFFE0E0E5)
    val glass = if (dark) Color(0xFF2C2C2E).copy(alpha = 0.86f) else Color(0xFFF7F7F9).copy(alpha = 0.92f)
    val glassBorder = if (dark) Color(0x33FFFFFF) else Color(0x1A000000)
    /** Navigation and tool bars (iOS: a light translucent gray over the content). */
    val bar = if (dark) Color(0xFF121212) else Color(0xFFF9F9F9)
    val red = system("red")

    /** A system color by name – or a calendar's own color "#RRGGBB" (phone calendars). */
    fun system(name: String): Color {
        if (name.startsWith("#") && name.length == 7) return Color(0xFF000000 or name.substring(1).toLong(16))
        return (if (dark) SYSTEM_DARK else SYSTEM_LIGHT)[name] ?: (if (dark) SYSTEM_DARK else SYSTEM_LIGHT).getValue("blue")
    }

    /** Tinted background of an event pill/block. */
    fun eventFill(color: Color) = color.copy(alpha = if (dark) 0.32f else 0.2f)

    /** Text on a tinted event: a deep shade of the color (light), a light one (dark) – as on Ubuntu. */
    fun eventText(color: Color) = if (dark) lerp(color, Color.White, 0.55f) else lerp(Color.Black, color, 0.55f)
}

val LocalPalette = staticCompositionLocalOf { Palette(false) }

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)

fun style(size: Float, weight: Int = 400, color: Color = Color.Unspecified, tabular: Boolean = false) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = FontWeight(weight), color = color,
        fontFeatureSettings = if (tabular) "tnum" else null, letterSpacing = if (size >= 28) (-0.6).sp else 0.sp)

// Getters, not values: a top-level val is filled once when the class loads – before the app language is known,
// so EN/FR kept „Oktober“ and „M D M D F S S“ (Michelle 3e568fce).
val MONTHS get() = listOf(tr("Januar"), tr("Februar"), tr("März"), tr("April"), tr("Mai"), tr("Juni"), tr("Juli"), tr("August"), tr("September"), tr("Oktober"), tr("November"), tr("Dezember"))
val WEEKDAY_LETTERS get() = tr("M D M D F S S").split(" ")
val WEEKDAYS_LONG get() = listOf(tr("Montag"), tr("Dienstag"), tr("Mittwoch"), tr("Donnerstag"), tr("Freitag"), tr("Samstag"), tr("Sonntag"))

/** Dates as each language writes them – the same as Ubuntu's theme.date_text: „Montag, 5. Oktober 2026“,
 *  „Monday, October 5, 2026“, „lundi 5 octobre 2026“. Never glue „{day}. {month}“ together elsewhere. */
object Dates {
    private fun lang() = I18n.language()

    fun shortMonth(month: Int): String {
        val name = MONTHS[month - 1]
        return if (name.length <= 4) name else name.take(3) + if (lang() == "en") "" else "."
    }

    /** Mo. · Mon · lun. */
    fun shortWeekday(index: Int): String = when (lang()) {
        "de" -> tr("Mo Di Mi Do Fr Sa So").split(" ")[index] + "."
        "fr" -> WEEKDAYS_LONG[index].take(3) + "."
        else -> WEEKDAYS_LONG[index].take(3)
    }

    fun dayMonth(day: LocalDate, short: Boolean = false): String {
        val month = if (short) shortMonth(day.monthValue) else MONTHS[day.monthValue - 1]
        return when (lang()) {
            "en" -> "$month ${day.dayOfMonth}"
            "fr" -> "${if (day.dayOfMonth == 1 && !short) "1er" else day.dayOfMonth.toString()} $month"
            else -> "${day.dayOfMonth}. $month"
        }
    }

    fun text(day: LocalDate, weekday: Boolean = true, year: Boolean = true, short: Boolean = false): String {
        var text = dayMonth(day, short)
        if (year) text += (if (lang() == "en") ", " else " ") + day.year
        if (weekday) {
            val name = if (short) shortWeekday(day.dayOfWeek.value - 1) else WEEKDAYS_LONG[day.dayOfWeek.value - 1]
            text = name + (if (lang() == "fr") " " else ", ") + text
        }
        return text
    }

    /** 5.10.2026 · 10/5/2026 · 05/10/2026 */
    fun numeric(day: LocalDate, year: Boolean = true): String = when (lang()) {
        "en" -> "${day.monthValue}/${day.dayOfMonth}" + if (year) "/${day.year}" else ""
        "fr" -> "%02d/%02d".format(day.dayOfMonth, day.monthValue) + if (year) "/${day.year}" else ""
        else -> "${day.dayOfMonth}.${day.monthValue}." + if (year) "${day.year}" else ""
    }
}

@Composable
fun palette() = LocalPalette.current
