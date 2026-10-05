package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth

enum class Screen { Month, Day, Year }

/** LiCal on the phone, like Apple's Calendar on the iPhone (Olaf's reference): year → month → day,
 *  a navigation bar with the red back button and ＋, a toolbar with "Heute" and "Kalender". */
@Composable
fun LiCalApp(store: Store, dark: Boolean = isSystemInDarkTheme(), start: Screen = Screen.Month, startDay: LocalDate = LocalDate.now(),
             request: EditRequest? = null) {
    val colors = remember(dark) { Palette(dark) }
    CompositionLocalProvider(LocalPalette provides colors) {
        var screen by remember { mutableStateOf(start) }
        var day by remember { mutableStateOf(startDay) }
        var month by remember { mutableStateOf(YearMonth.from(startDay)) }
        var visibleMonth by remember { mutableStateOf(YearMonth.from(startDay)) }
        var calendarsOpen by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(Settings.monthMode) }
        var modeMenu by remember { mutableStateOf(false) }
        // Opened by another app ("Termin hinzufügen", an event, an .ics file): straight into the editor.
        var editing by remember { mutableStateOf(request) }
        fun create(on: LocalDate, hour: Int? = null) {
            val calendar = store.defaultCalendar()
            editing = EditRequest(Editing.newEvent(on, java.time.LocalDateTime.now(), calendar, java.util.UUID.randomUUID().toString().replace("-", ""), hour), null, true)
        }
        fun edit(item: Occurrence) {
            // A phone calendar's occurrence carries no alerts: take them from the stored event.
            val alerts = if (item.event.id.startsWith(DEVICE)) store.event(item.event.id)?.alerts ?: emptyList() else item.event.alerts
            // Another zone: edited in the event's own times (card 7a9187d6).
            val own = Rules.forEditing(item)
            editing = EditRequest(item.event.copy(start = own.start, end = own.end, alerts = alerts), own.start, false)
        }
        val monthList = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            // Navigation bar: red "‹ 2026" / "‹ Juni" back to the larger view, red ＋ on the right.
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(44.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (screen) {
                    Screen.Month -> BackButton(if (mode == MonthMode.Details) "${MONTHS[visibleMonth.monthValue - 1].take(3)} ${visibleMonth.year}" else visibleMonth.year.toString()) { screen = Screen.Year }
                    Screen.Day -> BackButton(MONTHS[day.monthValue - 1]) { month = YearMonth.from(day); screen = Screen.Month }
                    Screen.Year -> Spacer(Modifier.size(1.dp))
                }
                Spacer(Modifier.weight(1f))
                if (screen == Screen.Month) Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (mode == MonthMode.Details) colors.red else Color.Transparent).clickable { modeMenu = true }
                    .semantics { contentDescription = tr("Monatsansicht wählen") }, contentAlignment = Alignment.Center) {
                    GlyphIcon(if (mode == MonthMode.Details) Glyph.List else if (mode == MonthMode.Stacked) Glyph.Stacked else Glyph.Compact,
                        if (mode == MonthMode.Details) Color.White else colors.red, 24.dp)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable {
                    create(if (screen == Screen.Month && YearMonth.from(day) != visibleMonth) visibleMonth.atDay(1) else day)
                }.semantics { contentDescription = tr("Neuer Termin") }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Plus, colors.red, 24.dp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Month -> {
                            if (mode != MonthMode.Details) BasicText(MONTHS[visibleMonth.monthValue - 1], style = style(30f, 700, colors.label),
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                            WeekdayLetters()
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                            if (mode == MonthMode.Details) {
                                // Details: the chosen day stays in the month in view; a tap only chooses it.
                                MonthDetails(store, if (YearMonth.from(day) == visibleMonth) day else visibleMonth.atDay(1),
                                    onDay = { picked -> day = picked }, onMonth = { shown -> visibleMonth = shown; month = shown }, onEvent = { edit(it) })
                            } else MonthScreen(store, month, monthList, mode == MonthMode.Compact, onVisibleMonth = { visibleMonth = it }) { picked -> day = picked; screen = Screen.Day }
                        }
                        Screen.Day -> DayScreen(store, day, onEvent = { edit(it) }, onSlot = { on, hour -> create(on, hour) }) { picked -> day = picked }
                        Screen.Year -> YearScreen(visibleMonth.year) { picked -> month = picked; visibleMonth = picked; screen = Screen.Month }
                    }
                }
            }
            // Toolbar: "Heute" and "Kalender" in red over a hairline (Apple's "Today · Calendars · Inbox";
            // the inbox comes with invitations).
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            Row(Modifier.fillMaxWidth().background(colors.bar).windowInsetsPadding(WindowInsets.navigationBars).height(50.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ToolbarText(tr("Heute"), Modifier.weight(1f), Alignment.CenterStart) {
                    val today = LocalDate.now()
                    day = today
                    month = YearMonth.from(today)
                    visibleMonth = month
                    if (screen == Screen.Year) screen = Screen.Month
                }
                ToolbarText(tr("Kalender"), Modifier.weight(1f), Alignment.Center) { calendarsOpen = true }
                Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (calendarsOpen) CalendarsSheet(store) { calendarsOpen = false }
            if (modeMenu) ModeMenu(mode, onClose = { modeMenu = false }) { chosen ->
                mode = chosen
                Settings.monthMode = chosen
                modeMenu = false
            }
            editing?.let { request ->
                EditorSheet(store, request) { saved ->
                    editing = null
                    // A new event: show its day (also when it went to a phone calendar and got a new id there).
                    if (request.isNew && saved != null) {
                        day = Rules.parse(saved.start).date
                        screen = Screen.Day
                    }
                }
            }
        }
        }
    }
}

