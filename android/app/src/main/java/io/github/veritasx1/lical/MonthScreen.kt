package io.github.veritasx1.lical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

private const val MONTHS_AROUND = 120
private val ROW_HEIGHT = 118.dp
private val COMPACT_ROW_HEIGHT = 66.dp
private val LABEL_HEIGHT = 24.dp
private const val PILLS = 4

/** One row of the month list: a week of one month (days of other months stay empty). The first week
 *  of a month carries the month's name above its 1st, as on the iPhone. */
data class WeekRowData(val month: YearMonth, val days: List<LocalDate>, val first: Boolean)

fun monthRows(center: YearMonth): List<WeekRowData> {
    val rows = mutableListOf<WeekRowData>()
    for (offset in -MONTHS_AROUND..MONTHS_AROUND) {
        val month = center.plusMonths(offset.toLong())
        Rules.monthWeeks(month.year, month.monthValue).forEachIndexed { index, days -> rows.add(WeekRowData(month, days, index == 0)) }
    }
    return rows
}

/** Month view like Apple's Calendar on the iPhone (Olaf's reference): the weeks scroll on without
 *  end, each month starts on a row of its own with its name above the 1st; the large title above
 *  shows the month in view. Day numbers centered and regular, weekends gray, today white in a red
 *  circle, up to four small tinted event pills per day, "+N" for more, hairlines between the weeks. */
@Composable
fun MonthScreen(store: Store, focus: YearMonth, state: LazyListState, compact: Boolean = true, onVisibleMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    val center = remember { YearMonth.now() }
    val rows = remember(center) { monthRows(center) }
    val firstWeek = remember(rows) { rows.withIndex().filter { it.value.first }.associate { it.value.month to it.index } }
    val visibleCallback by rememberUpdatedState(onVisibleMonth)
    val dayCallback by rememberUpdatedState(onDay)
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { LABEL_HEIGHT.roundToPx() }
    LaunchedEffect(focus) {
        val index = firstWeek[focus] ?: return@LaunchedEffect
        // Open at the month's first week; its small name stays just above (the large title says it).
        if (rows.getOrNull(state.firstVisibleItemIndex)?.month != focus || state.firstVisibleItemIndex != index) state.scrollToItem(index, labelPx)
    }
    // The month in view: the one owning the top row – or the next, once that row is mostly gone.
    val visible by remember {
        derivedStateOf {
            val info = state.layoutInfo.visibleItemsInfo.firstOrNull()
            val index = if (info != null && info.offset + info.size * 2 / 3 < 0) state.firstVisibleItemIndex + 1 else state.firstVisibleItemIndex
            rows.getOrNull(index)?.month
        }
    }
    LaunchedEffect(visible) { visible?.let { visibleCallback(it) } }
    val revision = store.revision
    val today = remember { LocalDate.now() }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(rows.size, key = { rows[it].month.toString() + rows[it].days.first() }) { index ->
            WeekRow(store, rows[index], today, revision, compact) { dayCallback(it) }
        }
    }
}

