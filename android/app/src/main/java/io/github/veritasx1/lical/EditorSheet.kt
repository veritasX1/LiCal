package io.github.veritasx1.lical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

/** What the editor works on: a new event, or an occurrence of a stored one. */
data class EditRequest(val event: Event, val occurrenceStart: String?, val isNew: Boolean)

private enum class Picker { None, StartDate, StartTime, EndDate, EndTime }

/** The event editor like the iPhone's: a sheet with ✕ and a red ✓, the title large on top, then
 *  grouped cards – start/end as date and time pills that open a calendar or a time wheel inline,
 *  all-day, repeat, calendar, notes, and "Termin löschen" for an existing event. Saved only with ✓. */
@Composable
fun EditorSheet(store: Store, request: EditRequest, onClose: (saved: Event?) -> Unit) {
    val colors = palette()
    val series = remember(request) { store.event(request.event.id) }
    var draft by remember(request) { mutableStateOf(if (request.isNew && request.event.title == "Neuer Termin") request.event.copy(title = "") else request.event) }
    val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var picker by remember(request) { mutableStateOf(Picker.None) }
    var confirmDiscard by remember(request) { mutableStateOf(false) }
    var confirmDelete by remember(request) { mutableStateOf(false) }
    val changed = draft != request.event || request.isNew
    // A read-only calendar (holidays): the event is shown, not changed.
    val readOnly = !request.isNew && store.isReadOnly(request.event.calendar)
    val lock = if (readOnly) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } } } } else Modifier
    var sharing by remember(request) { mutableStateOf(false) }
    var scanning by remember(request) { mutableStateOf(false) }
    var scanHint by remember(request) { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else scanHint = "LiCal darf die Kamera nicht benutzen – in den Einstellungen erlauben."
    }
    var alertHint by remember(request) { mutableStateOf<String?>(null) }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        alertHint = if (granted) null else "Hinweise brauchen Mitteilungen – in den Einstellungen für LiCal erlauben."
    }
    val groupBackground = if (colors.dark) Color(0xFF1C1C1E) else Color.White
    val sheetBackground = if (colors.dark) Color(0xFF000000) else Color(0xFFF2F2F7)

    fun save() {
        if (draft.title.isBlank()) draft = draft.copy(title = "Neuer Termin")
        val stored = if (series?.rrule != null && request.occurrenceStart != null) Editing.applyToSeries(series, request.occurrenceStart, draft) else draft
        onClose(store.put(stored))
    }

    // A new event: the keyboard opens for the title right away (like the iPhone).
    if (request.isNew) LaunchedEffect(request) { runCatching { titleFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(sheetBackground)) {
            // ✕  title  ✓
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton("Abbrechen", if (colors.dark) Color(0xFF2C2C2E) else Color.White, colors.label) {
                    if (changed && !request.isNew) confirmDiscard = true else onClose(null)
                }
                BasicText(if (request.isNew) "Neuer Termin" else if (readOnly) "Termin" else "Termin bearbeiten", style = style(17f, 600, colors.label).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f))
                if (readOnly) Spacer(Modifier.size(48.dp)) else CircleButton("Sichern", colors.red, Color.White, check = true) { save() }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.ime).padding(horizontal = 16.dp)
                ) {
                // A passed-on event (card b483dadf): fill the new event from its QR code.
                if (request.isNew) {
                    Group(groupBackground) {
                        BasicText("Aus QR-Code übernehmen", style = style(17f, 400, colors.red), modifier = Modifier.fillMaxWidth().clickable {
                            scanHint = null
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED) scanning = true
                            else camera.launch(android.Manifest.permission.CAMERA)
                        }.padding(14.dp))
                    }
                    scanHint?.let { BasicText(it, style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 16.dp, top = 6.dp)) }
                    Spacer(Modifier.height(18.dp))
                }
                Group(groupBackground, lock) {
                    BasicTextField(draft.title, { draft = draft.copy(title = it) }, textStyle = style(22f, 700, colors.label),
                        cursorBrush = SolidColor(colors.red), modifier = Modifier.fillMaxWidth().padding(14.dp)
                            .focusRequester(titleFocus).semantics { contentDescription = "Titel" },
                        decorationBox = { inner -> if (draft.title.isEmpty()) BasicText("Titel", style = style(22f, 700, colors.tertiary)); inner() })
                    Divider(colors)
                    BasicTextField(draft.location, { draft = draft.copy(location = it) }, textStyle = style(17f, 400, colors.label),
                        cursorBrush = SolidColor(colors.red), singleLine = true, modifier = Modifier.fillMaxWidth().padding(14.dp),
                        decorationBox = { inner -> if (draft.location.isEmpty()) BasicText("Ort oder Videoanruf", style = style(17f, 400, colors.tertiary)); inner() })
                }
                Spacer(Modifier.height(18.dp))
                Group(groupBackground, lock) {
                    val start = Rules.parse(draft.start)
                    val endDay = Editing.lastDay(draft)
                    val endTime = Rules.parse(draft.end).at.toLocalTime()
                    TimeRow("Beginn", dateText(start.date), if (draft.allDay) null else "%02d:%02d".format(start.at.hour, start.at.minute),
                        picker == Picker.StartDate, picker == Picker.StartTime,
                        { picker = if (picker == Picker.StartDate) Picker.None else Picker.StartDate },
                        { picker = if (picker == Picker.StartTime) Picker.None else Picker.StartTime })
                    AnimatedVisibility(picker == Picker.StartDate) {
                        InlineCalendar(start.date) { day -> draft = Editing.setStart(draft, Moment(day.atStartOfDay(), false)) }
                    }
                    AnimatedVisibility(picker == Picker.StartTime) {
                        TimeWheel(start.at.hour, start.at.minute) { h, m -> draft = Editing.setStart(draft, Moment(start.date.atTime(h, m), true)) }
                    }
                    Divider(colors)
                    TimeRow("Ende", dateText(endDay), if (draft.allDay) null else "%02d:%02d".format(endTime.hour, endTime.minute),
                        picker == Picker.EndDate, picker == Picker.EndTime,
                        { picker = if (picker == Picker.EndDate) Picker.None else Picker.EndDate },
                        { picker = if (picker == Picker.EndTime) Picker.None else Picker.EndTime })
                    AnimatedVisibility(picker == Picker.EndDate) {
                        InlineCalendar(endDay) { day -> draft = Editing.setEnd(draft, Moment(day.atStartOfDay(), false)) }
                    }
                    AnimatedVisibility(picker == Picker.EndTime) {
                        val end = Rules.parse(draft.end).at
                        TimeWheel(end.hour, end.minute) { h, m -> draft = Editing.setEnd(draft, Moment(end.toLocalDate().atTime(h, m), true)) }
                    }
                    Divider(colors)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Ganztägig", style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                        IosSwitch(draft.allDay) { on -> draft = Editing.setAllDay(draft, on); picker = Picker.None }
                    }
                    // Time zone (card 7a9187d6): with "Zeitzonen-Unterstützung" or when the event has one; the clock time stays.
                    if (!draft.allDay && (Settings.timeZones || draft.tz != null)) {
                        Divider(colors)
                        val zones = listOf<String?>(null) + (Settings.ZONES + listOfNotNull(draft.tz, Rules.localZone())).distinct().sortedBy { Settings.zoneLabel(it) }
                        MenuRow("Zeitzone", draft.tz?.let { Settings.zoneLabel(it) } ?: "Ortszeit",
                            zones.map { it?.let(Settings::zoneLabel) ?: "Ortszeit (schwebend)" }, enabled = !readOnly) { index -> draft = draft.copy(tz = zones[index]) }
                    }
                    Divider(colors)
                    MenuRow("Wiederholen", Editing.repeatLabel(draft.rrule), Editing.REPEATS.map { it.second }) { index ->
                        draft = draft.copy(rrule = Editing.REPEATS[index].first, exdates = emptyList())
                    }
                }
                // Absence with a deputy (card 471febc2): Urlaub, Krank … – all-day, quick lengths. Only in
                // LiCal's own calendars: Google and other phone calendars have no field for it (it would be lost).
                if (!draft.calendar.startsWith(DEVICE) && draft.calendar != Holidays.CALENDAR) Spacer(Modifier.height(18.dp))
                if (!draft.calendar.startsWith(DEVICE) && draft.calendar != Holidays.CALENDAR) Group(groupBackground, lock) {
                    val labels = listOf("Keine") + Editing.ABSENCES.map { it.label }
                    MenuRow("Abwesenheit", if (draft.absence == null) "Keine" else Editing.absenceLabel(draft.absence), labels, enabled = !readOnly) { index ->
                        draft = Editing.setAbsence(draft, if (index == 0) null else Editing.ABSENCES[index - 1].key)
                    }
                    if (draft.absence != null) {
                        Divider(colors)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText("Vertretung", style = style(17f, 400, colors.label), modifier = Modifier.width(110.dp))
                            BasicTextField(draft.deputy ?: "", { draft = Editing.setDeputy(draft, it).copy(deputy = it.ifEmpty { null }) },
                                textStyle = style(17f, 400, colors.label), singleLine = true, cursorBrush = SolidColor(colors.red),
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Vertretung" },
                                decorationBox = { inner -> if ((draft.deputy ?: "").isEmpty()) BasicText("Name", style = style(17f, 400, colors.tertiary)); inner() })
                        }
                        Divider(colors)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (weeks in 1..4) {
                                BasicText("$weeks Wo.", style = style(15f, 500, colors.red).copy(textAlign = TextAlign.Center), maxLines = 1,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.red.copy(alpha = 0.12f))
                                        .clickable { draft = Editing.absenceWeeks(draft, weeks) }
                                        .semantics { contentDescription = if (weeks == 1) "1 Woche" else "$weeks Wochen" }.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
                if (series?.rrule != null && !request.isNew) BasicText("Änderungen gelten für alle Termine dieser Serie.",
                    style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                Spacer(Modifier.height(18.dp))
                Group(groupBackground, lock) {
                    val calendar = store.calendar(draft.calendar)
                    val choices = store.writableCalendars()
                    MenuRow("Kalender", calendar?.name ?: "", choices.map { it.name }, dot = colors.system(calendar?.color ?: "blue"),
                        dots = choices.map { colors.system(it.color) }, enabled = !readOnly) { index -> draft = draft.copy(calendar = choices[index].id) }
                }
                // Alerts (card 91adf47e) like the iPhone: "Hinweis", then "Zweiter Hinweis" once the first is set.
                // A phone calendar's all-day event gets no "am Tag (9:00)" (Google stores no alert after the start).
                Spacer(Modifier.height(18.dp))
                Group(groupBackground, lock) {
                    val options = Alerts.choices(draft.allDay).filter { !draft.calendar.startsWith(DEVICE) || it.first >= 0 }
                    val labels = listOf("Keiner") + options.map { it.second }
                    fun choose(slot: Int, index: Int) {
                        val chosen = if (index == 0) null else options[index - 1].first
                        val current = draft.alerts.map<Int, Int?> { it }.toMutableList().apply { while (size < 2) add(null) }
                        current[slot] = chosen
                        if (slot == 0 && chosen == null) current[1] = null
                        draft = Alerts.setAlerts(draft, current)
                        if (chosen != null && !draft.calendar.startsWith(DEVICE) && !Reminders.mayNotify(context) && android.os.Build.VERSION.SDK_INT >= 33)
                            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    MenuRow("Hinweis", Alerts.label(draft.alerts.getOrNull(0), draft.allDay), labels, enabled = !readOnly) { choose(0, it) }
                    if (draft.alerts.isNotEmpty()) {
                        Divider(colors)
                        MenuRow("Zweiter Hinweis", Alerts.label(draft.alerts.getOrNull(1), draft.allDay), labels, enabled = !readOnly) { choose(1, it) }
                    }
                }
                if (alertHint != null) BasicText(alertHint!!, style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                Spacer(Modifier.height(18.dp))
                Group(groupBackground, lock) {
                    BasicTextField(draft.notes, { draft = draft.copy(notes = it) }, textStyle = style(17f, 400, colors.label),
                        cursorBrush = SolidColor(colors.red), modifier = Modifier.fillMaxWidth().height(110.dp).padding(14.dp),
                        decorationBox = { inner -> if (draft.notes.isEmpty()) BasicText("Notizen", style = style(17f, 400, colors.tertiary)); inner() })
                }
                if (readOnly) BasicText("Dieser Kalender kann nur gelesen werden.", style = style(13f, 400, colors.secondary),
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                if (!request.isNew) {
                    Spacer(Modifier.height(18.dp))
                    Group(groupBackground) {
                        BasicText("Als QR-Code teilen", style = style(17f, 400, colors.red), modifier = Modifier.fillMaxWidth().clickable { sharing = true }.padding(14.dp))
                    }
                }
                if (!request.isNew && !readOnly) {
                    Spacer(Modifier.height(18.dp))
                    Group(groupBackground, lock) {
                        BasicText("Termin löschen", style = style(17f, 500, colors.red).copy(textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        if (sharing) QrShareSheet(series ?: draft) { sharing = false }
        if (scanning) QrScanner { text ->
            scanning = false
            val found = text?.let { Ics.fromIcs(it) }
            if (found != null) {
                draft = draft.copy(title = found.title, allDay = found.allDay, start = found.start, end = found.end, location = found.location,
                    notes = found.notes, rrule = found.rrule, exdates = emptyList(), tz = found.tz)
                scanHint = "Termin übernommen – prüfen und mit ✓ sichern."
            } else if (text != null) {
                scanHint = "In diesem QR-Code steht kein Termin."
            }
        }
        if (confirmDiscard) ActionSheet(listOf("Änderungen verwerfen" to true), onCancel = { confirmDiscard = false }) { onClose(null) }
        if (confirmDelete) {
            val repeating = series?.rrule != null && request.occurrenceStart != null
            val choices = if (repeating) listOf("Nur diesen Termin löschen" to true, "Alle Termine löschen" to true) else listOf("Termin löschen" to true)
            ActionSheet(choices, onCancel = { confirmDelete = false }) { index ->
                if (repeating && index == 0) store.put(Editing.skipOccurrence(series!!, request.occurrenceStart!!)) else store.delete(request.event.id)
                onClose(null)
            }
        }
    }
}

private fun dateText(day: LocalDate) = "${day.dayOfMonth}. ${MONTHS[day.monthValue - 1].take(3)}${if (MONTHS[day.monthValue - 1].length > 3) "." else ""} ${day.year}"

@Composable
private fun Group(background: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).then(modifier), content = content)
}

@Composable
private fun Divider(colors: Palette) = Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))

@Composable
private fun CircleButton(label: String, background: Color, foreground: Color, check: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(background).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension / 20f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.4f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            if (check) drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(3f * s, 10.5f * s); lineTo(8f * s, 15.5f * s); lineTo(17f * s, 4.5f * s) }, foreground, style = stroke)
            else {
                drawLine(foreground, androidx.compose.ui.geometry.Offset(4f * s, 4f * s), androidx.compose.ui.geometry.Offset(16f * s, 16f * s), 2.4f * s, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(16f * s, 4f * s), androidx.compose.ui.geometry.Offset(4f * s, 16f * s), 2.4f * s, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, date: String, time: String?, dateOpen: Boolean, timeOpen: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    val colors = palette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
        Pill(date, dateOpen, "$label Datum", onDate)
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            Pill(time, timeOpen, "$label Uhrzeit", onTime)
        }
    }
}

@Composable
private fun Pill(text: String, open: Boolean, description: String, onClick: () -> Unit) {
    val colors = palette()
    val fill = if (colors.dark) Color(0xFF2C2C2E) else Color(0xFFEFEFF0)
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(fill).clickable(onClick = onClick).semantics { contentDescription = "$description $text" }
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        BasicText(text, style = style(17f, 400, if (open) colors.red else colors.label, tabular = true))
    }
}

/** An iOS switch: a green track with a white knob. */
@Composable
fun IosSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = palette()
    val track = if (on) colors.system("green") else if (colors.dark) Color(0xFF39393D) else Color(0xFFE9E9EA)
    Box(Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(track).clickable { onChange(!on) }.padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White).border(0.5.dp, Color(0x14000000), CircleShape))
    }
}

