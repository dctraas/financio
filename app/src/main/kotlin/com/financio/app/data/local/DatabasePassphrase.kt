package com.financio.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates the SQLCipher database passphrase once, then stores it encrypted-at-rest under a
 * key that never leaves the Android Keystore — the passphrase itself is never held as plain
 * text anywhere except briefly in memory while opening the database. Nothing here is derived
 * from anything the user types; there is no "database password" to forget.
 */
class DatabasePassphraseProvider(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getOrCreatePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_CIPHERTEXT, null)
        val iv = prefs.getString(KEY_IV, null)
        return if (stored != null && iv != null) {
            decrypt(Base64.decode(stored, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP))
        } else {
            generateAndStore()
        }
    }

    private fun generateAndStore(): ByteArray {
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val ciphertext = cipher.doFinal(passphrase)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey() =
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // No setUserAuthenticationRequired(true): the biometric prompt gates the app's
                    // UI, not this key, so an interrupted import job can still finish in the background.
                    .build()
            )
            generator.generateKey()
        }

    companion object {
        private const val PREFS_NAME = "financio_db_key"
        private const val KEY_CIPHERTEXT = "passphrase_ciphertext"
        private const val KEY_IV = "passphrase_iv"
        private const val KEYSTORE_ALIAS = "financio_db_passphrase_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
