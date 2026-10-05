package io.github.veritasx1.lical

import android.app.Application
import io.github.veritasx1.lical.i18n.I18n

/** Sets the language before any screen, widget or alarm builds its texts. */
class LiCalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
    }
}
