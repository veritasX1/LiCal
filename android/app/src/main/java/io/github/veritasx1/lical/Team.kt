package io.github.veritasx1.lical

import org.json.JSONArray
import org.json.JSONObject

/** The team sync's core (card ac0d1e1a, docs/TEAMSYNC.md §4) – local only, no network here. Twin of
 *  linux/lical/team.py (shared/cases/teamsync.json): a member's stream of signed, encrypted changes,
 *  merged per event by (Lamport clock, device) – any way, any order, any repetition gives the same.
 *  Levels: "busy" (default: only "Belegt" and absences) or "details" (title, place); never notes or alerts. */
object Team {
    val LEVELS = listOf("busy", "details")

    /** The event as the team sees it at this level, as JSON (the format of docs/DATA.md). */
    fun projection(event: Event, level: String = "busy"): JSONObject {
        val view = JSONObject().put("id", event.id).put("allDay", event.allDay).put("start", event.start).put("end", event.end)
        event.rrule?.let { view.put("rrule", it) }
        if (event.exdates.isNotEmpty()) view.put("exdates", JSONArray(event.exdates))
        event.tz?.let { view.put("tz", it) }
        when {
            event.absence != null -> {
                view.put("absence", event.absence).put("title", Editing.absenceLabel(event.absence))
                event.deputy?.let { view.put("deputy", it) }
            }
            level == "details" -> {
                view.put("title", event.title)
                if (event.location.isNotEmpty()) view.put("location", event.location)
            }
            else -> view.put("title", "Belegt")
        }
        return view
    }

    fun signedBytes(stream: String, op: JSONObject): ByteArray {
        val box = op.getJSONObject("box")
        return "lical v1 op\n$stream\n${op.getString("device")}\n${op.getInt("seq")}\n${op.getLong("clock")}\n${box.getString("n")}\n${box.getString("c")}".toByteArray()
    }

    fun aad(stream: String, device: String, seq: Int) = "lical v1 $stream/$device#$seq"

    /** One member's stream as a device holds it. */
    class Stream(val id: String, val owner: String, private val key: ByteArray) {
        private val ops = sortedMapOf<Pair<String, Int>, JSONObject>(compareBy({ it.first }, { it.second }))
        private val versions = mutableMapOf<String, Pair<Long, String>>()
        private val events = mutableMapOf<String, JSONObject?>()
        var clock = 0L
            private set

        fun write(identity: Crypto.Identity, device: String, change: JSONObject, nonce: ByteArray = Crypto.randomBytes(12)): JSONObject {
            val seq = (ops.keys.filter { it.first == device }.maxOfOrNull { it.second } ?: 0) + 1
            clock += 1
            val op = JSONObject().put("device", device).put("seq", seq).put("clock", clock)
                .put("box", Crypto.sealText(key, change.toString(), aad(id, device, seq), nonce))
            op.put("sig", identity.sign(signedBytes(id, op)))
            if (!receive(op)) throw Crypto.CryptoError("only the owner writes in a stream")
            return op
        }

        fun put(identity: Crypto.Identity, device: String, event: Event, level: String = "busy") =
            write(identity, device, JSONObject().put("kind", "put").put("event", projection(event, level)))

        fun delete(identity: Crypto.Identity, device: String, eventId: String) =
            write(identity, device, JSONObject().put("kind", "delete").put("id", eventId))

        /** Take an entry: false if known, forged or damaged (then nothing changes). */
        fun receive(op: JSONObject): Boolean {
            val place = try { op.getString("device") to op.getInt("seq") } catch (error: Exception) { return false }
            val opClock = op.optLong("clock", -1)
            if (opClock < 0 || place in ops) return false
            if (!Crypto.verify(owner, signedBytes(id, op), op.optString("sig"))) return false
            val change = try { JSONObject(Crypto.openText(key, op.getJSONObject("box"), aad(id, place.first, place.second))) } catch (error: Exception) { return false }
            val kind = change.optString("kind")
            val eventId = (if (kind == "put") change.optJSONObject("event")?.optString("id") else change.optString("id"))?.ifEmpty { null } ?: return false
            ops[place] = op
            clock = maxOf(clock, opClock)
            val version = opClock to place.first
            val known = versions[eventId]
            if (known == null || compareValuesBy(version, known, { it.first }, { it.second }) > 0) {
                versions[eventId] = version
                events[eventId] = if (kind == "put") change.getJSONObject("event") else null
            }
            return true
        }

        fun heads(): Map<String, Int> = ops.keys.groupBy { it.first }.mapValues { (_, places) -> places.maxOf { it.second } }

        fun since(heads: Map<String, Int>): List<JSONObject> = ops.filterKeys { it.second > (heads[it.first] ?: 0) }.values.toList()

        fun state(): List<JSONObject> = events.values.filterNotNull().sortedWith(compareBy({ it.getString("start") }, { it.getString("id") }))
    }
}
