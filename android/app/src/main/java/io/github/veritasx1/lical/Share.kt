package io.github.veritasx1.lical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** What goes into the code (card b483dadf): title, time, place, repetition – the notes only on
 *  request; never the calendar, the absence, the deputy or the id (privacy first). */
fun sharedEvent(series: Event, withNotes: Boolean) = Event(id = "", calendar = "", title = series.title, allDay = series.allDay,
    start = series.start, end = series.end, location = series.location, notes = if (withNotes) series.notes else "", rrule = series.rrule, tz = series.tz)

/** The code's modules, or null when the text is too long for one QR code. */
fun qrMatrix(text: String): BitMatrix? = try {
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
} catch (error: WriterException) {
    null
} catch (error: IllegalArgumentException) {
    null
}

/** An iOS-like sheet with the event's QR code: the title, the code on white, one switch for the notes. */
@Composable
fun QrShareSheet(series: Event, onClose: () -> Unit) {
    val colors = palette()
    var withNotes by remember { mutableStateOf(false) }
    val text = Ics.toIcs(sharedEvent(series, withNotes))
    val matrix = remember(text) { qrMatrix(text) }
    val card = if (colors.dark) Color(0xFF1C1C1E) else Color.White
    val sheet = if (colors.dark) Color(0xFF000000) else Color(0xFFF2F2F7)
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(sheet).clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                BasicText("Als QR-Code teilen", style = style(17f, 600, colors.label).copy(textAlign = TextAlign.Center), modifier = Modifier.align(Alignment.Center))
                BasicText("Fertig", style = style(17f, 600, colors.red), modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onClose).padding(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            BasicText(series.title, style = style(22f, 700, colors.label).copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(14.dp))
            if (matrix != null) {
                Canvas(Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).semantics { contentDescription = "QR-Code des Termins" }) {
                    val module = size.width / matrix.width
                    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                        if (matrix[x, y]) drawRect(Color.Black, Offset(x * module, y * module), Size(module + 0.5f, module + 0.5f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(if (matrix != null) "Mit der Kamera eines anderen Telefons scannen – nur dieser Termin ist im Code."
                else "Zu viel Text für einen QR-Code. Ohne Notizen teilen.",
                style = style(13f, 400, colors.secondary).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicText("Notizen mitgeben", style = style(17f, 400, if (series.notes.isEmpty()) colors.tertiary else colors.label), modifier = Modifier.weight(1f))
                IosSwitch(withNotes) { if (series.notes.isNotEmpty()) withNotes = it }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
