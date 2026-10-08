package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.provider.CalendarContract
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The home screen widgets of the iPhone's Calendar (card b1a0984b): "Als Nächstes" (small and wide),
 *  "Monat" and "Liste". Each is one picture drawn at the widget's real size (Inter, Apple's colors,
 *  light/dark with the phone); a tap opens LiCal on today. Kept current: after LiCal saves, when the
 *  phone's calendars change (DAVx⁵ – also with LiCal closed, via a content-triggered job), when the
 *  next event is over and at midnight. */
object WidgetArt {
    enum class Kind { UpNext, Month, List }

    private class Ink(val context: Context, val density: Float, val dark: Boolean) {
        val palette = Palette(dark)
        val inter: Typeface? = runCatching { ResourcesCompat.getFont(context, R.font.inter) }.getOrNull()
        val background = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val label = palette.label.toArgb()
        val secondary = palette.secondary.toArgb()
        val red = palette.red.toArgb()
        fun dp(value: Float) = value * density

        fun paint(size: Float, weight: Int, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = inter
            // this@Ink: TextPaint has a "density" of its own (1.0) – the screen's is meant.
            textSize = size * this@Ink.density * context.resources.configuration.fontScale.coerceIn(0.85f, 1.15f)
            this.color = color
            fontVariationSettings = "'wght' $weight"
        }

