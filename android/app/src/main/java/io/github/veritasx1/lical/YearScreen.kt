package io.github.veritasx1.lical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

private const val YEARS_AROUND = 50
private val MONTH_HEIGHT = 150.dp

/** Year view like the iPhone's: the years scroll on, each with twelve small months in three columns,
 *  month names in red, the days small and all in the same size, today in a red circle. One drawing per
 *  year (not hundreds of little views) – the scrolling stays smooth also across year changes.
 *  A tap on a month opens it in the month view. */
@Composable
fun YearScreen(focus: Int, onMonth: (YearMonth) -> Unit) {
    val colors = palette()
    val center = remember { LocalDate.now().year }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = YEARS_AROUND + (focus - center))
    val today = remember { LocalDate.now() }
    val measurer = rememberTextMeasurer(cacheSize = 120)
    // Every text measured once: day numbers 1–31 (normal and today), month names.
    val days = remember(colors) { (1..31).associateWith { measurer.measure(it.toString(), style(10f, 500, colors.label, tabular = true)) } }
    val todayText = remember(colors) { measurer.measure(today.dayOfMonth.toString(), style(10f, 700, Color.White, tabular = true)) }
    val names = remember(colors) { MONTHS.map { measurer.measure(it, style(15f, 700, colors.red)) } }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(2 * YEARS_AROUND + 1, key = { it }) { index ->
            val year = center - YEARS_AROUND + index
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                BasicText(year.toString(), style = style(30f, 700, if (year == today.year) colors.red else colors.label),
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                Canvas(Modifier.fillMaxWidth().height(MONTH_HEIGHT * 4 + 8.dp).pointerInput(year) {
                    detectTapGestures { offset ->
                        val column = (offset.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                        val row = ((offset.y - 8.dp.toPx()) / MONTH_HEIGHT.toPx()).toInt().coerceIn(0, 3)
                        onMonth(YearMonth.of(year, row * 3 + column + 1))
                    }
                }) {
                    val monthWidth = size.width / 3f
                    val cell = (monthWidth - 12.dp.toPx()) / 7f
                    val rowHeight = 17.dp.toPx()
                    for (m in 0 until 12) {
                        val left = (m % 3) * monthWidth + 6.dp.toPx()
                        val top = 8.dp.toPx() + (m / 3) * MONTH_HEIGHT.toPx()
                        drawText(names[m], topLeft = Offset(left + 2.dp.toPx(), top))
                        Rules.monthWeeks(year, m + 1).forEachIndexed { week, dates ->
                            dates.forEachIndexed { weekday, date ->
                                if (date.monthValue != m + 1) return@forEachIndexed
                                val cx = left + weekday * cell + cell / 2
                                val cy = top + 30.dp.toPx() + week * rowHeight
                                val isToday = date == today
                                val text: TextLayoutResult = if (isToday) todayText else days.getValue(date.dayOfMonth)
                                if (isToday) drawCircle(colors.red, 8.dp.toPx(), Offset(cx, cy))
                                drawText(text, topLeft = Offset(cx - text.size.width / 2f, cy - text.size.height / 2f))
                            }
                        }
                    }
                }
            }
        }
    }
}
