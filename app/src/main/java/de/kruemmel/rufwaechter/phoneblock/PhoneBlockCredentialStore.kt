package de.kruemmel.rufwaechter.phoneblock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PhoneBlockCredentialStore(context: Context) {
    private val credentialFile = AtomicFile(context.noBackupFilesDir.resolve(FILE_NAME))

    @Synchronized
    fun save(credentials: PhoneBlockCredentials) {
        require(credentials.isValid()) { "Unvollständige PhoneBlock-Zugangsdaten" }
        val plaintext = buildString {
            append(FORMAT_VERSION).append('\n')
            append(credentials.mode.name).append('\n')
            append(credentials.username).append('\n')
            append(credentials.secret)
        }.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        plaintext.fill(0)

        val output = credentialFile.startWrite()
        try {
            val data = DataOutputStream(output)
            data.writeInt(FORMAT_VERSION)
            data.writeInt(cipher.iv.size)
            data.write(cipher.iv)
            data.writeInt(ciphertext.size)
            data.write(ciphertext)
            data.flush()
            credentialFile.finishWrite(output)
        } catch (error: Exception) {
            credentialFile.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun load(): PhoneBlockCredentials? = runCatching {
        if (!credentialFile.baseFile.exists()) return null
        val (iv, ciphertext) = credentialFile.openRead().use { input ->
            DataInputStream(input).use { data ->
                require(data.readInt() == FORMAT_VERSION)
                val ivLength = data.readInt()
                require(ivLength in 12..32)
                val iv = ByteArray(ivLength).also(data::readFully)
                val cipherLength = data.readInt()
                require(cipherLength in 1..MAX_CIPHERTEXT_BYTES)
                val ciphertext = ByteArray(cipherLength).also(data::readFully)
                require(data.read() == -1)
                iv to ciphertext
            }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)
        try {
            val parts = plaintext.toString(Charsets.UTF_8).split('\n', limit = 4)
            require(parts.size == 4 && parts[0].toInt() == FORMAT_VERSION)
            PhoneBlockCredentials(
                mode = PhoneBlockAuthMode.valueOf(parts[1]),
                username = parts[2],
                secret = parts[3],
            ).takeIf(PhoneBlockCredentials::isValid)
        } finally {
            plaintext.fill(0)
        }
    }.getOrNull()

    @Synchronized
    fun clear() {
        credentialFile.delete()
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    fun isConfigured(): Boolean = load() != null

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("PhoneBlock-Schlüssel fehlt")
    }

    private fun getOrCreateKey(): SecretKey {
        runCatching { getExistingKey() }.getOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val FILE_NAME = "phoneblock-credentials.bin"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "rufwaechter.phoneblock.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_CIPHERTEXT_BYTES = 16 * 1024
    }
}