/** The month's look, as a small menu under the button (iOS: Kompakt, Gestapelt, Details). */
@Composable
private fun ModeMenu(current: MonthMode, onClose: () -> Unit, onChoose: (MonthMode) -> Unit) {
    val colors = palette()
    val card = if (colors.dark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)
    Box(Modifier.fillMaxSize().clickable(onClick = onClose)) {
        Column(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 48.dp, end = 12.dp).width(230.dp)
            .shadow(24.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(card)) {
            MonthMode.entries.forEachIndexed { index, choice ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                Row(Modifier.fillMaxWidth().clickable { onChoose(choice) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (choice == current) "✓" else "", style = style(15f, 700, colors.label), modifier = Modifier.width(24.dp))
                    BasicText(choice.label, style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                    GlyphIcon(when (choice) { MonthMode.Compact -> Glyph.Compact; MonthMode.Stacked -> Glyph.Stacked; MonthMode.Details -> Glyph.List }, colors.label, 20.dp)
                }
            }
        }
    }
}

/** A red back button like UIKit's: chevron and the title of the larger view. */
@Composable
private fun BackButton(label: String, onClick: () -> Unit) {
    val colors = palette()
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.Back, colors.red, 20.dp)
        BasicText(label, style = style(17f, 400, colors.red))
    }
}

@Composable
private fun ToolbarText(label: String, modifier: Modifier, alignment: Alignment, onClick: () -> Unit) {
    val colors = palette()
    Box(modifier.fillMaxSize(), contentAlignment = alignment) {
        BasicText(label, style = style(17f, 400, colors.red), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp))
    }
}

/** The calendars like "Kalender" on the iPhone: grouped by account – LiCal's own, then the phone's
 *  (Google via DAVx⁵, holidays …); a tap shows or hides one. The phone's calendars need Android's
 *  permission, asked only when Olaf taps "Kalender des Handys zeigen". */
