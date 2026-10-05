package io.github.veritasx1.lical

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        const val DAY = "day"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Settings.init(this)
        val store = Store(File(filesDir, "kalender.json"), DeviceCalendars(this))
        // DAVx⁵ (or another app) changed the phone's calendars: draw again. Android allows watching
        // only with the calendar permission – so only once it is granted (at start or later).
        var watching = false
        store.onDeviceAllowed = {
            if (!watching) {
                watching = true
                contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true,
                    object : android.database.ContentObserver(android.os.Handler(mainLooper)) {
                        override fun onChange(selfChange: Boolean) {
                            store.refreshDevice()
                            Thread { CalendarWidgets.refresh(applicationContext) }.start()
                        }
                    })
            }
        }
        store.refreshDevice()
        // Alerts (card 91adf47e): set the alarm now and after every change of LiCal's events.
        store.onSaved = { Thread { Reminders.ring(applicationContext); CalendarWidgets.refresh(applicationContext) }.start() }
        Thread { Reminders.ring(applicationContext) }.start()
        this.store = store
        // Opened from a notification or by another app (LiCal as the phone's calendar app).
        incoming.value = IncomingIntents.read(this, store, intent) ?: Incoming()
        setContent {
            val wanted = incoming.value
            androidx.compose.runtime.key(wanted) { LiCalApp(store, start = wanted.screen, startDay = wanted.day, request = wanted.request) }
        }
    }

    private lateinit var store: Store
    private val incoming = androidx.compose.runtime.mutableStateOf(Incoming())

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        IncomingIntents.read(this, store, intent)?.let { incoming.value = it }
    }
}
