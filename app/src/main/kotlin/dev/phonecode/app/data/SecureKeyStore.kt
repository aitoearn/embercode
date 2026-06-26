package dev.phonecode.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Per-provider API keys, stored encrypted in the Android Keystore. Never leaves the device. */
class SecureKeyStore(context: Context) {
    private val prefs: SharedPreferences

    /**
     * True when the device Keystore was unavailable and keys fell back to a PLAINTEXT private file.
     * In that state keys are NOT encrypted and live in a separate file (so previously-encrypted keys
     * are not visible and must be re-entered). Exposed so the UI can warn the user instead of failing
     * silently - callers should surface it on the providers screen.
     */
    val usingPlaintextFallback: Boolean

    init {
        val (p, plaintext) = createPrefs(context)
        prefs = p
        usingPlaintextFallback = plaintext
    }

    fun get(providerId: String): String? = prefs.getString(providerId, null)

    fun put(providerId: String, key: String) {
        prefs.edit().apply { if (key.isBlank()) remove(providerId) else putString(providerId, key) }.apply()
    }

    private companion object {
        private const val FILE = "phonecode_provider_keys"

        /**
         * Encrypted prefs with two recovery tiers: a corrupt store is dropped and recreated, and a
         * device whose Keystore is entirely broken/unavailable falls back to a plain private file -
         * an app that can't launch protects nothing. Returns (prefs, usedPlaintextFallback).
         */
        fun createPrefs(context: Context): Pair<SharedPreferences, Boolean> = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = try {
                build(context, masterKey)
            } catch (e: Exception) {
                // Standard recovery for a corrupt encrypted store (e.g. after a backup/restore or
                // keystore reset): drop the file and recreate, rather than crashing on launch.
                context.deleteSharedPreferences(FILE)
                build(context, masterKey)
            }
            prefs to false
        } catch (e: Exception) {
            context.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE) to true
        }

        private fun build(context: Context, masterKey: MasterKey) = EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
