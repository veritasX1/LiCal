package io.github.veritasx1.lical

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.veritasx1.lical.i18n.tr

/** Web addresses, mail and phone links and the way back into LiMail/LiNotes – tappable in the notes (card 45400a07). */
private val LINK = Regex("""(?:https?://|www\.|(?:mailto|tel):|(?:limail|lical|linotes)://)[^\s<>"']+[^\s<>"'.,;:!?)\]]""")

/** The notes with their links: blue, underlined, a tap opens them (the system hands limail:// to LiMail). */
fun linked(text: String, color: Color) = buildAnnotatedString {
    var at = 0
    for (m in LINK.findAll(text)) {
        append(text.substring(at, m.range.first))
        val url = m.value.let { if (it.startsWith("www.")) "https://$it" else it }
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)))) { append(m.value) }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/**
 * A tapped event like on the iPhone (card 45400a07): first what it is – title, place, day and time, calendar, notes with
 * tappable links –, „Bearbeiten“ top right opens the editor. Before, a tap went straight into editing and the notes were cut.
 */
@Composable
fun EventDetails(store: Store, item: Occurrence, onEdit: () -> Unit, onClose: () -> Unit) {
    val colors = palette()
    val card = if (colors.dark) Color(0xFF2C2C2E) else Color.White
    val start = Rules.parse(item.start)
    val end = Rules.parse(item.end)
    val calendar = store.calendar(item.event.calendar)
    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(if (colors.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)).padding(vertical = 14.dp)
            .verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Fertig"), style = style(17f, 400, colors.red), modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
                Box(Modifier.weight(1f))
                if (!store.isReadOnly(item.event.calendar)) BasicText(tr("Bearbeiten"), style = style(17f, 600, colors.red),
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onEdit).padding(10.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(card)
                .padding(horizontal = 16.dp, vertical = 14.dp)) {
                BasicText(item.title.ifBlank { tr("Neuer Termin") }, style = style(22f, 700, colors.label))
                if (item.event.location.isNotBlank()) BasicText(item.event.location, style = style(15f, 400, colors.secondary),
                    modifier = Modifier.padding(top = 2.dp))
                BasicText(Dates.text(start.date), style = style(15f, 400, colors.label), modifier = Modifier.padding(top = 10.dp))
                val time = when {
                    item.allDay -> tr("ganztägig")
                    start.date == end.date -> tr("von {from} bis {to}", "from" to start.at.toLocalTime().toString(), "to" to end.at.toLocalTime().toString())
                    else -> tr("bis {day}, {to}", "day" to Dates.text(end.date, weekday = false), "to" to end.at.toLocalTime().toString())
                }
                BasicText(time, style = style(15f, 400, colors.secondary))
            }
            if (calendar != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(card)
                .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Kalender"), style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                Box(Modifier.size(12.dp).clip(CircleShape).background(colors.system(calendar.color)))
                BasicText(calendar.name, style = style(17f, 400, colors.secondary), modifier = Modifier.padding(start = 8.dp))
            }
            if (item.event.notes.isNotBlank()) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(card).padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicText(tr("Notizen"), style = style(13f, 400, colors.secondary))
                BasicText(linked(item.event.notes, colors.system("blue")), style = style(17f, 400, colors.label), modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.height(8.dp))
        }
    }
}