        /** Text with its top at `top`, shortened with "…" to `width`; returns the line height. */
        fun text(canvas: Canvas, value: String, x: Float, top: Float, size: Float, weight: Int, color: Int, width: Float,
                 align: Paint.Align = Paint.Align.LEFT): Float {
            val paint = paint(size, weight, color).apply { textAlign = align }
            val shown = TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, x, top - paint.ascent(), paint)
            return paint.descent() - paint.ascent()
        }
    }

    private val STAMP = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    /** What is still to come: today's events not yet over (all-day ones included), then the next days. */
    fun upcoming(store: Store, now: LocalDateTime, days: Long = 7): List<Occurrence> =
        store.occurrences(now.toLocalDate(), now.toLocalDate().plusDays(days)).filter { item ->
            val (first, last) = Rules.daysCovered(item)
            if (item.allDay || first != last) last >= now.toLocalDate() else Rules.parse(item.end).at > now
        }

    private fun dayHeading(day: LocalDate, today: LocalDate) = when (day) {
        today -> tr("Heute").uppercase()
        today.plusDays(1) -> tr("Morgen").uppercase()
        else -> Dates.text(day, year = false, short = true).uppercase()
    }

    private fun shownDay(item: Occurrence, today: LocalDate) = maxOf(Rules.daysCovered(item).first, today)

    /** One event like the iPhone widget: a colored bar, the title, the time below. Returns its height. */
    private fun event(ink: Ink, canvas: Canvas, store: Store, item: Occurrence, x: Float, top: Float, width: Float, compact: Boolean): Float {
        val color = ink.palette.system(store.calendar(item.event.calendar)?.color ?: "blue").toArgb()
        val titleHeight = ink.paint(13f, 600, ink.label).let { it.descent() - it.ascent() }
        val timeHeight = if (compact) 0f else ink.paint(12f, 400, ink.secondary).let { it.descent() - it.ascent() }
        val height = titleHeight + timeHeight
        canvas.drawRoundRect(RectF(x, top + ink.dp(1f), x + ink.dp(3.5f), top + height - ink.dp(1f)), ink.dp(2f), ink.dp(2f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val textX = x + ink.dp(9f)
        val textWidth = width - ink.dp(9f)
        val (first, last) = Rules.daysCovered(item)
        val time = if (item.allDay || first != last) tr("ganztägig")
            else "${Rules.parse(item.start).at.format(STAMP)}–${Rules.parse(item.end).at.format(STAMP)}"
        if (compact) {
            ink.text(canvas, item.title, textX, top, 13f, 600, ink.label, textWidth * 0.68f)
            ink.text(canvas, time, x + width, top + ink.dp(1f), 12f, 400, ink.secondary, textWidth * 0.32f, Paint.Align.RIGHT)
        } else {
            ink.text(canvas, item.title, textX, top, 13f, 600, ink.label, textWidth)
            ink.text(canvas, time, textX, top + titleHeight, 12f, 400, ink.secondary, textWidth)
        }
        return height
    }

    /** A column of events with day headings ("MORGEN") when the day changes; returns how many fitted. */
    private fun events(ink: Ink, canvas: Canvas, store: Store, items: List<Occurrence>, today: LocalDate, x: Float, top: Float,
                       width: Float, bottom: Float, headings: Boolean, startDay: LocalDate?, compact: Boolean = false): Int {
        var y = top
        var day = startDay
        var shown = 0
        for (item in items) {
            val itemDay = shownDay(item, today)
            val needsHeading = headings && itemDay != day
            val headingHeight = if (needsHeading) ink.dp(20f) else 0f
            val rowHeight = (if (compact) ink.dp(18f) else ink.dp(34f)) + ink.dp(8f)
            if (y + headingHeight + rowHeight - ink.dp(8f) > bottom) break
            if (needsHeading) {
                ink.text(canvas, dayHeading(itemDay, today), x, y + ink.dp(4f), 11f, 600, ink.secondary, width)
                y += headingHeight
                day = itemDay
            }
            y += event(ink, canvas, store, item, x, y, width, compact) + ink.dp(8f)
            shown++
        }
        return shown
    }

    fun draw(context: Context, kind: Kind, store: Store, now: LocalDateTime, widthDp: Float, heightDp: Float, dark: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val ink = Ink(context, density, dark)
        val width = (widthDp * density).toInt().coerceAtLeast(1)
        val height = (heightDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The system's widget corners (Android 12+), at most Apple's ~22 dp.
        val radius = minOf(ink.dp(22f), if (android.os.Build.VERSION.SDK_INT >= 31)
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) else ink.dp(22f))
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.background })
        val pad = ink.dp(16f)
        val today = now.toLocalDate()
        when (kind) {
            Kind.UpNext -> upNext(ink, canvas, store, now, width.toFloat(), height.toFloat(), pad)
            Kind.Month -> month(ink, canvas, store, today, width.toFloat(), height.toFloat(), pad)
            Kind.List -> list(ink, canvas, store, now, width.toFloat(), height.toFloat(), pad)
        }
        return bitmap
    }

    /** Red weekday, the day large – the head of "Als Nächstes" (iPhone: "SONNTAG" / "4"). Returns its bottom. */
    private fun dateHead(ink: Ink, canvas: Canvas, today: LocalDate, x: Float, top: Float, width: Float): Float {
        val weekday = ink.text(canvas, WEEKDAYS_LONG[today.dayOfWeek.value - 1].uppercase(), x, top, 11f, 700, ink.red, width)
        val number = ink.text(canvas, today.dayOfMonth.toString(), x - ink.dp(1f), top + weekday - ink.dp(2f), 32f, 400, ink.label, width)
        return top + weekday + number
    }

    private fun upNext(ink: Ink, canvas: Canvas, store: Store, now: LocalDateTime, width: Float, height: Float, pad: Float) {
        val today = now.toLocalDate()
        val items = upcoming(store, now)
        val wide = width > height * 1.6f
        val columnWidth = if (wide) width * 0.42f - pad else width - 2 * pad
        val headBottom = dateHead(ink, canvas, today, pad, pad, columnWidth)
        val todays = items.filter { shownDay(it, today) == today }
        val firstColumn = if (wide) todays else items
        val shown = events(ink, canvas, store, firstColumn, today, pad, headBottom + ink.dp(8f), columnWidth, height - pad,
            headings = !wide, startDay = today)
        if (firstColumn.isEmpty()) ink.text(canvas, if (items.isEmpty()) tr("Keine Termine") else tr("Keine weiteren Termine heute"),
            pad, headBottom + ink.dp(8f), 12f, 400, ink.secondary, columnWidth)
        if (wide) {
            val rest = items.drop(shown)
            val x = width * 0.46f
            events(ink, canvas, store, rest, today, x, pad, width - x - pad, height - pad, headings = true, startDay = null)
        }
    }

    private fun month(ink: Ink, canvas: Canvas, store: Store, today: LocalDate, width: Float, height: Float, pad: Float) {
        val inner = width - 2 * pad
        val top = pad + ink.text(canvas, MONTHS[today.monthValue - 1].uppercase(), pad + inner / 14 - ink.dp(4f), pad, 11f, 700, ink.red, inner) + ink.dp(6f)
        val weeks = Rules.monthWeeks(today.year, today.monthValue)
        val column = inner / 7
        val row = (height - pad - top) / (weeks.size + 1)
        WEEKDAY_LETTERS.forEachIndexed { index, letter ->
            ink.text(canvas, letter, pad + column * index + column / 2, top, 9f, 600, ink.secondary, column, Paint.Align.CENTER)
        }
        val numberPaint = ink.paint(11f, 500, ink.label)
        val numberHeight = numberPaint.descent() - numberPaint.ascent()
        weeks.forEachIndexed { r, week ->
            week.forEachIndexed { c, day ->
                if (day.monthValue != today.monthValue) return@forEachIndexed
                val cx = pad + column * c + column / 2
                val cy = top + row * (r + 1) + row / 2
                val isToday = day == today
                if (isToday) canvas.drawCircle(cx, cy, minOf(column, row) * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.red })
                ink.text(canvas, day.dayOfMonth.toString(), cx, cy - numberHeight / 2, 11f, if (isToday) 700 else 500,
                    if (isToday) 0xFFFFFFFF.toInt() else ink.label, column, Paint.Align.CENTER)
            }
        }
    }

    private fun list(ink: Ink, canvas: Canvas, store: Store, now: LocalDateTime, width: Float, height: Float, pad: Float) {
        val today = now.toLocalDate()
        val weekday = ink.text(canvas, WEEKDAYS_LONG[today.dayOfWeek.value - 1].uppercase(), pad, pad, 11f, 700, ink.red, width - 2 * pad)
        ink.text(canvas, Dates.dayMonth(today), pad, pad + weekday, 22f, 600, ink.label, width - 2 * pad)
        val top = pad + weekday + ink.dp(36f)
        val items = upcoming(store, now, 14)
        if (items.isEmpty()) ink.text(canvas, tr("Keine Termine in den nächsten zwei Wochen"), pad, top, 13f, 400, ink.secondary, width - 2 * pad)
        events(ink, canvas, store, items, today, pad, top, width - 2 * pad, height - pad, headings = true, startDay = null)
    }

    /** When "Als Nächstes" changes by itself: the end of the next event still running or to come today. */
    fun nextChange(store: Store, now: LocalDateTime): LocalDateTime? = upcoming(store, now, 1)
        .filter { !it.allDay }.map { Rules.parse(it.end).at }.filter { it > now }.minOrNull()
}

