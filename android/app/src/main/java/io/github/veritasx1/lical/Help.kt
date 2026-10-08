package io.github.veritasx1.lical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.veritasx1.lical.i18n.tr
import org.json.JSONObject

/**
 * Hilfe and Über LiCal (card d38290c7) – twin of linux/lical/help.py and LiMail's Help.kt: the same texts
 * (shared/hilfe/hilfe.json, copied into the assets), translated through the shared catalogue; entries marked
 * "nur: ubuntu" are left out on the phone. Look after ~/Team/LI-GESTALTUNG.md.
 */
object HelpContent {
    fun sections(json: JSONObject): List<Pair<String, List<Pair<String, String>>>> {
        val a = json.optJSONArray("abschnitte") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val s = a.getJSONObject(i)
            val e = s.optJSONArray("eintraege") ?: return@mapNotNull null
            val entries = (0 until e.length()).map { e.getJSONObject(it) }.filter { it.optString("nur").let { n -> n.isEmpty() || n == "android" } }
                .map { tr(it.getString("titel")) to tr(it.getString("text")) }
            if (entries.isEmpty()) null else tr(s.getString("titel")) to entries
        }
    }
}

@Composable
private fun groupedColors(): Pair<Color, Color> {
    val dark = palette().dark
    return (if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)) to (if (dark) Color(0xFF1C1C1E) else Color.White)
}

@Composable
private fun Page(title: String, back: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val colors = palette()
    val (grouped, _) = groupedColors()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(grouped).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {
            // Back with the previous title, like the iPhone and LiMail's NavBar (LI-GESTALTUNG.md)
            Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClose).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Back, colors.red, 24.dp)
                BasicText(back, style = style(17f, 400, colors.red))
            }
            BasicText(title, style = style(34f, 700, colors.label), modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    val colors = palette()
    val (_, cell) = groupedColors()
    if (title != null) BasicText(title.uppercase(), style = style(13f, 400, colors.secondary),
        modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 6.dp))
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cell)) { content() }
}

@Composable
fun HelpScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val json = remember { runCatching { JSONObject(context.assets.open("hilfe.json").use { it.readBytes().decodeToString() }) }.getOrDefault(JSONObject()) }
    val colors = palette()
    var open by remember { mutableStateOf<String?>(null) }
    Page(tr("Hilfe"), tr("Kalender"), onClose) {
        HelpContent.sections(json).forEach { (title, entries) ->
            Group(title) {
                entries.forEachIndexed { i, (question, answer) ->
                    val key = title + question
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (open == key) null else key }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(question, style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                            BasicText(if (open == key) "−" else "+", style = style(20f, 400, colors.red))
                        }
                        AnimatedVisibility(open == key) {
                            BasicText(answer, style = style(15f, 400, colors.secondary), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (i < entries.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val colors = palette()
    var licenses by remember { mutableStateOf(false) }
    if (licenses) LicensesScreen { licenses = false }
    Page(tr("Über LiCal"), tr("Kalender"), onClose) {
        Group(null) {
            listOf(tr("Version") to version, tr("Entwickler") to "Olaf Winkler", tr("Lizenz") to "GNU GPL 3.0").forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(k, style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                    BasicText(v, style = style(17f, 400, colors.secondary))
                }
                Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
            }
            // Website like the Ubuntu about dialog – opens only when tapped
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://lisoftware.de/lical/"))) }
            }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Website"), style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
                BasicText("lisoftware.de/lical", style = style(17f, 400, colors.red))
            }
        }
        BasicText(tr("Ein Kalender nach dem Vorbild von Apples Kalender – ohne Konto, ohne Werbung, ohne Datensammlung.") + "\n© 2026 Olaf Winkler",
            style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp))
        // Rechtliches like Apple's Einstellungen › Info and LiMail (card 86f796be)
        Group(null) { NavRow(tr("Lizenzen")) { licenses = true } }
    }
}

@Composable
private fun NavRow(title: String, value: String? = null, onClick: () -> Unit) {
    val colors = palette()
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = style(17f, 400, colors.label), modifier = Modifier.weight(1f))
        if (value != null) BasicText(value, style = style(17f, 400, colors.secondary))
        GlyphIcon(Glyph.Chevron, colors.tertiary, 16.dp, Modifier.padding(start = 6.dp))
    }
}

/** Lizenzen (card 86f796be): LiCal's own licence and everything the app ships, each with its full text – from
 *  shared/lizenzen (tools/lizenzen.py), the same file the Ubuntu about dialog reads. */
@Composable
private fun LicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val data = remember { runCatching { JSONObject(context.assets.open("lizenzen.json").use { it.readBytes().decodeToString() }) }.getOrNull() }
    val colors = palette()
    var showing by remember { mutableStateOf<Pair<String, String>?>(null) }
    showing?.let { (name, text) ->
        Page(name, tr("Lizenzen"), { showing = null }) {
            BasicText(text, style = style(13f, 400, colors.label), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    Page(tr("Lizenzen"), tr("Über LiCal"), onClose) {
        if (data == null) {
            BasicText(tr("Die Lizenzliste fehlt."), style = style(15f, 400, colors.secondary), modifier = Modifier.padding(16.dp))
            return@Page
        }
        val texts = data.getJSONObject("texts")
        val app = data.getJSONObject("app")
        Group(null) { NavRow("LiCal – GPL-3.0", app.optString("copyright")) { showing = "LiCal – GPL-3.0" to texts.optString(app.getString("text")) } }
        BasicText(tr("LiCal ist freie Software: Sie dürfen es unter den Bedingungen der GNU General Public License, Version 3 oder später, weitergeben und verändern."),
            style = style(13f, 400, colors.secondary), modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp))
        val libs = data.getJSONArray("android")
        Group(tr("Mitgelieferte Bibliotheken")) {
            for (i in 0 until libs.length()) {
                val lib = libs.getJSONObject(i)
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showing = lib.getString("name") to texts.optString(lib.getString("text")) }
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(lib.getString("name"), style = style(17f, 400, colors.label))
                        BasicText(listOf(lib.getString("version"), lib.getString("license")).filter { it.isNotEmpty() }.joinToString(" · "),   // no „– ·“ without a version (40bc456e)
                            style = style(13f, 400, colors.secondary))
                        BasicText(tr(lib.getString("use")), style = style(13f, 400, colors.secondary))
                    }
                    GlyphIcon(Glyph.Chevron, colors.tertiary, 16.dp, Modifier.padding(start = 6.dp))
                }
                if (i < libs.length() - 1) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
            }
        }
    }
}
