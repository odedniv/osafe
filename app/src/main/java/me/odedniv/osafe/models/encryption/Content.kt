package me.odedniv.osafe.models.encryption

import me.odedniv.osafe.models.random
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Content(
        val cipherType: CipherType,
        val digestType: DigestType,
        val iv: ByteArray,
        val digest: ByteArray,
        val content: ByteArray) {

    enum class CipherType {
        AES_128;

        companion object {
            val TRANSFORMATIONS = hashMapOf(
                    AES_128 to "AES/CBC/PKCS5Padding"
            )
            val ALGORITHMS = hashMapOf(
                    AES_128 to "AES"
            )
        }

        val transformation by lazy { TRANSFORMATIONS[this]!! }
        val algorithm by lazy { ALGORITHMS[this]!! }
    }

    enum class DigestType {
        SHA_1;

        companion object {
            val ALGORITHMS = hashMapOf(
                    SHA_1 to "SHA-1"
            )
        }

        val algorithm by lazy { ALGORITHMS[this]!! }
    }

    companion object {
        private val DEFAULT_CIPHER_TYPE = CipherType.AES_128
        private val DEFAULT_DIGEST_TYPE = DigestType.SHA_1

        fun encrypt(key: ByteArray, content: ByteArray): Content {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_TYPE.transformation)
            val iv = random(cipher.blockSize)
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key.copyOf(cipher.blockSize), DEFAULT_CIPHER_TYPE.algorithm),
                    IvParameterSpec(iv)
            )

            return Content(
                    cipherType = DEFAULT_CIPHER_TYPE,
                    digestType = DEFAULT_DIGEST_TYPE,
                    iv = iv,
                    digest = MessageDigest.getInstance(DEFAULT_DIGEST_TYPE.algorithm).digest(content),
                    content = cipher.doFinal(content)
            )
        }
    }

    fun decrypt(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherType.transformation)
        cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key.copyOf(cipher.blockSize), cipherType.algorithm),
                IvParameterSpec(iv)
        )

        val content = cipher.doFinal(content)
        if (!digest.contentEquals(MessageDigest.getInstance(digestType.algorithm).digest(content))) {
            throw RuntimeException("Signature verification failed")
        }
        return content
    }
}