/** Shared by the three widget kinds: draw on update and on resize; refresh all after any change. */
abstract class CalendarWidget(private val kind: WidgetArt.Kind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = CalendarWidgets.refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        CalendarWidgets.draw(context, kind, javaClass, intArrayOf(id))

    override fun onEnabled(context: Context) = CalendarWidgets.refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(CalendarWidgets.REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) CalendarWidgets.refresh(context)
    }
}

class UpNextWidget : CalendarWidget(WidgetArt.Kind.UpNext)
class MonthWidget : CalendarWidget(WidgetArt.Kind.Month)
class ListWidget : CalendarWidget(WidgetArt.Kind.List)

object CalendarWidgets {
    const val REFRESH = "io.github.veritasx1.lical.WIDGETS"
    private const val JOB = 4711

    private val KINDS = listOf(WidgetArt.Kind.UpNext to UpNextWidget::class.java, WidgetArt.Kind.Month to MonthWidget::class.java,
        WidgetArt.Kind.List to ListWidget::class.java)

    private fun store(context: Context) = Store(File(context.filesDir, "kalender.json"), DeviceCalendars(context)).apply { refreshDevice() }

    /** Redraw every placed widget (and the date icon), then arrange the next refresh. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        var any = false
        for ((kind, type) in KINDS) {
            val ids = manager.getAppWidgetIds(ComponentName(context, type))
            if (ids.isNotEmpty()) {
                any = true
                draw(context, kind, type, ids)
            }
        }
        DateWidget.refresh(context)
        if (!any) return
        watchCalendars(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        val next = listOfNotNull(WidgetArt.nextChange(store(context), now), now.toLocalDate().plusDays(1).atStartOfDay()).min()
        val intent = PendingIntent.getBroadcast(context, 3, Intent(context, UpNextWidget::class.java).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5000, intent)
    }

    fun draw(context: Context, kind: WidgetArt.Kind, type: Class<*>, ids: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        val store = store(context)
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val now = LocalDateTime.now()
        val open = PendingIntent.getActivity(context, 5, Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.DAY, now.toLocalDate().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            // Portrait: the narrow width and the tall height the launcher reports.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defaultSize(kind).first
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defaultSize(kind).second
            val views = RemoteViews(context.packageName, R.layout.calendar_widget).apply {
                setImageViewBitmap(R.id.widget_picture, WidgetArt.draw(context, kind, store, now, width.toFloat(), height.toFloat(), dark))
                setContentDescription(R.id.widget_picture, describe(kind, store, now))
                setOnClickPendingIntent(R.id.widget_picture, open)
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun defaultSize(kind: WidgetArt.Kind) = when (kind) {
        WidgetArt.Kind.UpNext, WidgetArt.Kind.Month -> 160 to 160
        WidgetArt.Kind.List -> 330 to 330
    }

    /** For TalkBack: what the picture shows. */
    private fun describe(kind: WidgetArt.Kind, store: Store, now: LocalDateTime): String {
        val today = now.toLocalDate()
        val head = Dates.text(today, year = false)
        if (kind == WidgetArt.Kind.Month) return tr("LiCal, {head}", "head" to head)
        val next = WidgetArt.upcoming(store, now).take(3).joinToString("; ") { it.title }
        return tr("LiCal, {head}. {if}", "head" to head, "if" to (if (next.isEmpty()) "Keine Termine" else next))
    }

    /** DAVx⁵ changed the phone's calendars – also while LiCal is closed: Android starts this job then. */
    fun watchCalendars(context: Context) {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB, ComponentName(context, CalendarWatchJob::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(3_000).setTriggerContentMaxDelay(30_000).build()
        scheduler.schedule(job)
    }
}

/** Runs when the phone's calendar store changed: redraw the widgets, then watch again. */
class CalendarWatchJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try { CalendarWidgets.refresh(applicationContext) } finally { jobFinished(params, false) }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}
