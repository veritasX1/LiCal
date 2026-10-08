package io.github.veritasx1.lical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The glass capsules of iOS 26: a light, slightly see-through surface with a hairline and a soft shadow. */
@Composable
fun GlassCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = palette()
    Row(
        modifier.shadow(10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(CircleShape).background(colors.glass).border(0.5.dp, colors.glassBorder, CircleShape).height(48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content,
    )
}

/** A round icon button inside (or as) a glass capsule; 48 dp – Apple's minimum of 44 pt. */
@Composable
fun GlassIcon(glyph: Glyph, label: String, onClick: () -> Unit) {
    val colors = palette()
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, colors.label, 22.dp)
    }
}

@Composable
fun GlassText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: Glyph? = null) {
    val colors = palette()
    GlassCapsule(modifier) {
        Row(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 18.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                GlyphIcon(leading, colors.label, 18.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            }
            BasicText(text, style = style(17f, 500, colors.label))
        }
    }
}

enum class Glyph { Back, Chevron, Search, Plus, List, Calendars, Inbox, Compact, Stacked }

/** LiCal's own symbols, drawn like SF Symbols (regular weight, round ends). */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension / 24f
    val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(vararg points: Pair<Float, Float>) = Path().apply {
        moveTo(points[0].first * s, points[0].second * s)
        points.drop(1).forEach { lineTo(it.first * s, it.second * s) }
    }
    when (glyph) {
        Glyph.Back -> drawPath(path(15f to 4.5f, 7.5f to 12f, 15f to 19.5f), color, style = Stroke(2.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // "›" at the end of a row that opens a page, as in the iPhone's lists (LiMail's Glyph.Chevron)
        Glyph.Chevron -> drawPath(path(9f to 5f, 16f to 12f, 9f to 19f), color, style = Stroke(2.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        Glyph.Search -> {
            drawCircle(color, 7f * s, Offset(10.5f * s, 10.5f * s), style = stroke)
            drawPath(path(15.6f to 15.6f, 21f to 21f), color, style = Stroke(2.4f * s, cap = StrokeCap.Round))
        }
        Glyph.Plus -> {
            drawPath(path(12f to 4f, 12f to 20f), color, style = stroke)
            drawPath(path(4f to 12f, 20f to 12f), color, style = stroke)
        }
        Glyph.List -> {  // month with a list below (iOS: list.bullet.below.rectangle)
            drawRoundRect(color, Offset(3.5f * s, 4f * s), Size(17f * s, 8f * s), androidx.compose.ui.geometry.CornerRadius(2f * s), style = stroke)
            drawPath(path(4f to 16f, 20f to 16f), color, style = stroke)
            drawPath(path(4f to 20f, 20f to 20f), color, style = stroke)
        }
        Glyph.Compact -> {  // a capsule with stripes: show the month compact
            drawRoundRect(color, Offset(2.5f * s, 8f * s), Size(19f * s, 8f * s), androidx.compose.ui.geometry.CornerRadius(4f * s), style = stroke)
            for (x in listOf(8.5f, 12f, 15.5f)) drawPath(path(x to 8.5f, x to 15.5f), color, style = Stroke(1.6f * s))
        }
        Glyph.Stacked -> {  // stacked rows: show the events as pills
            for (y in listOf(5f, 10.5f, 16f)) drawRoundRect(color, Offset(3f * s, y * s), Size(18f * s, 3.4f * s), androidx.compose.ui.geometry.CornerRadius(1.7f * s))
        }
        Glyph.Calendars -> {
            drawRoundRect(color, Offset(3.5f * s, 5f * s), Size(17f * s, 15f * s), androidx.compose.ui.geometry.CornerRadius(3f * s), style = stroke)
            drawPath(path(3.5f to 9.5f, 20.5f to 9.5f), color, style = stroke)
            for (x in listOf(8f, 12f, 16f)) for (y in listOf(13.5f, 17f)) drawCircle(color, 1.1f * s, Offset(x * s, y * s))
        }
        Glyph.Inbox -> {
            drawPath(path(3.5f to 13f, 6f to 5f, 18f to 5f, 20.5f to 13f, 20.5f to 19f, 3.5f to 19f, 3.5f to 13f), color, style = stroke)
            drawPath(path(3.5f to 13f, 8.5f to 13f, 9.5f to 15.5f, 14.5f to 15.5f, 15.5f to 13f, 20.5f to 13f), color, style = stroke)
        }
    }
}

/** Diagonal stripes over an absence (as on Ubuntu), so it reads as "away" at a glance. */
fun DrawScope.stripes(color: Color) {
    val step = 7.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(color.copy(alpha = 0.3f), Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
        x += step
    }
}

/** An event as a small tinted pill (month view): calendar color, title in a deep shade of it. */
@Composable
fun EventPill(title: String, color: Color, modifier: Modifier = Modifier, height: Dp = 18.dp, fontSize: Float = 11f, striped: Boolean = false) {
    val colors = palette()
    Box(modifier.height(height).clip(RoundedCornerShape(5.dp)).background(colors.eventFill(color))
        .then(if (striped) Modifier.drawBehind { stripes(color) } else Modifier).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart) {
        BasicText(title, style = style(fontSize, 600, colors.eventText(color)), maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
    }
}


/** iOS's pop-up button symbol "chevron.up.chevron.down": two small chevrons stacked, in the value's color. */
@Composable
fun UpDownChevron(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.padding(start = 6.dp).size(width = 9.dp, height = 15.dp)) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        val style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val up = androidx.compose.ui.graphics.Path().apply { moveTo(stroke, h * 0.40f); lineTo(w / 2, h * 0.12f); lineTo(w - stroke, h * 0.40f) }
        val down = androidx.compose.ui.graphics.Path().apply { moveTo(stroke, h * 0.60f); lineTo(w / 2, h * 0.88f); lineTo(w - stroke, h * 0.60f) }
        drawPath(up, color, style = style)
        drawPath(down, color, style = style)
    }
}