/** A row that opens a short list to choose from (iOS: a pop-up menu with its stacked up-down chevron). */
@Composable
private fun MenuRow(label: String, value: String, choices: List<String>, dot: Color? = null, dots: List<Color>? = null, enabled: Boolean = true, onChoose: (Int) -> Unit) {
    val colors = palette()
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { open = !open }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // The label stays whole; a long value (an e-mail address as calendar name) is shortened with "…".
            BasicText(label, style = style(17f, 400, colors.label), maxLines = 1, softWrap = false)
            // The value takes the rest, flush right; only a value too long for it ends in "…".
            Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) Box(Modifier.padding(end = 8.dp).size(12.dp).clip(CircleShape).background(dot))
                BasicText(value, style = style(17f, 400, colors.secondary), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
            }
            UpDownChevron(colors.secondary)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 14.dp)) {
                choices.forEachIndexed { index, choice ->
                    Row(Modifier.fillMaxWidth().clickable { onChoose(index); open = false }.padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BasicText(if (choice == value) "✓" else "", style = style(15f, 700, colors.red), modifier = Modifier.width(24.dp))
                        dots?.getOrNull(index)?.let { Box(Modifier.padding(end = 8.dp).size(12.dp).clip(CircleShape).background(it)) }
                        BasicText(choice, style = style(17f, 400, colors.label))
                    }
                }
            }
        }
    }
}

