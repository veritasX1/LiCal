package io.github.veritasx1.lical

import org.json.JSONObject
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Building blocks for the team sync (card ac0d1e1a) – byte for byte as linux/lical/crypto.py (shared
 *  vectors in shared/cases/teamsync.json): P-256, ECDH, HKDF-SHA256, AES-256-GCM, ECDSA. Pure JVM. */
object Crypto {
    class CryptoError(message: String) : Exception(message)

    private val random = SecureRandom()

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
    fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }
    fun newKey() = randomBytes(32)
    fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    fun hkdf(secret: ByteArray, info: ByteArray, length: Int = 32, salt: ByteArray? = null): ByteArray {
        val prk = hmac(salt ?: ByteArray(32), secret)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val take = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, take)
            offset += take
            counter++
        }
        return out
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: String) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad.toByteArray())
    }

    fun sealText(key: ByteArray, text: String, aad: String, nonce: ByteArray = randomBytes(12)): JSONObject =
        JSONObject().put("n", b64(nonce)).put("c", b64(gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(text.toByteArray())))

    fun openText(key: ByteArray, box: JSONObject, aad: String): String = try {
        gcm(Cipher.DECRYPT_MODE, key, unb64(box.getString("n")), aad).doFinal(unb64(box.getString("c"))).decodeToString()
    } catch (error: Exception) {
        throw CryptoError("decryption failed")
    }

    // ---- P-256 ----

    private val curve: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun fixed(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun encodePublic(key: ECPublicKey): ByteArray = byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)

    fun decodePublic(bytes: ByteArray): ECPublicKey {
        if (bytes.size != 65 || bytes[0] != 4.toByte()) throw CryptoError("bad public key")
        val point = ECPoint(BigInteger(1, bytes.copyOfRange(1, 33)), BigInteger(1, bytes.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve)) as ECPublicKey
    }

    /** A P-256 key pair: signs a member's changes, receives keys. The private part never leaves the device. */
    class Identity(val private: ECPrivateKey, val publicBytes: ByteArray) {
        val public: String get() = b64(publicBytes)

        fun scalar(): ByteArray = fixed(private.s)

        fun sign(data: ByteArray): String = b64(Signature.getInstance("SHA256withECDSA").apply { initSign(private); update(data) }.sign())

        fun agree(public: String): ByteArray =
            KeyAgreement.getInstance("ECDH").apply { init(private); doPhase(decodePublic(unb64(public)), true) }.generateSecret()

        companion object {
            fun create(): Identity {
                val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
                return Identity(pair.private as ECPrivateKey, encodePublic(pair.public as ECPublicKey))
            }

            /** From the 32-byte secret (the public point computed here – Java cannot derive it). */
            fun fromScalar(raw: ByteArray): Identity {
                val scalar = BigInteger(1, raw)
                val private = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, curve)) as ECPrivateKey
                return Identity(private, multiplyBase(scalar))
            }
        }
    }

    fun verify(public: String, data: ByteArray, signature: String): Boolean = try {
        Signature.getInstance("SHA256withECDSA").apply { initVerify(decodePublic(unb64(public))); update(data) }.verify(unb64(signature))
    } catch (error: Exception) {
        false
    }

    /** ECIES: a 32-byte key for the holder of [recipientPublic] (ephemeral/nonce fixed only in tests). */
    fun wrapKey(key: ByteArray, recipientPublic: String, aad: String, ephemeral: Identity = Identity.create(),
                nonce: ByteArray = randomBytes(12)): JSONObject {
        val wrapping = hkdf(ephemeral.agree(recipientPublic), "lical v1 wrap".toByteArray() + ephemeral.publicBytes + unb64(recipientPublic))
        return JSONObject().put("e", ephemeral.public).put("n", b64(nonce)).put("c", b64(gcm(Cipher.ENCRYPT_MODE, wrapping, nonce, aad).doFinal(key)))
    }

    fun unwrapKey(identity: Identity, wrapped: JSONObject, aad: String): ByteArray = try {
        val ephemeral = wrapped.getString("e")
        val wrapping = hkdf(identity.agree(ephemeral), "lical v1 wrap".toByteArray() + unb64(ephemeral) + identity.publicBytes)
        gcm(Cipher.DECRYPT_MODE, wrapping, unb64(wrapped.getString("n")), aad).doFinal(unb64(wrapped.getString("c")))
    } catch (error: Exception) {
        throw CryptoError("unwrap failed")
    }

    fun fingerprint(public: String): String = toHex(sha256("lical v1 fingerprint".toByteArray() + unb64(public)))

    fun safetyNumber(publicA: String, publicB: String): String {
        val (first, second) = listOf(unb64(publicA), unb64(publicB)).sortedWith { a, b -> compareBytes(a, b) }
        val digest = sha256("lical v1 safety".toByteArray() + first + second)
        val text = BigInteger(1, digest.copyOfRange(0, 12)).mod(BigInteger.TEN.pow(20)).toString().padStart(20, '0')
        return text.chunked(5).joinToString(" ")
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (index in 0 until minOf(a.size, b.size)) {
            val difference = (a[index].toInt() and 0xFF) - (b[index].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return a.size - b.size
    }

    // The public point of a scalar: k·G on P-256 (affine, double-and-add).
    private val P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    private val A: BigInteger = P.subtract(BigInteger.valueOf(3))
    private val N = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)
    private val G = Pair(BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16),
        BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16))

    private fun add(p1: Pair<BigInteger, BigInteger>?, p2: Pair<BigInteger, BigInteger>?): Pair<BigInteger, BigInteger>? {
        if (p1 == null) return p2
        if (p2 == null) return p1
        val (x1, y1) = p1
        val (x2, y2) = p2
        if (x1 == x2 && (y1 + y2).mod(P) == BigInteger.ZERO) return null
        val slope = if (x1 == x2 && y1 == y2) (BigInteger.valueOf(3) * x1 * x1 + A) * (BigInteger.valueOf(2) * y1).modInverse(P)
        else (y2 - y1) * (x2 - x1).mod(P).modInverse(P)
        val s = slope.mod(P)
        val x3 = (s * s - x1 - x2).mod(P)
        return Pair(x3, (s * (x1 - x3) - y1).mod(P))
    }

    private fun multiplyBase(scalar: BigInteger): ByteArray {
        var result: Pair<BigInteger, BigInteger>? = null
        var addend: Pair<BigInteger, BigInteger>? = G
        var k = scalar.mod(N)
        while (k.signum() > 0) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        val point = result ?: throw CryptoError("bad scalar")
        return byteArrayOf(4) + fixed(point.first) + fixed(point.second)
    }
}
