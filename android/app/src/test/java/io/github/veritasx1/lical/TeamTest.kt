package io.github.veritasx1.lical

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Card ac0d1e1a, step 1: Crypto.kt and Team.kt exactly as crypto.py/team.py – every case of
 *  shared/cases/teamsync.json; and entries signed here are written for the Python test to check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TeamTest {
    private val cases = File(System.getProperty("lical.cases"))

    /** JSON with sorted keys, so Python's and Kotlin's objects compare by content. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":${canonical(value.get(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private fun stream(case: JSONObject) = Team.Stream(case.getString("stream"), case.getString("owner"), Crypto.hex(case.getString("key")))

    @Test
    fun sameAsUbuntu() {
        val all = JSONArray(File(cases, "teamsync.json").readText())
        var checked = 0
        for (index in 0 until all.length()) {
            val case = all.getJSONObject(index)
            when (case.getString("kind")) {
                "hkdf" -> assertEquals(case.getString("result"), Crypto.toHex(Crypto.hkdf(Crypto.hex(case.getString("secret")), case.getString("info").toByteArray())))
                "seal" -> {
                    val box = Crypto.sealText(Crypto.hex(case.getString("key")), case.getString("text"), case.getString("aad"), Crypto.hex(case.getString("nonce")))
                    assertEquals(canonical(case.getJSONObject("result")), canonical(box))
                    assertEquals(case.getString("text"), Crypto.openText(Crypto.hex(case.getString("key")), case.getJSONObject("result"), case.getString("aad")))
                }
                "public" -> assertEquals(case.getString("result"), Crypto.Identity.fromScalar(Crypto.hex(case.getString("scalar"))).public)
                "ecdh" -> assertEquals(case.getString("result"), Crypto.toHex(Crypto.Identity.fromScalar(Crypto.hex(case.getString("scalar"))).agree(case.getString("public"))))
                "verify" -> assertEquals(case.getBoolean("result"), Crypto.verify(case.getString("public"), case.getString("data").toByteArray(), case.getString("signature")))
                "wrap" -> {
                    val wrapped = Crypto.wrapKey(Crypto.hex(case.getString("key")), case.getString("recipient"), case.getString("aad"),
                        Crypto.Identity.fromScalar(Crypto.hex(case.getString("ephemeral"))), Crypto.hex(case.getString("nonce")))
                    assertEquals(canonical(case.getJSONObject("result")), canonical(wrapped))
                    val anna = Crypto.Identity.fromScalar(Crypto.hex(case.getString("recipientScalar")))
                    assertArrayEquals(Crypto.hex(case.getString("key")), Crypto.unwrapKey(anna, case.getJSONObject("result"), case.getString("aad")))
                }
                "fingerprint" -> assertEquals(case.getString("result"), Crypto.fingerprint(case.getString("public")))
                "safety" -> assertEquals(case.getString("result"), Crypto.safetyNumber(case.getString("b"), case.getString("a")))
                "projection" -> assertEquals(canonical(case.getJSONObject("result")),
                    canonical(Team.projection(Store.eventFrom(case.getJSONObject("event")), case.getString("level"))))
                "stream" -> {
                    val stream = stream(case)
                    val ops = case.getJSONArray("ops")
                    for (i in 0 until ops.length()) stream.receive(ops.getJSONObject(i))
                    val result = case.getJSONObject("result")
                    assertEquals(canonical(result.getJSONArray("state")), canonical(JSONArray(stream.state())))
                    assertEquals(canonical(result.getJSONObject("heads")), canonical(JSONObject(stream.heads())))
                }
                "refused" -> {
                    val stream = stream(case)
                    val ops = case.getJSONArray("ops")
                    for (i in 0 until ops.length()) assertFalse(stream.receive(ops.getJSONObject(i)))
                    assertTrue(stream.state().isEmpty())
                }
                else -> continue
            }
            checked++
        }
        assertEquals(all.length(), checked)
    }

    /** Entries written here (Kotlin's own signatures and JSON) – team.py must read them too. */
    @Test
    fun writesForPython() {
        val olaf = Crypto.Identity.fromScalar(Crypto.sha256("lical test olaf".toByteArray()))
        val eve = Crypto.Identity.fromScalar(Crypto.sha256("lical test eve".toByteArray()))
        val key = Crypto.sha256("lical test stream key".toByteArray())
        val stream = Team.Stream("s-olaf", olaf.public, key)
        val ops = JSONArray()
        ops.put(stream.put(olaf, "android", Event("k1", "privat", "Zahnarzt Dr. Weber", false, "2026-10-09T08:30", "2026-10-09T09:15",
            location = "Köln", notes = "geheim"), "details"))
        ops.put(stream.put(olaf, "android", Event("k2", "privat", "Urlaub", true, "2026-10-19", "2026-10-24", absence = "urlaub", deputy = "Jens")))
        ops.put(stream.delete(olaf, "android", "k1"))
        assertEquals(listOf("k2"), stream.state().map { it.getString("id") })
        assertFalse(ops.toString().contains("Zahnarzt") || ops.toString().contains("geheim"))
        try { stream.put(eve, "eve", Event("x", "privat", "x", true, "2026-10-01", "2026-10-02")); throw AssertionError("Eve wrote") } catch (error: Crypto.CryptoError) { }
        File(cases, "../../android/app/build").mkdirs()
        File(cases, "../../android/app/build/team-kotlin-ops.json").writeText(JSONObject().put("owner", olaf.public).put("ops", ops).toString())
    }
}