/** The calendar that opens under a date (iOS graphical date picker): month with ‹ ›, the chosen day
 *  in red on a light red circle, today in red. */
@Composable
private fun InlineCalendar(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val colors = palette()
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${MONTHS[month.monthValue - 1]} ${month.year}", style = style(17f, 600, colors.label), modifier = Modifier.weight(1f))
            BasicText("‹", style = style(26f, 500, colors.red), modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }.padding(horizontal = 14.dp))
            BasicText("›", style = style(26f, 500, colors.red), modifier = Modifier.clip(CircleShape).clickable { month = month.plusMonths(1) }.padding(horizontal = 14.dp))
        }
        WeekdayLetters()
        for (week in Rules.monthWeeks(month.year, month.monthValue)) {
            Row(Modifier.fillMaxWidth()) {
                for (day in week) {
                    Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                        if (YearMonth.from(day) != month) return@Box
                        val chosen = day == selected
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (chosen) colors.red.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onPick(day) }, contentAlignment = Alignment.Center) {
                            BasicText(day.dayOfMonth.toString(), style = style(19f, if (chosen) 700 else 400,
                                if (chosen || day == today) colors.red else colors.label, tabular = true))
                        }
                    }
                }
            }
        }
    }
}

/** A time wheel like iOS: hours and minutes (in 5-minute steps) roll under a gray band. */
@Composable
private fun TimeWheel(hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    val colors = palette()
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
            .background(if (colors.dark) Color(0xFF2C2C2E) else Color(0xFFEFEFF0)))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Wheel((0..23).toList(), hour) { onPick(it, minute) }
            BasicText(":", style = style(21f, 500, colors.label))
            Wheel((0..55 step 5).toList(), minute - minute % 5) { onPick(hour, it) }
        }
    }
}

