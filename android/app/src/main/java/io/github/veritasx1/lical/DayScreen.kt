package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val HOUR = 52.dp
private val GUTTER = 54.dp
private const val DAYS_AROUND = 3650

/** Day view like the iPhone's: the week as a strip of letters and numbers (the chosen day in a black
 *  circle, today red), the date below it, all-day events, then the hours – swipe sideways for the next
 *  or previous day. */
@Composable
fun DayScreen(store: Store, day: LocalDate, onEvent: (Occurrence) -> Unit = {}, onSlot: (LocalDate, Int) -> Unit = { _, _ -> }, onDay: (LocalDate) -> Unit) {
    val colors = palette()
    val origin = remember { LocalDate.now() }
    val pager = rememberPagerState(initialPage = DAYS_AROUND + ChronoUnit.DAYS.between(origin, day).toInt()) { 2 * DAYS_AROUND + 1 }
    LaunchedEffect(day) {
        val page = DAYS_AROUND + ChronoUnit.DAYS.between(origin, day).toInt()
        if (pager.currentPage != page) pager.scrollToPage(page)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> onDay(origin.plusDays((page - DAYS_AROUND).toLong())) }
    }
    Column(Modifier.fillMaxSize()) {
        WeekStrip(day, onDay)
        BasicText(Dates.text(day),
            style = style(13f, 600, colors.label).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val shown = origin.plusDays((page - DAYS_AROUND).toLong())
            DayTimeline(store, shown, onEvent) { hour -> onSlot(shown, hour) }
        }
    }
}

@Composable
private fun WeekStrip(day: LocalDate, onDay: (LocalDate) -> Unit) {
    val colors = palette()
    val monday = Rules.weekStart(day)
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        WeekdayLetters()
        Row(Modifier.fillMaxWidth()) {
            for (offset in 0 until 7) {
                val date = monday.plusDays(offset.toLong())
                val selected = date == day
                val background = when {
                    selected && date == today -> colors.red
                    selected -> colors.label
                    else -> Color.Transparent
                }
                val text = when {
                    selected -> colors.background
                    date == today -> colors.red
                    date.dayOfWeek.value >= 6 -> colors.secondary
                    else -> colors.label
                }
                Box(Modifier.weight(1f).height(40.dp).clickable { onDay(date) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                        BasicText(date.dayOfMonth.toString(), style = style(17f, if (selected || date == today) 600 else 400, text, tabular = true))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(store: Store, day: LocalDate, onEvent: (Occurrence) -> Unit, onSlot: (Int) -> Unit) {
    val colors = palette()
    val revision = store.revision
    val items = remember(day, revision) { store.occurrences(day, day.plusDays(1)) }
    val allDay = items.filter { it.allDay || Rules.daysCovered(it).let { (a, b) -> b > a } }
    val blocks = remember(items) { Rules.timelineLayout(items, day) }
    val byKey = items.associateBy { it.key }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var scrolled by remember(day) { mutableStateOf(false) }
    LaunchedEffect(day, scroll.maxValue) {
        if (scrolled || scroll.maxValue == 0) return@LaunchedEffect
        // Open at the morning: the first event (half an hour before), at the latest 7:30 – today at most an hour before now.
        var hour = blocks.minOfOrNull { it.top / 60f - 0.5f }?.coerceAtMost(7.5f) ?: 7.5f
        if (day == LocalDate.now()) hour = minOf(hour, LocalTime.now().hour - 1f)
        scroll.scrollTo(with(density) { (HOUR * maxOf(0f, hour)).roundToPx() })
        scrolled = true
    }
    Column(Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                BasicText(tr("ganztägig"), style = style(9.5f, 500, colors.secondary).copy(textAlign = TextAlign.End), softWrap = false, maxLines = 1,
                    modifier = Modifier.width(GUTTER - 4.dp).padding(top = 5.dp))
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    for (item in allDay) EventPill(item.title + (item.event.deputy?.let { tr(" · Vertretung: {it}", "it" to it) } ?: ""),
                        colors.system(store.calendar(item.event.calendar)?.color ?: "blue"),
                        Modifier.fillMaxWidth().padding(bottom = 2.dp).clickable { onEvent(item) }, height = 22.dp, fontSize = 12f,
                        striped = item.event.absence != null)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val width = maxWidth
            // Long press on a free hour: a new event there (like the iPhone).
            Box(Modifier.fillMaxWidth().height(HOUR * 24 + 16.dp).pointerInput(day) {
                detectTapGestures(onLongPress = { offset -> onSlot(((offset.y - 8.dp.toPx()) / HOUR.toPx()).toInt().coerceIn(0, 23)) })
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val hour = HOUR.toPx()
                    for (h in 1 until 24) {
                        val y = 8.dp.toPx() + h * hour
                        drawLine(colors.separator, Offset(GUTTER.toPx() - 6.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                    }
                }
                val now = LocalTime.now()
                for (h in 1 until 24) {
                    val nearNow = day == LocalDate.now() && kotlin.math.abs(now.hour * 60 + now.minute - h * 60) < 14
                    if (!nearNow) BasicText("%02d:00".format(h), style = style(11f, 500, colors.secondary, tabular = true).copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(GUTTER - 10.dp).offset(y = 8.dp + HOUR * h - 7.dp))
                }
                val area = width - GUTTER - 8.dp
                for (block in blocks) {
                    val item = byKey.getValue(block.key)
                    val color = colors.system(store.calendar(item.event.calendar)?.color ?: "blue")
                    val left = GUTTER + area * block.column / block.columns
                    val top = 8.dp + HOUR * (block.top / 60f) + 1.dp
                    val height = maxOf(18.dp, HOUR * ((block.bottom - block.top) / 60f) - 2.dp)
                    Box(Modifier.offset(x = left, y = top).width(area / block.columns - 2.dp).height(height)
                        .clip(RoundedCornerShape(6.dp)).background(colors.eventFill(color)).clickable { onEvent(item) }) {
                        Box(Modifier.width(4.dp).fillMaxSize().background(color))
                        Column(Modifier.padding(start = 9.dp, top = 3.dp, end = 4.dp)) {
                            BasicText(item.title, style = style(12.5f, 600, colors.eventText(color)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (height > 36.dp) BasicText(listOfNotNull(item.event.location.ifEmpty { null }, Rules.parse(item.start).at.toLocalTime().toString()).joinToString(" · "),
                                style = style(12f, 400, colors.eventText(color).copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (day == LocalDate.now()) {
                    val y = 8.dp + HOUR * ((now.hour * 60 + now.minute) / 60f)
                    Canvas(Modifier.fillMaxWidth().height(4.dp).offset(y = y - 2.dp)) {
                        drawLine(colors.red, Offset(GUTTER.toPx() - 6.dp.toPx(), size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                    }
                    // The time on a small red label, like the iPhone.
                    Box(Modifier.offset(x = 4.dp, y = y - 9.dp).clip(RoundedCornerShape(5.dp)).background(colors.red).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        BasicText("%02d:%02d".format(now.hour, now.minute), style = style(11f, 700, Color.White, tabular = true))
                    }
                }
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
