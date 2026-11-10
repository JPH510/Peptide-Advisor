package com.peptideadvisor.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable key held in the Android Keystore, wrapping a
 * plain SharedPreferences blob.
 *
 * Deliberately not androidx.security's EncryptedSharedPreferences — that has been
 * deprecated, and it's the obvious thing to reach for here. This is a dozen lines
 * and does the same job with no dependency.
 */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // IV is generated per encryption and stored alongside; it isn't secret.
        val packed = cipher.iv + ciphertext
        prefs.edit()
            .putString(key, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    fun has(key: String): Boolean = !get(key).isNullOrBlank()

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val API_KEY = "anthropic_api_key"

        private const val PREFS = "peptide_advisor_secrets"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "peptide_advisor_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