@Composable
private fun CalendarsSheet(store: Store, onClose: () -> Unit) {
    val colors = palette()
    val revision = store.revision
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { store.refreshDevice() }
    val card = if (colors.dark) Color(0xFF2C2C2E) else Color.White
    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(if (colors.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)).padding(vertical = 14.dp)
            .verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Kalender"), style = style(20f, 700, colors.label), modifier = Modifier.weight(1f))
                BasicText(tr("Fertig"), style = style(17f, 600, colors.red), modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
            }
            key(revision) {
                val groups = linkedMapOf<String, List<CalendarInfo>>("LICAL" to store.calendars)
                store.deviceCalendars.groupBy { it.account }.forEach { (account, list) ->
                    groups[account] = list.mapNotNull { store.calendar(DEVICE + it.id) }
                }
                groups.forEach { (account, list) ->
                    BasicText((if (account == "LICAL") "LiCal" else account).uppercase(), style = style(13f, 400, colors.secondary),
                        modifier = Modifier.padding(start = 32.dp, top = 14.dp, bottom = 6.dp))
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
                        list.forEachIndexed { index, calendar ->
                            Row(Modifier.fillMaxWidth().clickable { store.setVisible(calendar.id, !calendar.visible) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val color = colors.system(calendar.color)
                                Box(Modifier.size(24.dp).clip(CircleShape).background(if (calendar.visible) color else color.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center) {
                                    if (calendar.visible) BasicText("✓", style = style(14f, 700, Color.White))
                                }
                                BasicText(calendar.name, style = style(17f, 400, colors.label), modifier = Modifier.padding(start = 14.dp).weight(1f))
                                if (store.isReadOnly(calendar.id)) BasicText(tr("nur lesen"), style = style(13f, 400, colors.secondary))
                            }
                            if (index < list.lastIndex) Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
                        }
                    }
                }
                HolidaysGroup(store, card)
                // Widgets like the iPhone's (card b1a0984b) – the launcher shows its own "Hinzufügen" dialog.
                val context = androidx.compose.ui.platform.LocalContext.current
                var widgetsOpen by remember { mutableStateOf(false) }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
                    Column(Modifier.fillMaxWidth().clickable { widgetsOpen = !widgetsOpen }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        BasicText(tr("Widgets auf den Startbildschirm"), style = style(17f, 400, colors.red))
                        BasicText(tr("Als Nächstes, Monat, Liste – und Apples Kalender-Symbol mit dem heutigen Datum."),
                            style = style(13f, 400, colors.secondary), modifier = Modifier.padding(top = 2.dp))
                    }
                    if (widgetsOpen) listOf(tr("Als Nächstes") to UpNextWidget::class.java, tr("Monat") to MonthWidget::class.java,
                        "Liste" to ListWidget::class.java, tr("Datum (wie ein App-Symbol)") to DateWidget::class.java).forEach { (name, type) ->
                        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
                        BasicText(name, style = style(17f, 400, colors.label), modifier = Modifier.fillMaxWidth()
                            .clickable { DateWidget.pin(context, type); widgetsOpen = false }.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                // Settings (iOS: Settings › Kalender): default calendar, time zone support.
                var defaultOpen by remember { mutableStateOf(false) }
                val writable = store.writableCalendars()
                val chosen = store.calendar(store.defaultCalendar())
                var zones by remember { mutableStateOf(Settings.timeZones) }
                BasicText("EINSTELLUNGEN", style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 32.dp, top = 14.dp, bottom = 6.dp))
                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
                    Row(Modifier.fillMaxWidth().clickable { defaultOpen = !defaultOpen }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // The label stays on one line; a long name (an e-mail address) is shortened with "…".
                        BasicText("Standardkalender", style = style(17f, 400, colors.label), maxLines = 1, softWrap = false)
                        BasicText(chosen?.name ?: "", style = style(17f, 400, colors.secondary).copy(textAlign = TextAlign.End), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        UpDownChevron(colors.secondary)
                    }
                    if (defaultOpen) writable.forEach { calendar ->
                        Row(Modifier.fillMaxWidth().clickable { Settings.defaultCalendar = calendar.id; defaultOpen = false; store.touch() }
                            .padding(start = 32.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.system(calendar.color)))
                            BasicText(calendar.name, style = style(16f, 400, colors.label), modifier = Modifier.padding(start = 10.dp).weight(1f))
                            if (calendar.id == chosen?.id) BasicText("✓", style = style(16f, 600, colors.red))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(card).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Zeitzonen-Unterstützung"), style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                    IosSwitch(zones) { zones = it; Settings.timeZones = it }
                }
                // Language: like the system or chosen here – takes effect at the next start.
                Spacer(Modifier.height(8.dp))
                val i18n = io.github.veritasx1.lical.i18n.I18n
                var language by remember { mutableStateOf(i18n.chosen) }
                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
                    (listOf<String?>(null) + i18n.LANGUAGES.keys).forEach { code ->
                        Row(Modifier.fillMaxWidth().clickable { i18n.chosen = code; language = code }.padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            BasicText(code?.let { i18n.LANGUAGES[it] } ?: tr("Sprache: wie das System"), style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                            if (code == language) BasicText("✓", style = style(17f, 600, colors.red))
                        }
                    }
                }
                BasicText(tr("Die Sprache wechselt beim nächsten Start von LiCal."), style = style(13f, 400, colors.secondary),
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp))
                if (!store.devicePermitted()) {
                    Column(Modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(card).clickable {
                        permission.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))
                    }.padding(16.dp)) {
                        BasicText(tr("Kalender des Handys zeigen"), style = style(17f, 400, colors.red))
                        BasicText(tr("Google (über DAVx⁵), Feiertage und andere Kalender des Handys erscheinen in LiCal und lassen sich hier bearbeiten. Es wird nichts kopiert."),
                            style = style(13f, 400, colors.secondary), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** "ANDERE": the computed holidays (card 7a9187d6) – on/off and the Bundesland. */
@Composable
private fun HolidaysGroup(store: Store, card: Color) {
    val colors = palette()
    val info = store.holidayInfo()
    var open by remember { mutableStateOf(false) }
    BasicText("ANDERE", style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 32.dp, top = 14.dp, bottom = 6.dp))
    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
        Row(Modifier.fillMaxWidth().clickable { store.setVisible(Holidays.CALENDAR, !info.visible) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val color = colors.system(info.color)
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (info.visible) color else color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                if (info.visible) BasicText("✓", style = style(14f, 700, Color.White))
            }
            BasicText(info.name, style = style(17f, 400, colors.label), modifier = Modifier.padding(start = 14.dp).weight(1f))
            BasicText(tr("nur lesen"), style = style(13f, 400, colors.secondary))
        }
        Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Bundesland", style = style(17f, 400, colors.label), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 38.dp))
            BasicText(Holidays.STATES.firstOrNull { it.first == store.holidays.state }?.second.orEmpty(), style = style(17f, 400, colors.secondary).copy(textAlign = TextAlign.End),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
            UpDownChevron(colors.secondary)
        }
        if (open) Holidays.STATES.forEach { (key, name) ->
            Row(Modifier.fillMaxWidth().clickable { store.setHolidays(store.holidays.copy(state = key)); open = false }
                .padding(start = 70.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, style = style(16f, 400, colors.label), modifier = Modifier.weight(1f))
                if (key == store.holidays.state) BasicText("✓", style = style(16f, 600, colors.red))
            }
        }
    }
    if (!store.holidays.chosen && !info.visible && store.holidays.visible)
        BasicText(tr("Ausgeblendet, weil der Feiertagskalender des Handys schon gezeigt wird."), style = style(13f, 400, colors.secondary),
            modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 6.dp))
}

@Composable
private fun key(value: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(value) { content() }
