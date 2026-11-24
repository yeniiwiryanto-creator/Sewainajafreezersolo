package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Section 7: Data Security & Encryption Utility for protecting sensitive NIK/KTP metadata
 * and Admin PIN access control.
 */
object SecurityCryptoUtils {

    private const val KEYSTORE_ALIAS = "sewain_aja_freezer_soc_aes_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                existing.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback deterministic 256-bit AES key for local JVM / Robolectric unit tests
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("SEWAinAjaFreezerSOC_SoloRaya_SecretKey_2026".toByteArray(Charsets.UTF_8))
            SecretKeySpec(digest, "AES")
        }
    }

    fun encryptSensitiveText(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            plainText
        }
    }

    fun decryptSensitiveText(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            val ivSize = combined[0].toInt()
            if (ivSize <= 0 || combined.size <= 1 + ivSize) return encryptedBase64
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val cipherBytes = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            encryptedBase64
        }
    }

    fun hashAdminPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("SAF_SOC_PIN_$pin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyAdminPin(inputPin: String, expectedHash: String): Boolean {
        return hashAdminPin(inputPin) == expectedHash
    }

    fun encryptBytesAesGcm(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return combined
    }

    fun decryptBytesAesGcm(combinedBytes: ByteArray): ByteArray {
        require(combinedBytes.size > 13) { "Data terenkripsi tidak valid atau korup." }
        val ivSize = combinedBytes[0].toInt() and 0xFF
        require(ivSize in 12..16 && combinedBytes.size > 1 + ivSize) { "Header IV enkripsi tidak valid." }
        val iv = combinedBytes.copyOfRange(1, 1 + ivSize)
        val cipherBytes = combinedBytes.copyOfRange(1 + ivSize, combinedBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherBytes)
    }

    fun encryptEnvelopeJson(plainJsonText: String): String {
        val cipherCombined = encryptBytesAesGcm(plainJsonText.toByteArray(Charsets.UTF_8))
        return org.json.JSONObject().apply {
            put("algorithm", TRANSFORMATION)
            put("keyAlias", KEYSTORE_ALIAS)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(cipherCombined))
            put("sha256Plain", sha256Hex(plainJsonText.toByteArray(Charsets.UTF_8)))
        }.toString(2)
    }

    fun decryptEnvelopeJson(envelopeOrPlainJson: String): String {
        val trimmed = envelopeOrPlainJson.trim()
        if (!trimmed.startsWith("{")) return trimmed
        val obj = org.json.JSONObject(trimmed)
        if (!obj.has("ciphertextBase64")) return trimmed
        val cipherCombined = Base64.getDecoder().decode(obj.getString("ciphertextBase64"))
        val decrypted = String(decryptBytesAesGcm(cipherCombined), Charsets.UTF_8)
        val expectedHash = obj.optString("sha256Plain", "")
        if (expectedHash.isNotBlank()) {
            val actualHash = sha256Hex(decrypted.toByteArray(Charsets.UTF_8))
            require(actualHash.equals(expectedHash, ignoreCase = true)) {
                "Integritas payload terenkripsi tidak cocok (checksum mismatch)."
            }
        }
        return decrypted
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        java.io.BufferedInputStream(java.io.FileInputStream(file)).use { bis ->
            var read = bis.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = bis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Strictly rejects Zip Slip / path traversal entries (../, ..\, leading /, drive letters, null bytes).
     */
    fun sanitizeZipEntryPath(rawName: String, extractionRoot: java.io.File): java.io.File? {
        if (rawName.isBlank() || rawName.contains('\u0000')) return null
        val normalized = rawName.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() ||
            normalized.startsWith("..") ||
            normalized.contains("/../") ||
            normalized.endsWith("/..") ||
            normalized.contains(":")
        ) {
            return null
        }
        val candidate = java.io.File(extractionRoot, normalized)
        val rootCanonical = extractionRoot.canonicalPath
        val candCanonical = candidate.canonicalPath
        val prefix = if (rootCanonical.endsWith(java.io.File.separator)) rootCanonical else rootCanonical + java.io.File.separator
        if (candCanonical != rootCanonical && !candCanonical.startsWith(prefix)) {
            return null
        }
        return candidate
    }
}
