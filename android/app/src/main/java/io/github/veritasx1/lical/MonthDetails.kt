package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGES_AROUND = 600

/** "Details" month like the iPhone's: the month on a gray ground at the top (swipe for the next or
 *  previous one), the chosen day in a black circle (today in red), dots for days with events – and
 *  below it the chosen day's events with start and end time and a colored bar. */
@Composable
fun MonthDetails(store: Store, day: LocalDate, onDay: (LocalDate) -> Unit, onMonth: (YearMonth) -> Unit, onEvent: (Occurrence) -> Unit) {
    val colors = palette()
    val origin = remember { YearMonth.now() }
    val pager = rememberPagerState(initialPage = PAGES_AROUND + ChronoUnit.MONTHS.between(origin, YearMonth.from(day)).toInt()) { 2 * PAGES_AROUND + 1 }
    LaunchedEffect(day) {
        val page = PAGES_AROUND + ChronoUnit.MONTHS.between(origin, YearMonth.from(day)).toInt()
        if (pager.currentPage != page) pager.scrollToPage(page)
    }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onMonth(origin.plusMonths((it - PAGES_AROUND).toLong())) } }
    val revision = store.revision
    val grid = if (colors.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxWidth().background(grid)) { page ->
            val month = origin.plusMonths((page - PAGES_AROUND).toLong())
            val busy = remember(month, revision) {
                store.occurrences(month.atDay(1), month.plusMonths(1).atDay(1)).flatMap { item ->
                    val (a, b) = Rules.daysCovered(item)
                    generateSequence(a) { it.plusDays(1) }.takeWhile { it <= b }.toList()
                }.toSet()
            }
            Column {
                for (week in Rules.monthWeeks(month.year, month.monthValue)) {
                    val inMonth = week.map { YearMonth.from(it) == month }
                    Box(Modifier.fillMaxWidth().height(50.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                            val column = size.width / 7
                            val first = inMonth.indexOfFirst { it }
                            val last = inMonth.indexOfLast { it }
                            drawLine(colors.separator, Offset(column * first, 0f), Offset(column * (last + 1), 0f), 0.5.dp.toPx())
                        }
                        Row(Modifier.fillMaxSize()) {
                            week.forEachIndexed { index, date ->
                                Column(Modifier.weight(1f).fillMaxSize().clickable(enabled = inMonth[index]) { onDay(date) }.padding(top = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (!inMonth[index]) return@Column
                                    val selected = date == day
                                    val today = date == LocalDate.now()
                                    val circle = when {
                                        selected && today -> colors.red
                                        selected -> colors.label
                                        else -> Color.Transparent
                                    }
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
                                        BasicText(date.dayOfMonth.toString(), style = style(17f, if (selected || today) 600 else 400, when {
                                            selected -> colors.background
                                            today -> colors.red
                                            date.dayOfWeek.value >= 6 -> colors.secondary
                                            else -> colors.label
                                        }, tabular = true))
                                    }
                                    if (date in busy) Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(colors.secondary))
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        val items = remember(day, revision) { store.occurrences(day, day.plusDays(1)) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(maxOf(items.size, 0)) { index ->
                val item = items[index]
                val color = colors.system(store.calendar(item.event.calendar)?.color ?: "blue")
                Row(Modifier.fillMaxWidth().clickable { onEvent(item) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(56.dp)) {
                        if (item.allDay || Rules.daysCovered(item).let { (a, b) -> b > a }) {
                            BasicText(tr("ganztägig"), style = style(11f, 500, colors.label))
                        } else {
                            BasicText(Rules.parse(item.start).at.toLocalTime().toString(), style = style(11f, 500, colors.label, tabular = true))
                            BasicText(Rules.parse(item.end).at.toLocalTime().toString(), style = style(11f, 400, colors.secondary, tabular = true))
                        }
                    }
                    Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(1.5.dp)).background(color))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        BasicText(item.title, style = style(15f, 500, colors.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.event.absence != null) BasicText(Editing.absenceText(item.event), style = style(12f, 400, colors.secondary), maxLines = 1)
                        if (item.event.location.isNotEmpty()) BasicText(item.event.location, style = style(12f, 400, colors.secondary), maxLines = 1)
                    }
                }
                Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
            }
            if (items.isEmpty()) item {
                BasicText(tr("Keine Termine"), style = style(15f, 400, colors.secondary).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
