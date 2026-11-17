package com.fgjaee.autopilot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API keys are encrypted in Android Keystore; never committed or logged. */
class ProviderSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("provider", Context.MODE_PRIVATE)
    private val alias = "AutoPilotProviderKey"

    val endpoint: String get() = prefs.getString("endpoint", "https://api.openai.com/v1")!!
    val model: String get() = prefs.getString("model", "gpt-4.1-mini")!!

    fun save(endpoint: String, model: String, apiKey: String) {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            "Endpoint must start with http:// or https://"
        }
        require(model.isNotBlank()) { "Model cannot be empty" }
        val edit = prefs.edit().putString("endpoint", endpoint.trim().trimEnd('/'))
            .putString("model", model.trim())
        if (apiKey.isNotEmpty()) edit.putString("api_key", encrypt(apiKey.trim()))
        edit.apply()
    }

    fun clearApiKey() { prefs.edit().remove("api_key").apply() }

    fun apiKey(): String {
        val blob = prefs.getString("api_key", null) ?: return ""
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val prior = store.getKey(alias, null)
        if (prior is SecretKey) return prior
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
