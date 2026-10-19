package dev.retrobit.agent.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Encrypted key/value vault. Values are encrypted with an AES-GCM key held in
 * the Android Keystore, then stored as Base64 in private SharedPreferences.
 * If the Keystore is unavailable (rare, e.g. broken emulator images) we fall
 * back to private prefs so the app remains usable, clearly flagged to the UI.
 */
class SecretStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("retrobit_secrets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val entriesKey = "entries_v1"
    private val requestsKey = "requests_v1"
    private val alias = "retrobit_vault_key"

    var keystoreAvailable: Boolean = true
        private set

    private fun secretKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
    }.onFailure { keystoreAvailable = false }.getOrNull()

    private fun encrypt(plain: String): String {
        val key = secretKey() ?: return "plain:$plain"
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + encrypted
            "enc:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            keystoreAvailable = false
            "plain:$plain"
        }
    }

    private fun decrypt(stored: String): String {
        if (stored.startsWith("plain:")) return stored.removePrefix("plain:")
        if (!stored.startsWith("enc:")) return stored
        val key = secretKey() ?: return ""
        return runCatching {
            val combined = Base64.decode(stored.removePrefix("enc:"), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val payload = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrElse { "" }
    }

    fun secrets(): List<SecretEntry> {
        val raw = prefs.getString(entriesKey, null) ?: return emptyList()
        val decoded = runCatching {
            json.decodeFromString(ListSerializer(StoredSecret.serializer()), raw)
        }.getOrElse { return emptyList() }
        return decoded.map { SecretEntry(it.key, decrypt(it.value), it.note, it.expose, it.updatedAt) }
    }

    fun get(key: String): String? = secrets().firstOrNull { it.key == key }?.value

    fun put(entry: SecretEntry) {
        val current = secrets().filterNot { it.key == entry.key } + entry
        persist(current)
    }

    fun remove(key: String) {
        persist(secrets().filterNot { it.key == key })
        removeRequest(key)
    }

    private fun persist(entries: List<SecretEntry>) {
        val stored = entries.map {
            StoredSecret(it.key, encrypt(it.value), it.note, it.exposeToPreview, it.updatedAt)
        }
        prefs.edit()
            .putString(entriesKey, json.encodeToString(ListSerializer(StoredSecret.serializer()), stored))
            .apply()
    }

    fun requests(): List<SecretRequest> {
        val raw = prefs.getString(requestsKey, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SecretRequest.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun request(key: String, description: String) {
        if (requests().any { it.key == key }) return
        val next = requests() + SecretRequest(key, description)
        prefs.edit().putString(requestsKey, json.encodeToString(ListSerializer(SecretRequest.serializer()), next)).apply()
    }

    fun removeRequest(key: String) {
        val next = requests().filterNot { it.key == key }
        prefs.edit().putString(requestsKey, json.encodeToString(ListSerializer(SecretRequest.serializer()), next)).apply()
    }

    /** Secrets explicitly marked "expose to preview" — injected into window.__RETROBIT_ENV__. */
    fun exposedForPreview(): Map<String, String> =
        secrets().filter { it.exposeToPreview }.associate { it.key to it.value }

    @kotlinx.serialization.Serializable
    private data class StoredSecret(
        val key: String,
        val value: String,
        val note: String = "",
        val expose: Boolean = false,
        val updatedAt: Long = 0,
    )
}
