package com.hector.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small local encrypted-memory store. AES-256/GCM key is generated and held by Android Keystore.
 * Only ciphertext and nonce are written to app-private preferences. Android backups are disabled
 * in the manifest. This store does not itself authenticate the caller or unlock protected actions.
 */
class SecureMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hector_secure_memory_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun write(name: String, value: ByteArray) {
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(value)
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        try {
            System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
            System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
            check(prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
                "Could not persist encrypted memory"
            }
        } finally {
            packed.fill(0)
            encrypted.fill(0)
        }
    }

    @Synchronized
    fun read(name: String): ByteArray? {
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
        val encoded = prefs.getString(name, null) ?: return null
        val packed = try { Base64.decode(encoded, Base64.NO_WRAP) } catch (_: IllegalArgumentException) { return null }
        if (packed.size <= NONCE_BYTES + TAG_BYTES) {
            packed.fill(0)
            return null
        }
        val nonce = packed.copyOfRange(0, NONCE_BYTES)
        val ciphertext = packed.copyOfRange(NONCE_BYTES, packed.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, nonce))
            cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        } finally {
            packed.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
        }
    }

    @Synchronized
    fun delete(name: String) {
        prefs.edit().remove(name).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "hector_secure_memory_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
    }
}