@Composable
private fun WeekRow(store: Store, row: WeekRowData, today: LocalDate, revision: Int, compact: Boolean, onDay: (LocalDate) -> Unit) {
    val colors = palette()
    val items = remember(row, revision) { store.occurrences(row.days.first(), row.days.last().plusDays(1)) }
    val layout = remember(items) { Rules.weekLayout(items, row.days) }
    val byKey = remember(items) { items.associateBy { it.key } }
    val inMonth = remember(row) { row.days.map { YearMonth.from(it) == row.month } }
    Column(Modifier.fillMaxWidth()) {
        if (row.first) {
            // The month's name above its 1st, in the 1st's column (it may run on to the right).
            val column = inMonth.indexOfFirst { it }
            // Late in the week the name would run off the screen: then it ends at the right edge instead.
            val atEnd = column >= 5
            Row(Modifier.fillMaxWidth().height(LABEL_HEIGHT).padding(top = 2.dp, end = if (atEnd) 10.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                if (column > 0 && !atEnd) Spacer(Modifier.weight(column.toFloat()))
                if (atEnd) Spacer(Modifier.weight(1f))
                BasicText(MONTHS[row.month.monthValue - 1], style = style(16f, 700, if (row.month == YearMonth.from(today)) colors.red else colors.label),
                    softWrap = false, maxLines = 1, modifier = if (atEnd) Modifier else Modifier.weight((7 - column).toFloat()).padding(start = 8.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(if (compact) COMPACT_ROW_HEIGHT else ROW_HEIGHT)) {
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                val column = size.width / 7
                val first = inMonth.indexOfFirst { it }
                val last = inMonth.indexOfLast { it }
                if (first >= 0) drawLine(colors.separator, Offset(column * first, 0f), Offset(column * (last + 1), 0f), 0.5.dp.toPx())
            }
            Row(Modifier.fillMaxSize()) {
                row.days.forEachIndexed { column, day ->
                    Column(Modifier.weight(1f).fillMaxSize().clickable(enabled = inMonth[column]) { onDay(day) }.padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!inMonth[column]) return@Column
                        DayNumber(day, today, colors, compact)
                        Spacer(Modifier.height(3.dp))
                        if (compact) {
                            // Compact (Apple's default): small colored marks, one per event, under the number.
                            val marks = items.filter { val (a, b) = Rules.daysCovered(it); a <= day && day <= b }
                                .map { colors.system(store.calendar(it.event.calendar)?.color ?: "blue") }
                            EventMarks(marks)
                            return@Column
                        }
                        val cell = Rules.cellRows(layout, column, PILLS)
                        val barsHere = layout.bars.filter { it.first <= column && column <= it.last && it.lane < cell.lanes }
                        for (lane in 0 until cell.lanes) {
                            val bar = barsHere.firstOrNull { it.lane == lane }
                            if (bar == null) Spacer(Modifier.height(17.dp)) else {
                                val item = byKey.getValue(bar.key)
                                EventPill(if (bar.first == column || column == 0) item.title else "", colors.system(store.calendar(item.event.calendar)?.color ?: "blue"),
                                    Modifier.fillMaxWidth().padding(start = if (bar.first == column) 1.5.dp else 0.dp, end = if (bar.last == column) 1.5.dp else 0.dp, bottom = 1.dp),
                                    height = 16.dp, fontSize = 9.5f, striped = item.event.absence != null)
                            }
                        }
                        for (key in cell.lines) {
                            val item = byKey.getValue(key)
                            EventPill(item.title, colors.system(store.calendar(item.event.calendar)?.color ?: "blue"),
                                Modifier.fillMaxWidth().padding(horizontal = 1.5.dp).padding(bottom = 1.dp), height = 16.dp, fontSize = 9.5f)
                        }
                        if (cell.hidden > 0) BasicText("+${cell.hidden}", style = style(9.5f, 500, colors.secondary))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayNumber(day: LocalDate, today: LocalDate, colors: Palette, compact: Boolean) {
    val weekend = day.dayOfWeek.value >= 6
    Box(Modifier.size(if (compact) 36.dp else 30.dp).clip(CircleShape).background(if (day == today) colors.red else Color.Transparent), contentAlignment = Alignment.Center) {
        BasicText(day.dayOfMonth.toString(), style = style(if (compact) 19f else 17f, if (day == today || compact) 600 else 400, when {
            day == today -> Color.White
            weekend -> colors.secondary
            else -> colors.label
        }, tabular = true).copy(textAlign = TextAlign.Center))
    }
}

/** The small weekday letters under the title (Monday first in Germany), as on the iPhone. */
@Composable
fun WeekdayLetters(modifier: Modifier = Modifier) {
    val colors = palette()
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        WEEKDAY_LETTERS.forEachIndexed { index, letter ->
            BasicText(letter, style = style(11f, 500, if (index >= 5) colors.secondary else colors.label).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
        }
    }
}

/** The compact month's event marks: one event a dot, more a short capsule of their colors (at most four). */
@Composable
private fun EventMarks(colorsOfEvents: List<Color>) {
    if (colorsOfEvents.isEmpty()) return
    val shown = colorsOfEvents.take(4)
    Canvas(Modifier.size(width = if (shown.size == 1) 6.dp else (7 * shown.size).dp, height = 6.dp)) {
        val height = size.height * 0.75f
        val top = (size.height - height) / 2
        if (shown.size == 1) {
            drawCircle(shown[0], size.height / 2.4f, center)
            return@Canvas
        }
        val segment = size.width / shown.size
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, top + height, height / 2, height / 2))
        }
        clipPath(path) {
            shown.forEachIndexed { index, color -> drawRect(color, Offset(index * segment, top), androidx.compose.ui.geometry.Size(segment + 0.5f, height)) }
        }
    }
}