@Composable
private fun Wheel(values: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val colors = palette()
    val itemHeight = 36.dp
    val start = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val index = (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 0) 1 else 0).coerceIn(0, values.lastIndex)
                if (values[index] != selected) onPick(values[index])
            }
        }
    }
    LazyColumn(state = state, modifier = Modifier.width(72.dp).height(itemHeight * 5), flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = itemHeight * 2), horizontalAlignment = Alignment.CenterHorizontally) {
        items(values.size) { index ->
            Box(Modifier.height(itemHeight).fillMaxWidth().clickable { onPick(values[index]) }, contentAlignment = Alignment.Center) {
                BasicText("%02d".format(values[index]), style = style(21f, if (values[index] == selected) 500 else 400,
                    if (values[index] == selected) colors.label else colors.secondary, tabular = true))
            }
        }
    }
}

/** An iOS action sheet: the choices in a card, "Abbrechen" below. */
@Composable
fun ActionSheet(choices: List<Pair<String, Boolean>>, onCancel: () -> Unit, onChoose: (Int) -> Unit) {
    val colors = palette()
    val card = if (colors.dark) Color(0xFF2C2C2E) else Color.White
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = onCancel), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(card)) {
                choices.forEachIndexed { index, (label, destructive) ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                    BasicText(label, style = style(19f, 400, if (destructive) colors.red else colors.system("blue")).copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(index) }.padding(vertical = 17.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText("Abbrechen", style = style(19f, 600, colors.system("blue")).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(card).clickable(onClick = onCancel).padding(vertical = 17.dp))
        }
    }
}
