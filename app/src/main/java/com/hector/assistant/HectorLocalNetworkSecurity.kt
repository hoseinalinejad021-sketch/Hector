package com.hector.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security primitives for opt-in local-network work.
 * This does not open a socket or claim that a remote device has been authenticated.
 */
internal class HectorLocalNetworkSecurity(context: Context) {
    private val prefs = context.getSharedPreferences("hector_network_security", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun createInvite(): String {
        val code = (random.nextInt(900000) + 100000)
        val expiresAt = System.currentTimeMillis() + INVITE_TTL_MS
        val saved = prefs.edit()
            .putString("invite_code", code.toString())
            .putLong("invite_expires_at", expiresAt)
            .commit()
        check(saved) { "Could not save the temporary pairing invite." }
        return code.toString()
    }

    fun approveLocalPeer(deviceName: String, code: String): Boolean {
        val normalizedName = deviceName.trim().take(80)
        if (normalizedName.isBlank() || code.length != 6 || code.any { !it.isDigit() }) return false
        if (System.currentTimeMillis() > prefs.getLong("invite_expires_at", 0L)) {
            clearInvite()
            return false
        }
        if (prefs.getString("invite_code", null) != code) return false
        val peers = getPeers()
        val id = normalizedName.lowercase()
        peers.put(id, JSONObject()
            .put("id", id)
            .put("name", normalizedName)
            .put("approved_at_ms", System.currentTimeMillis())
            .put("status", "locally_approved_pending_remote_handshake"))
        val saved = prefs.edit().putString("peers", peers.toString()).commit()
        if (saved) clearInvite()
        return saved
    }

    fun revokePeer(deviceName: String): Boolean {
        val peers = getPeers()
        val id = deviceName.trim().lowercase()
        if (!peers.has(id)) return false
        peers.remove(id)
        return prefs.edit().putString("peers", peers.toString()).commit()
    }

    fun peerSummary(): String {
        val peers = getPeers()
        if (peers.length() == 0) return "No locally approved devices. Remote pairing is not yet implemented."
        val out = StringBuilder("Local device registry (not proof of remote authentication):\n")
        val keys = peers.keys()
        while (keys.hasNext()) {
            val item = peers.optJSONObject(keys.next()) ?: continue
            out.append("• ").append(item.optString("name"))
                .append(" — pending secure remote handshake\n")
        }
        return out.toString()
    }

    /** Encrypts a selected sync payload; the passphrase is never stored. */
    fun encryptSyncPayload(payload: String, passphrase: CharArray): String {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "Payload too large." }
        require(passphrase.size >= 12) { "Use a passphrase with at least 12 characters." }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(ENVELOPE_VERSION.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("version", ENVELOPE_VERSION)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("cipher", "AES-256-GCM")
            .put("iterations", PBKDF2_ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
    }

    fun decryptSyncPayload(envelopeText: String, passphrase: CharArray): String {
        require(envelopeText.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) { "Envelope too large." }
        require(passphrase.size >= 12) { "Use the original passphrase with at least 12 characters." }
        val envelope = JSONObject(envelopeText)
        require(envelope.optString("version") == ENVELOPE_VERSION) { "Unsupported sync format." }
        require(envelope.optString("kdf") == "PBKDF2WithHmacSHA256" &&
            envelope.optString("cipher") == "AES-256-GCM" &&
            envelope.optInt("iterations") == PBKDF2_ITERATIONS) { "Unsupported crypto parameters." }
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(salt.size == 16 && iv.size == 12 && ciphertext.size <= MAX_PAYLOAD_BYTES + 32) {
            "Invalid encrypted payload."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(ENVELOPE_VERSION.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun getPeers(): JSONObject = try {
        JSONObject(prefs.getString("peers", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun clearInvite() {
        prefs.edit().remove("invite_code").remove("invite_expires_at").commit()
    }

    companion object {
        private const val ENVELOPE_VERSION = "hector-sync-v1"
        private const val PBKDF2_ITERATIONS = 210_000
        private const val INVITE_TTL_MS = 5 * 60 * 1000L
        private const val MAX_PAYLOAD_BYTES = 256 * 1024
        private const val MAX_ENVELOPE_BYTES = 400 * 1024
    }
}
