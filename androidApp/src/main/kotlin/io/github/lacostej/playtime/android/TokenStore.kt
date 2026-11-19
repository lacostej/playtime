package io.github.lacostej.playtime.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Nintendo session token, encrypted with a key that never leaves the Android Keystore.
 * Stored in noBackupFilesDir and excluded from backups (see data_extraction_rules.xml).
 */
class TokenStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "session.bin")

    fun save(sessionToken: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        file.writeBytes(byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(sessionToken.encodeToByteArray()))
    }

    /** Null when there is no saved login, or it can't be decrypted (e.g. the key was reset). */
    fun load(): String? {
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            val ivSize = bytes[0].toInt()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, ivSize))
            cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize).decodeToString()
        }.getOrNull()
    }

    fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "nintendo-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
